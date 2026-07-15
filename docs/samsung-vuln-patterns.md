# Samsung OEM 应用漏洞模式分析（140 个真实漏洞）

> 基于 Oversecured 对三星预装应用的安全审计（140 个漏洞，总赏金 $163,475）。
> 本文档整理高频漏洞模式、技术细节和防护建议，作为移动安全知识库参考。

## 一、漏洞模式分类总览

| 模式 | 出现次数 | 典型漏洞编号 | 最高赏金 |
|------|---------|-------------|---------|
| 隐式 Intent/广播拦截 | ~40+ | 039, 052-054, 074-076 | $13,770 |
| Intent 重定向 / LaunchAnyWhere | ~10 | 001, 005, 017, 045 | $7,230 |
| Provider 代理跳板（arbitrary provider access） | ~12 | 013, 024, 034, 043, 056 | $6,340 |
| Provider 路径穿越 / 任意文件读写 | ~12 | 002, 009, 040, 126 | $10,310 |
| AIDL Service 授权绕过 / SQLi | ~5 | 026, 038 | $810 |
| WebView 漏洞（XSS / URL 注入） | ~5 | 025, 007, 089 | $980 |
| 原生库加载 → RCE | 1 | 015 | $3,240 |
| 硬编码密钥 | 2 | 113, 135 | $660 |
| 框架级权限降级 | ~30 | 061-082, 097-107 | $3,480 |
| 敏感数据日志泄露 | ~15 | 082, 110, 112, 138 | $460 |

---

## 二、核心漏洞模式详解

### 模式 1：Provider 代理跳板（Arbitrary Provider Access）

**概念**：攻击者无法直接查询目标 App 的私有 Provider（因为没权限），但可以通过一个已导出的 Activity/Service 作为"跳板"——该组件在 App 身份下执行 `ContentResolver.query(attackerUri)`，从而访问 App 才能访问的 Provider。

**真实案例**：
- Samsung Pay（#056，$6,340）：导出 Activity 接收 caller 的 `uri` extra，在其身份下查询任意 Provider
- Samsung Email（#049）、DeX（#027）、Camera（#059）等 12 个 App 都有此问题

**漏洞代码模式**：
```java
// 导出 Activity onCreate()
String uri = getIntent().getStringExtra("uri");
// VULNERABLE: 在 App 身份下查询攻击者指定的 URI
Cursor c = getContentResolver().query(Uri.parse(uri), ...);
```

**利用步骤**：
1. 发现导出 Activity/Service 接收 URI 参数
2. 构造目标 Provider 的 content:// URI
3. 通过该组件查询，数据在 App 身份下返回
4. 通过 logcat / setResult / TextView 读取结果

**防护**：
- 不要在导出组件中接受 caller 控制的 URI 并做 ContentResolver 查询
- 如果必须接受 URI，用 `ContentResolver.validateCallAttributes` 或 `Binder.getCallingUid()` 验证调用者
- 用 `<grantUriPermission>` 白名单限制可访问的 URI

---

### 模式 2：AIDL/Messenger Service 授权绕过

**概念**：导出的 Service 对调用者做权限校验，但校验逻辑信任 caller 传入的 `packageName` 字符串，而非 `Binder.getCallingUid()` 返回的真实 UID。

**真实案例**：
- Quick Share（#038，CVE-2022-30745，$810）：`BinderManager.getPackageNameForSignature()` 优先使用 `ExchangeData.packageName`（caller 控制）而非安全 UID
- Samsung Billing SQLi（#026，CVE-2022-36839）：AIDL 方法直接拼接 SQL

**漏洞代码模式**：
```java
// Messenger handler
case MSG_ADMIN_QUERY:
    String pkg = msg.getData().getString("packageName");  // caller 传入！
    if (whitelist.contains(pkg)) {  // 信任了 caller 的字段
        // 执行特权操作
    }
    // 正确做法: Binder.getCallingUid() → PackageManager.getNameForUid()
```

**利用步骤**：
1. bind 到导出 Service
2. 构造 Messenger message，`packageName` extra 填白名单内的包名
3. Service 信任该字段，执行特权操作
4. 攻击者获取本不该有的数据/功能

**防护**：
- 永远用 `Binder.getCallingUid()` 获取真实调用者
- 不要信任 caller 传入的身份字段
- Signature 级权限保护敏感 Service

---

### 模式 3：Provider 路径穿越（Path Traversal via openFile）

**概念**：导出 Provider 的 `openFile()` 用 `uri.getLastPathSegment()` 拼接路径，不过滤 `../`。

**真实案例**：
- FactoryCamera（#002，CVE-2022-39858，$10,310）：广播接收器拼接 `/sys/class/camera/flash/` + arg4，无过滤
- Contacts Storage（#009，CVE-2022-33690）：CallLogProvider `openFile()` + `getLastPathSegment()`
- Cameralyzer（#032，CVE-2022-36832）：本地 HTTP 服务器 `/DCIM/` + `../`

**漏洞代码模式**：
```java
public ParcelFileDescriptor openFile(Uri uri, String mode) {
    String seg = uri.getLastPathSegment();  // 可能含 ../
    File f = new File(rootDir, seg);        // VULNERABLE: 无 canonical path 检查
    return ParcelFileDescriptor.open(f, ...);
}
```

**利用**：
```
content://com.example.provider/data/..%2F..%2Fdatabases%2Fsecret.db
```

**防护**：
```java
File f = new File(rootDir, seg);
if (!f.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
    throw new SecurityException("path traversal");
}
```

---

### 模式 4：隐式广播/Intent 敏感数据泄露

**概念**：系统 App 通过隐式广播或隐式 Activity 传递敏感数据（token、电话号码、设备信息），任何注册了匹配 Intent Filter 的恶意 App 都能拦截。

**真实案例**：
- SmartThings（#054，$13,770）：~50 个隐式广播携带设备信息、token、用户活动
- Samsung Cloud（#039，$4,300）：隐式 Activity 携带 auth token + 信用卡尾号
- Framework（#075）：`READ_PHONE_STATE` 保护的广播携带需要 `READ_CALL_LOG` 的电话号码

**漏洞代码模式**：
```java
// 发送方 — 隐式广播带敏感数据
Intent i = new Intent("com.samsung.DEVICE_UPDATED");
i.putExtra("auth_token", token);       // VULNERABLE: 隐式广播
i.putExtra("phone_number", number);
sendBroadcast(i);

// 攻击方 — 高优先级接收器拦截
// manifest: <receiver android:exported="true" android:priority="999">
//   <intent-filter><action android:name="com.samsung.DEVICE_UPDATED"/></intent-filter>
```

**防护**：
- 用显式 Intent（`setPackage()`）发送敏感广播
- 用 signature 级自定义权限保护
- 不要在 Intent extra 里放 token/密码等；改用 ContentProvider + 权限

---

### 模式 5：Intent 重定向 / LaunchAnyWhere

**概念**：导出组件从 Intent extra 中取出一个嵌套的 Intent 对象，直接 `startActivity()` / `sendBroadcast()`，攻击者借此访问非导出组件。

**真实案例**：
- Settings（#001，CVE-2022-28781，$4,850）：system UID 的 Activity 转发嵌套 Intent → LaunchAnyWhere
- Galaxy Store（#005，$7,230）：`install_complete` 广播接收器提取嵌套 Intent

**漏洞代码模式**：
```java
// 导出 Receiver
Intent inner = (Intent) getIntent().getParcelableExtra("android.intent.extra.INTENT");
startActivity(inner);  // VULNERABLE: 转发攻击者控制的 Intent
```

**防护**：
- 从 extra 取出的 Intent 必须校验目标组件是否在白名单内
- 剥离 `FLAG_GRANT_*` 权限标志
- Android 14 限制了隐式 Intent 转发，但显式 Intent 仍可被滥用

---

### 模式 6：硬编码密钥

**真实案例**：
- NFC（#113，CVE-2023-21426）：AES key = `MD5("PIN")` 的 hex，用于加密 NFC 卡模拟 PIN
- Weather（#135）：硬编码 AES key 加密含敏感数据的日志

**防护**：
- 密钥存 Android Keystore，用 `setUserAuthenticationRequired` 绑定
- 永远不要硬编码密钥；即使存在 native .so 里也可被逆向

---

## 三、CVE / SVE 索引

| 漏洞编号 | CVE | SVE | 模式 |
|---------|-----|-----|------|
| 001 | CVE-2022-28781 | SVE-2022-0285 | Intent 重定向 |
| 002 | CVE-2022-39858 | SVE-2022-0311 | 路径穿越 |
| 005 | CVE-2022-33708/09/10/30754 | SVE-2022-0352 | 广播 Intent 重定向 |
| 009 | CVE-2022-33690 | SVE-2022-0687 | Provider 路径穿越 |
| 015 | CVE-2022-39880 | SVE-2022-0746 | 原生库 RCE |
| 025 | CVE-2022-39890 | SVE-2022-0776 | WebView Universal XSS |
| 026 | CVE-2022-36839 | SVE-2022-0783 | AIDL SQLi |
| 032 | CVE-2022-36832 | SVE-2022-0807 | HTTP 路径穿越 |
| 038 | CVE-2022-30745 | SVE-2022-0866 | 授权绕过 |
| 039 | CVE-2022-33713 | SVE-2022-0867 | 隐式 Intent 泄露 |
| 054 | CVE-2022-39865..71 | SVE-2022-0968 | 隐式广播泄露 |
| 075 | CVE-2022-39903 | SVE-2022-2136 | 权限降级 |
| 113 | CVE-2023-21426 | SVE-2022-2278 | 硬编码密钥 |

---

## 四、审计方法论

1. **攻击面枚举**：列出所有 `exported=true` 的 Activity/Service/Provider/Receiver
2. **数据流追踪**：从 Intent extra → sink（startActivity / ContentResolver.query / File.open / System.load）
3. **权限模型检查**：导出组件是否有签名级权限保护？校验是否信任 caller 字段？
4. **Intent 转发检测**：是否有 `getParcelableExtra("...Intent")` → `startActivity()` 模式
5. **Provider openFile 检查**：是否有 `getLastPathSegment()` → `new File()` 无 canonical 检查
6. **隐式广播审计**：`sendBroadcast` 是否携带敏感 extra？是否有 `setPackage` 或权限保护
7. **静态扫描 + 动态验证**：用 Oversecured/MobSF/drozer 自动化，再手动验证 PoC

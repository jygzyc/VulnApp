package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 20 exploit chains covering <b>every</b> Android app-layer vulnerability type.
 *
 * <p>Sourced from the IMA mobile-security knowledge base (Android App漏洞之战 20
 * articles + Android 组件逻辑漏洞漫谈 25 articles) and the decx composite-chain
 * framework. Difficulty is strictly ascending: #1 is the easiest, #20 the hardest.</p>
 *
 * <p>Each chain has a {@code category} matching one of the standard vuln types and
 * an {@code objective} describing the real data an attacker obtains.</p>
 */
public final class Chains {

    private Chains() {}

    public enum Diff { EASY, MEDIUM, HARD, INSANE }

    public static final class Chain {
        public final int id;
        public final String name;
        public final Diff  difficulty;
        public final String category;
        public final String objective;
        /** Brief how-to (what the attacker does). */
        public final String hint;

        public Chain(int id, String name, Diff difficulty, String category,
                     String objective, String hint) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.category = category;
            this.objective = objective;
            this.hint = hint;
        }
    }

    public static List<Chain> all() {
        List<Chain> c = new ArrayList<>();

        // ===== EASY (1-6): single-component, direct access =====

        c.add(new Chain(1, "导出 Activity 泄露余额",
                Diff.EASY, "Activity",
                "直接看到钱包余额 ¥12,800",
                "启动导出的 AccountActivity,无需登录"));

        c.add(new Chain(2, "SQL 注入读取通讯录",
                Diff.EASY, "Content Provider",
                "dump 全部联系人:姓名/手机/身份证号/邮箱",
                "对 ContactsProvider 注入 ' OR '1'='1"));

        c.add(new Chain(3, "路径穿越读取数据库",
                Diff.EASY, "Content Provider",
                "读取 wallet.db → 支付密码 / PAY_TOKEN",
                "FileBackupProvider 的 ../ 穿越"));

        c.add(new Chain(4, "明文存储泄露密钥",
                Diff.EASY, "存储安全",
                "从 SharedPreferences 读取 JWT + AppSecret",
                "adb backup 或 FileBackupProvider 读 mochat_prefs.xml"));

        c.add(new Chain(5, "敏感信息日志泄露",
                Diff.EASY, "信息泄露",
                "从 logcat 读取余额、token、调试信息",
                "adb logcat 抓取 debuggable App 的 Log 输出"));

        c.add(new Chain(6, "不安全配置(allowBackup)",
                Diff.EASY, "安全配置",
                "完整备份 App 数据到 PC",
                "adb backup 提取全部 DB + prefs"));

        // ===== MEDIUM (7-12): cross-component, IPC =====

        c.add(new Chain(7, "WebView JS Bridge RCE",
                Diff.MEDIUM, "WebView",
                "JS 调用 exec() 执行任意命令 / getToken() 偷 JWT",
                "deeplink mochat://mall/open → MoChat.exec('id')"));

        c.add(new Chain(8, "WebView 文件同源绕过",
                Diff.MEDIUM, "WebView",
                "通过 file:// 读取 App 私有文件",
                "setAllowFileAccessFromFileURLs(true) + XHR"));

        c.add(new Chain(9, "导出 Service Messenger 滥用",
                Diff.MEDIUM, "Service",
                "通过 WalletService 发起未授权转账",
                "bindService → Messenger.send(MSG_PAY)"));

        c.add(new Chain(10, "广播 Token 拦截",
                Diff.MEDIUM, "Broadcast Receiver",
                "注册接收器拦截 TOKEN_BROADCAST → JWT",
                "Manifest 声明 receiver 监听 token 广播"));

        c.add(new Chain(11, "有序广播劫持",
                Diff.MEDIUM, "Broadcast Receiver",
                "高优先级接收器抢截并篡改广播内容",
                "priority=MAX + abortBroadcast()"));

        c.add(new Chain(12, "Fragment 注入",
                Diff.MEDIUM, "Fragment",
                "加载任意 Fragment 类,绕过界面权限",
                "Intent extra 传入 Fragment 类名 → 反射实例化"));

        // ===== HARD (13-17): crypto, intent composition, auth =====

        c.add(new Chain(13, "Intent 重定向",
                Diff.HARD, "Intent",
                "穿透到非导出组件,执行受保护操作",
                "ShareReceiver 取出嵌套 Intent → startActivity"));

        c.add(new Chain(14, "PendingIntent 劫持",
                Diff.HARD, "PendingIntent",
                "篡改 mutable PendingIntent 的 URI extra",
                "bind PushService → MSG_GET_PENDING → mutate"));

        c.add(new Chain(15, "Messenger 解密 Oracle",
                Diff.HARD, "Service",
                "逆向 XOR → 解密银行卡号",
                "bind WalletService → MSG_DECRYPT 解密 BLOB"));

        c.add(new Chain(16, "Parcel 序列化错位",
                Diff.HARD, "Parcel",
                "伪造 paid=true → 免费下单",
                "OrderService PaymentOrder write/read 不匹配"));

        c.add(new Chain(17, "验证码本地校验绕过",
                Diff.HARD, "验证码",
                "绕过短信验证码,任意修改密码",
                "Frida hook 或 patch 本地校验逻辑"));

        // ===== INSANE (18-20): full reverse + composition =====

        c.add(new Chain(18, "AIDL 授权绕过 + 密钥提取",
                Diff.INSANE, "Service",
                "伪造包名 → dump 密钥表 → 拿到 PAY_TOKEN",
                "MSG_ADMIN_QUERY 传 packageName=com.mochat.app.trusted"));

        c.add(new Chain(19, "ZipSlip + 动态加载 RCE",
                Diff.INSANE, "插件化",
                "写入恶意 .so/.dex → System.load/DexClassLoader",
                "OrderService MSG_INSTALL zip 带 ../../ 路径穿越"));

        c.add(new Chain(20, "Native 加密逆向 + 反调试绕过",
                Diff.INSANE, "加密/反调试",
                "逆向 libmochat.so XOR + OBFUSCATE → 恢复全部密文",
                "Ghidra/IDA 逆向 + Frida bypass anti-debug"));

        return Collections.unmodifiableList(c);
    }
}

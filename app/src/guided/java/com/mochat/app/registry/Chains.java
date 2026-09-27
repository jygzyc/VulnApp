package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 20 exploit chains covering <b>every</b> Android app-layer vulnerability type,
 * cross-verified against two feishu wiki documents:
 * <ul>
 *   <li>「Android WebView 安全分析」— 12 WebView vuln subclasses</li>
 *   <li>「Android Service 组件安全攻防」— 8 Service vuln subclasses</li>
 * </ul>
 * plus the IMA mobile-security knowledge base (45 articles) and the decx
 * composite-chain framework.
 *
 * <p>Difficulty is strictly ascending: #1 EASY → #20 INSANE.</p>
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

        // ===== EASY (1-5) =====

        c.add(new Chain(1, "导出 Activity 泄露余额",
                Diff.EASY, "Activity",
                "直接看到钱包余额 ¥12,800",
                "启动导出的 AccountActivity,无需登录"));

        c.add(new Chain(2, "SQL 注入读取通讯录",
                Diff.EASY, "Content Provider",
                "dump 全部联系人:姓名/手机/身份证号",
                "ContactsProvider 注入 ' OR '1'='1"));

        c.add(new Chain(3, "路径穿越读取数据库",
                Diff.EASY, "Content Provider",
                "读取 wallet.db → 支付密码/PAY_TOKEN",
                "FileBackupProvider ../ 穿越"));

        c.add(new Chain(4, "明文存储泄露密钥",
                Diff.EASY, "存储安全",
                "从 SharedPreferences 读取 JWT + AppSecret",
                "adb backup 或路径穿越读 mochat_prefs.xml"));

        c.add(new Chain(5, "敏感信息日志泄露",
                Diff.EASY, "信息泄露",
                "logcat 读取余额/token/调试信息",
                "adb logcat 抓取 debuggable App 日志"));

        // ===== MEDIUM (6-12) =====

        c.add(new Chain(6, "WebView JS Bridge RCE",
                Diff.MEDIUM, "WebView-RCE",
                "JS 调 exec() 执行命令 / getToken() 偷 JWT",
                "deeplink → MoChat.exec('id')"));

        c.add(new Chain(7, "WebView 文件同源绕过",
                Diff.MEDIUM, "WebView-File",
                "file:// 读取 App 私有文件(应用克隆)",
                "setAllowFileAccessFromFileURLs + XHR"));

        c.add(new Chain(8, "WebView MITM (SSL Bypass)",
                Diff.MEDIUM, "WebView-MITM",
                "忽略 SSL 错误 → 中间人注入恶意 JS",
                "onReceivedSslError handler.proceed()"));

        c.add(new Chain(9, "WebView URL 校验绕过",
                Diff.MEDIUM, "WebView-URL",
                "绕过白名单加载攻击者页面",
                "String.contains() / 后缀匹配缺陷"));

        c.add(new Chain(10, "导出 Service 消息伪造",
                Diff.MEDIUM, "Service",
                "通过 Messenger 发起未授权转账",
                "bindService → Messenger.send(MSG_PAY)"));

        c.add(new Chain(11, "广播 Token 拦截",
                Diff.MEDIUM, "Broadcast Receiver",
                "注册接收器拦截 TOKEN_BROADCAST → JWT",
                "Manifest 声明 receiver 监听广播"));

        c.add(new Chain(12, "Fragment 注入",
                Diff.MEDIUM, "Fragment",
                "加载任意 Fragment 绕过界面权限",
                "Intent extra 传 Fragment 类名 → 反射加载"));

        // ===== HARD (13-17) =====

        c.add(new Chain(13, "Intent 重定向 (intent:// 注入)",
                Diff.HARD, "Intent",
                "穿透到非导出组件执行受保护操作",
                "ShareReceiver 取嵌套 Intent → startActivity"));

        c.add(new Chain(14, "PendingIntent 劫持",
                Diff.HARD, "PendingIntent",
                "篡改 mutable PendingIntent URI extra",
                "bind PushService → mutate file_uri"));

        c.add(new Chain(15, "Messenger 解密 Oracle",
                Diff.HARD, "Service",
                "逆向 XOR → 解密银行卡号",
                "bind WalletService → MSG_DECRYPT"));

        c.add(new Chain(16, "Parcel 序列化错位",
                Diff.HARD, "Parcel",
                "伪造 paid=true → 免费下单",
                "OrderService PaymentOrder write/read 不匹配"));

        c.add(new Chain(17, "验证码本地校验绕过",
                Diff.HARD, "验证码",
                "绕过短信验证码修改密码",
                "Frida hook 或 patch 本地校验逻辑"));

        // ===== INSANE (18-20) =====

        c.add(new Chain(18, "WebView 竞态攻击 (Symlink TOCTOU)",
                Diff.INSANE, "WebView-Race",
                "符号链接竞态窃取任意文件",
                "延时替换符号链接 + WebView file:// 读取"));

        c.add(new Chain(19, "ZipSlip + 动态加载 RCE",
                Diff.INSANE, "插件化",
                "写入恶意 .so/.dex → System.load",
                "OrderService MSG_INSTALL zip 带 ../../"));

        c.add(new Chain(20, "Native 加密逆向 + 反调试",
                Diff.INSANE, "加密/反调试",
                "逆向 libmochat.so XOR + OBFUSCATE",
                "Ghidra 逆向 + Frida bypass anti-debug"));

        return Collections.unmodifiableList(c);
    }
}

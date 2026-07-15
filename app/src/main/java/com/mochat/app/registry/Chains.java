package com.mochat.app.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Challenge registry (single source of truth for the UI list).
 *
 * <p>Mirrors the static-registry pattern used by typical vulnerable-app training
 * projects: a static
 * list of {@link Chain} entries, each binding a display name / difficulty / chain id
 * to the vulnerable component. The MainActivity renders this list; tapping an item
 * launches the component's demo activity or shows its writeup hint.</p>
 *
 * <p>The {@code chainId} matches the numbered files in {@code docs/chains/}.</p>
 */
public final class Chains {

    private Chains() {}

    public enum Diff { EASY, MEDIUM, HARD, INSANE }

    public static final class Chain {
        public final int id;
        public final String name;
        public final Diff  difficulty;
        public final String category;   // component / webview / intent / storage / network / resilience / parcel
        public final String component;  // vulnerable component class (for hint display)
        public final String modern;     // modern-Android exploitability note

        public Chain(int id, String name, Diff difficulty, String category,
                     String component, String modern) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.category = category;
            this.component = component;
            this.modern = modern;
        }
    }

    public static List<Chain> all() {
        List<Chain> c = new ArrayList<>();
        // ---- app-layer chains, all confirmed exploitable on Android 13/14/15 ----
        c.add(new Chain(1,  "Wallet Heist",               Diff.INSANE, "storage",   "PaymentActivity + WalletService", "Fully exploitable"));
        c.add(new Chain(2,  "Chat Exfiltration",          Diff.INSANE, "component", "Contacts/Message/FileBackup providers", "Fully exploitable"));
        c.add(new Chain(3,  "Mall WebView RCE",           Diff.INSANE, "webview",   "MallH5Activity",                  "Fully exploitable"));
        c.add(new Chain(4,  "PendingIntent File Theft",   Diff.HARD,   "intent",    "PushService",                     "Explicit-intent + FLAG_MUTABLE variant"));
        c.add(new Chain(5,  "Intent Redirection",         Diff.HARD,   "intent",    "ShareReceiver",                   "Classic pattern still works on A14"));
        c.add(new Chain(6,  "Wallet Crypto Oracle",       Diff.HARD,   "storage",   "WalletService (Messenger)",       "Fully exploitable"));
        c.add(new Chain(7,  "Mini-App AppSecret Leak",    Diff.HARD,   "webview",   "MallH5Activity (mini-app)",       "Fully exploitable"));
        c.add(new Chain(8,  "Plugin Dynamic-Load RCE",    Diff.INSANE, "storage",   "OrderService",                    "Android 14: app-chmod-444 variant"));
        c.add(new Chain(9,  "ZipSlip RCE",                Diff.HARD,   "storage",   "OrderService",                    "Fully exploitable"));
        c.add(new Chain(10, "MITM -> JWT Forge -> ATO",   Diff.HARD,   "network",   "PayWebActivity",                  "Fully exploitable"));
        c.add(new Chain(11, "App-Level Parcel Mismatch",  Diff.HARD,   "parcel",    "OrderService (PaymentOrder)",     "Fully exploitable"));
        c.add(new Chain(12, "Biometric + Frida Repack",   Diff.MEDIUM, "resilience","KeyguardBypassActivity",          "Fully exploitable"));
        c.add(new Chain(13, "4-Stage ACE via Exported Surface",   Diff.INSANE, "component", "LiveWallPreview + NotificationReceiver", "Fully exploitable"));
        c.add(new Chain(14, "Exported Account Takeover",  Diff.MEDIUM, "component", "AccountActivity + TokenReceiver", "Fully exploitable"));
        return Collections.unmodifiableList(c);
    }
}

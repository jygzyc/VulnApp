package com.mochat.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mochat.app.R;

/** About / training-app info page. */
public final class AboutFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        TextView tv = new TextView(requireContext());
        tv.setPadding(48, 64, 48, 48);
        tv.setTextColor(getResources().getColor(R.color.text_primary, null));
        tv.setTextSize(14f);
        tv.setLineSpacing(4f, 1.2f);
        tv.setText("MoChat 漏洞靶场 v2.0\n\n"
                + "这是一个故意包含漏洞的 Android 训练应用,覆盖了 Android APP 端的全部漏洞类型:\n\n"
                + "• Activity 安全 (导出、越权、UI 劫持)\n"
                + "• Service 安全 (Messenger/AIDL 滥用)\n"
                + "• Broadcast Receiver (泄露、有序广播劫持)\n"
                + "• Content Provider (SQLi、路径穿越)\n"
                + "• WebView (JS bridge、file://、SSL bypass)\n"
                + "• Fragment 注入\n"
                + "• Intent/PendingIntent (重定向、劫持)\n"
                + "• 存储安全 (明文密钥、allowBackup)\n"
                + "• 加密误用 (硬编码 key、XOR)\n"
                + "• 验证码本地校验绕过\n"
                + "• Parcel 序列化错位\n"
                + "• 插件化/动态加载 (ZipSlip、DexClassLoader)\n"
                + "• Native 逆向 + 反调试\n\n"
                + "共 20 条攻击链,难度从入门到极难递增。\n"
                + "每条链路可通过第三方 App / Intent / deeplink 触发。\n\n"
                + "⚠️ 仅供安全研究、CTF、教学使用。");
        return tv;
    }
}

package com.mochat.app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mochat.app.ui.ChatFragment;
import com.mochat.app.ui.MallFragment;
import com.mochat.app.ui.ProfileFragment;
import com.mochat.app.ui.WalletFragment;

/**
 * MoChat super-app shell. A bottom navigation switches between four real business
 * tabs (Chats / Wallet / Mall / Me). The vulnerable components declared in the
 * manifest are embedded inside these tabs so that each exploit chain has a natural
 * business context — an attacker interacts with the app as a user would, then
 * abuses the exposed surface.
 */
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        // Edge-to-edge: apply system-bar insets as padding so content doesn't
        // overlap the status bar, camera cutout, or gesture-nav bar.
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(this::onTabSelected);

        if (s == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contentFrame, new ChatFragment())
                    .commit();
        }
    }

    private boolean onTabSelected(@NonNull android.view.MenuItem item) {
        Fragment frag;
        int id = item.getItemId();
        if (id == R.id.nav_wallet)        frag = new WalletFragment();
        else if (id == R.id.nav_mall)     frag = new MallFragment();
        else if (id == R.id.nav_profile)  frag = new ProfileFragment();
        else                              frag = new ChatFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentFrame, frag)
                .commit();
        return true;
    }
}

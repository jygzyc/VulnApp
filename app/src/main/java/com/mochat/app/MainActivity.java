package com.mochat.app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mochat.app.ui.ChainListFragment;
import com.mochat.app.ui.AboutFragment;

/**
 * Main entry point. The home screen is a training-app banner + the list of 20
 * exploit chains. Tapping a chain opens a detail fragment.
 */
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        // Edge-to-edge insets.
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            Fragment frag;
            int id = item.getItemId();
            if (id == R.id.nav_about) frag = new AboutFragment();
            else frag = new ChainListFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contentFrame, frag)
                    .commit();
            return true;
        });

        if (s == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contentFrame, new ChainListFragment())
                    .commit();
        }
    }

    /** Called by ChainListFragment when a chain is tapped. */
    public void showChainDetail(int chainId) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentFrame, com.mochat.app.ui.ChainDetailFragment.newInstance(chainId))
                .addToBackStack(null)
                .commit();
    }
}

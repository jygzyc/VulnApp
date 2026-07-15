package com.mochat.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mochat.app.R;
import com.mochat.app.registry.Chains;
import com.mochat.app.settings.KeyguardBypassActivity;

import java.util.List;

/**
 * Profile tab — user card + security-check entry (chain #12) + the exploit-chain
 * reference list (moved here from the old launcher so the app opens on real IM,
 * not on a vuln catalogue).
 */
public final class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             android.os.Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        // Security check -> opens the exported KeyguardBypassActivity (chain #12).
        v.findViewById(R.id.securityBtn).setOnClickListener(b ->
                startActivity(new Intent(requireContext(), KeyguardBypassActivity.class)));

        // Chain reference list.
        RecyclerView list = v.findViewById(R.id.chainList);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(new ChainAdapter(Chains.all(), this::openWriteup));

        return v;
    }

    private void openWriteup(Chains.Chain c) {
        String url = "https://github.com/jygzyc/VulnApp/blob/main/docs/chains/chain-"
                + String.format("%02d", c.id) + ".md";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) { }
    }

    /** Renders each chain as a compact card with a colour-coded difficulty chip. */
    static final class ChainAdapter extends RecyclerView.Adapter<ChainAdapter.VH> {
        interface Click { void on(Chains.Chain c); }
        private final List<Chains.Chain> items;
        private final Click click;

        ChainAdapter(List<Chains.Chain> items, Click click) {
            this.items = items; this.click = click;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chain, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Chains.Chain c = items.get(position);
            h.id.setText(String.format("#%02d", c.id));
            h.name.setText(c.name);
            h.category.setText(c.category.toUpperCase());
            h.component.setText(c.component);
            h.modern.setText(c.modern);

            int bg, fg;
            switch (c.difficulty) {
                case EASY:   bg = R.color.chip_easy_bg;   fg = R.color.chip_easy_fg;   break;
                case MEDIUM: bg = R.color.chip_medium_bg; fg = R.color.chip_medium_fg; break;
                case HARD:   bg = R.color.chip_hard_bg;   fg = R.color.chip_hard_fg;   break;
                default:     bg = R.color.chip_insane_bg; fg = R.color.chip_insane_fg; break;
            }
            h.diff.setText(c.difficulty.name());
            h.diff.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), bg));
            h.diff.setTextColor(ContextCompat.getColor(h.itemView.getContext(), fg));

            h.itemView.setOnClickListener(v -> click.on(c));
        }

        @Override public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView id, name, diff, category, component, modern;
            VH(@NonNull View v) {
                super(v);
                id = v.findViewById(R.id.chainId);
                name = v.findViewById(R.id.chainName);
                diff = v.findViewById(R.id.chainDiff);
                category = v.findViewById(R.id.chainCategory);
                component = v.findViewById(R.id.chainComponent);
                modern = v.findViewById(R.id.chainModern);
            }
        }
    }
}

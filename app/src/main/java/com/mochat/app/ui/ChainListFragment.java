package com.mochat.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mochat.app.MainActivity;
import com.mochat.app.R;
import com.mochat.app.registry.Chains;
import com.mochat.app.registry.Chains.Chain;
import com.mochat.app.registry.Chains.Diff;

import java.util.List;

/** Lists all 20 exploit chains. Tapping opens the detail fragment. */
public final class ChainListFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chain_list, container, false);
        RecyclerView rv = v.findViewById(R.id.chainRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(new ChainAdapter(Chains.all(), chainId ->
                ((MainActivity) requireActivity()).showChainDetail(chainId)));
        return v;
    }

    static final class ChainAdapter extends RecyclerView.Adapter<ChainAdapter.VH> {
        interface Click { void on(int chainId); }
        private final List<Chain> items;
        private final Click click;

        ChainAdapter(List<Chain> items, Click click) {
            this.items = items;
            this.click = click;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chain, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Chain c = items.get(position);
            h.id.setText(String.format("%02d", c.id));
            h.name.setText(c.name);
            h.category.setText(c.category);
            applyDiff(h, c.difficulty);
            h.itemView.setOnClickListener(v -> click.on(c.id));
        }

        private void applyDiff(@NonNull VH h, Diff d) {
            int bg, fg;
            switch (d) {
                case EASY:   bg = R.color.chip_easy_bg;   fg = R.color.chip_easy_fg;   break;
                case MEDIUM: bg = R.color.chip_medium_bg; fg = R.color.chip_medium_fg; break;
                case HARD:   bg = R.color.chip_hard_bg;   fg = R.color.chip_hard_fg;   break;
                default:     bg = R.color.chip_insane_bg; fg = R.color.chip_insane_fg; break;
            }
            String label;
            switch (d) {
                case EASY:   label = "入门"; break;
                case MEDIUM: label = "中等"; break;
                case HARD:   label = "困难"; break;
                default:     label = "极难"; break;
            }
            h.diff.setText(label);
            h.diff.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), bg));
            h.diff.setTextColor(ContextCompat.getColor(h.itemView.getContext(), fg));
        }

        @Override public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView id, name, category, diff;
            VH(@NonNull View v) {
                super(v);
                id = v.findViewById(R.id.chainId);
                name = v.findViewById(R.id.chainName);
                category = v.findViewById(R.id.chainCategory);
                diff = v.findViewById(R.id.chainDiff);
            }
        }
    }
}

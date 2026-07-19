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

import com.mochat.app.R;
import com.mochat.app.registry.Chains;
import com.mochat.app.registry.Chains.Chain;
import com.mochat.app.registry.Chains.Diff;

import java.util.List;

/** Shows the details of a single exploit chain (difficulty, type, objective, hint). */
public final class ChainDetailFragment extends Fragment {

    private static final String ARG_ID = "chain_id";

    public static ChainDetailFragment newInstance(int chainId) {
        Bundle args = new Bundle();
        args.putInt(ARG_ID, chainId);
        ChainDetailFragment f = new ChainDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chain_detail, container, false);

        int chainId = getArguments().getInt(ARG_ID, 1);
        Chain chain = findChain(chainId);
        if (chain == null) return v;

        // Header
        ((TextView) v.findViewById(R.id.detailId)).setText(String.format("%02d", chain.id));
        ((TextView) v.findViewById(R.id.detailName)).setText(chain.name);

        TextView diffTv = v.findViewById(R.id.detailDiff);
        applyDiff(diffTv, chain.difficulty);

        // Info rows
        setRow(v, R.id.rowCategory, getString(R.string.chain_detail_category), chain.category);
        setRow(v, R.id.rowObjective, getString(R.string.chain_detail_objective), chain.objective);
        setRow(v, R.id.rowHint, getString(R.string.chain_detail_hint), chain.hint);

        return v;
    }

    private Chain findChain(int id) {
        for (Chain c : Chains.all()) {
            if (c.id == id) return c;
        }
        return null;
    }

    private void applyDiff(TextView tv, Diff d) {
        int bg, fg; String label;
        switch (d) {
            case EASY:   bg = R.color.chip_easy_bg;   fg = R.color.chip_easy_fg;   label = "入门 EASY"; break;
            case MEDIUM: bg = R.color.chip_medium_bg; fg = R.color.chip_medium_fg; label = "中等 MEDIUM"; break;
            case HARD:   bg = R.color.chip_hard_bg;   fg = R.color.chip_hard_fg;   label = "困难 HARD"; break;
            default:     bg = R.color.chip_insane_bg; fg = R.color.chip_insane_fg; label = "极难 INSANE"; break;
        }
        tv.setText(label);
        tv.setBackgroundColor(ContextCompat.getColor(requireContext(), bg));
        tv.setTextColor(ContextCompat.getColor(requireContext(), fg));
    }

    private void setRow(View root, int rowId, String label, String value) {
        View row = root.findViewById(rowId);
        if (row != null) {
            ((TextView) row.findViewById(R.id.infoLabel)).setText(label);
            ((TextView) row.findViewById(R.id.infoValue)).setText(value);
        }
    }
}

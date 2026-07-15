package com.mochat.app.ui;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mochat.app.R;
import com.mochat.app.util.DbHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat tab — renders the contact list with a preview of the latest message from
 * {@code chat.db}. This is the user-facing IM surface; the exported
 * {@code ContactsProvider}/{@code MessageProvider} expose the same data to other
 * apps (chain #2).
 */
public final class ChatFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        RecyclerView list = v.findViewById(R.id.chatList);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(new ChatAdapter(loadContacts()));
        return v;
    }

    private List<Contact> loadContacts() {
        List<Contact> out = new ArrayList<>();
        try (SQLiteDatabase db = new DbHelpers.ChatDb(requireContext()).getReadableDatabase();
             Cursor c = db.rawQuery(
                     "SELECT name, phone FROM " + DbHelpers.ChatDb.T_CONTACTS + " ORDER BY name",
                     null)) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String phone = c.getString(1);
                long[] ts = lastMessageTs(name);
                String preview = ts[1] == 0 ? "" : String.valueOf((char) ts[1]); // placeholder
                out.add(new Contact(name, phone, lastMessagePreview(name),
                        ts[0], (int) (Math.random() * 4)));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private String lastMessagePreview(String name) {
        try (SQLiteDatabase db = new DbHelpers.ChatDb(requireContext()).getReadableDatabase();
             Cursor c = db.rawQuery(
                     "SELECT body FROM " + DbHelpers.ChatDb.T_MESSAGES +
                             " WHERE from_user=? OR to_user=? ORDER BY ts DESC LIMIT 1",
                     new String[]{name, name})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Throwable ignored) { }
        return "";
    }

    /** @return [timestamp, 0] */
    private long[] lastMessageTs(String name) {
        try (SQLiteDatabase db = new DbHelpers.ChatDb(requireContext()).getReadableDatabase();
             Cursor c = db.rawQuery(
                     "SELECT ts FROM " + DbHelpers.ChatDb.T_MESSAGES +
                             " WHERE from_user=? OR to_user=? ORDER BY ts DESC LIMIT 1",
                     new String[]{name, name})) {
            if (c.moveToFirst()) return new long[]{c.getLong(0), 0};
        } catch (Throwable ignored) { }
        return new long[]{0, 0};
    }

    static final class Contact {
        final String name, phone, preview;
        final long ts;
        final int unread;
        Contact(String n, String p, String pv, long t, int u) {
            name = n; phone = p; preview = pv; ts = t; unread = u;
        }
    }

    static final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<Contact> items;
        ChatAdapter(List<Contact> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Contact c = items.get(position);
            h.avatar.setText(c.name.isEmpty() ? "?" : c.name.substring(0, 1));
            h.name.setText(c.name);
            h.preview.setText(c.preview.isEmpty() ? c.phone : c.preview);
            h.time.setText(c.ts > 0 ? formatTime(c.ts) : "");
            if (c.unread > 0) {
                h.unread.setVisibility(View.VISIBLE);
                h.unread.setText(String.valueOf(c.unread));
            } else {
                h.unread.setVisibility(View.GONE);
            }
        }

        private String formatTime(long ts) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
            return f.format(new java.util.Date(ts * 1000));
        }

        @Override public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView avatar, name, preview, time, unread;
            VH(@NonNull View v) {
                super(v);
                avatar  = v.findViewById(R.id.chatAvatar);
                name    = v.findViewById(R.id.chatName);
                preview = v.findViewById(R.id.chatPreview);
                time    = v.findViewById(R.id.chatTime);
                unread  = v.findViewById(R.id.chatUnread);
            }
        }
    }
}

package vn.edu.usth.tip.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.network.responses.SessionResponse;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

    public interface OnRevoke { void onRevoke(SessionResponse session); }

    private List<SessionResponse> items = new ArrayList<>();
    private final OnRevoke listener;

    public SessionAdapter(OnRevoke listener) {
        this.listener = listener;
    }

    public void submit(List<SessionResponse> data) {
        items = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SessionResponse s = items.get(position);
        h.tvName.setText(s.getDeviceName() != null ? s.getDeviceName() : "Thiết bị không tên");
        h.tvCurrent.setVisibility(s.isCurrent() ? View.VISIBLE : View.GONE);
        h.tvLastSeen.setText("Hoạt động: " + formatTime(s.getLastSeenAt()));
        // Thiết bị hiện tại: ẩn nút thu hồi (đăng xuất chính mình dùng nút ở màn trước).
        h.btnRevoke.setVisibility(s.isCurrent() ? View.GONE : View.VISIBLE);
        h.btnRevoke.setOnClickListener(v -> listener.onRevoke(s));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** "2026-06-10T08:30:00+07:00" → "2026-06-10 08:30". */
    private static String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        String t = iso.replace('T', ' ');
        return t.length() >= 16 ? t.substring(0, 16) : t;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName, tvCurrent, tvLastSeen, btnRevoke;

        VH(@NonNull View v) {
            super(v);
            tvName     = v.findViewById(R.id.tv_device_name);
            tvCurrent  = v.findViewById(R.id.tv_current_badge);
            tvLastSeen = v.findViewById(R.id.tv_last_seen);
            btnRevoke  = v.findViewById(R.id.btn_revoke);
        }
    }
}

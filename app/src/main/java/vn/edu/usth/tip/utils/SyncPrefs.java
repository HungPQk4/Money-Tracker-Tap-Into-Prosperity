package vn.edu.usth.tip.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lưu cursor (server-generated syncTimestamp) cho từng entity.
 * KHÔNG BAO GIỜ lưu System.currentTimeMillis() vào đây — phải dùng timestamp từ server.
 */
public class SyncPrefs {
    private static final String PREFS_NAME = "sync_cursors";

    public static final String KEY_TX       = "cursor_tx";
    public static final String KEY_ACCOUNT  = "cursor_account";
    public static final String KEY_CATEGORY = "cursor_category";
    public static final String KEY_BUDGET   = "cursor_budget";
    public static final String KEY_GOAL     = "cursor_goal";
    public static final String KEY_DEBT     = "cursor_debt";

    public static String getCursor(Context ctx, String key) {
        return getPrefs(ctx).getString(key, null);
    }

    public static void setCursor(Context ctx, String key, String serverTimestamp) {
        getPrefs(ctx).edit().putString(key, serverTimestamp).apply();
    }

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

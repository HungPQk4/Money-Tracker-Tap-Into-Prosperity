package vn.edu.usth.tip.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_FULL_NAME = "user_full_name";
    
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public TokenManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveToken(String token, String fullName) {
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public String getFullName() {
        return sharedPreferences.getString(KEY_FULL_NAME, null);
    }

    public void clear() {
        editor.clear();
        editor.apply();
    }
}

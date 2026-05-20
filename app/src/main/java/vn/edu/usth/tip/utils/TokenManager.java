package vn.edu.usth.tip.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_FULL_NAME = "user_full_name";
    private static final String KEY_USER_ID = "user_id";
    
    private final SharedPreferences sharedPreferences;

    public TokenManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveAuthData(String token, String fullName, String userId) {
        sharedPreferences.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public String getFullName() {
        return sharedPreferences.getString(KEY_FULL_NAME, null);
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
}

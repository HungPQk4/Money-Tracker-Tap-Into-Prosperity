package vn.edu.usth.tip.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {
    private static final String PREF_NAME = "AuthPrefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_FULL_NAME = "user_full_name";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences sharedPreferences;

    public TokenManager(Context context) {
        sharedPreferences = createEncryptedPrefs(context);
    }

    private static SharedPreferences createEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Corrupted keystore can leave the prefs unreadable; wipe and re-throw so the
            // caller can fall back to a fresh login rather than silently losing the token.
            context.deleteSharedPreferences(PREF_NAME);
            throw new RuntimeException("Failed to initialise secure storage", e);
        }
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

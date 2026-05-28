package vn.edu.usth.tip.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {
    private static final String PREF_NAME        = "AuthPrefs";
    private static final String PREF_NAME_PLAIN  = "AuthPrefs_plain";
    private static final String KEY_TOKEN        = "jwt_token";
    private static final String KEY_FULL_NAME    = "user_full_name";
    private static final String KEY_USER_ID      = "user_id";

    // Singleton: the constructor accesses Android Keystore which can throw SecurityException
    // on background threads (OkHttp Dispatcher, WorkManager, etc.). Creating the instance once
    // on the main thread (app startup) and sharing it everywhere avoids all background Keystore access.
    private static volatile TokenManager instance;

    public static TokenManager getOrCreate(Context context) {
        if (instance == null) {
            synchronized (TokenManager.class) {
                if (instance == null) {
                    instance = new TokenManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context appContext;
    private final SharedPreferences sharedPreferences;

    // In-memory cache so background-thread reads (interceptors, workers) never touch Keystore.
    private volatile String cachedToken;
    private volatile String cachedFullName;
    private volatile String cachedUserId;

    public TokenManager(Context context) {
        this.appContext    = context.getApplicationContext();
        sharedPreferences  = createEncryptedPrefs(appContext);
        cachedToken    = sharedPreferences.getString(KEY_TOKEN,     null);
        cachedFullName = sharedPreferences.getString(KEY_FULL_NAME, null);
        cachedUserId   = sharedPreferences.getString(KEY_USER_ID,   null);
    }

    private static SharedPreferences createEncryptedPrefs(Context context) {
        // EncryptedSharedPreferences.create() does NOT decrypt existing data immediately.
        // Calling getAll() right after forces early decryption so we can catch corruption
        // here (in the constructor on the main thread) rather than later on a background thread.
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences prefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            prefs.getAll(); // triggers decryption of all existing keys — detects plaintext/corrupted data early
            return prefs;
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            android.util.Log.w("TokenManager", "Encrypted prefs corrupted or plaintext, deleting and recreating", e);
            context.deleteSharedPreferences(PREF_NAME);
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
            } catch (GeneralSecurityException | IOException | RuntimeException e2) {
                android.util.Log.e("TokenManager", "Keystore unavailable, using plain prefs fallback", e2);
                return context.getSharedPreferences(PREF_NAME_PLAIN, Context.MODE_PRIVATE);
            }
        }
    }

    public void saveAuthData(String token, String fullName, String userId) {
        cachedToken    = token;
        cachedFullName = fullName;
        cachedUserId   = userId;
        sharedPreferences.edit()
                .putString(KEY_TOKEN,     token)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_USER_ID,   userId)
                .apply();
    }

    public String getToken()    { return cachedToken; }
    public String getFullName() { return cachedFullName; }
    public String getUserId()   { return cachedUserId; }

    public void clear() {
        cachedToken    = null;
        cachedFullName = null;
        cachedUserId   = null;
        try {
            sharedPreferences.edit().clear().apply();
        } catch (RuntimeException e) {
            // EncryptedSharedPreferences.apply() can throw SecurityException (subclass of RuntimeException)
            // from background threads when clearKeysIfNeeded() → getAll() tries to decrypt corrupted/plaintext keys.
            android.util.Log.e("TokenManager", "Failed to clear encrypted prefs, deleting file", e);
            appContext.deleteSharedPreferences(PREF_NAME);
            appContext.deleteSharedPreferences(PREF_NAME_PLAIN);
        }
    }
}

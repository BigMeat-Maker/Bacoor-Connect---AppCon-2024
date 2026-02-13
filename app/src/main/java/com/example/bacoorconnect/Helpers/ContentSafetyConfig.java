package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class ContentSafetyConfig {
    private static final String TAG = "ContentSafetyConfig";
    private static final String PREFS_NAME = "content_safety_config";
    private static final String KEY_CONTENT_SAFETY = "content_safety_key";

    public static String getContentSafetyKey(Context context) {
        return getSecureConfig(context, KEY_CONTENT_SAFETY);
    }

    public static String getEndpoint() {
        return "https://japaneast.api.cognitive.microsoft.com/";
    }

    private static String getSecureConfig(Context context, String key) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            EncryptedSharedPreferences prefs = (EncryptedSharedPreferences)
                    EncryptedSharedPreferences.create(
                            context,
                            PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );

            String value = prefs.getString(key, null);
            Log.d(TAG, "Retrieved " + key + ": " + (value != null && !value.isEmpty() ? "SET" : "NOT SET"));
            return value;
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error reading config", e);
            return null;
        }
    }

    public static void setCredentials(Context context, String contentSafetyKey) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            EncryptedSharedPreferences prefs = (EncryptedSharedPreferences)
                    EncryptedSharedPreferences.create(
                            context,
                            PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );

            prefs.edit()
                    .putString(KEY_CONTENT_SAFETY, contentSafetyKey)
                    .apply();

            Log.d(TAG, "Content Safety credentials saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving Content Safety config", e);
        }
    }

    public static boolean hasCredentials(Context context) {
        String key = getContentSafetyKey(context);
        return key != null && !key.isEmpty();
    }
}
package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SerpApiConfig {
    private static final String TAG = "SerpApiConfig";
    private static final String PREFS_NAME = "serpapi_config";

    public static String getApiKey(Context context) {
        String apiKey = getSecureConfig(context, "api_key");
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "SERPAPI key not configured. Check Firebase Remote Config.");
        }
        return apiKey;
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

            return prefs.getString(key, null);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error reading SERPAPI config", e);
            return null;
        }
    }

    public static void setCredentials(Context context, String apiKey) {
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
                    .putString("api_key", apiKey)
                    .apply();

            Log.d(TAG, "SERPAPI credentials saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving SERPAPI config", e);
        }
    }

    public static boolean hasCredentials(Context context) {
        String apiKey = getApiKey(context);
        return apiKey != null && !apiKey.isEmpty();
    }
}
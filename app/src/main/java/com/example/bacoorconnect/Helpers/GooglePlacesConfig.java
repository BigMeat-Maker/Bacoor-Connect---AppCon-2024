package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class GooglePlacesConfig {
    private static final String TAG = "GooglePlacesConfig";
    private static final String PREFS_NAME = "google_places_config";

    public static String getApiKey(Context context) {
        return getSecureConfig(context, "google_places_api_key");
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
            Log.e(TAG, "Error reading Google Places config", e);
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
                    .putString("google_places_api_key", apiKey)
                    .apply();

            Log.d(TAG, "Google Places API key saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving Google Places config", e);
        }
    }
}

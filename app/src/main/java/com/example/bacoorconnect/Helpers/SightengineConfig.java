package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SightengineConfig {
    private static final String TAG = "SightengineConfig";
    private static final String PREFS_NAME = "sightengine_config";

    public static String getApiUser(Context context) {
        String apiUser = getSecureConfig(context, "api_user");
        if (apiUser == null || apiUser.isEmpty()) {
            Log.e(TAG, "Sightengine API user not configured. Check Firebase Remote Config.");
        }
        return apiUser;
    }

    public static String getApiSecret(Context context) {
        String apiSecret = getSecureConfig(context, "api_secret");
        if (apiSecret == null || apiSecret.isEmpty()) {
            Log.e(TAG, "Sightengine API secret not configured. Check Firebase Remote Config.");
        }
        return apiSecret;
    }

    public static String getBaseUrl() {
        return "https://api.sightengine.com/1.0/check.json";
    }

    // Optional: Get confidence threshold from Remote Config
    public static float getConfidenceThreshold(Context context) {
        // You can store this in regular SharedPreferences since it's not sensitive
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "sightengine_settings", Context.MODE_PRIVATE);
        return prefs.getFloat("confidence_threshold", 0.7f); // Default 70%
    }

    public static void setConfidenceThreshold(Context context, float threshold) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "sightengine_settings", Context.MODE_PRIVATE);
        prefs.edit().putFloat("confidence_threshold", threshold).apply();
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
            Log.e(TAG, "Error reading Sightengine config", e);
            return null;
        }
    }

    public static void setCredentials(Context context, String apiUser, String apiSecret) {
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
                    .putString("api_user", apiUser)
                    .putString("api_secret", apiSecret)
                    .apply();

            Log.d(TAG, "Sightengine credentials saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving Sightengine config", e);
        }
    }

    public static boolean hasCredentials(Context context) {
        String apiUser = getApiUser(context);
        String apiSecret = getApiSecret(context);
        return apiUser != null && !apiUser.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty();
    }

    public static void clearCredentials(Context context) {
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

            prefs.edit().clear().apply();
            Log.d(TAG, "Sightengine credentials cleared");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error clearing Sightengine config", e);
        }
    }
}
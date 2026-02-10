package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class AzureConfig {
    private static final String TAG = "AzureConfig";
    private static final String PREFS_NAME = "azure_config";

    public static String getAzureKey(Context context) {
        return getSecureConfig(context, "azure_key");
    }

    public static String getAzureEndpoint(Context context) {
        return "https://bacconformrecognizer.cognitiveservices.azure.com/";
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
            Log.e(TAG, "Error reading Azure config", e);
            return null;
        }
    }

    public static void setCredentials(Context context, String azureKey) {
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
                    .putString("azure_key", azureKey)
                    .apply();

            Log.d(TAG, "Azure key saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving Azure config", e);
        }
    }

    public static boolean hasCredentials(Context context) {
        String key = getAzureKey(context);
        return key != null && !key.isEmpty();
    }
}
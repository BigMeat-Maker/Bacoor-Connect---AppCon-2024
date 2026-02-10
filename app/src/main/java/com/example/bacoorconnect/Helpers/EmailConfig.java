package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class EmailConfig {
    private static final String TAG = "EmailConfig";
    private static final String PREFS_NAME = "email_config";

    public static String getEmailAddress(Context context) {
        String email = getSecureConfig(context, "email_address");
        if (email == null || email.isEmpty()) {
            Log.e(TAG, "Email address not configured. Check Firebase Remote Config.");
        }
        return email;
    }

    public static String getEmailPassword(Context context) {
        String password = getSecureConfig(context, "email_password");
        if (password == null || password.isEmpty()) {
            Log.e(TAG, "Email password not configured. Check Firebase Remote Config.");
        }
        return password;
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
            Log.e(TAG, "Error reading email config", e);
            return null;
        }
    }

    public static void setCredentials(Context context, String email, String password) {
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
                    .putString("email_address", email)
                    .putString("email_password", password)
                    .apply();

            Log.d(TAG, "Email credentials saved to secure storage");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving email config", e);
        }
    }

    public static boolean hasCredentials(Context context) {
        String email = getEmailAddress(context);
        String password = getEmailPassword(context);
        return email != null && !email.isEmpty() &&
                password != null && !password.isEmpty();
    }
}
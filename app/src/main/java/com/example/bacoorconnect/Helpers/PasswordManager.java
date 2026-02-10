package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class PasswordManager {
    private static final String TAG = "PasswordManager";
    private static final String PREFS_NAME = "secure_passwords";
    private static final String KEY_PREFIX = "temp_pass_";

    public static void saveTempPassword(Context context, String tempUserId, String password) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            sharedPreferences.edit()
                    .putString(KEY_PREFIX + tempUserId, password)
                    .apply();

            Log.d(TAG, "Password saved securely for: " + tempUserId);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to save password securely", e);
        }
    }

    public static String getTempPassword(Context context, String tempUserId) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String password = sharedPreferences.getString(KEY_PREFIX + tempUserId, null);

            // Remove after retrieval (one-time use)
            if (password != null) {
                sharedPreferences.edit()
                        .remove(KEY_PREFIX + tempUserId)
                        .apply();
            }

            return password;
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to retrieve password", e);
            return null;
        }
    }

    public static void clearTempPassword(Context context, String tempUserId) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            sharedPreferences.edit()
                    .remove(KEY_PREFIX + tempUserId)
                    .apply();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to clear password", e);
        }
    }
}
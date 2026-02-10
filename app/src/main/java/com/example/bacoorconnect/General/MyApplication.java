package com.example.bacoorconnect.General;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.example.bacoorconnect.Helpers.AzureConfig;
import com.example.bacoorconnect.Helpers.EmailConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class MyApplication extends Application implements Configuration.Provider {

    private static final String TAG = "MyApplication";

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        setupEmailFromRemoteConfig();
        setupAzureFromRemoteConfig();
    }

    private void setupEmailFromRemoteConfig() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();

        java.util.Map<String, Object> defaults = new java.util.HashMap<>();
        defaults.put("email_address", "");
        defaults.put("email_password", "");
        remoteConfig.setDefaultsAsync(defaults);

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean updated = task.getResult();
                        Log.d(TAG, "Remote Config fetch " + (updated ? "successful" : "not needed"));

                        String email = remoteConfig.getString("email_address");
                        String password = remoteConfig.getString("email_password");

                        if (!email.isEmpty() && !password.isEmpty()) {
                            EmailConfig.setCredentials(this, email, password);
                            Log.d(TAG, "Email credentials saved to secure storage");
                        } else {
                            Log.e(TAG, "Email credentials not found in Remote Config");
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch Remote Config", task.getException());
                    }
                });
    }

    private void setupAzureFromRemoteConfig() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();

        java.util.Map<String, Object> defaults = new java.util.HashMap<>();
        defaults.put("azure_key", "");
        remoteConfig.setDefaultsAsync(defaults);

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean updated = task.getResult();
                        Log.d(TAG, "Azure Remote Config fetch " + (updated ? "successful" : "not needed"));

                        String azureKey = remoteConfig.getString("azure_key");

                        if (!azureKey.isEmpty()) {
                            AzureConfig.setCredentials(this, azureKey);
                            Log.d(TAG, "Azure credentials saved to secure storage");
                        } else {
                            Log.e(TAG, "Azure key not found in Remote Config");
                        }
                    } else {
                        Log.e(TAG, "ERROR: Failed to fetch Azure Remote Config", task.getException());
                        Log.e(TAG, "App cannot process ID verification without Azure key!");
                    }
                });
    }

}
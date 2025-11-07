package com.example.bacoorconnect.General;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.work.Configuration;

public class MyApplication extends Application implements Configuration.Provider {

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build();
    }
}

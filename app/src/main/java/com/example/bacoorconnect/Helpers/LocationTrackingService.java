package com.example.bacoorconnect.Helpers;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.bacoorconnect.General.FrontpageActivity;
import com.example.bacoorconnect.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LocationTrackingService extends Service {
    private static final String TAG = "LocationTracking";
    private static final String CHANNEL_ID = "LocationTrackingServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int PROXIMITY_NOTIFICATION_ID = 2;
    private static final long UPDATE_INTERVAL = 10000; // 10 seconds
    private static final float PROXIMITY_THRESHOLD = 200; // meters
    private static final long REPORTS_REFRESH_INTERVAL = 60000; // 1 minute

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Map<String, Report> confirmedReports = new HashMap<>();
    private Map<String, Long> notifiedReports = new HashMap<>(); // Store notification timestamp
    private long lastReportsRefresh = 0;
    private String currentUserId;
    private DatabaseReference userStatusRef;
    private DatabaseReference reportsRef;
    private ValueEventListener reportsListener;

    @Override
    public void onCreate() {
        super.onCreate();

        currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) {
            stopSelf();
            return;
        }

        createNotificationChannel();
        setupUserStatus();
        initializeLocationClient();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createServiceNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifies you when you're near confirmed reports");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createServiceNotification() {
        Intent notificationIntent = new Intent(this, FrontpageActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bacoor Connect")
                .setContentText("Monitoring nearby reports...")
                .setSmallIcon(R.drawable.alerts)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void setupUserStatus() {
        userStatusRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(currentUserId)
                .child("status");

        DatabaseReference connectedRef = FirebaseDatabase.getInstance()
                .getReference(".info/connected");

        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected != null && connected) {
                    userStatusRef.setValue("online");
                    userStatusRef.onDisconnect().setValue("offline");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Connected ref error: " + error.getMessage());
            }
        });
    }

    private void initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    checkProximity(location);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadConfirmedReports();
        startLocationUpdates();
        return START_STICKY;
    }

    private void loadConfirmedReports() {
        // Refresh reports periodically
        long now = System.currentTimeMillis();
        if (now - lastReportsRefresh < REPORTS_REFRESH_INTERVAL) {
            return;
        }
        lastReportsRefresh = now;

        if (reportsListener != null) {
            reportsRef.removeEventListener(reportsListener);
        }

        reportsRef = FirebaseDatabase.getInstance().getReference("Report");
        reportsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Report> newReports = new HashMap<>();

                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    try {
                        Double lat = getDoubleValue(reportSnapshot, "latitude", "lat");
                        Double lon = getDoubleValue(reportSnapshot, "longitude", "lon");

                        if (lat == null || lon == null) {
                            Log.w(TAG, "Missing coordinates for report " + reportSnapshot.getKey());
                            continue;
                        }

                        int upvotes = getIntValue(reportSnapshot, "upvotes");
                        int downvotes = getIntValue(reportSnapshot, "downvotes");

                        // Only include confirmed reports (more upvotes than downvotes)
                        if (upvotes > downvotes) {
                            String category = reportSnapshot.child("category").getValue(String.class);
                            String description = reportSnapshot.child("description").getValue(String.class);
                            String reportId = reportSnapshot.getKey();
                            String userId = reportSnapshot.child("userId").getValue(String.class);

                            Report report = new Report(lat, lon, category, description, reportId, userId);
                            newReports.put(reportId, report);

                            Log.d(TAG, "Loaded confirmed report: " + reportId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing report: " + e.getMessage());
                    }
                }

                confirmedReports = newReports;
                Log.d(TAG, "Total confirmed reports loaded: " + confirmedReports.size());

                // Clean up old notifications (older than 1 hour)
                long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
                notifiedReports.entrySet().removeIf(entry -> entry.getValue() < oneHourAgo);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load reports: " + error.getMessage());
            }
        };

        reportsRef.addValueEventListener(reportsListener);
    }

    private Double getDoubleValue(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            if (snapshot.child(key).exists()) {
                return snapshot.child(key).getValue(Double.class);
            }
        }
        return null;
    }

    private int getIntValue(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value instanceof Long) {
            return ((Long) value).intValue();
        } else if (value instanceof Integer) {
            return (Integer) value;
        }
        return 0;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(5000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start location updates: " + e.getMessage());
        }
    }

    private void checkProximity(Location userLocation) {
        Log.d(TAG, String.format("Checking proximity at: %.6f, %.6f",
                userLocation.getLatitude(), userLocation.getLongitude()));

        for (Report report : confirmedReports.values()) {
            // Skip if already notified in the last hour
            if (notifiedReports.containsKey(report.reportId)) {
                continue;
            }

            float[] distance = new float[1];
            Location.distanceBetween(
                    userLocation.getLatitude(), userLocation.getLongitude(),
                    report.lat, report.lon, distance
            );

            if (distance[0] <= PROXIMITY_THRESHOLD) {
                sendProximityNotification(report, distance[0]);
                notifiedReports.put(report.reportId, System.currentTimeMillis());
            }
        }
    }

    private void sendProximityNotification(Report report, float distance) {
        // Create intent to open FrontpageActivity with the report feed
        Intent intent = new Intent(this, FrontpageActivity.class);
        intent.putExtra("OPEN_REPORT_FEED", true);
        intent.putExtra("FOCUS_REPORT_ID", report.reportId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                report.reportId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.alerts)
                .setContentTitle("📍 Nearby Confirmed Report!")
                .setContentText(String.format("%s - %.0f meters away", report.category, distance))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(report.description))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(PROXIMITY_NOTIFICATION_ID + report.reportId.hashCode(), notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (reportsRef != null && reportsListener != null) {
            reportsRef.removeEventListener(reportsListener);
        }

        setUserOffline();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        setUserOffline();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private void setUserOffline() {
        if (currentUserId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(currentUserId)
                    .child("status")
                    .setValue("offline");
        }
    }

    private static class Report {
        final double lat, lon;
        final String category, description, reportId, userId;

        Report(double lat, double lon, String category, String description, String reportId, String userId) {
            this.lat = lat;
            this.lon = lon;
            this.category = category;
            this.description = description;
            this.reportId = reportId;
            this.userId = userId;
        }
    }
}
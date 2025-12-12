package com.example.bacoorconnect.Helpers;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.example.bacoorconnect.R;
import com.google.android.gms.location.*;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class LocationTrackingService extends Service {
    private final List<String> notifiedReports = new ArrayList<>();

    public static final String CHANNEL_ID = "LocationTrackingServiceChannel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private List<Report> confirmedReports = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Location Tracking Service";
            String description = "Notification for Location Tracking Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Location Tracking Service";
            String description = "Notification for Location Tracking Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(FirebaseAuth.getInstance().getUid())
                .child("status");

        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
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
            public void onCancelled(@NonNull DatabaseError error) { }
        });

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Tracking")
                .setContentText("The app is tracking your location.")
                .setSmallIcon(R.drawable.alerts)
                .build();

        startForeground(1, notification);

        loadConfirmedReports();

        startLocationUpdates();

        return START_NOT_STICKY;
    }

    private void loadConfirmedReports() {
        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                confirmedReports.clear();
                notifiedReports.clear();
                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    Log.d("loadConfirmedReports", "Processing report: " + reportSnapshot.getKey());

                    Double lat = null;
                    Double lon = null;

                    if (reportSnapshot.child("latitude").exists()) {
                        lat = reportSnapshot.child("latitude").getValue(Double.class);
                    }
                    else if (reportSnapshot.child("lat").exists()) {
                        lat = reportSnapshot.child("lat").getValue(Double.class);
                    }

                    if (reportSnapshot.child("longitude").exists()) {
                        lon = reportSnapshot.child("longitude").getValue(Double.class);
                    }
                    else if (reportSnapshot.child("lon").exists()) {
                        lon = reportSnapshot.child("lon").getValue(Double.class);
                    }

                    if (lat == null || lon == null) {
                        Log.w("LocationTracking", "Missing coordinates for report " + reportSnapshot.getKey() +
                                ", using default Bacoor location");
                        if (lat == null) lat = 14.4451;
                        if (lon == null) lon = 120.9511;
                    }

                    String category = reportSnapshot.child("category").getValue(String.class);
                    String description = reportSnapshot.child("description").getValue(String.class);
                    String reportId = reportSnapshot.getKey();
                    String userId = reportSnapshot.child("userId").getValue(String.class);

                    int upvotes = 0;
                    int downvotes = 0;

                    if (reportSnapshot.child("upvotes").exists()) {
                        Object upvotesObj = reportSnapshot.child("upvotes").getValue();
                        if (upvotesObj instanceof Long) {
                            upvotes = ((Long) upvotesObj).intValue();
                        } else if (upvotesObj instanceof Integer) {
                            upvotes = (Integer) upvotesObj;
                        }
                    }

                    if (reportSnapshot.child("downvotes").exists()) {
                        Object downvotesObj = reportSnapshot.child("downvotes").getValue();
                        if (downvotesObj instanceof Long) {
                            downvotes = ((Long) downvotesObj).intValue();
                        } else if (downvotesObj instanceof Integer) {
                            downvotes = (Integer) downvotesObj;
                        }
                    }

                    Log.d("ReportDebug", "Report ID: " + reportId +
                            ", Lat: " + lat + ", Lon: " + lon +
                            ", Upvotes: " + upvotes + ", Downvotes: " + downvotes);

                    if (upvotes > downvotes) {
                        confirmedReports.add(new Report(lat, lon, category, description, reportId, userId));
                        Log.d("Confirmation", "Added confirmed report: " + reportId);
                    }
                }
                Log.d("ConfirmedReports", "Total confirmed reports loaded: " + confirmedReports.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", "Failed to load reports: " + error.getMessage());
            }
        });
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    checkProximity(location);
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void checkProximity(Location userLocation) {
        Log.d("ProxCheck", "Checking proximity at: Lat=" + userLocation.getLatitude() + ", Lon=" + userLocation.getLongitude());

        for (Report report : confirmedReports) {
            if (notifiedReports.contains(report.reportId)) {
                continue;
            }

            float[] distance = new float[1];
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(),
                    report.lat, report.lon, distance);

            Log.d("ProxCheck", "Distance to report " + report.reportId + ": " + distance[0] + " meters");

            if (distance[0] <= 200) {
                sendProximityNotification(report);
                notifiedReports.add(report.reportId);
            }
        }
    }



    private void sendProximityNotification(Report report) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.alerts)
                .setContentTitle("Nearby Confirmed Report!")
                .setContentText("Category: " + report.category + "\n" + report.description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(2, builder.build());
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        super.onDestroy();
        setUserOffline();
    }

    private static class Report {
        double lat, lon;
        String category, description, reportId, userId;

        Report(double lat, double lon, String category, String description, String reportId, String userId) {
            this.lat = lat;
            this.lon = lon;
            this.category = category;
            this.description = description;
            this.reportId = reportId;
            this.userId = userId;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        setUserOffline();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private void setUserOffline() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(userId)
                    .child("status")
                    .setValue("offline");
        }
    }



}

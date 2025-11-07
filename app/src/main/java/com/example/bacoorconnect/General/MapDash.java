package com.example.bacoorconnect.General;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;

import com.example.bacoorconnect.Helpers.Mappart;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportActivity;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.UserProfile;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapDash extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    public double currentLat, currentLon;
    private NavigationView navigationView;
    private TextView locationTextView;
    private CardView reportButton;
    private ImageView menuIcon, dashNotif;
    private DatabaseReference auditRef;
    private FusedLocationProviderClient fusedLocationClient;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapdash);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        drawerLayout = findViewById(R.id.drawer_layout);
        locationTextView = findViewById(R.id.location_text);
        navigationView = findViewById(R.id.nav_view);
        reportButton = findViewById(R.id.report_button);
        dashNotif = findViewById(R.id.notification);

        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);
        reportButton.setEnabled(true);

        reportButton.setOnClickListener(v -> handleReportClick());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        NavigationHeader.setupNavigationHeader(this, navigationView);
        menuIcon = findViewById(R.id.menu_icon);
        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            if (isGuest && (item.getItemId() == R.id.nav_history || item.getItemId() == R.id.nav_profile)) {
                Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                return false;
            }

            Intent intent = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                intent = new Intent(this, Dashboard.class);
            } else if (id == R.id.Nav_Service) {
                intent = new Intent(this, services.class);
            } else if (id == R.id.nav_service) {
                intent = new Intent(this, weatherDash.class);
            } else if (id == R.id.nav_about) {
                intent = new Intent(this, AboutUs.class);
            } else if (id == R.id.nav_feedback) {
                intent = new Intent(this, contactus.class);
            } else if (id == R.id.nav_map) {
                intent = new Intent(this, MapDash.class);
            }else if (id == R.id.nav_history) {
                intent = new Intent(this, ReportHistoryActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, UserProfile.class);
            }

            if (intent != null) {
                intent.putExtra("isGuest", isGuest);
                startActivity(intent);
            }

            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        dashNotif.setOnClickListener(v -> {
            startActivity(new Intent(MapDash.this, NotificationCenter.class));
        });

        if (isGuest) {
            disableGuestFeatures();
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isAnonymous()) {
                setReportButtonState(false);
                Toast.makeText(this, "Guest users cannot submit reports", Toast.LENGTH_LONG).show();
            } else {
                currentUserId = currentUser.getUid();
                checkUserStrikesAndUpdateReportButton();
            }
        } else {
            setReportButtonState(false);
        }

        loadMapFragment();
    }

    private void disableGuestFeatures() {
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.nav_history).setVisible(false);
        menu.findItem(R.id.nav_profile).setVisible(false);
        Toast.makeText(this, "Guest mode: Limited access", Toast.LENGTH_SHORT).show();
    }
    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLon = location.getLongitude();
                        userLocation(currentLat, currentLon);
                    } else {
                        Toast.makeText(MapDash.this, "Location not available", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void userLocation(double lat, double lon) {
        currentLat = lat;
        currentLon = lon;

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressString = new StringBuilder();

                if (address.getThoroughfare() != null) {
                    addressString.append(address.getThoroughfare());
                }
                if (address.getLocality() != null) {
                    addressString.append(", ").append(address.getLocality());
                }
                if (address.getAdminArea() != null) {
                    addressString.append(", ").append(address.getAdminArea());
                }
                if (address.getCountryName() != null) {
                    addressString.append(", ").append(address.getCountryName());
                }

                locationTextView.setText(addressString.toString());

                logActivity(currentUserId, "Location", "Updated Location", "Location Text", "Success", "User's location updated", "N/A");
            } else {
                locationTextView.setText("Address not found");
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Unable to get address for location: " + e.getMessage());
            locationTextView.setText("Unable to get address");
        }
    }

    private String getCurrentUserID() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private void loadMapFragment() {
        Mappart mapFragment = new Mappart();
        Bundle args = new Bundle();
        args.putBoolean("reportButtonState", reportButton.isEnabled());
        args.putDouble("initialLat", currentLat);
        args.putDouble("initialLon", currentLon);
        mapFragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.map_placeholder, mapFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void handleReportClick() {
        Mappart mapFragment = (Mappart) getSupportFragmentManager().findFragmentById(R.id.map_placeholder);

        if (mapFragment != null) {
            GeoPoint reportLocation = mapFragment.getReportMarkerLocation();
            GeoPoint userLocation = mapFragment.getLastKnownUserLocation();

            if (userLocation == null) {
                Toast.makeText(this, "ErrorCheck: User location unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!mapFragment.isWithinBacoor(userLocation)) {
                setReportButtonState(false);
                new AlertDialog.Builder(this)
                        .setTitle("Reporting Disabled")
                        .setMessage("Sorry, you cannot report while outside Bacoor.")
                        .setPositiveButton("OK", null)
                        .show();

                logActivity(currentUserId, "Report Attempt", "Blocked", "Outside Bacoor", "Failed",
                        "User attempted to report outside allowed area", "N/A");
                return;
            }

            setReportButtonState(true);

            GeoPoint finalReportLocation = (reportLocation != null) ? reportLocation : userLocation;

            Intent intent = new Intent(MapDash.this, ReportActivity.class);
            intent.putExtra("location", "Lat: " + finalReportLocation.getLatitude() + ", Lon: " + finalReportLocation.getLongitude());
            intent.putExtra("lat", finalReportLocation.getLatitude());
            intent.putExtra("lon", finalReportLocation.getLongitude());

            startActivityForResult(intent, 100);

            logActivity(currentUserId, "Report Attempt", "User Report",
                    (reportLocation == null) ? "Placed Report at User Location" : "Placed Report at Pinned Location",
                    "Success", "User successfully accessed reporting", "N/A");

        } else {
            Toast.makeText(this, "ErrorCheck: Map fragment missing", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK) {
            Mappart mapFragment = (Mappart) getSupportFragmentManager().findFragmentById(R.id.map_placeholder);

            if (mapFragment != null && mapFragment.isAdded()) {
                mapFragment.removeCurrentMarker();
                Log.e("MapDash", "CurrentMarkerRemoved");
            } else {
                Log.e("MapDash", "Mappart fragment is not available or not added to fragment manager.");
            }
        }
    }

    private void checkUserStrikesAndUpdateReportButton() {
        DatabaseReference strikesRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUserId).child("strikes");

        long expirationPeriod = 7 * 24 * 60 * 60 * 1000;
        long currentTime = System.currentTimeMillis();

        strikesRef.get().addOnSuccessListener(dataSnapshot -> {
            int totalStrikes = 0;

            for (DataSnapshot strikeSnapshot : dataSnapshot.getChildren()) {
                Long count = strikeSnapshot.child("count").getValue(Long.class);
                Long timestamp = strikeSnapshot.child("timestamp").getValue(Long.class);

                if (count != null && timestamp != null) {
                    if (currentTime - timestamp <= expirationPeriod) {
                        totalStrikes += count;
                    }
                }
            }

            if (totalStrikes >= 5) {
                setReportButtonState(false);
                Toast.makeText(MapDash.this, "Reporting disabled: 5 or more active strikes", Toast.LENGTH_LONG).show();
            } else {
                setReportButtonState(true);
            }
        }).addOnFailureListener(e -> {
            Log.e("StrikeCheck", "Error retrieving user strikes", e);
            setReportButtonState(true);
        });
    }

    public void setReportButtonState(boolean isEnabled) {
        reportButton.setEnabled(isEnabled);
        reportButton.setAlpha(isEnabled ? 1.0f : 0.5f);
    }

    public void updateLocation(double lat, double lon) {
        currentLat = lat;
        currentLon = lon;
    }

    private void logActivity(String userId, String type, String action, String target,
                             String status, String notes, String changes) {
        if (userId == null) return;

        String logId = auditRef.push().getKey();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("dateTime", dateTime);
        logData.put("userId", userId);
        logData.put("type", type);
        logData.put("action", action);
        logData.put("target", target);
        logData.put("status", status);
        logData.put("notes", notes);
        logData.put("changes", changes);

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView);
        } else {
            super.onBackPressed();
        }
    }
}

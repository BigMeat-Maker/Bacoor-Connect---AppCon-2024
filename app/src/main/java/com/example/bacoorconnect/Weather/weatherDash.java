package com.example.bacoorconnect.Weather;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.bacoorconnect.Emergency.EarthquakeView;
import com.example.bacoorconnect.General.NavigationHandler;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.General.NotificationCenter;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportFeedActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class weatherDash extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon, DashNotif;
    private final String TAG = "WeatherDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.bacoorconnect.R.layout.activity_weather_dash);

        // UI setup
        menuIcon = findViewById(com.example.bacoorconnect.R.id.menu_icon);
        drawerLayout = findViewById(com.example.bacoorconnect.R.id.drawer_layout);
        navigationView = findViewById(com.example.bacoorconnect.R.id.nav_view);
        DashNotif = findViewById(com.example.bacoorconnect.R.id.notification);

        setupNavigation();

        BottomNavigationView serviceNavigation = findViewById(com.example.bacoorconnect.R.id.bottomservice_navigation);
        serviceNavigation.setSelectedItemId(com.example.bacoorconnect.R.id.nav_weather);

        serviceNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == com.example.bacoorconnect.R.id.nav_weather) return true;

            if (itemId == com.example.bacoorconnect.R.id.nav_earthquake) {
                startActivity(new Intent(this, EarthquakeView.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == com.example.bacoorconnect.R.id.nav_RL) {
                startActivity(new Intent(this, ReportFeedActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });

        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);
        if (isGuest) {
            disableGuestFeatures();
        }

        loadDailyWeatherFragment();
        loadHourlyWeatherFragment();
    }

    private void setupNavigation() {
        NavigationHeader.setupNavigationHeader(this, navigationView);
        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            if (isGuest && (item.getItemId() == com.example.bacoorconnect.R.id.nav_history || item.getItemId() == com.example.bacoorconnect.R.id.nav_profile)) {
                Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                return false;
            }

            NavigationHandler navigationHandler = new NavigationHandler(this, drawerLayout);
            navigationHandler.handleMenuSelection(item);
            return true;
        });

        DashNotif.setOnClickListener(v -> {
            startActivity(new Intent(weatherDash.this, NotificationCenter.class));
        });
    }

    private void disableGuestFeatures() {
        Menu menu = navigationView.getMenu();
        menu.findItem(com.example.bacoorconnect.R.id.nav_history).setVisible(false);
        menu.findItem(com.example.bacoorconnect.R.id.nav_profile).setVisible(false);

        Toast.makeText(this, "Guest mode: Limited access", Toast.LENGTH_SHORT).show();
    }

    private void loadDailyWeatherFragment() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("WeatherForecast");
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DailyForecast> dailyList = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    DailyForecast forecast = snap.getValue(DailyForecast.class);
                    if (forecast != null) dailyList.add(forecast);
                }
                if (!dailyList.isEmpty()) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(com.example.bacoorconnect.R.id.DailyWeather, WeatherDaily.newInstance(dailyList))
                            .commit();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Daily forecast error: " + error.getMessage());
            }
        });
    }

    private void loadHourlyWeatherFragment() {
        DatabaseReference hourlyRef = FirebaseDatabase.getInstance().getReference("HourlyForecast");
        hourlyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<HourlyForecast> hourlyList = new ArrayList<>();
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    for (DataSnapshot timeSnapshot : dateSnapshot.getChildren()) {
                        HourlyForecast forecast = timeSnapshot.getValue(HourlyForecast.class);
                        if (forecast != null) hourlyList.add(forecast);
                    }
                }
                if (!hourlyList.isEmpty()) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.HourlyWeather, WeatherHourly.newInstance(hourlyList))
                            .commit();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Hourly forecast error: " + error.getMessage());
            }
        });
    }
}
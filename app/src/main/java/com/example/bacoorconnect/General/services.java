package com.example.bacoorconnect.General;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.Emergency.EarthquakeView;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportFeedActivity;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class services extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private DatabaseReference auditRef;
    private String currentUserId;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        Button reportlistBtn = findViewById(R.id.nav_RL);
        Button earthquakesBtn = findViewById(R.id.nav_earthquake);
        Button weatherBtn = findViewById(R.id.nav_weather);

        reportlistBtn.setOnClickListener(v -> {
            loadFragment(ReportFeedActivity.newInstance());
        });

        earthquakesBtn.setOnClickListener(v -> {
            loadFragment(EarthquakeView.newInstance());
        });

        weatherBtn.setOnClickListener(v -> {
            loadFragment(weatherDash.newInstance());
        });

        // Setup bottom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.nav_service);
        } else {
            android.util.Log.e("ServicesActivity", "BottomNavigationView not found. Check layout ID.");
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}

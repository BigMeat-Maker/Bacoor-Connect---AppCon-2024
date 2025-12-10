package com.example.bacoorconnect.Emergency;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.bacoorconnect.General.BottomNavHelper;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.General.NotificationCenter;
import com.example.bacoorconnect.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmergencyResources extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private String currentUserId;
    private boolean isGuest = false;
    private Button hotlinesBtn, hospitalsBtn, guidelinesBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergencyresources);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            isGuest = false;
        } else {
            isGuest = true;
        }

        hotlinesBtn = findViewById(R.id.nav_hotlines);
        hospitalsBtn = findViewById(R.id.nav_hospitals);
        guidelinesBtn = findViewById(R.id.nav_guidelines);

        hotlinesBtn.setSelected(true);

        hotlinesBtn.setOnClickListener(v -> {
            loadFragment(new EmergencyHotlines());
            resetTabs();
            hotlinesBtn.setSelected(true);
        });

        hospitalsBtn.setOnClickListener(v -> {
            loadFragment(new EmergencyHospitals());
            resetTabs();
            hospitalsBtn.setSelected(true);
        });

        guidelinesBtn.setOnClickListener(v -> {
            loadFragment(new EmergencyGuides());
            resetTabs();
            guidelinesBtn.setSelected(true);
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.nav_service);

        if (isGuest) {
            disableGuestFeatures();
        }

        loadFragment(new EmergencyHotlines());
    }

    private void disableGuestFeatures() {
        // Guest mode restrictions now handled in Emergency Hotlines fragment
        bottomNavigationView.getMenu().findItem(R.id.nav_ri).setEnabled(false);
        bottomNavigationView.getMenu().findItem(R.id.nav_ri).setVisible(false);

        Toast.makeText(this, "Guest mode: Limited access", Toast.LENGTH_SHORT).show();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_service);
        }
    }

    private void resetTabs() {
        hotlinesBtn.setSelected(false);
        hospitalsBtn.setSelected(false);
        guidelinesBtn.setSelected(false);
    }

}
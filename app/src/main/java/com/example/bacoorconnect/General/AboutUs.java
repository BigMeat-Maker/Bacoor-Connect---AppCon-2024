package com.example.bacoorconnect.General;

import android.content.Intent;
import android.os.Bundle;

import com.example.bacoorconnect.Emergency.EmergencyResources;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.UserProfile;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.material.navigation.NavigationView;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

public class AboutUs extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private ImageView menuIcon;
    private NavigationView navigationView;
    private ImageView DashNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
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
            if (isGuest && (item.getItemId() == R.id.nav_history || item.getItemId() == R.id.nav_profile)) {
                Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                return false;
            }

            Intent intent = null;
            if (item.getItemId() == R.id.nav_home) {
                intent = new Intent(this, Dashboard.class);
            } else if (item.getItemId() == R.id.nav_service) {
                intent = new Intent(this, EmergencyResources.class);
            } else if (item.getItemId() == R.id.nav_service) {
                intent = new Intent(this, weatherDash.class);
            } else if (item.getItemId() == R.id.nav_profile) {
                intent = new Intent(this, UserProfile.class);
            } else if (item.getItemId() == R.id.nav_about) {
                intent = new Intent(this, AboutUs.class);
            } else if (item.getItemId() == R.id.nav_history) {
                intent = new Intent(this, ReportHistoryActivity.class);
            } else if (item.getItemId() == R.id.nav_feedback) {
                intent = new Intent(this, contactus.class);
            } else if (item.getItemId() == R.id.nav_map) {
                intent = new Intent(this, MapDash.class);
            }

            if (intent != null) {
                intent.putExtra("isGuest", isGuest);
                startActivity(intent);
            }

            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        DashNotif = findViewById(R.id.notification);

        DashNotif.setOnClickListener(v -> {
            Intent intent = new Intent(AboutUs.this, NotificationCenter.class);
            startActivity(intent);
        });

        if (isGuest) {
            disableGuestFeatures();
        }
    }

    private void disableGuestFeatures() {
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.nav_history).setVisible(false);
        menu.findItem(R.id.nav_profile).setVisible(false);

        Toast.makeText(this, "Guest mode: Limited access", Toast.LENGTH_SHORT).show();
    }
}

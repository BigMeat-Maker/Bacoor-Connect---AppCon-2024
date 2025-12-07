package com.example.bacoorconnect.General;

import android.content.Intent;
import android.os.Bundle;

import com.example.bacoorconnect.Emergency.EmergencyResources;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.UserProfile;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

public class AboutUs extends AppCompatActivity {
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.nav_service);
        } else {
            android.util.Log.e("ServicesActivity", "BottomNavigationView not found. Check layout ID.");
        }
    }

}

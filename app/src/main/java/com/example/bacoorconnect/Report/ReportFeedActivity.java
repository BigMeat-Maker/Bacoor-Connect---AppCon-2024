package com.example.bacoorconnect.Report;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.Emergency.EarthquakeView;
import com.example.bacoorconnect.General.NavigationHandler;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.General.NotificationCenter;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportFeedActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<Report> reportList = new ArrayList<>();
    private DatabaseReference reportRef = FirebaseDatabase.getInstance().getReference("Report");
    private DrawerLayout drawerLayout;
    private BottomNavigationView bottomNavigationView;
    private NavigationView navigationView;
    private ImageView menuIcon, DashNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_feed);

        recyclerView = findViewById(R.id.reportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(this, reportList);
        recyclerView.setAdapter(adapter);

        loadReports();

        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        NavigationHeader.setupNavigationHeader(this, navigationView);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            NavigationHandler navigationHandler = new NavigationHandler(this, drawerLayout);
            navigationHandler.handleMenuSelection(item);
            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        DashNotif = findViewById(R.id.notification);

        DashNotif.setOnClickListener(v -> {
            Intent intent = new Intent(ReportFeedActivity.this, NotificationCenter.class);
            startActivity(intent);
        });

        BottomNavigationView serviceNavigation = findViewById(R.id.bottomservice_navigation);
        serviceNavigation.setSelectedItemId(R.id.nav_RL);

        serviceNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_weather) {
                startActivity(new Intent(ReportFeedActivity.this, weatherDash.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_earthquake) {
                startActivity(new Intent(ReportFeedActivity.this, EarthquakeView.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_RL) {
                return true;
            }
            return false;
        });


    }

    private void loadReports() {
        reportRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reportList.clear();
                for (DataSnapshot reportSnap : snapshot.getChildren()) {
                    Report report = reportSnap.getValue(Report.class);
                    if (report != null) {
                        report.setReportId(reportSnap.getKey());
                        reportList.add(report);
                    }
                }
                Collections.reverse(reportList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ReportFeedActivity.this, "Failed to load reports.", Toast.LENGTH_SHORT).show();
            }
        });
    }


}

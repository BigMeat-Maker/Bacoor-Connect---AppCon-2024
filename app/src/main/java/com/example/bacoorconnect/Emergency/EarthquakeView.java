package com.example.bacoorconnect.Emergency;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.General.NavigationHandler;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.General.NotificationCenter;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportFeedActivity;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class EarthquakeView extends AppCompatActivity {

    private RecyclerView earthquakeRecyclerView;
    private EarthquakeAdapter earthquakeAdapter;
    private List<Earthquake> earthquakeList;
    private BottomNavigationView bottomNavigationView;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon, DashNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earthquake_view);

        earthquakeRecyclerView = findViewById(R.id.earthquakeRecyclerView);
        earthquakeRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        earthquakeList = new ArrayList<>();
        earthquakeAdapter = new EarthquakeAdapter(earthquakeList);
        earthquakeRecyclerView.setAdapter(earthquakeAdapter);

        fetchEarthquakeData();

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
            Intent intent = new Intent(EarthquakeView.this, NotificationCenter.class);
            startActivity(intent);
        });

        BottomNavigationView serviceNavigation = findViewById(R.id.bottomservice_navigation);
        serviceNavigation.setSelectedItemId(R.id.nav_earthquake);
        serviceNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_weather) {
                startActivity(new Intent(EarthquakeView.this, weatherDash.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_earthquake) {
                return true;
            } else if (itemId == R.id.nav_RL) {
                startActivity(new Intent(EarthquakeView.this, ReportFeedActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void fetchEarthquakeData() {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("Earthquakes");

        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                earthquakeList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Earthquake earthquake = dataSnapshot.getValue(Earthquake.class);
                    if (earthquake != null) {
                        earthquakeList.add(earthquake);
                    }
                }
                earthquakeAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("EarthquakeView", "Database Error: " + error.getMessage());
            }
        });
    }

    // Earthquake Data Model
    public static class Earthquake {
        private String date;
        private String location;
        private String magnitude;
        private String time;

        public Earthquake() {

        }

        public Earthquake(String date, String location, String magnitude, String time) {
            this.date = date;
            this.location = location;
            this.magnitude = magnitude;
            this.time = time;
        }

        public String getDate() { return date; }
        public String getLocation() { return location; }
        public String getMagnitude() { return magnitude; }
        public String getTime() { return time; }

        public void setDate(String date) { this.date = date; }
        public void setLocation(String location) { this.location = location; }
        public void setMagnitude(String magnitude) { this.magnitude = magnitude; }
        public void setTime(String time) { this.time = time; }
    }

    // RecyclerView Adapter
    public class EarthquakeAdapter extends RecyclerView.Adapter<EarthquakeAdapter.EarthquakeViewHolder> {
        private List<Earthquake> earthquakeList;

        public EarthquakeAdapter(List<Earthquake> earthquakeList) {
            this.earthquakeList = earthquakeList;
        }

        @NonNull
        @Override
        public EarthquakeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_earthquake_adapter, parent, false);
            return new EarthquakeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EarthquakeViewHolder holder, int position) {
            Earthquake earthquake = earthquakeList.get(position);
            holder.magnitudeText.setText("Magnitude: " + earthquake.getMagnitude());
            holder.locationText.setText("Location: " + earthquake.getLocation());
            holder.timeText.setText("Time: " + earthquake.getTime());
            holder.dateText.setText("Date: " + earthquake.getDate());
        }

        @Override
        public int getItemCount() {
            return earthquakeList.size();
        }

        public class EarthquakeViewHolder extends RecyclerView.ViewHolder {
            TextView magnitudeText, locationText, timeText, dateText;

            public EarthquakeViewHolder(View itemView) {
                super(itemView);
                magnitudeText = itemView.findViewById(R.id.magnitudeText);
                locationText = itemView.findViewById(R.id.locationText);
                timeText = itemView.findViewById(R.id.timeText);
            }
        }
    }
}

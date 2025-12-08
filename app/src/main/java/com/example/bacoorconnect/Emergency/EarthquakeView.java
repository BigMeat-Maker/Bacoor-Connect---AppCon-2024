package com.example.bacoorconnect.Emergency;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class EarthquakeView extends Fragment {

    private RecyclerView earthquakeRecyclerView;
    private EarthquakeAdapter earthquakeAdapter;
    private List<Earthquake> earthquakeList;

    public EarthquakeView() {
        // Required empty public constructor
    }

    public static EarthquakeView newInstance() {
        return new EarthquakeView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout
        View view = inflater.inflate(R.layout.activity_earthquake_view, container, false);

        earthquakeRecyclerView = view.findViewById(R.id.earthquakeRecyclerView);
        earthquakeRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        earthquakeList = new ArrayList<>();
        earthquakeAdapter = new EarthquakeAdapter(earthquakeList);
        earthquakeRecyclerView.setAdapter(earthquakeAdapter);

        fetchEarthquakeData();

        return view;
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
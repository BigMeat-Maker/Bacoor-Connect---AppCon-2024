package com.example.bacoorconnect.Emergency;

import android.Manifest;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EarthquakeView extends Fragment {

    private RecyclerView earthquakeRecyclerView;
    private EarthquakeAdapter earthquakeAdapter;
    private List<Object> displayList = new ArrayList<>();
    private List<Earthquake> allEarthquakes = new ArrayList<>();
    private List<Earthquake> olderQuakes = new ArrayList<>();
    private WeeklySummary weeklySummary;
    private FusedLocationProviderClient fusedLocationClient;
    private Double userLatitude;
    private Double userLongitude;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    fetchUserLocationAndData();
                } else {
                    fetchEarthquakeData();
                }
            });

    public EarthquakeView() {
    }

    public static EarthquakeView newInstance() {
        return new EarthquakeView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_earthquake, container, false);
        earthquakeRecyclerView = view.findViewById(R.id.earthquakeRecyclerView);
        earthquakeRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        earthquakeAdapter = new EarthquakeAdapter();
        earthquakeRecyclerView.setAdapter(earthquakeAdapter);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        fetchUserLocationAndData();
        return view;
    }

    private void fetchUserLocationAndData() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            userLatitude = location.getLatitude();
                            userLongitude = location.getLongitude();
                            fetchEarthquakeData();
                        } else {
                            requestCurrentLocationAndFetch();
                        }
                    })
                    .addOnFailureListener(error -> {
                        Log.w("EarthquakeView", "Unable to get last known location", error);
                        fetchEarthquakeData();
                    });
        } catch (SecurityException ex) {
            Log.w("EarthquakeView", "Location permission unavailable at runtime", ex);
            fetchEarthquakeData();
        }
    }

    private void requestCurrentLocationAndFetch() {
        if (!hasLocationPermission()) {
            fetchEarthquakeData();
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            userLatitude = location.getLatitude();
                            userLongitude = location.getLongitude();
                        }
                        fetchEarthquakeData();
                    })
                    .addOnFailureListener(error -> {
                        Log.w("EarthquakeView", "Unable to get current location", error);
                        fetchEarthquakeData();
                    });
        } catch (SecurityException ex) {
            Log.w("EarthquakeView", "Location permission unavailable at runtime", ex);
            fetchEarthquakeData();
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void fetchEarthquakeData() {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference("Earthquakes");
        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allEarthquakes.clear();
                displayList.clear();
                olderQuakes.clear();

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                List<Earthquake> todaysQuakes = new ArrayList<>();
                boolean hasCoordinatesInDataset = false;

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Earthquake earthquake = dataSnapshot.getValue(Earthquake.class);
                    if (earthquake != null) {
                        earthquake.setLatitude(readCoordinate(dataSnapshot, "latitude", "lat"));
                        earthquake.setLongitude(readCoordinate(dataSnapshot, "longitude", "lon", "lng"));
                        if (earthquake.hasCoordinates()) {
                            hasCoordinatesInDataset = true;
                        }
                        allEarthquakes.add(earthquake);

                        if (today.equals(earthquake.getDate())) {
                            todaysQuakes.add(earthquake);
                        } else {
                            olderQuakes.add(earthquake);
                        }
                    }
                }

                if (canSortByDistance(hasCoordinatesInDataset)) {
                    todaysQuakes.sort((q1, q2) -> Double.compare(getDistanceKm(q1), getDistanceKm(q2)));
                    olderQuakes.sort((q1, q2) -> Double.compare(getDistanceKm(q1), getDistanceKm(q2)));
                } else {
                    olderQuakes.sort((q1, q2) -> safeString(q2.getDate()).compareTo(safeString(q1.getDate())));
                }

                weeklySummary = calculateWeeklySummary(allEarthquakes);

                displayList.add("TODAY_HEADER");

                if (!todaysQuakes.isEmpty()) {
                    for (Earthquake quake : todaysQuakes) {
                        displayList.add(quake);
                    }
                } else {
                    displayList.add("NO_QUAKES_TODAY");
                }

                displayList.add("WEEKLY_HEADER");

                displayList.add(weeklySummary);

                if (!olderQuakes.isEmpty()) {
                    displayList.add("HORIZONTAL_LIST");
                }

                earthquakeAdapter.setData(displayList, weeklySummary, olderQuakes);
                earthquakeAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("EarthquakeView", "Database Error: " + error.getMessage());
            }
        });
    }

    private WeeklySummary calculateWeeklySummary(List<Earthquake> earthquakes) {
        WeeklySummary summary = new WeeklySummary();

        if (earthquakes.isEmpty()) {
            summary.totalQuakes = 0;
            summary.averageMagnitude = 0.0;
            summary.strongestMagnitude = 0.0;
            return summary;
        }

        double totalMagnitude = 0;
        double strongest = 0;

        for (Earthquake quake : earthquakes) {
            try {
                double mag = Double.parseDouble(quake.getMagnitude());
                totalMagnitude += mag;
                if (mag > strongest) {
                    strongest = mag;
                }
            } catch (NumberFormatException e) {
                Log.w("EarthquakeCalc", "Invalid magnitude format: " + quake.getMagnitude());
            }
        }

        summary.totalQuakes = earthquakes.size();
        summary.averageMagnitude = earthquakes.size() > 0 ? totalMagnitude / earthquakes.size() : 0;
        summary.strongestMagnitude = strongest;

        return summary;
    }

    private Double readCoordinate(DataSnapshot dataSnapshot, String... keys) {
        for (String key : keys) {
            DataSnapshot child = dataSnapshot.child(key);
            if (!child.exists()) {
                continue;
            }

            Object value = child.getValue();
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }

            if (value instanceof String) {
                try {
                    return Double.parseDouble(((String) value).trim());
                } catch (NumberFormatException ignored) {
                    // Try next key format.
                }
            }
        }
        return null;
    }

    private boolean canSortByDistance(boolean hasCoordinatesInDataset) {
        return hasCoordinatesInDataset && userLatitude != null && userLongitude != null;
    }

    private double getDistanceKm(Earthquake earthquake) {
        if (userLatitude == null || userLongitude == null || earthquake == null || !earthquake.hasCoordinates()) {
            return Double.MAX_VALUE;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                userLatitude,
                userLongitude,
                earthquake.getLatitude(),
                earthquake.getLongitude(),
                results
        );
        return results[0] / 1000d;
    }

    private String formatLocationWithDistance(Earthquake earthquake) {
        String location = safeString(earthquake != null ? earthquake.getLocation() : null);
        if (location.isEmpty()) {
            location = "Unknown location";
        }

        double distanceKm = getDistanceKm(earthquake);
        if (distanceKm == Double.MAX_VALUE) {
            return location;
        }

        return String.format(Locale.getDefault(), "%s (%.1f km)", location, distanceKm);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public static class Earthquake {
        private String date;
        private String location;
        private String magnitude;
        private String time;
        private Double latitude;
        private Double longitude;

        public Earthquake() {}

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
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }

        public void setDate(String date) { this.date = date; }
        public void setLocation(String location) { this.location = location; }
        public void setMagnitude(String magnitude) { this.magnitude = magnitude; }
        public void setTime(String time) { this.time = time; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }

        public int getMagnitudeColor() {
            try {
                // Clean the magnitude string
                String cleanMag = magnitude.replaceAll("[^0-9.]", "").trim();
                double mag = Double.parseDouble(cleanMag);

                if (mag < 3.0) {
                    return R.color.magnitude_low; // Light green/blue
                } else if (mag < 5.0) {
                    return R.color.magnitude_medium; // Yellow
                } else if (mag < 7.0) {
                    return R.color.magnitude_high; // Orange
                } else {
                    return R.color.magnitude_extreme; // Red
                }
            } catch (NumberFormatException | NullPointerException e) {
                return R.color.magnitude_default; // Gray for invalid
            }
        }

        // Helper method to parse magnitude as double
        public double getMagnitudeAsDouble() {
            try {
                String cleanMag = magnitude.replaceAll("[^0-9.]", "").trim();
                return Double.parseDouble(cleanMag);
            } catch (NumberFormatException | NullPointerException e) {
                return 0.0;
            }
        }
    }

    // Weekly Summary Model
    public static class WeeklySummary {
        public int totalQuakes;
        public double averageMagnitude;
        public double strongestMagnitude;
    }

    public class EarthquakeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_TODAY_HEADER = 0;
        private static final int TYPE_TODAY_QUAKE = 1;
        private static final int TYPE_NO_QUAKES = 2;
        private static final int TYPE_WEEKLY_HEADER = 3;
        private static final int TYPE_WEEKLY_SUMMARY = 4;
        private static final int TYPE_HORIZONTAL_LIST = 5;

        private List<Object> displayItems = new ArrayList<>();
        private WeeklySummary weeklySummary;
        private List<Earthquake> olderQuakes = new ArrayList<>();

        public void setData(List<Object> items, WeeklySummary summary, List<Earthquake> olderQuakes) {
            this.displayItems = items;
            this.weeklySummary = summary;
            this.olderQuakes = olderQuakes;
        }

        @Override
        public int getItemViewType(int position) {
            Object item = displayItems.get(position);

            if (item instanceof String) {
                String str = (String) item;
                if (str.equals("TODAY_HEADER")) return TYPE_TODAY_HEADER;
                if (str.equals("NO_QUAKES_TODAY")) return TYPE_NO_QUAKES;
                if (str.equals("WEEKLY_HEADER")) return TYPE_WEEKLY_HEADER;
                if (str.equals("HORIZONTAL_LIST")) return TYPE_HORIZONTAL_LIST;
            }

            if (item instanceof Earthquake) {
                return TYPE_TODAY_QUAKE;
            }

            if (item instanceof WeeklySummary) return TYPE_WEEKLY_SUMMARY;

            return TYPE_TODAY_QUAKE; // Default
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            switch (viewType) {
                case TYPE_TODAY_HEADER:
                    View headerView = inflater.inflate(R.layout.item_earthquake_header, parent, false);
                    return new HeaderViewHolder(headerView);

                case TYPE_TODAY_QUAKE:
                    View todayView = inflater.inflate(R.layout.item_earthquake_today, parent, false);
                    return new TodayQuakeViewHolder(todayView);

                case TYPE_NO_QUAKES:
                    View noQuakesView = inflater.inflate(R.layout.item_no_quakes, parent, false);
                    return new NoQuakesViewHolder(noQuakesView);

                case TYPE_WEEKLY_HEADER:
                    View weeklyHeaderView = inflater.inflate(R.layout.item_weekly_header, parent, false);
                    return new WeeklyHeaderViewHolder(weeklyHeaderView);

                case TYPE_WEEKLY_SUMMARY:
                    View summaryView = inflater.inflate(R.layout.item_weekly_summary, parent, false);
                    return new WeeklySummaryViewHolder(summaryView);

                case TYPE_HORIZONTAL_LIST:
                    View horizontalView = inflater.inflate(R.layout.item_horizontal_list, parent, false);
                    return new HorizontalListViewHolder(horizontalView);

                default:
                    View defaultView = inflater.inflate(R.layout.item_earthquake_today, parent, false);
                    return new TodayQuakeViewHolder(defaultView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = displayItems.get(position);

            switch (holder.getItemViewType()) {
                case TYPE_TODAY_QUAKE:
                    Earthquake todayQuake = (Earthquake) item;
                    TodayQuakeViewHolder todayHolder = (TodayQuakeViewHolder) holder;
                    todayHolder.magnitudeText.setText("M " + safeString(todayQuake.getMagnitude()));
                    todayHolder.locationText.setText(formatLocationWithDistance(todayQuake));
                    todayHolder.timeText.setText(safeString(todayQuake.getTime()));

                    // DEBUG: Log the magnitude value
                    Log.d("EarthquakeDebug", "Today Quake - Magnitude: " + todayQuake.getMagnitude() +
                            ", Cleaned: " + todayQuake.getMagnitudeAsDouble());

                    // Apply magnitude-based color
                    applyMagnitudeColor(todayQuake, todayHolder.magnitudeText);
                    break;

                case TYPE_WEEKLY_SUMMARY:
                    WeeklySummaryViewHolder summaryHolder = (WeeklySummaryViewHolder) holder;
                    if (weeklySummary != null) {
                        summaryHolder.totalQuakesText.setText(String.valueOf(weeklySummary.totalQuakes));
                        summaryHolder.averageMagText.setText(String.format("%.1f", weeklySummary.averageMagnitude));
                        summaryHolder.strongestMagText.setText(String.format("%.1f", weeklySummary.strongestMagnitude));

                        // Apply color to strongest magnitude
                        applyMagnitudeColorToStrongest(weeklySummary.strongestMagnitude, summaryHolder.strongestMagText);
                    }
                    break;

                case TYPE_HORIZONTAL_LIST:
                    HorizontalListViewHolder horizontalHolder = (HorizontalListViewHolder) holder;
                    setupHorizontalRecyclerView(horizontalHolder.horizontalRecyclerView);
                    break;
            }
        }

        private void applyMagnitudeColor(Earthquake quake, TextView magnitudeText) {
            try {
                double mag = quake.getMagnitudeAsDouble();
                int colorRes;

                if (mag < 3.0) {
                    colorRes = R.color.magnitude_low;
                } else if (mag < 5.0) {
                    colorRes = R.color.magnitude_medium;
                } else if (mag < 7.0) {
                    colorRes = R.color.magnitude_high;
                } else {
                    colorRes = R.color.magnitude_extreme;
                }

                // Get the actual color
                int color = ContextCompat.getColor(magnitudeText.getContext(), colorRes);

                // Change the background color
                GradientDrawable background = (GradientDrawable) magnitudeText.getBackground();
                if (background != null) {
                    background.setColor(color);
                }

                // Keep text white for contrast
                magnitudeText.setTextColor(Color.WHITE);

                Log.d("EarthquakeColor", "Changed circle color for magnitude " + mag);

            } catch (Exception e) {
                Log.e("EarthquakeColor", "Error applying color: " + e.getMessage());
            }
        }

        private void applyMagnitudeColorToStrongest(double magnitude, TextView magnitudeText) {
            int colorRes;
            if (magnitude < 3.0) {
                colorRes = R.color.magnitude_low;
            } else if (magnitude < 5.0) {
                colorRes = R.color.magnitude_medium;
            } else if (magnitude < 7.0) {
                colorRes = R.color.magnitude_high;
            } else {
                colorRes = R.color.magnitude_extreme;
            }
            magnitudeText.setTextColor(ContextCompat.getColor(magnitudeText.getContext(), colorRes));
        }

        private void setupHorizontalRecyclerView(RecyclerView horizontalRecyclerView) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(
                    horizontalRecyclerView.getContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false
            );
            horizontalRecyclerView.setLayoutManager(layoutManager);

            HorizontalQuakeAdapter horizontalAdapter = new HorizontalQuakeAdapter();
            horizontalAdapter.setData(olderQuakes);
            horizontalRecyclerView.setAdapter(horizontalAdapter);
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        // ViewHolder classes
        class HeaderViewHolder extends RecyclerView.ViewHolder {
            HeaderViewHolder(View itemView) { super(itemView); }
        }

        class TodayQuakeViewHolder extends RecyclerView.ViewHolder {
            TextView magnitudeText, locationText, timeText;

            TodayQuakeViewHolder(View itemView) {
                super(itemView);
                magnitudeText = itemView.findViewById(R.id.magnitudeText);
                locationText = itemView.findViewById(R.id.locationText);
                timeText = itemView.findViewById(R.id.timeText);
            }
        }

        class NoQuakesViewHolder extends RecyclerView.ViewHolder {
            NoQuakesViewHolder(View itemView) { super(itemView); }
        }

        class WeeklyHeaderViewHolder extends RecyclerView.ViewHolder {
            WeeklyHeaderViewHolder(View itemView) { super(itemView); }
        }

        class WeeklySummaryViewHolder extends RecyclerView.ViewHolder {
            TextView totalQuakesText, averageMagText, strongestMagText;

            WeeklySummaryViewHolder(View itemView) {
                super(itemView);
                totalQuakesText = itemView.findViewById(R.id.totalQuakesText);
                averageMagText = itemView.findViewById(R.id.averageMagText);
                strongestMagText = itemView.findViewById(R.id.strongestMagText);
            }
        }

        class HorizontalListViewHolder extends RecyclerView.ViewHolder {
            RecyclerView horizontalRecyclerView;

            HorizontalListViewHolder(View itemView) {
                super(itemView);
                horizontalRecyclerView = itemView.findViewById(R.id.horizontalRecyclerView);
            }
        }
    }

    // Horizontal RecyclerView Adapter
    public class HorizontalQuakeAdapter extends RecyclerView.Adapter<HorizontalQuakeAdapter.HorizontalQuakeViewHolder> {

        private List<Earthquake> earthquakeList = new ArrayList<>();

        public void setData(List<Earthquake> earthquakes) {
            this.earthquakeList = earthquakes;
        }

        @NonNull
        @Override
        public HorizontalQuakeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_horizontal_quake, parent, false);
            return new HorizontalQuakeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HorizontalQuakeViewHolder holder, int position) {
            Earthquake earthquake = earthquakeList.get(position);
            holder.magnitudeText.setText("M " + safeString(earthquake.getMagnitude()));

            // Shorten location
            String location = formatLocationWithDistance(earthquake);
            if (location.length() > 30) {
                location = location.substring(0, 27) + "...";
            }
            holder.locationText.setText(location);
            holder.dateText.setText(safeString(earthquake.getDate()));

            // Apply magnitude-based BACKGROUND color
            try {
                double mag = earthquake.getMagnitudeAsDouble();
                int colorRes;

                if (mag < 3.0) {
                    colorRes = R.color.magnitude_low;
                } else if (mag < 5.0) {
                    colorRes = R.color.magnitude_medium;
                } else if (mag < 7.0) {
                    colorRes = R.color.magnitude_high;
                } else {
                    colorRes = R.color.magnitude_extreme;
                }

                int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);

                // Change the circle background color
                GradientDrawable background = (GradientDrawable) holder.magnitudeText.getBackground();
                if (background != null) {
                    background.setColor(color);
                }

                // Keep text white
                holder.magnitudeText.setTextColor(Color.WHITE);

            } catch (Exception e) {
                // Default fallback
                GradientDrawable background = (GradientDrawable) holder.magnitudeText.getBackground();
                if (background != null) {
                    background.setColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.magnitude_default));
                }
            }
        }

        @Override
        public int getItemCount() {
            return earthquakeList.size();
        }

        class HorizontalQuakeViewHolder extends RecyclerView.ViewHolder {
            TextView magnitudeText, locationText, dateText;

            HorizontalQuakeViewHolder(View itemView) {
                super(itemView);
                magnitudeText = itemView.findViewById(R.id.magnitudeText);
                locationText = itemView.findViewById(R.id.locationText);
                dateText = itemView.findViewById(R.id.dateText);
            }
        }
    }
}
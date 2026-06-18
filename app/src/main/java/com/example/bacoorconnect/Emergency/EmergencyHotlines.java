package com.example.bacoorconnect.Emergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.General.AboutUs;
import com.example.bacoorconnect.General.Dashboard;
import com.example.bacoorconnect.General.FrontpageActivity;
import com.example.bacoorconnect.General.MapDash;
import com.example.bacoorconnect.Helpers.GooglePlacesConfig;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportIncident;
import com.example.bacoorconnect.UserProfile;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EmergencyHotlines extends Fragment implements HotlineAdapter.OnHotlineInteractionListener {

    private DatabaseReference auditRef;
    private RecyclerView hotlinesRecyclerView;
    private HotlineAdapter adapter;
    private List<Hotline> hotlineList;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation = null;
    private boolean hasFetchedLocation = false;
    private boolean hasFetchedHotlines = false;

    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    fetchUserLocation();
                } else {
                    useDefaultLocation();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency_hotlines, container, false);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hotlines", "Emergency Resources", "Success", "User accessed the emergency resources: hotlines page", "N/A");

        hotlinesRecyclerView = view.findViewById(R.id.hotlinesRecyclerView);
        hotlineList = new ArrayList<>();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        if (hasLocationPermission()) {
            fetchUserLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }

        // Setup quick access click listeners
        setupQuickAccessListeners(view);

        return view;
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchUserLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    processLocation(location);
                } else {
                    CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.getToken())
                            .addOnSuccessListener(loc -> {
                                if (loc != null) processLocation(loc);
                                else useDefaultLocation();
                            })
                            .addOnFailureListener(e -> useDefaultLocation());
                }
            }).addOnFailureListener(e -> useDefaultLocation());
        } catch (SecurityException e) {
            useDefaultLocation();
        }
    }

    private void useDefaultLocation() {
        Location defaultLocation = new Location("fallback");
        // Coordinates for default Bacoor location
        defaultLocation.setLatitude(14.422);
        defaultLocation.setLongitude(120.945);
        processLocation(defaultLocation);
    }

    private void processLocation(Location location) {
        currentUserLocation = location;
        hasFetchedLocation = true;
        fetchEmergencyPlacesFromGoogle(location);
    }

    private void fetchEmergencyPlacesFromGoogle(Location location) {
        String apiKey = GooglePlacesConfig.getApiKey(getContext());
        if (apiKey == null || apiKey.isEmpty()) {
            fetchHotlinesFromFirebase();
            return;
        }

        // We'll fetch police and fire stations
        String policeUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + location.getLatitude() + "," + location.getLongitude() +
                "&radius=5000&type=police&key=" + apiKey;
        
        String fireUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + location.getLatitude() + "," + location.getLongitude() +
                "&radius=5000&type=fire_station&key=" + apiKey;

        hotlineList.clear();
        final int[] requestsCompleted = {0};

        Callback placesCallback = new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                checkCompletion();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    parsePlacesResponse(response.body().string());
                }
                checkCompletion();
            }

            private void checkCompletion() {
                requestsCompleted[0]++;
                if (requestsCompleted[0] >= 2) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (hotlineList.isEmpty()) {
                                fetchHotlinesFromFirebase();
                            } else {
                                hasFetchedHotlines = true;
                                attemptSyncAndRender();
                            }
                        });
                    }
                }
            }
        };

        client.newCall(new Request.Builder().url(policeUrl).build()).enqueue(placesCallback);
        client.newCall(new Request.Builder().url(fireUrl).build()).enqueue(placesCallback);
    }

    private void parsePlacesResponse(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray results = jsonObject.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject place = results.getJSONObject(i);
                String name = place.getString("name");
                String vicinity = place.optString("vicinity", "");
                
                JSONObject locationObj = place.getJSONObject("geometry").getJSONObject("location");
                double lat = locationObj.getDouble("lat");
                double lng = locationObj.getDouble("lng");
                
                String placeId = place.optString("place_id");
                
                Hotline hotline = new Hotline(name, vicinity, "Fetching...", lat, lng, "");
                synchronized (hotlineList) {
                    hotlineList.add(hotline);
                }

                if (placeId != null && !placeId.isEmpty()) {
                    fetchPlaceDetails(placeId, hotline);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchPlaceDetails(String placeId, Hotline hotline) {
        String apiKey = GooglePlacesConfig.getApiKey(getContext());
        if (apiKey == null || apiKey.isEmpty()) return;

        String url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=" + placeId +
                "&fields=formatted_phone_number" +
                "&key=" + apiKey;

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONObject result = jsonObject.optJSONObject("result");
                        if (result != null && result.has("formatted_phone_number")) {
                            hotline.setPhoneNumber(result.getString("formatted_phone_number"));
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void fetchHotlinesFromFirebase() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Hotlines");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hotlineList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        Hotline hotline = dataSnapshot.getValue(Hotline.class);
                        if (hotline != null) {
                            hotlineList.add(hotline);
                        }
                    }
                }
                
                if (hotlineList.isEmpty()) {
                    hotlineList.addAll(getFallbackHotlines());
                }
                
                hasFetchedHotlines = true;
                attemptSyncAndRender();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (hotlineList.isEmpty()) {
                    hotlineList.addAll(getFallbackHotlines());
                }
                hasFetchedHotlines = true;
                attemptSyncAndRender();
            }
        });
    }

    private List<Hotline> getFallbackHotlines() {
        List<Hotline> fallbacks = new ArrayList<>();
        fallbacks.add(new Hotline("RER 161", "Bacoor", "161", 14.450, 120.960, ""));
        fallbacks.add(new Hotline("BDRRMO", "Bacoor", "(046)4170727", 14.448, 120.942, ""));
        fallbacks.add(new Hotline("PNP Bacoor", "Bacoor Police Station", "09777520819", 14.415, 120.947, ""));
        fallbacks.add(new Hotline("BFP Bacoor", "Bacoor Fire Station", "09666959711", 14.398, 120.963, ""));
        fallbacks.add(new Hotline("City Information Office", "Bacoor City Hall", "(046)4814120", 14.402, 120.950, ""));
        return fallbacks;
    }

    private void attemptSyncAndRender() {
        if (!hasFetchedLocation || !hasFetchedHotlines) return;

        if (currentUserLocation != null && !hotlineList.isEmpty()) {
            hotlineList.sort((h1, h2) -> {
                Location loc1 = new Location("");
                loc1.setLatitude(h1.getLatitude());
                loc1.setLongitude(h1.getLongitude());
                
                Location loc2 = new Location("");
                loc2.setLatitude(h2.getLatitude());
                loc2.setLongitude(h2.getLongitude());
                
                float dist1 = currentUserLocation.distanceTo(loc1);
                float dist2 = currentUserLocation.distanceTo(loc2);
                return Float.compare(dist1, dist2);
            });
        }

        setupRecyclerView();
    }

    private void setupRecyclerView() {
        if (adapter == null) {
            adapter = new HotlineAdapter(hotlineList, currentUserLocation, this);
            hotlinesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            hotlinesRecyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(hotlineList, currentUserLocation);
        }
    }

    @Override
    public void onCallClicked(Hotline hotline) {
        String phoneStr = hotline.getPhoneNumber();
        String cleanPhone = phoneStr != null ? phoneStr.replaceAll("[^0-9+]", "") : "";
        if (cleanPhone.isEmpty()) {
            Toast.makeText(getContext(), "No valid phone number available.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + cleanPhone));
        startActivity(callIntent);
        logActivity("Unknown", "Phone Call", "Dialed", hotline.getName() + " - " + hotline.getPhoneNumber(), "Success", "User initiated a phone call", "N/A");
    }

    @Override
    public void onLocationClicked(Hotline hotline) {
        Intent intent = new Intent(getContext(), MapDash.class);
        intent.putExtra("targetLat", hotline.getLatitude());
        intent.putExtra("targetLon", hotline.getLongitude());
        intent.putExtra("targetName", hotline.getName());
        startActivity(intent);
        logActivity("Unknown", "Location", "Map Open", "Viewed", hotline.getAddress(), "Success", "user opened location internally", "N/A");
    }

    private void setupQuickAccessListeners(View view) {
        // Home
        view.findViewById(R.id.quick_home).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), Dashboard.class);
            startActivity(intent);
        });

        // Traffic Map
        view.findViewById(R.id.quick_map).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Service
        view.findViewById(R.id.quick_service).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Report History
        view.findViewById(R.id.quick_history).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), ReportIncident.class);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
            }
        });

        // Profile
        view.findViewById(R.id.quick_profile).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), UserProfile.class);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
            }
        });

        // About
        view.findViewById(R.id.quick_about).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutUs.class);
            startActivity(intent);
        });

        // Feedback
        view.findViewById(R.id.quick_feedback).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutUs.class);
            startActivity(intent);
        });

        // Logout
        view.findViewById(R.id.quick_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            if (requireActivity() instanceof FrontpageActivity) {
                ((FrontpageActivity) requireActivity()).showWelcomeScreen();
            }
        });
    }

    private void logActivity(String userId, String type, String action, String target, String status, String notes, String changes) {
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
}


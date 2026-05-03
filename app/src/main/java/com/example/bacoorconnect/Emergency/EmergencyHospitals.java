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
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

public class EmergencyHospitals extends Fragment implements HospitalAdapter.OnHospitalInteractionListener {

    private DatabaseReference auditRef;
    private FloatingActionButton scrolltotopBtn;
    private NestedScrollView nestedScrollView;
    
    private RecyclerView hospitalsRecyclerView;
    private HospitalAdapter adapter;
    private List<Hospital> hospitalList;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation = null;
    private boolean hasFetchedLocation = false;
    private boolean hasFetchedHospitals = false;

    private static final String GOOGLE_PLACES_API_KEY = "AIzaSyAh_s1ran_97S3SWQ63z5zZLMfi_e25cRE"; // Replace with actual API key
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
        View view = inflater.inflate(R.layout.fragment_emergency_hospitals, container, false);

        nestedScrollView = view.findViewById(R.id.nestedScrollView);
        scrolltotopBtn = view.findViewById(R.id.scrollToTopBtn);
        hospitalsRecyclerView = view.findViewById(R.id.hospitalsRecyclerView);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hospitals", "Emergency Resources", "Success", "user accessed the emergency resources: hospitals page", "N/A");

        scrolltotopBtn.setOnClickListener(v -> {
            nestedScrollView.smoothScrollTo(0,0);
        });

        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > 600) {
                        scrolltotopBtn.show();
                    } else {
                        scrolltotopBtn.hide();
                    }
                }
        );

        nestedScrollView.post(() -> {
            if (nestedScrollView.getScrollY() > 600) {
                scrolltotopBtn.show();
            } else {
                scrolltotopBtn.hide();
            }
        });

        hospitalList = new ArrayList<>();
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        if (hasLocationPermission()) {
            fetchUserLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }

        return view;
    }

    private void fetchHospitalsFromGooglePlaces(Location location) {
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + location.getLatitude() + "," + location.getLongitude() +
                "&radius=5000" + // 5 km radius
                "&type=hospital" +
                "&key=" + GOOGLE_PLACES_API_KEY;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    // Fallback to Firebase or hardcoded list if API fails
                    fetchHospitalsFromFirebase();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    requireActivity().runOnUiThread(() -> parseGooglePlacesResponse(responseData));
                } else {
                    requireActivity().runOnUiThread(() -> fetchHospitalsFromFirebase());
                }
            }
        });
    }

    private void parseGooglePlacesResponse(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray results = jsonObject.getJSONArray("results");

            hospitalList.clear();

            for (int i = 0; i < results.length(); i++) {
                JSONObject place = results.getJSONObject(i);
                String name = place.getString("name");
                
                String vicinity = place.optString("vicinity", "No address available");
                
                JSONObject locationObj = place.getJSONObject("geometry").getJSONObject("location");
                double lat = locationObj.getDouble("lat");
                double lng = locationObj.getDouble("lng");
                
                String placeId = place.optString("place_id");
                
                // Note: Phone numbers aren't included in Nearby Search. Making a separate detail request.
                Hospital hospital = new Hospital(name, vicinity, "Fetching contact...", lat, lng, "");
                hospitalList.add(hospital);

                if (placeId != null && !placeId.isEmpty()) {
                    fetchPlaceDetails(placeId, hospital);
                } else {
                    hospital.setPhoneNumber("Phone not available");
                }
            }

            if (hospitalList.isEmpty()) {
                fetchHospitalsFromFirebase();
            } else {
                hasFetchedHospitals = true;
                attemptSyncAndRender();
            }

        } catch (Exception e) {
            fetchHospitalsFromFirebase();
        }
    }

    private void fetchPlaceDetails(String placeId, Hospital hospital) {
        String url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=" + placeId +
                "&fields=formatted_phone_number" +
                "&key=" + GOOGLE_PLACES_API_KEY;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                hospital.setPhoneNumber("Phone not available");
                requireActivity().runOnUiThread(() -> {
                     if (adapter != null) adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);
                        JSONObject result = jsonObject.optJSONObject("result");
                        if (result != null && result.has("formatted_phone_number")) {
                            String phone = result.getString("formatted_phone_number");
                            hospital.setPhoneNumber(phone);
                        } else {
                            hospital.setPhoneNumber("Phone not available");
                        }
                    } catch (Exception e) {
                        hospital.setPhoneNumber("Phone not available");
                    }
                } else {
                    hospital.setPhoneNumber("Phone not available");
                }
                requireActivity().runOnUiThread(() -> {
                     if (adapter != null) adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void fetchHospitalsFromFirebase() {
        DatabaseReference hospitalsRef = FirebaseDatabase.getInstance().getReference("Hospitals");
        hospitalsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hospitalList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        Hospital hospital = dataSnapshot.getValue(Hospital.class);
                        if (hospital != null) {
                            hospitalList.add(hospital);
                        }
                    }
                }
                
                // Fallback if Firebase dataset is empty
                if (hospitalList.isEmpty()) {
                    hospitalList.addAll(getFallbackHospitals());
                }
                
                hasFetchedHospitals = true;
                attemptSyncAndRender();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback if Firebase request fails completely
                if (hospitalList.isEmpty()) {
                    hospitalList.addAll(getFallbackHospitals());
                }
                hasFetchedHospitals = true; // prevent infinite loading block
                attemptSyncAndRender();
            }
        });
    }

    private List<Hospital> getFallbackHospitals() {
        List<Hospital> fallbacks = new ArrayList<>();
        fallbacks.add(new Hospital("Bacoor Doctors Medical Center", "Bacoor Doctors Medical Center, Bacoor Boulevard", "(046)4166275", 14.437, 120.957, ""));
        fallbacks.add(new Hospital("Crisostomo General Hospital", "CWXM+M8R, General Tirona Highway, Bacoor, 4102 Cavite", "(046)4348239", 14.448, 120.942, ""));
        fallbacks.add(new Hospital("Medical Center Imus", "9XFH+GW Bacoor, Cavite", "(046)4773087", 14.415, 120.947, ""));
        fallbacks.add(new Hospital("Molino Doctors Hospital", "201 Molino Rd, Bacoor, Cavite", "(046)4770830", 14.398, 120.963, ""));
        fallbacks.add(new Hospital("Prime Global Care Medical Center Inc.", "9XXF+5Q Bacoor, Cavite", "(046)9705314", 14.402, 120.950, ""));
        fallbacks.add(new Hospital("South City Hospital and Medical Center", "115 Daang Hari Road, Molino, Bacoor, 4102 Cavite", "(02)82499100", 14.372, 120.978, ""));
        fallbacks.add(new Hospital("Southeast Asian Medical Center", "CX4G+9P Bacoor, Cavite", "09452259136", 14.450, 120.960, ""));
        fallbacks.add(new Hospital("St. Dominic Medical Center", "FX56+M7X, Bacoor, Cavite", "(046)4172520", 14.422, 120.945, ""));
        fallbacks.add(new Hospital("St. Michael Medical Hospital", "212 molino 2 proper, Bacoor, 4102 Cavite", "(046)4771707", 14.411, 120.966, ""));
        return fallbacks;
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
        // Coordinates for St. Dominic Medical Center, Bacoor
        defaultLocation.setLatitude(14.422);
        defaultLocation.setLongitude(120.945);
        processLocation(defaultLocation);
    }

    private void processLocation(Location location) {
        currentUserLocation = location;
        hasFetchedLocation = true;
        fetchHospitalsFromGooglePlaces(location);
    }
    
    private void attemptSyncAndRender() {
        // Only render the list if BOTH loc and firebase syncs are done (or failed gracefully)
        if (!hasFetchedLocation || !hasFetchedHospitals) return;

        if (currentUserLocation != null && !hospitalList.isEmpty()) {
            hospitalList.sort((h1, h2) -> {
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
            adapter = new HospitalAdapter(hospitalList, currentUserLocation, this);
            hospitalsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            hospitalsRecyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(hospitalList, currentUserLocation);
        }
    }

    @Override
    public void onCallClicked(Hospital hospital) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + hospital.getPhoneNumber()));
        startActivity(callIntent);
        logActivity("Unknown", "Phone Call", "Dialed", hospital.getPhoneNumber(), "Success", "user initiated a phone call", "N/A");
    }

    @Override
    public void onLocationClicked(Hospital hospital) {
        Intent intent = new Intent(getContext(), com.example.bacoorconnect.General.MapDash.class);
        intent.putExtra("targetLat", hospital.getLatitude());
        intent.putExtra("targetLon", hospital.getLongitude());
        intent.putExtra("targetName", hospital.getName());
        startActivity(intent);
        logActivity("Location", "Map Open", "Viewed", hospital.getAddress(), "Success", "user opened location internally", "N/A");
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

package com.example.bacoorconnect.General;

import android.app.Dialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;

import com.example.bacoorconnect.Emergency.EmergencyGuides;
import com.example.bacoorconnect.Emergency.EmergencyHospitals;
import com.example.bacoorconnect.Emergency.Hotline;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
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

public class Dashboard extends Fragment {

    private DatabaseReference auditRef;
    private LinearLayout dashboardHotlinesContainer;
    private List<Hotline> hotlineList;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation = null;
    private boolean hasFetchedLocation = false;
    private boolean hasFetchedHotlines = false;

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
        View view = inflater.inflate(R.layout.activity_dashboard, container, false);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hotlines", "Emergency Resources", "Success", "User accessed the emergency resources: hotlines page", "N/A");

        dashboardHotlinesContainer = view.findViewById(R.id.dashboardHotlinesContainer);
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

        setupEmergencyResourcesCards(view);
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
        // Coordinates for St. Dominic Medical Center area
        defaultLocation.setLatitude(14.422);
        defaultLocation.setLongitude(120.945);
        processLocation(defaultLocation);
    }

    private void processLocation(Location location) {
        currentUserLocation = location;
        hasFetchedLocation = true;
        fetchHotlinesFromGooglePlaces(location);
    }

    private void fetchHotlinesFromGooglePlaces(Location location) {
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + location.getLatitude() + "," + location.getLongitude() +
                "&radius=5000" +
                "&type=police|fire_station|local_government_office" +
                "&key=" + GOOGLE_PLACES_API_KEY;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> fetchHotlinesFromFirebase());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    requireActivity().runOnUiThread(() -> parseGooglePlacesResponse(responseData));
                } else {
                    requireActivity().runOnUiThread(() -> fetchHotlinesFromFirebase());
                }
            }
        });
    }

    private void parseGooglePlacesResponse(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray results = jsonObject.getJSONArray("results");

            hotlineList.clear();

            for (int i = 0; i < results.length(); i++) {
                JSONObject place = results.getJSONObject(i);
                String name = place.getString("name");

                String vicinity = place.optString("vicinity", "No address available");

                JSONObject locationObj = place.getJSONObject("geometry").getJSONObject("location");
                double lat = locationObj.getDouble("lat");
                double lng = locationObj.getDouble("lng");

                String placeId = place.optString("place_id");

                Hotline hotline = new Hotline(name, vicinity, "Fetching contact...", lat, lng, "");
                hotlineList.add(hotline);

                if (placeId != null && !placeId.isEmpty()) {
                    fetchPlaceDetails(placeId, hotline);
                } else {
                    hotline.setPhoneNumber("Phone not available");
                }
            }

            if (hotlineList.isEmpty()) {
                fetchHotlinesFromFirebase();
            } else {
                hasFetchedHotlines = true;
                attemptSyncAndRender();
            }

        } catch (Exception e) {
            fetchHotlinesFromFirebase();
        }
    }

    private void fetchPlaceDetails(String placeId, Hotline hotline) {
        String url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=" + placeId +
                "&fields=formatted_phone_number" +
                "&key=" + GOOGLE_PLACES_API_KEY;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                hotline.setPhoneNumber("Phone not available");
                requireActivity().runOnUiThread(() -> renderDashboardHotlines());
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
                            hotline.setPhoneNumber(phone);
                        } else {
                            hotline.setPhoneNumber("Phone not available");
                        }
                    } catch (Exception e) {
                        hotline.setPhoneNumber("Phone not available");
                    }
                } else {
                    hotline.setPhoneNumber("Phone not available");
                }
                requireActivity().runOnUiThread(() -> renderDashboardHotlines());
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
            // Show only the 4 closest on the dashboard
            if (hotlineList.size() > 4) {
                hotlineList.subList(4, hotlineList.size()).clear();
            }
        }

        renderDashboardHotlines();
    }

    private void renderDashboardHotlines() {
        if (dashboardHotlinesContainer == null) return;
        dashboardHotlinesContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (Hotline hotline : hotlineList) {
            View itemView = inflater.inflate(R.layout.item_dashboard_hotline, dashboardHotlinesContainer, false);
            
            TextView titleView = itemView.findViewById(R.id.hotline_title);
            ImageView iconView = itemView.findViewById(R.id.hotline_icon);

            titleView.setText(hotline.getName());

            // Adjust icon based on name or keyword dynamically if needed
            int iconResId = R.drawable.logo_alert161;
            if (hotline.getName().contains("BFP") || hotline.getName().toLowerCase().contains("fire")) {
                iconResId = R.drawable.hotline_bfp;
                iconView.setImageResource(iconResId);
            } else if (hotline.getName().contains("PNP") || hotline.getName().toLowerCase().contains("police")) {
                iconResId = R.drawable.hotline_pnp;
                iconView.setImageResource(iconResId);
            } else if (hotline.getName().contains("BDRRMO")) {
                iconResId = R.drawable.hotline_bdrrmo;
                iconView.setImageResource(iconResId);
            } else {
                 iconView.setImageResource(iconResId);
            }

            final int finalIconRes = iconResId;
            itemView.setOnClickListener(v -> showHotlineInfoDialog(
                hotline.getName(),
                finalIconRes, 
                "Local emergency service details fetched dynamically for " + hotline.getName(),
                hotline.getPhoneNumber(),
                hotline.getPhoneNumber(),
                hotline.getAddress()
            ));

            dashboardHotlinesContainer.addView(itemView);
        }
    }

    private void showHotlineInfoDialog(String serviceName, int iconResId, String description,
                                       String displayPhone, String dialPhone, String address) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_hotline_info);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Set dialog content
        ImageView icon = dialog.findViewById(R.id.dialog_icon);
        TextView name = dialog.findViewById(R.id.dialog_service_name);
        TextView desc = dialog.findViewById(R.id.dialog_description);
        TextView phone = dialog.findViewById(R.id.dialog_phone);
        TextView addr = dialog.findViewById(R.id.dialog_address);
        Button closeBtn = dialog.findViewById(R.id.dialog_btn_close);
        Button callBtn = dialog.findViewById(R.id.dialog_btn_call);

        icon.setImageResource(iconResId);
        name.setText(serviceName);
        desc.setText(description);
        phone.setText(displayPhone);
        addr.setText(address);

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        callBtn.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + dialPhone));
            startActivity(callIntent);
            logActivity("Unknown", "Phone Call", "Dialed", serviceName + " - " + displayPhone, "Success", "User initiated a phone call", "N/A");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupEmergencyResourcesCards(View view) {
        CardView hospitalCard = view.findViewById(R.id.card_hospitals);
        if (hospitalCard != null) {
            hospitalCard.setOnClickListener(v -> {
                if (getActivity() instanceof FrontpageActivity) {
                    ((FrontpageActivity) getActivity()).loadEmergencyFragment(
                            new EmergencyHospitals(),
                            "Emergency Hospitals"
                    );
                }
                logActivity("Unknown", "Navigation", "Opened Hospital Directory", "Emergency Resources", "Success", "User accessed hospital directory", "N/A");
            });
        }

        CardView guidesCard = view.findViewById(R.id.card_emergency_guides);
        if (guidesCard != null) {
            guidesCard.setOnClickListener(v -> {
                if (getActivity() instanceof FrontpageActivity) {
                    ((FrontpageActivity) getActivity()).loadEmergencyFragment(
                            new EmergencyGuides(),
                            "Emergency Guides"
                    );
                }
                logActivity("Unknown", "Navigation", "Opened Emergency Guides", "Emergency Resources", "Success", "User accessed emergency guides", "N/A");
            });
        }
    }

    private void navigateToFragment(Fragment fragment) {
        if (getActivity() != null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private void setupQuickAccessListeners(View view) {
        // Home

        // Traffic Map
        view.findViewById(R.id.quick_map).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Service
        view.findViewById(R.id.quick_service).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), services.class);
            startActivity(intent);
        });

        // Report History
        view.findViewById(R.id.quick_history).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), ReportHistoryActivity.class);
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
            Intent intent = new Intent(getActivity(), contactus.class);
            startActivity(intent);
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


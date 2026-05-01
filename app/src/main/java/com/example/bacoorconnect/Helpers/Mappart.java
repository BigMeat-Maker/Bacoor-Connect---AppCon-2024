package com.example.bacoorconnect.Helpers;

// Android Imports
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.Manifest;

// Android Permissions & Activity Compatibility
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

// osmdroid Imports
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

// Firebase Imports
import com.example.bacoorconnect.General.MapDash;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.Report;
import com.example.bacoorconnect.Report.ReportDetailsFrag;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mappart extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private String mParam1;
    private String mParam2;

    private Location lastKnownLocation;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private Marker userMarker;
    private Button recenterButton;

    // Radius in meters for report visibility and reporting (2 km = 2000 meters)
    private static final double VISIBILITY_RADIUS_METERS = 2000.0;

    // Polygon overlay for the radius circle
    private Polygon radiusCircle;
    private ValueAnimator circleAnimator;
    private int circleAlpha = 255;

    // Track if circle is currently displayed
    private boolean isRadiusCircleVisible = false;

    // Variables to store lat and lon
    private double currentLat;
    private double currentLon;
    private double getterLat;
    private double getterLon;

    // Declare a list to store reports
    private List<Report> reportList = new ArrayList<>();
    private Toast currentToast;

    // Keep track of the marker for removal
    private Marker currentMarker;

    // All markers on map
    private List<Marker> allMarkers = new ArrayList<>();

    // Filter UI elements
    private ImageView btnFilterToggle;
    private ImageView btnFilterAll, btnFilterAccident, btnFilterFire, btnFilterTraffic, btnFilterNatural;
    private String currentFilterCategory = "all";
    private boolean isDropdownVisible = false;
    private Map<String, List<Marker>> categoryMarkers = new HashMap<>();

    public Mappart() {
    }

    public static Mappart newInstance(String param1, String param2) {
        Mappart fragment = new Mappart();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_mappart, container, false);
        mapView = rootView.findViewById(R.id.map);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(15.0);
        mapView.setMaxZoomLevel(19.5);

        locationOverlay = new MyLocationNewOverlay(mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        if (getArguments() != null) {
            double initialLat = getArguments().getDouble("initialLat", 0);
            double initialLon = getArguments().getDouble("initialLon", 0);
            if (initialLat != 0 && initialLon != 0) {
                lastKnownLocation = new Location("");
                lastKnownLocation.setLatitude(initialLat);
                lastKnownLocation.setLongitude(initialLon);
                centerMapOnUserLocation(lastKnownLocation);
            }
        }

        loadReportsFromFirebase();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        } else {
            locationOverlay.enableMyLocation();
        }

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                if (currentMarker != null) {
                    float newZoomLevel = (float) mapView.getZoomLevelDouble();
                    currentMarker.setIcon(resizeDrawable(getResources().getDrawable(R.drawable.location_decider), newZoomLevel));
                }
                updateMarkerSizes(event.getZoomLevel());
                mapView.invalidate();
                return true;
            }
        });

        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return v.onTouchEvent(event);
        });

        recenterButton = rootView.findViewById(R.id.btn_recenter);
        recenterButton.setOnClickListener(v -> recenterMap());

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                // Only allow placing marker if within the radius circle
                if (isWithinRadius(p)) {
                    moveMarkerTo(p);
                } else {
                    showToast("You can only report near you!");
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return true;
            }
        });
        mapView.getOverlays().add(eventsOverlay);

        btnFilterToggle = rootView.findViewById(R.id.btn_filter_toggle);
        btnFilterAll = rootView.findViewById(R.id.btn_filter_all);
        btnFilterAccident = rootView.findViewById(R.id.btn_filter_accident);
        btnFilterFire = rootView.findViewById(R.id.btn_filter_fire);
        btnFilterTraffic = rootView.findViewById(R.id.btn_filter_traffic);
        btnFilterNatural = rootView.findViewById(R.id.btn_filter_natural);

        setupFilterButtons();
        initializeCategoryMarkers();

        return rootView;
    }

    private void initializeCategoryMarkers() {
        categoryMarkers.put("all", new ArrayList<>());
        categoryMarkers.put("accident", new ArrayList<>());
        categoryMarkers.put("fire", new ArrayList<>());
        categoryMarkers.put("traffic", new ArrayList<>());
        categoryMarkers.put("naturaldisaster", new ArrayList<>());
    }

    private void setupFilterButtons() {
        btnFilterToggle.setOnClickListener(v -> toggleCategoryDropdown());

        btnFilterAll.setOnClickListener(v -> filterReportsByCategory("all"));
        btnFilterAccident.setOnClickListener(v -> filterReportsByCategory("accident"));
        btnFilterFire.setOnClickListener(v -> filterReportsByCategory("fire"));
        btnFilterTraffic.setOnClickListener(v -> filterReportsByCategory("traffic"));
        btnFilterNatural.setOnClickListener(v -> filterReportsByCategory("naturaldisaster"));
    }

    private void toggleCategoryDropdown() {
        if (isDropdownVisible) {
            hideCategoryButtons();
        } else {
            showCategoryButtons();
        }
        isDropdownVisible = !isDropdownVisible;
    }

    private void showCategoryButtons() {
        btnFilterAll.setVisibility(View.VISIBLE);
        btnFilterAccident.setVisibility(View.VISIBLE);
        btnFilterFire.setVisibility(View.VISIBLE);
        btnFilterTraffic.setVisibility(View.VISIBLE);
        btnFilterNatural.setVisibility(View.VISIBLE);
    }

    private void hideCategoryButtons() {
        btnFilterAll.setVisibility(View.GONE);
        btnFilterAccident.setVisibility(View.GONE);
        btnFilterFire.setVisibility(View.GONE);
        btnFilterTraffic.setVisibility(View.GONE);
        btnFilterNatural.setVisibility(View.GONE);
    }

    private void filterReportsByCategory(String category) {
        currentFilterCategory = category;
        updateFilterButtonAppearance();

        for (List<Marker> markers : categoryMarkers.values()) {
            for (Marker marker : markers) {
                marker.setVisible(false);
            }
        }

        if (category.equals("all")) {
            for (Marker marker : allMarkers) {
                marker.setVisible(false);
            }
        } else {
            List<Marker> markers = categoryMarkers.get(category);
            if (markers != null) {
                for (Marker marker : markers) {
                    marker.setVisible(false);
                }
            }
        }

        // Reapply radius filter after category change
        updateVisibleReportsByRadius();

        mapView.postInvalidate();

        if (isDropdownVisible) {
            toggleCategoryDropdown();
        }
    }

    private void updateFilterButtonAppearance() {
        int selectedColor = getResources().getColor(R.color.baconnect_dark_blue, null);
        int unselectedColor = getResources().getColor(R.color.baconnect_blue, null);

        btnFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterAccident.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterFire.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterTraffic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterNatural.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));

        switch (currentFilterCategory) {
            case "all":
                btnFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
                break;
            case "accident":
                btnFilterAccident.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
                break;
            case "fire":
                btnFilterFire.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
                break;
            case "traffic":
                btnFilterTraffic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
                break;
            case "naturaldisaster":
                btnFilterNatural.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
                break;
        }
    }

    private void centerMapOnUserLocation(Location location) {
        if (location != null && mapView != null) {
            GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            mapView.getController().setZoom(16.0);
            mapView.getController().animateTo(userLocation);
        }
    }

    private void loadReportsFromFirebase() {
        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");

        reportsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String reportId = snapshot.getKey();
                String category = snapshot.child("category").getValue(String.class);
                String description = snapshot.child("description").getValue(String.class);
                String location = snapshot.child("location").getValue(String.class);
                String userId = snapshot.child("userId").getValue(String.class);
                String precision = snapshot.child("addressPrecision").getValue(String.class);
                String imageUrl = snapshot.child("imageUrl").getValue(String.class);
                Double latitude = snapshot.child("latitude").getValue(Double.class);
                Double longitude = snapshot.child("longitude").getValue(Double.class);
                Integer upvotes = snapshot.child("upvotes").getValue(Integer.class);
                Integer downvotes = snapshot.child("downvotes").getValue(Integer.class);

                if (latitude != null && longitude != null) {
                    String reportMessage = "Location: " + location +
                            "\nUpvotes: " + upvotes +
                            "\nDownvotes: " + downvotes;

                    placeMarkerOnMap(latitude, longitude, category, description, reportMessage, reportId, userId, imageUrl, precision);
                    mapView.postInvalidate();
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String reportId = snapshot.getKey();

                boolean isVoteOnlyChange = snapshot.hasChildren()
                        && !snapshot.hasChild("category")
                        && !snapshot.hasChild("latitude")
                        && !snapshot.hasChild("longitude");

                if (isVoteOnlyChange) {
                    Log.d("Mappart", "Skipping marker update for vote-only change. Report ID: " + reportId);
                    return;
                }

                removeMarkerFromMap(reportId);
                onChildAdded(snapshot, previousChildName);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String reportId = snapshot.getKey();
                removeMarkerFromMap(reportId);
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", "Error fetching reports: " + error.getMessage());
            }
        });
    }

    private void removeMarkerFromMap(String reportId) {
        boolean markerRemoved = false;

        for (Overlay overlay : new ArrayList<>(mapView.getOverlays())) {
            if (overlay instanceof Marker) {
                Marker marker = (Marker) overlay;
                if (reportId.equals(marker.getId())) {
                    mapView.getOverlays().remove(marker);
                    markerRemoved = true;
                    allMarkers.remove(marker);

                    for (List<Marker> categoryList : categoryMarkers.values()) {
                        categoryList.remove(marker);
                    }
                    break;
                }
            }
        }

        if (markerRemoved) {
            mapView.post(() -> mapView.invalidate());
        }
    }

    private void updateMarkerSizes(double zoomLevel) {
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker) {
                Marker marker = (Marker) overlay;
                String[] meta = marker.getSubDescription() != null ? marker.getSubDescription().split("\\|") : null;

                if (meta != null && meta.length == 2) {
                    String category = meta[0];
                    String precision = meta[1];
                    Drawable newIcon = getReportIcon(category, (float) zoomLevel, precision);
                    if (newIcon != null) {
                        marker.setIcon(newIcon);
                    }
                }
            }
        }
        mapView.postInvalidate();
    }

    // Calculate distance between two GeoPoints in meters
    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        double lat1 = point1.getLatitude();
        double lon1 = point1.getLongitude();
        double lat2 = point2.getLatitude();
        double lon2 = point2.getLongitude();

        double earthRadius = 6371000; // meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    // Check if a GeoPoint is within the radius circle
    private boolean isWithinRadius(GeoPoint point) {
        if (lastKnownLocation == null) {
            return false;
        }

        GeoPoint userLocation = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        double distance = calculateDistance(userLocation, point);
        return distance <= VISIBILITY_RADIUS_METERS;
    }

    // Update which reports are shown based on user location and radius
    private void updateVisibleReportsByRadius() {
        if (lastKnownLocation == null) {
            // If no user location, show all reports
            for (Marker marker : allMarkers) {
                marker.setVisible(true);
            }
            return;
        }

        GeoPoint userLocation = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

        for (Marker marker : allMarkers) {
            GeoPoint reportLocation = (GeoPoint) marker.getPosition();
            double distance = calculateDistance(userLocation, reportLocation);

            // Show report if within bcircle
            if (distance <= VISIBILITY_RADIUS_METERS) {
                if (currentFilterCategory.equals("all")) {
                    marker.setVisible(true);
                } else {
                    String[] meta = marker.getSubDescription() != null ? marker.getSubDescription().split("\\|") : null;
                    if (meta != null && meta.length >= 1) {
                        String category = meta[0];
                        marker.setVisible(category.equalsIgnoreCase(currentFilterCategory));
                    } else {
                        marker.setVisible(false);
                    }
                }
            } else {
                marker.setVisible(false);
            }
        }

        mapView.postInvalidate();
    }

    // Update or create the radius circle overlay
    private void updateRadiusCircle() {
        if (lastKnownLocation == null) return;

        GeoPoint center = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

        // Create new circle polygon if it doesn't exist
        if (radiusCircle == null) {
            radiusCircle = new Polygon();
            radiusCircle.getFillPaint().setColor(Color.TRANSPARENT);
            radiusCircle.getOutlinePaint().setColor(Color.rgb(0, 170, 255)); // Blue color
            radiusCircle.getOutlinePaint().setStrokeWidth(5f);
            
            // Ensure the circle is enabled for drawing, but doesn't handle clicks
            radiusCircle.setEnabled(true);
            radiusCircle.setOnClickListener((polygon, mapView, eventPos) -> false);
            radiusCircle.setInfoWindow(null);
            
            // Add at index 0 so it's behind markers and MapEventsOverlay
            mapView.getOverlays().add(0, radiusCircle);
            startBlinkingAnimation();
        }

        // Generate circle points (approximation using 60 points)
        List<GeoPoint> circlePoints = new ArrayList<>();
        double radiusDegrees = VISIBILITY_RADIUS_METERS / 111320.0; // Approximate degrees per meter

        for (int i = 0; i <= 360; i += 6) {
            double angle = Math.toRadians(i);
            double latOffset = radiusDegrees * Math.sin(angle);
            double lonOffset = radiusDegrees * Math.cos(angle) / Math.cos(Math.toRadians(center.getLatitude()));

            GeoPoint point = new GeoPoint(
                    center.getLatitude() + latOffset,
                    center.getLongitude() + lonOffset
            );
            circlePoints.add(point);
        }

        radiusCircle.setPoints(circlePoints);
        isRadiusCircleVisible = true;
        mapView.postInvalidate();
    }

    private void startBlinkingAnimation() {
        if (circleAnimator != null) {
            circleAnimator.cancel();
        }

        circleAnimator = ValueAnimator.ofInt(70, 255); // Alpha range for blinking
        circleAnimator.setDuration(1200); // 1.2 seconds for a smooth blink
        circleAnimator.setRepeatMode(ValueAnimator.REVERSE);
        circleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        circleAnimator.setInterpolator(new LinearInterpolator());
        circleAnimator.addUpdateListener(animation -> {
            if (radiusCircle != null && radiusCircle.getOutlinePaint() != null) {
                int alpha = (int) animation.getAnimatedValue();
                // Apply alpha to the outline color
                radiusCircle.getOutlinePaint().setColor(Color.argb(alpha, 0, 170, 255));
                if (mapView != null) {
                    mapView.postInvalidate();
                }
            }
        });
        circleAnimator.start();
    }

    // Remove the radius circle from map
    private void removeRadiusCircle() {
        if (radiusCircle != null) {
            mapView.getOverlays().remove(radiusCircle);
            radiusCircle = null;
            isRadiusCircleVisible = false;
            if (circleAnimator != null) {
                circleAnimator.cancel();
            }
            mapView.postInvalidate();
        }
    }

    private void moveMarkerTo(GeoPoint point) {
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
        }
        float zoomLevel = (float) mapView.getZoomLevelDouble();
        currentMarker = new Marker(mapView);
        currentMarker.setPosition(point);
        currentMarker.setTitle("Location Selected");
        currentMarker.setId("tempmarker");
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        Drawable locationIcon = getResources().getDrawable(R.drawable.location_decider, null);
        if (locationIcon != null) {
            currentMarker.setIcon(resizeDrawable(locationIcon, zoomLevel));
        }

        mapView.getOverlays().add(currentMarker);

        currentLat = point.getLatitude();
        currentLon = point.getLongitude();

        // Get address from coordinates
        Geocoder geocoder = new Geocoder(getContext());
        try {
            List<android.location.Address> addresses = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);
                String addressString = address.getAddressLine(0);

                if (getActivity() instanceof MapDash) {
                    TextView locationText = getActivity().findViewById(R.id.location_text);
                    if (locationText != null) {
                        locationText.setText("Address: " + addressString);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (getActivity() instanceof MapDash) {
            ((MapDash) getActivity()).updateLocation(currentLat, currentLon);
        }

        mapView.invalidate();
    }

    public void removeCurrentMarker() {
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
            currentMarker = null;
            mapView.invalidate();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationOverlay.enableMyLocation();

            locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (lastLocation != null) {
                    setLocation(lastLocation.getLatitude(), lastLocation.getLongitude());
                    updateUserLocation(lastLocation);
                    centerMapOnUserLocation(lastLocation);
                } else {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            centerMapOnUserLocation(location);
                            updateUserLocation(location);
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}
                    }, null);
                }

                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        updateUserLocation(location);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                };

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 3, locationListener);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    public GeoPoint getReportMarkerLocation() {
        if (mapView != null) {
            for (Overlay overlay : mapView.getOverlays()) {
                if (overlay instanceof Marker) {
                    Marker marker = (Marker) overlay;
                    if ("tempmarker".equals(marker.getId())) {
                        return (GeoPoint) marker.getPosition();
                    }
                }
            }
        }
        return null;
    }

    private void recenterMap() {
        if (locationOverlay.getMyLocation() != null) {
            GeoPoint userLocation = new GeoPoint(locationOverlay.getMyLocation().getLatitude(), locationOverlay.getMyLocation().getLongitude());
            mapView.getController().animateTo(userLocation);
        }
    }

    public void updateUserLocation(Location location) {
        GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
        lastKnownLocation = location;
        this.currentLat = location.getLatitude();
        this.currentLon = location.getLongitude();

        if (getActivity() instanceof MapDash) {
            MapDash mapdash = (MapDash) getActivity();
            mapdash.updateLocation(location.getLatitude(), location.getLongitude());
        }

        // Update or create user marker
        if (userMarker == null) {
            userMarker = new Marker(mapView);
            userMarker.setPosition(userLocation);
            userMarker.setTitle("Your Location");
            mapView.getOverlays().add(userMarker);
        } else {
            userMarker.setPosition(userLocation);
            if (!mapView.getOverlays().contains(userMarker)) {
                mapView.getOverlays().add(userMarker);
            }
        }

        // Update the radius circle to follow user
        updateRadiusCircle();

        // Update which reports are visible based on new location
        updateVisibleReportsByRadius();

        mapView.postInvalidate();
    }

    public GeoPoint getLastKnownUserLocation() {
        return (lastKnownLocation != null) ? new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()) : null;
    }

    public void setLocation(double lat, double lon) {
        this.getterLat = lat;
        this.getterLon = lon;
    }

    public double getCurrentLat() {
        return getterLat;
    }

    public double getCurrentLon() {
        return getterLon;
    }

    private void showToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        currentToast.show();
    }

    void placeMarkerOnMap(double lat, double lon, String category, String description,
                          String reportMessage, String reportId, String userId, String imageUrl, String addressPrecision) {

        if (isAdded() && getView() != null) {
            mapView = getView().findViewById(R.id.map);

            if (mapView != null) {
                Marker reportMarker = new Marker(mapView);
                GeoPoint reportLocation = new GeoPoint(lat, lon);
                reportMarker.setPosition(reportLocation);
                reportMarker.setTitle("Report: " + category);

                float zoomLevel = (float) mapView.getZoomLevelDouble();
                Drawable icon = getReportIcon(category, zoomLevel, addressPrecision);

                if (icon != null) {
                    reportMarker.setIcon(icon);
                }

                reportMarker.setId(reportId);
                reportMarker.setSubDescription(category + "|" + addressPrecision);

                // Initially hide marker - will be shown if within radius
                reportMarker.setVisible(false);

                mapView.getOverlays().add(reportMarker);
                allMarkers.add(reportMarker);

                String categoryKey = category.toLowerCase();
                if (categoryMarkers.containsKey(categoryKey)) {
                    categoryMarkers.get(categoryKey).add(reportMarker);
                } else {
                    List<Marker> newCategoryList = new ArrayList<>();
                    newCategoryList.add(reportMarker);
                    categoryMarkers.put(categoryKey, newCategoryList);
                }

                mapView.postInvalidate();

                reportMarker.setOnMarkerClickListener((marker, mapView) -> {
                    openReportDetailsFragment(reportId, userId);
                    return true;
                });

                reportList.add(new Report(lat, lon, category, description, reportMessage, reportId, imageUrl, userId));

                // After adding marker, check if it should be visible based on current user location
                updateVisibleReportsByRadius();
            }
        }
    }

    private Drawable getReportIcon(String category, float zoomLevel, String precision) {
        if (category == null || precision == null) return null;

        String iconFileName;
        String fallbackIconFileName = null;

        Log.d("Mappart", "Getting icon for category: '" + category + "' with precision: '" + precision + "'");

        switch (category.toLowerCase()) {
            case "accident":
                iconFileName = "location_" + precision.toLowerCase() + "_accident";
                fallbackIconFileName = "location_precise_accident";
                break;
            case "fire":
                iconFileName = "location_" + precision.toLowerCase() + "_fire";
                fallbackIconFileName = "location_precise_fire";
                break;
            case "disaster":
            case "naturaldisaster":
                iconFileName = "location_" + precision.toLowerCase() + "_disaster";
                fallbackIconFileName = "location_precise_disaster";
                break;
            case "traffic":
                iconFileName = "location_" + precision.toLowerCase() + "_traffic";
                fallbackIconFileName = "location_precise_traffic";
                break;
            default:
                Log.e("Mappart", "Unknown category: " + category);
                return null;
        }

        Log.d("Mappart", "Looking for icon: " + iconFileName);

        int iconResId = getResources().getIdentifier(iconFileName, "drawable", getActivity().getPackageName());

        if (iconResId != 0) {
            Drawable drawable = getResources().getDrawable(iconResId, null);
            return resizeDrawable(drawable, zoomLevel);
        }

        if (fallbackIconFileName != null) {
            Log.d("Mappart", "Icon not found, trying fallback: " + fallbackIconFileName);
            int fallbackResId = getResources().getIdentifier(fallbackIconFileName, "drawable", getActivity().getPackageName());

            if (fallbackResId != 0) {
                Drawable drawable = getResources().getDrawable(fallbackResId, null);
                return resizeDrawable(drawable, zoomLevel);
            }
        }

        Log.e("Mappart", "No icon found for category: " + category + " with precision: " + precision);
        return null;
    }

    private Drawable resizeDrawable(Drawable image, float zoomLevel) {
        if (image == null) return null;

        Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
        int baseSize = 40;
        int maxSize = 50;
        int size = (int) (baseSize + (zoomLevel * 3));
        size = Math.max(baseSize, Math.min(size, maxSize));

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, size, size, false);
        return new BitmapDrawable(getResources(), resizedBitmap);
    }

    public void openReportDetailsFragment(String reportId, String userId) {
        double userLat = getCurrentLat();
        double userLon = getCurrentLon();

        ReportDetailsFrag reportDetailsFrag = new ReportDetailsFrag();

        Bundle args = new Bundle();
        args.putString("reportId", reportId);
        args.putString("userId", userId);
        args.putDouble("userLat", userLat);
        args.putDouble("userLon", userLon);

        reportDetailsFrag.setArguments(args);
        reportDetailsFrag.show(requireActivity().getSupportFragmentManager(), reportDetailsFrag.getTag());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (circleAnimator != null) {
            circleAnimator.cancel();
        }
        mapView.onDetach();
    }
}
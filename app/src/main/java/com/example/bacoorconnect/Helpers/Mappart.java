package com.example.bacoorconnect.Helpers;

// Android Imports
import android.content.Context;
import android.graphics.Bitmap;
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

//stuff for automated post deletion


public class Mappart extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private String mParam1;
    private String mParam2;

    private Location lastKnownLocation;

    private LocationManager locationManager;

    private LocationListener locationListener;

    private MapView mapView;
    private BoundingBox CameraBoundingBox;
    private BoundingBox OuterFogBoundary;
    private BoundingBox InnerFogBoundary;
    private MyLocationNewOverlay locationOverlay;
    private Marker userMarker;
    private Button recenterButton;

    // Variables to store lat and lon so this goes to database still unused
    // this takes the pin location data
    private double currentLat;
    private double currentLon;

    private double getterLat;
    private double getterLon;

    // Declare a list to store reports
    private List<Report> reportList = new ArrayList<>();

    private Toast currentToast;

    // Keep track of the marker for removal
    private Marker currentMarker;
    // HAVE THE CONSTRAINT ON INNERFOGBOUNDARY LOOSEN WHEN ZOOMED IN AND TIGHTEN WHEN ZOOMED OUT
    // MAKE THE FOG PRETTIER IDEA IS TO MAKE ANOTHER BACOOR MAP AND OVERLAY IT ON THE MAP AND HAVE IT MASK? IDK
    // make the camera soft bound like it bounds back if you go past bcc
    // Have buttons to move the camera to certain areas

    // SHIT I NEED TO DO
    // 3KM LIMIT FOR SHOWING REPORT PINS 1.5KM FOR NOTIFICATION ALERTS SHOULD CALCULATE USER POSITION

    //Map area may be too small hard to see
    //

    private ImageView btnFilterToggle;
    private Button btnFilterAll, btnFilterAccident, btnFilterFire, btnFilterTraffic, btnFilterNatural;
    private String currentFilterCategory = "all";
    private boolean isDropdownVisible = false;
    private List<Marker> allMarkers = new ArrayList<>();
    private Map<String, List<Marker>> categoryMarkers = new HashMap<>();

    public Mappart() {
    }

    // TODO: Rename and change types and number of parameters
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
        mapView.setMinZoomLevel(15.0); // Number for max zoom out the smaller the farther
        mapView.setMaxZoomLevel(19.5); // number for max zoom in the bigger the closer


        CameraBoundingBox = new BoundingBox(14.4779, 121.012, 14.356, 120.9249);
        // this is the real constraint on the camera which should limit where the camera goes(i need to fix so it adjusts)
        //CBB is the same as IFB because well idk its just the same
        //why is it the same? i should fix that
        OuterFogBoundary = new BoundingBox(14.56, 121.10, 14.27, 120.84);
        // Inner box
        //This bad boy is basically a larger IFB that exapnds out of it and it functions as a way to create the fog cells in between
        InnerFogBoundary = new BoundingBox(14.4779, 121.012, 14.356, 120.9249);
        // The outer box (what i do is i create the pollys to exist between the OuterFogBoundary and the CBB)
        // This is envelops the entirety of Bacoor but includes a lot of other cities still

        // Not entirely sure how  the overlay tracks the thing
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

        // Re requests location if its not enabled
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        } else {
            // Then go with the overlay logic if enabled
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
                } else {
                    Log.e("Mappart", "currentMarker is null during zoom");
                }

                updateMarkerSizes(event.getZoomLevel());
                mapView.invalidate(); // Redraw map
                return true;
            }
        });


        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);} else {
                v.getParent().requestDisallowInterceptTouchEvent(false);}
            return v.onTouchEvent(event);
        });

        Button recenterButton = rootView.findViewById(R.id.btn_recenter);
        recenterButton.setOnClickListener(v -> recenterMap());

        // Stinkyguy handles events on the maps
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (InnerFogBoundary.contains(p)) {
                    moveMarkerTo(p);

                } else {
                    // Inform the user they are trying to press outside the allowed area (it doesnt show the toast when i click on the fog)
                    if (currentToast != null) {
                        currentToast.cancel();
                    }
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                //disabled
                return true;
            }
        });
        mapView.getOverlays().add(eventsOverlay);

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                enforceCityBoundary();
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                enforceCityBoundary();
                return true;
            }
        });

        addFoggingOverlay();

        // ============ wa wa wa wat the stink============
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
                marker.setVisible(true);
            }
        } else {
            List<Marker> markers = categoryMarkers.get(category);
            if (markers != null) {
                for (Marker marker : markers) {
                    marker.setVisible(true);
                }
            }
        }

        mapView.postInvalidate();

        showReportListDialog(category);

        if (isDropdownVisible) {
            toggleCategoryDropdown();
        }
    }

    private void showReportListDialog(String category) {
        double userLat = getCurrentLat();
        double userLon = getCurrentLon();

        ReportListDialog dialog = ReportListDialog.newInstance(category, userLat, userLon);
        dialog.show(getParentFragmentManager(), "ReportListDialog");
    }


    private void updateFilterButtonAppearance() {
        int selectedColor = getResources().getColor(R.color.baconnect_dark_blue, null);
        int unselectedColor = getResources().getColor(R.color.baconnect_blue, null);

        btnFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterAccident.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterFire.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterTraffic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));
        btnFilterNatural.setBackgroundTintList(android.content.res.ColorStateList.valueOf(unselectedColor));

        // Set selected button
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
            mapView.getController().setZoom(16.0);  // camera level that starts the app at
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
                double latitude = snapshot.child("latitude").getValue(Double.class);
                double longitude = snapshot.child("longitude").getValue(Double.class);
                int upvotes = snapshot.child("upvotes").getValue(Integer.class);
                int downvotes = snapshot.child("downvotes").getValue(Integer.class);

                String reportMessage = "Location: " + location +
                        "\nUpvotes: " + upvotes +
                        "\nDownvotes: " + downvotes;

                placeMarkerOnMap(latitude, longitude, category, description, reportMessage, reportId, userId, imageUrl, precision);
                mapView.postInvalidate();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String reportId = snapshot.getKey();

                boolean isVoteOnlyChange = snapshot.hasChildren()
                        && !snapshot.hasChild("category")
                        && !snapshot.hasChild("latitude")
                        && !snapshot.hasChild("longitude");

                if (isVoteOnlyChange) {
                    Log.d("ReportFragment", "Skipping marker update for vote-only change. Report ID: " + reportId);
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
        } else {
            Log.d("MarkerRemoval", "Marker NOT found for report ID: " + reportId);
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

    private void enforceCityBoundary() {
        GeoPoint currentCenter = (GeoPoint) mapView.getMapCenter();

        if (!InnerFogBoundary.contains(currentCenter)) {
            GeoPoint correctedCenter = getValidCenterPosition(currentCenter);
            mapView.getController().setCenter(correctedCenter);
        }

        double currentZoom = mapView.getZoomLevel();
        if (currentZoom < mapView.getMinZoomLevel()) {
            mapView.getController().setZoom(mapView.getMinZoomLevel());
        } else if (currentZoom > mapView.getMaxZoomLevel()) {
            mapView.getController().setZoom(mapView.getMaxZoomLevel());
        }
    }

    private GeoPoint getValidCenterPosition(GeoPoint point) {
        double lat = Math.max(InnerFogBoundary.getLatSouth(), Math.min(InnerFogBoundary.getLatNorth(), point.getLatitude()));
        double lon = Math.max(InnerFogBoundary.getLonWest(), Math.min(InnerFogBoundary.getLonEast(), point.getLongitude()));
        return new GeoPoint(lat, lon);
    }

    // The stinky fog (big black polygons)
    private void addFoggingOverlay() {
        // Outer boundary junk
        double latNorthOuter = OuterFogBoundary.getLatNorth();
        double latSouthOuter = OuterFogBoundary.getLatSouth();
        double lonWestOuter = OuterFogBoundary.getLonWest();
        double lonEastOuter = OuterFogBoundary.getLonEast();

        // Inner boundary junk
        double latNorthInner = InnerFogBoundary.getLatNorth();
        double latSouthInner = InnerFogBoundary.getLatSouth();
        double lonWestInner = InnerFogBoundary.getLonWest();
        double lonEastInner = InnerFogBoundary.getLonEast();

        // Top Polygon
        Polygon topFogPolygon = createFogPolygon(
                latNorthOuter, latNorthInner, //I have it so it dont overlap i kinda forgot how i set it up so dont change this
                lonWestOuter, lonEastOuter
        );
        mapView.getOverlays().add(topFogPolygon);

        // Bottom Polygon
        Polygon bottomFogPolygon = createFogPolygon(
                latSouthInner, latSouthOuter,
                lonWestOuter, lonEastOuter
        );
        mapView.getOverlays().add(bottomFogPolygon);

        // Left Polygon
        Polygon leftFogPolygon = createFogPolygon(
                latNorthInner, latSouthInner,
                lonWestOuter, lonWestInner
        );
        mapView.getOverlays().add(leftFogPolygon);

        // Right Polygon
        Polygon rightFogPolygon = createFogPolygon(
                latNorthInner, latSouthInner,
                lonEastInner, lonEastOuter
        );
        mapView.getOverlays().add(rightFogPolygon);
    }

    private Polygon createFogPolygon(double latNorth, double latSouth, double lonWest, double lonEast) {
        Polygon fogPolygon = new Polygon();
        fogPolygon.getOutlinePaint().setAlpha(0);
        fogPolygon.getFillPaint().setColor(0x88000000);
        fogPolygon.getFillPaint().setAlpha(150);
        ArrayList<GeoPoint> fogPoints = new ArrayList<>();
        fogPoints.add(new GeoPoint(latNorth, lonWest));
        fogPoints.add(new GeoPoint(latNorth, lonEast));
        fogPoints.add(new GeoPoint(latSouth, lonEast));
        fogPoints.add(new GeoPoint(latSouth, lonWest));
        fogPolygon.setPoints(fogPoints);
        return fogPolygon;
    }



    //function for moving marker when a location is pressed
    private void moveMarkerTo(GeoPoint point) {
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
        }
        float zoomLevel = (float) mapView.getZoomLevelDouble();
        currentMarker = new Marker(mapView);
        currentMarker.setPosition(point);
        currentMarker.setTitle("Location Selected");
        currentMarker.setId("tempmarker");
        mapView.getOverlays().add(currentMarker);

        Drawable locationIcon = getResources().getDrawable(R.drawable.location_decider);
        if (locationIcon != null) {
            currentMarker.setIcon(resizeDrawable(locationIcon, zoomLevel));
        } else {
            Log.e("Mappart", "Failed to load location icon drawable");
        }

        currentLat = point.getLatitude();
        currentLon = point.getLongitude();


        Bundle bundle = new Bundle();
        bundle.putDouble("lat", currentLat);
        bundle.putDouble("lon", currentLon);

        // Geocoder gets the address from the lats and longs
        Geocoder geocoder = new Geocoder(getContext());
        try {
            List<android.location.Address> addresses = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);
                String addressString = address.getAddressLine(0);

                if (getActivity() instanceof MapDash) {
                    // Update location text in MapDash
                    TextView locationText = getActivity().findViewById(R.id.location_text);
                    if (locationText != null) {
                        locationText.setText("Address: " + addressString);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        // This enables reportbutton after moving, might change to a more distinct thing
        if (getActivity() instanceof MapDash) {
            CardView reportButton = getActivity().findViewById(R.id.report_button);
            if (reportButton != null) {
                reportButton.setEnabled(true);
            }
        }
        if (getActivity() instanceof MapDash) {
            ((MapDash) getActivity()).updateLocation(currentLat, currentLon); // Update the MapDash with the new location
        }


        mapView.postInvalidate();
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

                    //Cant check if user is here
                    @Override
                    public void onLocationChanged(Location location) {
                        // This should update it but I am not sure I will tweak this to maybe a required distance to be updated cause I doubt constant running of this method is going to be very efficient for an app and will cause lag
                        updateUserLocation(location);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                };

                // Register for location updates adjust seconds of update and meter according to what i think is best for systems
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
        } else {
        }
    }


    //ITS WORKING BUT NOT AVAILABLE IN THE EMULATOR BECAUSE THE STARTING LOCATION IS WEIRD
    public void updateUserLocation(Location location) {
        GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
        lastKnownLocation = location;
        this.currentLat = location.getLatitude();
        this.currentLon = location.getLongitude();

        if (getActivity() instanceof MapDash) {
            MapDash mapdash = (MapDash) getActivity();
            mapdash.updateLocation(location.getLatitude(), location.getLongitude());
        }

        if (isWithinBacoor(userLocation)) {

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
        } else {

            showOutOfBoundsPopup();

            // Remove marker if outside Bacoor
            if (userMarker != null) {
                mapView.getOverlays().remove(userMarker);
                userMarker = null;
            }

        }

        mapView.postInvalidate(); // Refresh the map
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

    //checks if person is within bacoor where supposedly it should disable some functions ~~Which i havent added yet or figured what to do
    public boolean isWithinBacoor(GeoPoint location) {

        return location.getLatitude() <= InnerFogBoundary.getLatNorth() &&
                location.getLatitude() >= InnerFogBoundary.getLatSouth() &&
                location.getLongitude() <= InnerFogBoundary.getLonEast() && //just to remember my stinky mistake
                location.getLongitude() >= InnerFogBoundary.getLonWest();   // reason why this checker was funky is because i forgot how longitude works since from the middle it increases since we're at the right side of the world
    }

    private void showOutOfBoundsPopup() {
        new AlertDialog.Builder(getContext())
                .setTitle("Location Alert")
                .setMessage("We see that you currently aren't within Bacoor city. Your actions will be limited. We apologize for the inconvenience.")
                .setPositiveButton("OK", null)
                .show();
    }


    void placeMarkerOnMap(double lat, double lon, String category, String description,
                          String reportMessage, String reportId, String userId, String imageUrl, String addressPrecision) {

        if (isAdded() && getView() != null) {
            mapView = getView().findViewById(R.id.map);

            if (mapView != null) {
                Marker reportMarker = new Marker(mapView);
                reportMarker.setPosition(new GeoPoint(lat, lon));
                reportMarker.setTitle("Report: " + category);

                float zoomLevel = (float) mapView.getZoomLevelDouble();
                Drawable icon = getReportIcon(category, zoomLevel, addressPrecision);

                if (icon != null) {
                    reportMarker.setIcon(icon);
                }

                reportMarker.setId(reportId);
                reportMarker.setSubDescription(category + "|" + addressPrecision);
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

                if (!currentFilterCategory.equals("all") && !currentFilterCategory.equals(categoryKey)) {
                    reportMarker.setVisible(false);
                }

                mapView.postInvalidate();

                reportMarker.setOnMarkerClickListener((marker, mapView) -> {
                    openReportDetailsFragment(reportId, userId);
                    return true;
                });

                reportList.add(new Report(lat, lon, category, description, reportMessage, reportId, imageUrl, userId));
            }
        }
    }


    //handles icon management
    private Drawable getReportIcon(String category, float zoomLevel, String precision) {
        if (category == null || precision == null) return null;

        String iconFileName;

        switch (category.toLowerCase()) {
            case "accident":
            case "fire":
            case "naturaldisaster":
            case "traffic":
                iconFileName = "location_" + precision.toLowerCase() + "_" + category.toLowerCase();
                break;
            default:
                return null;
        }

        int iconResId = getResources().getIdentifier(iconFileName, "drawable", getActivity().getPackageName());

        if (iconResId != 0) {
            Drawable drawable = getResources().getDrawable(iconResId, null);
            return resizeDrawable(drawable, zoomLevel);
        }

        return null;
    }


    private Drawable resizeDrawable(Drawable image, float zoomLevel) {
        if (image == null) return null;

        Bitmap bitmap = ((BitmapDrawable) image).getBitmap();

        // Adjust size based on zoom level (Higher zoom = larger icon)
        int baseSize = 40; // Minimum size at low zoom
        int maxSize = 50; // Maximum size at high zoom
        int size = (int) (baseSize + (zoomLevel * 3)); // Adjust dynamically

        // Ensure size stays within limits
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
        mapView.onDetach();
    }
}

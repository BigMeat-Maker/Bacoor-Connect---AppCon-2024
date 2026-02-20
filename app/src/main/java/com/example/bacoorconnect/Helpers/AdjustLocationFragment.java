package com.example.bacoorconnect.Helpers;

import android.content.res.AssetManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bacoorconnect.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.Projection;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Polygon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdjustLocationFragment extends BottomSheetDialogFragment {

    private MapView mapView;
    private TextView locationText;
    private Button placePinButton;
    private LinearLayout bottomPanel;
    private double selectedLat;
    private double selectedLon;
    private BottomSheetBehavior<?> behavior;

    // Boundary limits
    private static final BoundingBox CameraBoundingBox = new BoundingBox(14.4779, 121.012, 14.356, 120.9249);
    private static final BoundingBox OuterFogBoundary = new BoundingBox(14.56, 121.10, 14.27, 120.84);
    private static final BoundingBox InnerFogBoundary = new BoundingBox(14.4779, 121.012, 14.356, 120.9249);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(requireContext(), requireActivity().getSharedPreferences("osmdroid", requireActivity().MODE_PRIVATE));

        return inflater.inflate(R.layout.fragment_mapchange, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.mapView);
        locationText = view.findViewById(R.id.location_text);
        placePinButton = view.findViewById(R.id.change_location_button);
        bottomPanel = view.findViewById(R.id.bottom_panel);

        View parent = (View) view.getParent();
        if (parent != null) {
            behavior = BottomSheetBehavior.from(parent);
            behavior.setPeekHeight(600);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        mapView.setMinZoomLevel(15.0);
        mapView.setMaxZoomLevel(19.5);

        // FIX: Add touch listener to map view to prevent bottom sheet from closing
        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Disable bottom sheet dragging when touching the map
                if (behavior != null) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            behavior.setDraggable(false);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            behavior.setDraggable(true);
                            break;
                    }
                }
                // Return false to let the map handle the touch event
                return false;
            }
        });

        mapView.post(() -> {
            if (getView() != null && bottomPanel != null) {
                View parentView = (View) mapView.getParent();
                if (parentView != null) {
                    mapView.getLayoutParams().height = parentView.getHeight() - bottomPanel.getHeight();
                    mapView.requestLayout();
                }
            }
        });

        setupMap();

        placePinButton.setOnClickListener(v -> {
            IGeoPoint currentCenter = mapView.getMapCenter();

            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        currentCenter.getLatitude(),
                        currentCenter.getLongitude(),
                        1
                );

                String locationDetails = (addresses != null && !addresses.isEmpty())
                        ? addresses.get(0).getAddressLine(0)
                        : "Unknown Location";

                Bundle result = new Bundle();
                result.putString("locationDetails", locationDetails);
                result.putDouble("lat", currentCenter.getLatitude());
                result.putDouble("lon", currentCenter.getLongitude());

                requireActivity().getSupportFragmentManager()
                        .setFragmentResult("locationResult", result);

                dismiss();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "Error fetching address", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupMap() {
        mapView.setMultiTouchControls(true);

        IMapController mapController = mapView.getController();
        mapController.setZoom(17.5);

        Bundle arguments = getArguments();
        double userLat = 14.4450;
        double userLon = 120.9405;

        if (arguments != null) {
            userLat = arguments.getDouble("lat", userLat);
            userLon = arguments.getDouble("lon", userLon);
        }

        GeoPoint startPoint = new GeoPoint(userLat, userLon);
        mapController.setCenter(startPoint);
        updateLocationText(startPoint);
        loadBacoorBoundaryFromGeoJSON();

        addFoggingOverlay();

        // Simplified MapEventsReceiver
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                // When map is tapped, re-enable bottom sheet dragging
                if (behavior != null) {
                    behavior.setDraggable(true);
                }
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                // Disable bottom sheet dragging during long press
                if (behavior != null) {
                    behavior.setDraggable(false);
                }

                // Move camera to long press location
                mapView.getController().animateTo(p);
                selectedLat = p.getLatitude();
                selectedLon = p.getLongitude();
                updateLocationText(p);

                // Re-enable after a delay
                mapView.postDelayed(() -> {
                    if (behavior != null) {
                        behavior.setDraggable(true);
                    }
                }, 500);

                return true;
            }
        }));
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

    private void loadBacoorBoundaryFromGeoJSON() {
        AssetManager assetManager = getContext().getAssets();
        try {
            InputStream inputStream = assetManager.open("bacoor_boundary.geojson");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();

            String geoJsonString = new String(buffer, StandardCharsets.UTF_8);

            JSONObject geoJsonObject = new JSONObject(geoJsonString);
            JSONArray features = geoJsonObject.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject geometry = feature.getJSONObject("geometry");
                String geometryType = geometry.getString("type");

                if ("Polygon".equals(geometryType)) {
                    JSONArray coordinates = geometry.getJSONArray("coordinates").getJSONArray(0);

                    ArrayList<GeoPoint> boundaryPoints = new ArrayList<>();
                    for (int j = 0; j < coordinates.length(); j++) {
                        JSONArray coordinate = coordinates.getJSONArray(j);
                        double lon = coordinate.getDouble(0);
                        double lat = coordinate.getDouble(1);
                        boundaryPoints.add(new GeoPoint(lat, lon));
                    }

                    Polygon bacoorPolygon = new Polygon();
                    bacoorPolygon.setPoints(boundaryPoints);
                    bacoorPolygon.getFillPaint().setColor(0x00000000);
                    bacoorPolygon.getOutlinePaint().setColor(0xFF00FF00);
                    bacoorPolygon.getOutlinePaint().setStrokeWidth(100);
                    mapView.getOverlays().add(bacoorPolygon);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to load Bacoor boundary.", Toast.LENGTH_SHORT).show();
        }
    }

    private GeoPoint getValidCenterPosition(GeoPoint point) {
        double lat = Math.max(InnerFogBoundary.getLatSouth(), Math.min(InnerFogBoundary.getLatNorth(), point.getLatitude()));
        double lon = Math.max(InnerFogBoundary.getLonWest(), Math.min(InnerFogBoundary.getLonEast(), point.getLongitude()));
        return new GeoPoint(lat, lon);
    }

    private void addFoggingOverlay() {
        addFogPolygon(OuterFogBoundary.getLatNorth(), InnerFogBoundary.getLatNorth(),
                OuterFogBoundary.getLonWest(), OuterFogBoundary.getLonEast());

        addFogPolygon(InnerFogBoundary.getLatSouth(), OuterFogBoundary.getLatSouth(),
                OuterFogBoundary.getLonWest(), OuterFogBoundary.getLonEast());

        addFogPolygon(InnerFogBoundary.getLatNorth(), InnerFogBoundary.getLatSouth(),
                OuterFogBoundary.getLonWest(), InnerFogBoundary.getLonWest());

        addFogPolygon(InnerFogBoundary.getLatNorth(), InnerFogBoundary.getLatSouth(),
                InnerFogBoundary.getLonEast(), OuterFogBoundary.getLonEast());
    }

    private void addFogPolygon(double latNorth, double latSouth, double lonWest, double lonEast) {
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
        mapView.getOverlays().add(fogPolygon);
    }

    private void updateLocationText(GeoPoint geoPoint) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(geoPoint.getLatitude(), geoPoint.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locationDetails = address.getAddressLine(0);
                locationText.setText(locationDetails);
            } else {
                locationText.setText("Unknown Location");
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationText.setText("Error fetching location");
        }
    }

    @Override
    public void onDestroyView() {
        // Re-enable dragging when fragment is destroyed
        if (behavior != null) {
            behavior.setDraggable(true);
        }
        super.onDestroyView();
    }
}
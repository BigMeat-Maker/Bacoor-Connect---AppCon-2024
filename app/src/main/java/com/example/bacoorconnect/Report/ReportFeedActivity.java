package com.example.bacoorconnect.Report;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
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
import java.util.Collections;
import java.util.List;

public class ReportFeedActivity extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private ReportAdapter adapter;
    private List<Report> reportList = new ArrayList<>();
    private DatabaseReference reportRef = FirebaseDatabase.getInstance().getReference("Report");
    private double currentLatitude = 14.4597;
    private double currentLongitude = 120.9333;
    private String focusReportId;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFragmentAttached = false;

    public ReportFeedActivity() {
    }

    public static ReportFeedActivity newInstance() {
        return new ReportFeedActivity();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        isFragmentAttached = true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isFragmentAttached = false;
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_report_feed, container, false);

        // Get arguments
        if (getArguments() != null) {
            focusReportId = getArguments().getString("FOCUS_REPORT_ID");
        }

        recyclerView = view.findViewById(R.id.reportRecyclerView);
        emptyStateText = view.findViewById(R.id.reportEmptyState);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        getCurrentLocation();

        // Initialize adapter with location and highlight callback
        adapter = new ReportAdapter(requireContext(), reportList, currentLatitude, currentLongitude,
                position -> {
                    // Callback when adapter is ready to highlight
                    if (position >= 0 && isFragmentAttached) {
                        handler.postDelayed(() -> {
                            if (isFragmentAttached && recyclerView != null) {
                                recyclerView.smoothScrollToPosition(position);
                                // Optional: Add a subtle animation to highlight
                                recyclerView.post(() -> {
                                    if (isFragmentAttached && recyclerView != null) {
                                        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                                        if (holder != null) {
                                            holder.itemView.setBackgroundColor(
                                                    requireContext().getColor(R.color.highlight_color)
                                            );
                                            // Fade out the highlight after 2 seconds
                                            handler.postDelayed(() -> {
                                                if (isFragmentAttached && holder.itemView != null) {
                                                    holder.itemView.setBackgroundColor(
                                                            requireContext().getColor(android.R.color.transparent)
                                                    );
                                                }
                                            }, 2000);
                                        }
                                    }
                                });
                            }
                        }, 500); // Delay to ensure list is loaded
                    }
                });
        recyclerView.setAdapter(adapter);

        loadReports();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null);
    }

    private void getCurrentLocation() {
        if (getActivity() == null) return;

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void loadReports() {
        reportRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isFragmentAttached) return;

                Log.d("ReportFeed", "Fetched report snapshot count: " + snapshot.getChildrenCount());

                reportList.clear();
                int focusPosition = -1;

                for (DataSnapshot reportSnap : snapshot.getChildren()) {
                    try {
                        Report report = mapSnapshotToReport(reportSnap);
                        if (report != null) {
                            reportList.add(report);

                            // Check if this is the focused report
                            if (focusReportId != null && focusReportId.equals(reportSnap.getKey())) {
                                focusPosition = reportList.size() - 1;
                            }
                        }
                    } catch (Exception parseError) {
                        Log.e("ReportFeed", "Skipped malformed report node: " + reportSnap.getKey(), parseError);
                    }
                }
                Collections.reverse(reportList);

                // Adjust focus position after reversing
                if (focusPosition != -1) {
                    focusPosition = reportList.size() - 1 - focusPosition;
                }

                adapter.notifyDataSetChanged();
                updateEmptyState();

                if (focusPosition != -1) {
                    adapter.highlightPosition(focusPosition);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFragmentAttached) {
                    Log.e("ReportFeed", "Failed to load reports: " + error.getMessage());
                    if (emptyStateText != null) {
                        emptyStateText.setText("Unable to load reports right now.");
                        emptyStateText.setVisibility(View.VISIBLE);
                    }
                    if (recyclerView != null) {
                        recyclerView.setVisibility(View.GONE);
                    }
                    Toast.makeText(getContext(), "Failed to load reports.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private Report mapSnapshotToReport(DataSnapshot reportSnap) {
        if (reportSnap == null || !reportSnap.exists()) {
            return null;
        }

        Report report = new Report();
        report.setReportId(reportSnap.getKey());
        report.setUserId(readString(reportSnap, "userId"));
        report.setDescription(firstNonBlank(
                readString(reportSnap, "description"),
                readString(reportSnap, "reportMessage")
        ));
        report.setCategory(readString(reportSnap, "category"));
        report.setImageUrl(readString(reportSnap, "imageUrl"));
        report.setLocation(readString(reportSnap, "location"));
        report.setTimestamp(readLong(reportSnap, "timestamp", 0L));
        report.setUpvotes(readInt(reportSnap, "upvotes", 0));
        report.setDownvotes(readInt(reportSnap, "downvotes", 0));

        Double latitude = firstNonNull(
                readDouble(reportSnap, "latitude"),
                readDouble(reportSnap, "lat")
        );
        Double longitude = firstNonNull(
                readDouble(reportSnap, "longitude"),
                readDouble(reportSnap, "lon"),
                readDouble(reportSnap, "lng")
        );

        report.setLatitude(latitude != null ? latitude : 0d);
        report.setLongitude(longitude != null ? longitude : 0d);

        if ((report.getLatitude() == 0 || report.getLongitude() == 0) && report.getLocation() != null) {
            report.parseCoordinatesFromLocation();
        }

        return report;
    }

    private String readString(DataSnapshot parent, String key) {
        Object value = parent.child(key).getValue();
        return value == null ? null : String.valueOf(value);
    }

    private int readInt(DataSnapshot parent, String key, int fallback) {
        Object value = parent.child(key).getValue();
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private long readLong(DataSnapshot parent, String key, long fallback) {
        Object value = parent.child(key).getValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private Double readDouble(DataSnapshot parent, String key) {
        Object value = parent.child(key).getValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private void updateEmptyState() {
        if (emptyStateText == null || recyclerView == null) {
            return;
        }

        if (reportList.isEmpty()) {
            emptyStateText.setText("No reports available yet.");
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
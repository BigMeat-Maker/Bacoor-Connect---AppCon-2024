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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.bacoorconnect.Helpers.ReportFlag;
import com.example.bacoorconnect.Helpers.TrustScoreHelper;
import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportFeedActivity extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportAdapter adapter;
    private List<Report> reportList = new ArrayList<>();
    private DatabaseReference reportRef = FirebaseDatabase.getInstance().getReference("Report");
    private double currentLatitude = 14.4597;
    private double currentLongitude = 120.9333;
    private String focusReportId;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private boolean isFragmentAttached = false;

    private ValueEventListener reportListener;
    private boolean isRefreshing = false;
    private static final long AUTO_REFRESH_INTERVAL = 30000; // 30 secs

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
        handler.removeCallbacksAndMessages(null);
        stopAutoRefresh();
        if (reportListener != null && reportRef != null) {
            reportRef.removeEventListener(reportListener);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_report_feed, container, false);

        if (getArguments() != null) {
            focusReportId = getArguments().getString("FOCUS_REPORT_ID");
        }

        recyclerView = view.findViewById(R.id.reportRecyclerView);
        emptyStateText = view.findViewById(R.id.reportEmptyState);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        getCurrentLocation();

        adapter = new ReportAdapter(requireContext(), reportList, currentLatitude, currentLongitude,
                position -> {
                    if (position >= 0 && isFragmentAttached) {
                        handler.postDelayed(() -> {
                            if (isFragmentAttached && recyclerView != null) {
                                recyclerView.smoothScrollToPosition(position);
                                recyclerView.post(() -> {
                                    if (isFragmentAttached && recyclerView != null) {
                                        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                                        if (holder != null) {
                                            holder.itemView.setBackgroundColor(
                                                    requireContext().getColor(R.color.highlight_color)
                                            );
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
                        }, 500);
                    }
                });
        recyclerView.setAdapter(adapter);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::manualRefresh);
            swipeRefreshLayout.setColorSchemeColors(
                    getResources().getColor(R.color.baconnect_blue),
                    getResources().getColor(R.color.baconnect_dark_blue)
            );
        }

        loadReports();

        startAutoRefresh();

        return view;
    }

    private void startAutoRefresh() {
        if (autoRefreshRunnable == null) {
            autoRefreshRunnable = () -> {
                if (isFragmentAttached && !isRefreshing) {
                    Log.d("ReportFeed", "Auto-refreshing reports...");
                    refreshDataQuietly();
                }
                autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
            };
            autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
        }
    }

    private void stopAutoRefresh() {
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            autoRefreshRunnable = null;
        }
    }

    private void refreshDataQuietly() {

        if (reportListener != null) {
            reportRef.removeEventListener(reportListener);
        }
        loadReports();
    }

    private void manualRefresh() {
        if (isRefreshing) return;

        isRefreshing = true;


        if (reportListener != null) {
            reportRef.removeEventListener(reportListener);
        }
        loadReports();


        getCurrentLocation();


        handler.postDelayed(() -> {
            if (isRefreshing && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
                isRefreshing = false;
            }
        }, 5000);
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
                    adapter.updateLocation(currentLatitude, currentLongitude);
                }
            }
        }
    }

    private void loadReports() {
        if (reportListener != null) {
            reportRef.removeEventListener(reportListener);
        }

        reportListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isFragmentAttached) return;

                Log.d("ReportFeed", "Fetched report snapshot count: " + snapshot.getChildrenCount());

                List<Report> newReportList = new ArrayList<>();
                int focusPosition = -1;

                for (DataSnapshot reportSnap : snapshot.getChildren()) {
                    try {
                        Report report = mapSnapshotToReport(reportSnap);
                        if (report != null) {
                            newReportList.add(report);

                            if (focusReportId != null && focusReportId.equals(reportSnap.getKey())) {
                                focusPosition = newReportList.size() - 1;
                            }
                        }
                    } catch (Exception parseError) {
                        Log.e("ReportFeed", "Skipped malformed report node: " + reportSnap.getKey(), parseError);
                    }
                }
                Collections.reverse(newReportList);

                if (focusPosition != -1) {
                    focusPosition = newReportList.size() - 1 - focusPosition;
                }


                boolean hasChanges = reportList.size() != newReportList.size();
                if (!hasChanges) {
                    for (int i = 0; i < reportList.size(); i++) {
                        if (!reportList.get(i).getReportId().equals(newReportList.get(i).getReportId())) {
                            hasChanges = true;
                            break;
                        }
                    }
                }

                if (hasChanges) {
                    reportList.clear();
                    reportList.addAll(newReportList);
                    adapter.notifyDataSetChanged();
                }

                updateEmptyState();

                if (focusPosition != -1) {
                    adapter.highlightPosition(focusPosition);
                }


                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                isRefreshing = false;
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

                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    isRefreshing = false;
                }
            }
        };

        reportRef.addValueEventListener(reportListener);
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

        Integer flagCount = reportSnap.child("flagCount").getValue(Integer.class);
        if (flagCount != null) {
            report.setFlagCount(flagCount);
        }

        Integer editCount = reportSnap.child("editCount").getValue(Integer.class);
        if (editCount != null && editCount > 0) {
            report.setEditCount(editCount);
        }

        Long lastEdited = reportSnap.child("lastEdited").getValue(Long.class);
        if (lastEdited != null) {
            report.setLastEdited(lastEdited);
        }

        Object editHistoryObj = reportSnap.child("editHistory").getValue();
        if (editHistoryObj instanceof Map) {
            report.setEditHistory((Map<String, Object>) editHistoryObj);
        }

        Object flagsObj = reportSnap.child("flags").getValue();
        if (flagsObj instanceof Map) {
            Map<String, Map<String, Object>> rawFlags = (Map<String, Map<String, Object>>) flagsObj;
            Map<String, ReportFlag> convertedFlags = new HashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : rawFlags.entrySet()) {
                ReportFlag flag = new ReportFlag();
                Object userId = entry.getValue().get("userId");
                Object reason = entry.getValue().get("reason");
                Object timestamp = entry.getValue().get("timestamp");

                if (userId instanceof String) flag.setUserId((String) userId);
                if (reason instanceof String) flag.setReason((String) reason);
                if (timestamp instanceof Long) flag.setTimestamp((Long) timestamp);
                else if (timestamp instanceof Integer) flag.setTimestamp(((Integer) timestamp).longValue());

                convertedFlags.put(entry.getKey(), flag);
            }
            report.setFlags(convertedFlags);
        }

        report.setLatitude(latitude != null ? latitude : 0d);
        report.setLongitude(longitude != null ? longitude : 0d);

        if ((report.getLatitude() == 0 || report.getLongitude() == 0) && report.getLocation() != null) {
            report.parseCoordinatesFromLocation();
        }

        Object scanResultsObj = reportSnap.child("scanResults").getValue();
        if (scanResultsObj instanceof Map) {
            report.setScanResults((Map<String, Object>) scanResultsObj);
        }


        loadUserStatsForReport(report, reportSnap.child("userId").getValue(String.class));

        return report;
    }

    private void loadUserStatsForReport(Report report, String userId) {
        if (userId == null || userId.isEmpty()) return;

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double trustScore = snapshot.child("trustScore").getValue(Double.class);
                    Integer totalReports = snapshot.child("totalReports").getValue(Integer.class);
                    Integer approvedReports = snapshot.child("approvedReports").getValue(Integer.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    if (trustScore != null) report.setTrustScore(trustScore);
                    if (totalReports != null) report.setTotalReports(totalReports);
                    if (approvedReports != null) report.setApprovedReports(approvedReports);
                    if (joinDate != null) report.setJoinDate(joinDate);

                    if (adapter != null) {
                        int index = reportList.indexOf(report);
                        if (index != -1) {
                            adapter.notifyItemChanged(index);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ReportFeed", "Failed to load user stats", error.toException());
            }
        });
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reportListener != null && reportRef != null) {
            reportRef.removeEventListener(reportListener);
        }
        stopAutoRefresh();
    }
}
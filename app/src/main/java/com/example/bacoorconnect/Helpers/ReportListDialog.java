package com.example.bacoorconnect.Helpers;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.bacoorconnect.General.MapDash;
import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportListDialog extends DialogFragment {

    private String category;
    private LinearLayout containerReports;
    private double userLat = 14.4444; // Default Bacoor coordinates
    private double userLon = 120.9515;

    public static ReportListDialog newInstance(String category, double userLat, double userLon) {
        ReportListDialog dialog = new ReportListDialog();
        Bundle args = new Bundle();
        args.putString("category", category);
        args.putDouble("userLat", userLat);
        args.putDouble("userLon", userLon);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            category = getArguments().getString("category");
            userLat = getArguments().getDouble("userLat", 14.4444);
            userLon = getArguments().getDouble("userLon", 120.9515);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_report_list, null);

        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);
        containerReports = view.findViewById(R.id.container_reports);
        Button closeButton = view.findViewById(R.id.close_button);

        String categoryText = category.equals("all") ? "All Reports" :
                category.substring(0, 1).toUpperCase() + category.substring(1) + " Reports";
        title.setText(categoryText);

        loadReports();

        closeButton.setOnClickListener(v -> dismiss());

        builder.setView(view);
        return builder.create();
    }

    private void loadReports() {
        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");

        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                containerReports.removeAllViews();

                Log.d("ReportListDialog", "Total reports in database: " + snapshot.getChildrenCount());

                int reportCount = 0;
                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    String reportCategory = reportSnapshot.child("category").getValue(String.class);

                    if (category.equals("all") || (reportCategory != null && reportCategory.equals(category))) {
                        reportCount++;

                        // Get ALL report data fields
                        String reportId = reportSnapshot.getKey();
                        String description = reportSnapshot.child("description").getValue(String.class);
                        String userId = reportSnapshot.child("userId").getValue(String.class);
                        String locationText = reportSnapshot.child("location").getValue(String.class);

                        Double latitude = null;
                        Double longitude = null;

                        if (reportSnapshot.child("latitude").exists()) {
                            latitude = reportSnapshot.child("latitude").getValue(Double.class);
                        }
                        if (reportSnapshot.child("lat").exists()) {
                            latitude = reportSnapshot.child("lat").getValue(Double.class);
                        }
                        if (reportSnapshot.child("longitude").exists()) {
                            longitude = reportSnapshot.child("longitude").getValue(Double.class);
                        }
                        if (reportSnapshot.child("lon").exists()) {
                            longitude = reportSnapshot.child("lon").getValue(Double.class);
                        }

                        Long timestamp = reportSnapshot.child("timestamp").getValue(Long.class);
                        Integer upvotes = reportSnapshot.child("upvotes").getValue(Integer.class);
                        Integer downvotes = reportSnapshot.child("downvotes").getValue(Integer.class);

                        Log.d("ReportListDialog", "Processing report " + reportId + ":");
                        Log.d("ReportListDialog", "  Category: " + reportCategory);
                        Log.d("ReportListDialog", "  Description: " + description);
                        Log.d("ReportListDialog", "  Lat: " + latitude + ", Lon: " + longitude);

                        View cardView = createReportCard(
                                reportId, userId, reportCategory, description,
                                latitude, longitude, timestamp, upvotes, downvotes
                        );

                        containerReports.addView(cardView);
                    }
                }

                Log.d("ReportListDialog", "Reports displayed: " + reportCount);

                if (reportCount == 0) {
                    TextView emptyText = new TextView(getContext());
                    emptyText.setText("No reports found for this category");
                    emptyText.setTextSize(16);
                    emptyText.setPadding(32, 32, 32, 32);
                    emptyText.setTextColor(0xFF757575);
                    emptyText.setGravity(android.view.Gravity.CENTER);
                    containerReports.addView(emptyText);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ReportListDialog", "Error loading reports: " + error.getMessage());

                TextView errorText = new TextView(getContext());
                errorText.setText("Error loading reports: " + error.getMessage());
                errorText.setTextSize(16);
                errorText.setPadding(32, 32, 32, 32);
                errorText.setTextColor(0xFFF44336);
                errorText.setGravity(android.view.Gravity.CENTER);
                containerReports.addView(errorText);
            }
        });
    }

    private View createReportCard(String reportId, String userId, String category,
                                  String description, Double latitude, Double longitude,
                                  Long timestamp, Integer upvotes, Integer downvotes) {

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View cardView = inflater.inflate(R.layout.item_report_card_simple, null);

        TextView categoryTag = cardView.findViewById(R.id.text_category_tag);
        TextView descText = cardView.findViewById(R.id.text_description);
        TextView locationText = cardView.findViewById(R.id.text_location);
        TextView distanceText = cardView.findViewById(R.id.text_distance);
        TextView timeText = cardView.findViewById(R.id.text_time);
        TextView upvotesText = cardView.findViewById(R.id.text_upvotes);
        TextView downvotesText = cardView.findViewById(R.id.text_downvotes);


        if (categoryTag != null) {
            categoryTag.setText(category != null ? category.toUpperCase() : "UNKNOWN");
        }

        if (descText != null) {
            descText.setText(description != null ? description : "No description");
        }

        if (locationText != null && latitude != null && longitude != null) {
            DecimalFormat df = new DecimalFormat("0.00");
            String latStr = df.format(latitude);
            String lonStr = df.format(longitude);
            locationText.setText(latStr + ", " + lonStr);
        } else if (locationText != null) {
            locationText.setText("Location not set");
        }

        if (distanceText != null && userLat != 0.0 && userLon != 0.0 && latitude != null && longitude != null) {
            float distance = calculateDistance(userLat, userLon, latitude, longitude);
            if (distance < 1) {
                distanceText.setText(String.format("%.0fm away", distance * 1000));
            } else {
                distanceText.setText(String.format("%.1fkm away", distance));
            }
        } else if (distanceText != null) {
            distanceText.setText("--");
        }

        if (timeText != null && timestamp != null) {
            timeText.setText(getTimeAgo(timestamp));
        } else if (timeText != null) {
            timeText.setText("Recently");
        }

        if (upvotesText != null) {
            upvotesText.setText(String.valueOf(upvotes != null ? upvotes : 0));
        }
        if (downvotesText != null) {
            downvotesText.setText(String.valueOf(downvotes != null ? downvotes : 0));
        }

        cardView.setOnClickListener(v -> {
            if (getActivity() instanceof MapDash) {
                Mappart mapFragment = (Mappart) getParentFragmentManager()
                        .findFragmentById(R.id.map_placeholder);
                if (mapFragment != null) {
                    mapFragment.openReportDetailsFragment(reportId, userId);
                }
            }
            dismiss();
        });

        return cardView;
    }

    private float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000;
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "Just now";
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();

            DisplayMetrics displayMetrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenHeight = displayMetrics.heightPixels;

            // Set dialog to 65% of screen height
            int dialogHeight = (int) (screenHeight * 0.65);

            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dialogHeight
            );
            window.setGravity(Gravity.BOTTOM);
        }
    }
}
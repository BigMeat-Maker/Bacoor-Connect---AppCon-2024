package com.example.bacoorconnect.Report;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.bacoorconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class ReportDetailsFrag extends DialogFragment {

    private TextView descriptionView, upvoteCountView, downvoteCountView, user_distance;
    private TextView locationTextView, usernameView;
    private ImageView upvoteButton, downvoteButton, userProfileImageView;
    private DatabaseReference reportRef, usersRef, auditRef;
    private String reportId, currentUserId;
    private double userLat = 14.4597; // Default Bacoor coordinates
    private double userLon = 120.9333;

    public ReportDetailsFrag() {
    }

    public static ReportDetailsFrag newInstance(String reportId, double userLat, double userLon) {
        ReportDetailsFrag fragment = new ReportDetailsFrag();
        Bundle args = new Bundle();
        args.putString("reportId", reportId);
        args.putDouble("userLat", userLat);
        args.putDouble("userLon", userLon);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            reportId = getArguments().getString("reportId");
            userLat = getArguments().getDouble("userLat", 14.4597);
            userLon = getArguments().getDouble("userLon", 120.9333);
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        reportRef = FirebaseDatabase.getInstance().getReference("Report").child(reportId);
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report_details, container, false);

        // Initialize all views
        descriptionView = view.findViewById(R.id.report_description);
        upvoteCountView = view.findViewById(R.id.upvote_count);
        downvoteCountView = view.findViewById(R.id.downvote_count);
        upvoteButton = view.findViewById(R.id.upvote_button);
        downvoteButton = view.findViewById(R.id.downvote_button);
        user_distance = view.findViewById(R.id.user_distance);
        locationTextView = view.findViewById(R.id.location_text);
        usernameView = view.findViewById(R.id.Username);
        userProfileImageView = view.findViewById(R.id.user_profile_image);

        loadReportDetails();

        upvoteButton.setOnClickListener(v -> modifyVote(true));
        downvoteButton.setOnClickListener(v -> modifyVote(false));

        ImageView threedots = view.findViewById(R.id.threedots);
        threedots.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), threedots);
            popupMenu.getMenuInflater().inflate(R.menu.post_options_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.option_edit) {
                    Intent intent = new Intent(requireContext(), EditReport.class);
                    intent.putExtra("reportId", reportId);
                    startActivity(intent);
                    return true;
                } else if (item.getItemId() == R.id.option_delete) {
                    deleteReport();
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });

        return view;
    }

    private void loadReportDetails() {
        reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String description = snapshot.child("description").getValue(String.class);
                    int upvotes = snapshot.child("upvotes").getValue(Integer.class);
                    int downvotes = snapshot.child("downvotes").getValue(Integer.class);
                    String reportOwnerId = snapshot.child("userId").getValue(String.class);
                    double reportLat = snapshot.child("latitude").getValue(Double.class);
                    double reportLon = snapshot.child("longitude").getValue(Double.class);
                    String previousVote = snapshot.child("voters").child(currentUserId).getValue(String.class);
                    String category = snapshot.child("category").getValue(String.class);
                    String imageUrl = snapshot.child("imageUrl").getValue(String.class);
                    String location = snapshot.child("location").getValue(String.class);

                    ImageView reportImageView = getView().findViewById(R.id.report_image);

                    // Calculate and display distance
                    calculateDistance(reportLat, reportLon);

                    // Display location text
                    if (location != null && !location.isEmpty()) {
                        String formattedLocation = formatLocation(location);
                        locationTextView.setText(formattedLocation);
                    } else {
                        locationTextView.setText("Location not specified");
                    }

                    // Load report image
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .centerCrop()
                                .into(reportImageView);
                        reportImageView.setVisibility(View.VISIBLE);
                    } else {
                        reportImageView.setVisibility(View.GONE);
                    }

                    // Set text values
                    descriptionView.setText(description);
                    upvoteCountView.setText(String.valueOf(upvotes));
                    downvoteCountView.setText(String.valueOf(downvotes));

                    // Update category icon
                    if (category != null) {
                        updateCategoryIcon(category.toLowerCase());
                    }

                    // Load user data (name and profile picture)
                    if (reportOwnerId != null) {
                        fetchAndDisplayUserInfo(reportOwnerId);
                    }

                    // Update vote icons
                    updateVoteIcons(previousVote);

                    // Show/hide options button
                    ImageView threedots = getView().findViewById(R.id.threedots);
                    if (reportOwnerId != null && reportOwnerId.equals(currentUserId)) {
                        threedots.setVisibility(View.VISIBLE);
                    } else {
                        threedots.setVisibility(View.GONE);
                    }
                } else {
                    Toast.makeText(getActivity(), "Report not found.", Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to load report details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void calculateDistance(double reportLat, double reportLon) {
        Log.d("ReportDetailsFrag", "User Location: (" + userLat + ", " + userLon + ")");
        Log.d("ReportDetailsFrag", "Report Location: (" + reportLat + ", " + reportLon + ")");

        // Calculate the distance using the Location API
        float[] results = new float[1];
        android.location.Location.distanceBetween(userLat, userLon, reportLat, reportLon, results);

        float distanceInMeters = results[0];
        float distanceInKm = distanceInMeters / 1000;

        // Display the distance
        user_distance.setText(String.format("%.2f km", distanceInKm));
    }

    private String formatLocation(String location) {
        if (location == null || location.isEmpty()) {
            return "Location unknown";
        }

        try {
            if (location.contains("Lat:") && location.contains("Lon:")) {
                String[] parts = location.split(",");
                if (parts.length >= 2) {
                    String latPart = parts[0].replace("Lat:", "").trim();
                    String lonPart = parts[1].replace("Lon:", "").trim();

                    double lat = Double.parseDouble(latPart);
                    double lon = Double.parseDouble(lonPart);

                    return String.format("Lat: %.2f, Lon: %.2f", lat, lon);
                }
            }
            return location;
        } catch (NumberFormatException e) {
            return location;
        }
    }

    private void updateCategoryIcon(String category) {
        int categoryDrawable;
        switch (category) {
            case "accident":
                categoryDrawable = R.drawable.tag_roadaccident;
                break;
            case "fire":
                categoryDrawable = R.drawable.tag_fire;
                break;
            case "naturaldisaster":
                categoryDrawable = R.drawable.tag_disaster;
                break;
            case "traffic":
                categoryDrawable = R.drawable.tag_traffic;
                break;
            default:
                categoryDrawable = R.drawable.tag_roadaccident;
                break;
        }

        ImageView reportCategoryImageView = getView().findViewById(R.id.report_category);
        reportCategoryImageView.setImageResource(categoryDrawable);
    }

    private void fetchAndDisplayUserInfo(String userId) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get user name
                    String firstName = snapshot.child("firstName").getValue(String.class);
                    String lastName = snapshot.child("lastName").getValue(String.class);
                    if (firstName != null && lastName != null) {
                        usernameView.setText(firstName + " " + lastName);
                    } else if (firstName != null) {
                        usernameView.setText(firstName);
                    } else {
                        usernameView.setText("Unknown User");
                    }

                    // Get and load profile image
                    String profileImageUrl = snapshot.child("profileImage").getValue(String.class);
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.profile)
                                .into(userProfileImageView);
                    }
                } else {
                    usernameView.setText("Unknown User");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                usernameView.setText("Unknown User");
            }
        });
    }

    private void updateVoteIcons(String previousVote) {
        if ("upvote".equals(previousVote)) {
            upvoteButton.setImageResource(R.drawable.upvote_filled);
            downvoteButton.setImageResource(R.drawable.downvote_blank);
        } else if ("downvote".equals(previousVote)) {
            upvoteButton.setImageResource(R.drawable.upvote_blank);
            downvoteButton.setImageResource(R.drawable.downvote_filled);
        } else {
            upvoteButton.setImageResource(R.drawable.upvote_blank);
            downvoteButton.setImageResource(R.drawable.downvote_blank);
        }
    }

    private boolean isVotingInProgress = false;

    @SuppressLint("SetTextI18n")
    private void modifyVote(boolean isUpvote) {
        if (isVotingInProgress) return;
        isVotingInProgress = true;

        reportRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                int upvotes = snapshot.child("upvotes").getValue(Integer.class);
                int downvotes = snapshot.child("downvotes").getValue(Integer.class);
                String previousVote = snapshot.child("voters").child(currentUserId).getValue(String.class);
                HashMap<String, Object> updateData = new HashMap<>();

                if (previousVote != null) {
                    if (previousVote.equals("upvote") && isUpvote) {
                        upvotes--;
                        updateData.put("voters/" + currentUserId, null);
                        updateVoteIcons(null);
                    } else if (previousVote.equals("downvote") && !isUpvote) {
                        downvotes--;
                        updateData.put("voters/" + currentUserId, null);
                        updateVoteIcons(null);
                    } else if (previousVote.equals("upvote") && !isUpvote) {
                        upvotes--;
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                        updateVoteIcons("downvote");
                    } else if (previousVote.equals("downvote") && isUpvote) {
                        downvotes--;
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                        updateVoteIcons("upvote");
                    }
                } else {
                    if (isUpvote) {
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                        updateVoteIcons("upvote");
                    } else {
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                        updateVoteIcons("downvote");
                    }
                }

                updateData.put("upvotes", upvotes);
                updateData.put("downvotes", downvotes);

                upvoteCountView.setText(String.valueOf(upvotes));
                downvoteCountView.setText(String.valueOf(downvotes));

                reportRef.updateChildren(updateData).addOnCompleteListener(task -> isVotingInProgress = false);
            } else {
                isVotingInProgress = false;
            }
        }).addOnFailureListener(e -> {
            isVotingInProgress = false;
        });
    }

    private void deleteReport() {
        reportRef.removeValue().addOnSuccessListener(aVoid -> {
            logActivity(currentUserId, "Delete", "Deleted Report", reportId, "Success", "User deleted the report", "N/A");
            dismiss();
        });
    }

    private void logActivity(String userId, String type, String action, String target, String status, String notes, String changes) {
        String logId = auditRef.push().getKey();
        HashMap<String, Object> logData = new HashMap<>();
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
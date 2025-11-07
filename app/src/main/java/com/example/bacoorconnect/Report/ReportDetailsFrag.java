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
    private ImageView upvoteButton, downvoteButton, categoryImageView;
    private DatabaseReference reportRef, auditRef;
    private String reportId, currentUserId;
    private static double userLat;
    private static double userLon;

    public ReportDetailsFrag() {
    }

    public static ReportDetailsFrag newInstance(String reportId, String userId) {
        ReportDetailsFrag fragment = new ReportDetailsFrag();
        Bundle args = new Bundle();
        args.putString("reportId", reportId);
        args.putString("userId", userId);
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
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            userLat = getArguments().getDouble("userLat");
            userLon = getArguments().getDouble("userLon");

        }
        reportRef = FirebaseDatabase.getInstance().getReference("Report").child(reportId);
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report_details, container, false);

        descriptionView = view.findViewById(R.id.report_description);
        upvoteCountView = view.findViewById(R.id.upvote_count);
        downvoteCountView = view.findViewById(R.id.downvote_count);
        upvoteButton = view.findViewById(R.id.upvote_button);
        downvoteButton = view.findViewById(R.id.downvote_button);

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

                    ImageView reportImageView = getView().findViewById(R.id.report_image);

                    calculateDistance(reportLat, reportLon);

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(reportImageView);
                        reportImageView.setVisibility(View.VISIBLE);
                    } else {
                        reportImageView.setVisibility(View.GONE);
                    }

                    descriptionView.setText(description);
                    upvoteCountView.setText("" + upvotes);
                    downvoteCountView.setText("" + downvotes);

                    if (category != null) {
                        updateCategoryIcon(category.toLowerCase());
                    }

                    if (reportOwnerId != null) {
                        fetchAndDisplayUsername(reportOwnerId);
                    }

                    updateVoteIcons(previousVote);

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

        float distanceInMeters = results[0]; // Distance in meters

        // Debugging: Log the distance in meters
        Log.d("ReportDetailsFrag", "Distance (meters): " + distanceInMeters);

        // Convert the distance to kilometers
        float distanceInKm = distanceInMeters / 1000;

        // Debugging: Log the distance in kilometers
        Log.d("ReportDetailsFrag", "Distance (km): " + distanceInKm);

        // Display the distance in the TextView (you can modify this as needed)
        TextView userDistanceView = getView().findViewById(R.id.user_distance);
        userDistanceView.setText("Distance: " + String.format("%.2f", distanceInKm) + " km");
    }



    //self explanatory i think just a nifty lil thing to update the tag
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
                categoryDrawable = R.drawable.tag_roadaccident;  // Default to road accident if unknown
                break;
        }

        ImageView reportCategoryImageView = getView().findViewById(R.id.report_category);
        reportCategoryImageView.setImageResource(categoryDrawable);
    }

    //gets the firstname which i hope will be changed to username soon and pops it at a temp place at the fragment
    private void fetchAndDisplayUsername(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);

        userRef.child("firstName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String firstName = snapshot.getValue(String.class);
                    TextView usernameView = getView().findViewById(R.id.Username);
                    usernameView.setText(firstName != null ? firstName : "Unknown User");
                } else {
                    Toast.makeText(getActivity(), "User not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to load username.", Toast.LENGTH_SHORT).show();
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

                String voterPath = "voters/" + currentUserId;
                String previousVote = snapshot.child("voters").child(currentUserId).getValue(String.class);

                HashMap<String, Object> updateData = new HashMap<>();
                String action = isUpvote ? "upvote" : "downvote";

                // Handles voting scenarios like changes cancels etc
                if (previousVote != null) {
                    if (previousVote.equals("upvote") && isUpvote) {
                        upvotes--;
                        updateData.put(voterPath, null);
                        updateVoteIcons(null);
                    } else if (previousVote.equals("downvote") && !isUpvote) {
                        downvotes--;
                        updateData.put(voterPath, null);
                        updateVoteIcons(null);
                    } else if (previousVote.equals("upvote") && !isUpvote) {
                        upvotes--;
                        downvotes++;
                        updateData.put(voterPath, "downvote");
                        updateVoteIcons("downvote");
                    } else if (previousVote.equals("downvote") && isUpvote) {
                        downvotes--;
                        upvotes++;
                        updateData.put(voterPath, "upvote");
                        updateVoteIcons("upvote");
                    }
                } else {
                    if (isUpvote) {
                        upvotes++;
                        updateData.put(voterPath, "upvote");
                        updateVoteIcons("upvote");
                    } else {
                        downvotes++;
                        updateData.put(voterPath, "downvote");
                        updateVoteIcons("downvote");
                    }
                }

                updateData.put("upvotes", upvotes);
                updateData.put("downvotes", downvotes);

                upvoteCountView.setText(""+upvotes);
                downvoteCountView.setText(""+downvotes);

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
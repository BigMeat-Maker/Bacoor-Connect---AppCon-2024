package com.example.bacoorconnect.Report;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

// Add this interface
interface OnHighlightReadyListener {
    void onHighlightReady(int position);
}

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reportList;
    private Context context;
    private DatabaseReference reportRef, usersRef;
    private String currentUserId;
    private double currentLatitude = 14.4597; // Default Bacoor coordinates
    private double currentLongitude = 120.9333;
    private int highlightedPosition = -1;
    private OnHighlightReadyListener highlightListener;
    private Handler highlightHandler = new Handler();
    private Runnable clearHighlightRunnable;

    // Original constructor
    public ReportAdapter(Context context, List<Report> reportList, double currentLatitude, double currentLongitude) {
        this.context = context;
        this.reportList = reportList;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.reportRef = FirebaseDatabase.getInstance().getReference("Report");
        this.usersRef = FirebaseDatabase.getInstance().getReference("Users");
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
    }

    // New constructor with highlight listener
    public ReportAdapter(Context context, List<Report> reportList, double currentLatitude, double currentLongitude, OnHighlightReadyListener listener) {
        this(context, reportList, currentLatitude, currentLongitude);
        this.highlightListener = listener;
    }

    public void highlightPosition(int position) {
        this.highlightedPosition = position;
        notifyDataSetChanged();

        // Notify listener to scroll to position
        if (highlightListener != null) {
            highlightListener.onHighlightReady(position);
        }

        // Clear highlight after 3 seconds
        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
        }

        clearHighlightRunnable = () -> {
            highlightedPosition = -1;
            notifyDataSetChanged();
        };
        highlightHandler.postDelayed(clearHighlightRunnable, 3000);
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.report_item, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reportList.get(position);

        // Apply highlight if this position is highlighted
        if (position == highlightedPosition) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.highlight_color));
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));
        }

        holder.descriptionView.setText(report.getDescription());
        holder.upvoteCountView.setText(String.valueOf(report.getUpvotes()));
        holder.downvoteCountView.setText(String.valueOf(report.getDownvotes()));

        updateCategoryIcon(report.getCategory(), holder.categoryImageView);

        if (report.getLocation() != null && !report.getLocation().isEmpty()) {
            String formattedLocation = formatLocation(report.getLocation());
            holder.locationTextView.setText(formattedLocation);
        } else {
            holder.locationTextView.setText("Location not specified");
        }

        loadUserData(report.getUserId(), holder);

        if (report.getLat() != 0 && report.getLon() != 0) {
            double distance = calculateDistance(currentLatitude, currentLongitude,
                    report.getLat(), report.getLon());
            holder.distanceView.setText(String.format("%.2f km", distance));
        } else {
            holder.distanceView.setText("-- km");
        }

        if (report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(report.getImageUrl())
                    .into(holder.reportImage);
            holder.reportImage.setVisibility(View.VISIBLE);
        } else {
            holder.reportImage.setVisibility(View.GONE);
        }

        loadUserVote(report.getReportId(), holder.upvoteButton, holder.downvoteButton);

        holder.upvoteButton.setOnClickListener(v -> modifyVote(report, true, holder));
        holder.downvoteButton.setOnClickListener(v -> modifyVote(report, false, holder));

        if (report.getUserId() != null && report.getUserId().equals(currentUserId)) {
            holder.optionsButton.setVisibility(View.VISIBLE);
            holder.optionsButton.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(context, holder.optionsButton);
                popupMenu.getMenuInflater().inflate(R.menu.post_options_menu, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.option_edit) {
                        Intent intent = new Intent(context, EditReport.class);
                        intent.putExtra("reportId", report.getReportId());
                        context.startActivity(intent);
                        return true;
                    } else if (item.getItemId() == R.id.option_delete) {
                        deleteReport(report);
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            });
        } else {
            holder.optionsButton.setVisibility(View.GONE);
        }
    }

    private void loadUserData(String userId, ReportViewHolder holder) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String firstName = snapshot.child("firstName").getValue(String.class);
                    String lastName = snapshot.child("lastName").getValue(String.class);
                    if (firstName != null && lastName != null) {
                        holder.usernameView.setText(firstName + " " + lastName);
                    } else if (firstName != null) {
                        holder.usernameView.setText(firstName);
                    } else {
                        holder.usernameView.setText("Unknown User");
                    }

                    String profileImageUrl = snapshot.child("profileImage").getValue(String.class);
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Glide.with(context)
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.profile)
                                .into(holder.userProfileImageView);
                    }
                } else {
                    holder.usernameView.setText("Unknown User");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                holder.usernameView.setText("Unknown User");
            }
        });
    }

    private void loadUserVote(String reportId, ImageView upvoteButton, ImageView downvoteButton) {
        reportRef.child(reportId).child("voters").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String vote = snapshot.getValue(String.class);
                            if ("upvote".equals(vote)) {
                                upvoteButton.setImageResource(R.drawable.upvote_filled);
                                downvoteButton.setImageResource(R.drawable.downvote_blank);
                            } else if ("downvote".equals(vote)) {
                                upvoteButton.setImageResource(R.drawable.upvote_blank);
                                downvoteButton.setImageResource(R.drawable.downvote_filled);
                            }
                        } else {
                            upvoteButton.setImageResource(R.drawable.upvote_blank);
                            downvoteButton.setImageResource(R.drawable.downvote_blank);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        upvoteButton.setImageResource(R.drawable.upvote_blank);
                        downvoteButton.setImageResource(R.drawable.downvote_blank);
                    }
                });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return Math.round((earthRadius * c) * 100.0) / 100.0;
    }

    private void modifyVote(Report report, boolean isUpvote, ReportViewHolder holder) {
        reportRef.child(report.getReportId()).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                int upvotes = snapshot.child("upvotes").getValue(Integer.class);
                int downvotes = snapshot.child("downvotes").getValue(Integer.class);

                String previousVote = snapshot.child("voters").child(currentUserId).getValue(String.class);
                HashMap<String, Object> updateData = new HashMap<>();

                if (previousVote != null) {
                    if (previousVote.equals("upvote") && isUpvote) {
                        upvotes--;
                        updateData.put("voters/" + currentUserId, null);
                    } else if (previousVote.equals("downvote") && !isUpvote) {
                        downvotes--;
                        updateData.put("voters/" + currentUserId, null);
                    } else if (previousVote.equals("upvote") && !isUpvote) {
                        upvotes--;
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                    } else if (previousVote.equals("downvote") && isUpvote) {
                        downvotes--;
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                    }
                } else {
                    if (isUpvote) {
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                    } else {
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                    }
                }

                updateData.put("upvotes", upvotes);
                updateData.put("downvotes", downvotes);

                holder.upvoteCountView.setText(String.valueOf(upvotes));
                holder.downvoteCountView.setText(String.valueOf(downvotes));

                if (isUpvote) {
                    if (previousVote != null && previousVote.equals("upvote")) {
                        holder.upvoteButton.setImageResource(R.drawable.upvote_blank);
                    } else {
                        holder.upvoteButton.setImageResource(R.drawable.upvote_filled);
                        holder.downvoteButton.setImageResource(R.drawable.downvote_blank);
                    }
                } else {
                    if (previousVote != null && previousVote.equals("downvote")) {
                        holder.downvoteButton.setImageResource(R.drawable.downvote_blank);
                    } else {
                        holder.downvoteButton.setImageResource(R.drawable.downvote_filled);
                        holder.upvoteButton.setImageResource(R.drawable.upvote_blank);
                    }
                }

                reportRef.child(report.getReportId()).updateChildren(updateData);
            }
        });
    }

    private void updateCategoryIcon(String category, ImageView categoryImageView) {
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
        categoryImageView.setImageResource(categoryDrawable);
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    private void deleteReport(Report report) {
        reportRef.child(report.getReportId()).removeValue();
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

                    return String.format(Locale.getDefault(), "Lat: %.2f, Lon: %.2f", lat, lon);
                }
            }
            return location;
        } catch (NumberFormatException e) {
            return location;
        }
    }

    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView usernameView;
        TextView distanceView;
        TextView locationTextView;
        TextView descriptionView;
        TextView upvoteCountView;
        TextView downvoteCountView;

        ImageView userProfileImageView;
        ImageView categoryImageView;
        ImageView reportImage;
        ImageView upvoteButton;
        ImageView downvoteButton;
        ImageView optionsButton;

        public ReportViewHolder(View itemView) {
            super(itemView);

            usernameView = itemView.findViewById(R.id.reportUsername);
            distanceView = itemView.findViewById(R.id.distanceToUser);
            locationTextView = itemView.findViewById(R.id.locationtext);
            descriptionView = itemView.findViewById(R.id.reportDescription);
            upvoteCountView = itemView.findViewById(R.id.upvotecount);
            downvoteCountView = itemView.findViewById(R.id.downvotecount);

            userProfileImageView = itemView.findViewById(R.id.userProfile);
            categoryImageView = itemView.findViewById(R.id.reporttypelabel);
            reportImage = itemView.findViewById(R.id.reportImage);
            upvoteButton = itemView.findViewById(R.id.upvote);
            downvoteButton = itemView.findViewById(R.id.downvote);
            optionsButton = itemView.findViewById(R.id.threedots);
        }
    }
}
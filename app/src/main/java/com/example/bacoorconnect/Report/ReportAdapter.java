package com.example.bacoorconnect.Report;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.Helpers.VerificationBadgeHelper;
import com.example.bacoorconnect.R;
import com.google.android.flexbox.FlexboxLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

interface OnHighlightReadyListener {
    void onHighlightReady(int position);
}

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reportList;
    private Context context;
    private DatabaseReference reportRef, usersRef;
    private String currentUserId;
    private double currentLatitude = 14.4597;
    private double currentLongitude = 120.9333;
    private int highlightedPosition = -1;
    private OnHighlightReadyListener highlightListener;
    private Handler highlightHandler = new Handler();
    private Runnable clearHighlightRunnable;

    public ReportAdapter(Context context, List<Report> reportList, double currentLatitude, double currentLongitude) {
        this.context = context;
        this.reportList = reportList;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        this.currentUserId = user != null ? user.getUid() : null;
        this.reportRef = FirebaseDatabase.getInstance().getReference("Report");
        this.usersRef = FirebaseDatabase.getInstance().getReference("Users");
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
    }

    public ReportAdapter(Context context, List<Report> reportList, double currentLatitude, double currentLongitude, OnHighlightReadyListener listener) {
        this(context, reportList, currentLatitude, currentLongitude);
        this.highlightListener = listener;
    }

    public void highlightPosition(int position) {
        this.highlightedPosition = position;
        notifyDataSetChanged();

        if (highlightListener != null) {
            highlightListener.onHighlightReady(position);
        }

        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
        }

        clearHighlightRunnable = () -> {
            highlightedPosition = -1;
            notifyDataSetChanged();
        };
        highlightHandler.postDelayed(clearHighlightRunnable, 3000);
    }

    public void updateLocation(double lat, double lon) {
        this.currentLatitude = lat;
        this.currentLongitude = lon;
        notifyDataSetChanged();
    }

    private int getTrustBadgeColor(double trustScore) {
        if (trustScore >= 80) {
            return ContextCompat.getColor(context, R.color.badge_high);
        } else if (trustScore >= 50) {
            return ContextCompat.getColor(context, R.color.badge_medium);
        } else {
            return ContextCompat.getColor(context, R.color.badge_low);
        }
    }

    private String getTrustLevelText(double trustScore) {
        if (trustScore >= 80) {
            return "Verified";
        } else if (trustScore >= 50) {
            return "Contributor";
        } else if (trustScore > 0) {
            return "New";
        } else {
            return null;
        }
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

        loadUserData(report.getUserId(), holder, report);

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

        Map<String, Object> scanResults = report.getScanResults();
        if (holder.badgesContainer != null) {
            if (scanResults != null && !scanResults.isEmpty()) {
                VerificationBadgeHelper.addBadgesToContainer(context, holder.badgesContainer, scanResults);
                holder.badgesContainer.setVisibility(View.VISIBLE);
            } else {
                holder.badgesContainer.setVisibility(View.GONE);
            }
        }

        loadUserVote(report.getReportId(), holder.upvoteButton, holder.downvoteButton);

        holder.upvoteButton.setOnClickListener(v -> modifyVote(report, true, holder));
        holder.downvoteButton.setOnClickListener(v -> modifyVote(report, false, holder));

        if (currentUserId != null && currentUserId.equals(report.getUserId())) {
            holder.optionsButton.setVisibility(View.VISIBLE);
            holder.optionsButton.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(context, holder.optionsButton);
                popupMenu.getMenuInflater().inflate(R.menu.post_options_menu, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.option_edit) {
                        Intent intent = new Intent(context, EditReport.class);
                        intent.putExtra("reportId", report.getReportId());
                        intent.putExtra("userLat", currentLatitude);
                        intent.putExtra("userLon", currentLongitude);
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
        /* i hate this damn clickable son of a bitch im removing it
        holder.itemView.setOnClickListener(v -> {
            Intent detailIntent = new Intent(context, ReportDetailActivity.class);
            detailIntent.putExtra("title", "Report Details");
            detailIntent.putExtra("report_type", report.getCategory());
            detailIntent.putExtra("report_location", report.getLocation());
            detailIntent.putExtra("report_date", report.getFormattedTime());
            detailIntent.putExtra("report_status", resolveStatus(report));
            detailIntent.putExtra("report_description", report.getDescription());
            detailIntent.putExtra("report_upvotes", report.getUpvotes());
            detailIntent.putExtra("report_downvotes", report.getDownvotes());
            detailIntent.putExtra("report_latitude", report.getLatitude());
            detailIntent.putExtra("report_longitude", report.getLongitude());
            detailIntent.putExtra("report_timestamp", report.getTimestamp());
            detailIntent.putExtra("report_userId", report.getUserId());
            detailIntent.putExtra("report_image_url", report.getImageUrl());
            context.startActivity(detailIntent);
        }); */
    }

    private String resolveStatus(Report report) {
        if (report == null) {
            return "Neutral";
        }
        if (report.getUpvotes() > report.getDownvotes()) {
            return "Positive";
        }
        if (report.getDownvotes() > report.getUpvotes()) {
            return "Negative";
        }
        return "Neutral";
    }

    private void loadUserData(String userId, ReportViewHolder holder, Report report) {
        if (userId == null || userId.isEmpty()) {
            holder.usernameView.setText("Unknown User");
            if (holder.trustBadge != null) {
                holder.trustBadge.setVisibility(View.GONE);
            }
            return;
        }

        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Load name
                    String firstName = snapshot.child("firstName").getValue(String.class);
                    String lastName = snapshot.child("lastName").getValue(String.class);
                    if (firstName != null && lastName != null) {
                        holder.usernameView.setText(firstName + " " + lastName);
                    } else if (firstName != null) {
                        holder.usernameView.setText(firstName);
                    } else {
                        holder.usernameView.setText("Unknown User");
                    }

                    // Load stats
                    Double trustScore = snapshot.child("trustScore").getValue(Double.class);
                    Integer totalReports = snapshot.child("totalReports").getValue(Integer.class);
                    Integer approvedReports = snapshot.child("approvedReports").getValue(Integer.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    if (report != null) {
                        if (trustScore != null) report.setTrustScore(trustScore);
                        if (totalReports != null) report.setTotalReports(totalReports);
                        if (approvedReports != null) report.setApprovedReports(approvedReports);
                        if (joinDate != null) report.setJoinDate(joinDate);
                    }

                    // Display trust badge
                    if (holder.trustBadge != null && trustScore != null && trustScore > 0) {
                        String trustText = getTrustLevelText(trustScore);
                        if (trustText != null) {
                            holder.trustBadge.setText(trustText + " " + (int)Math.round(trustScore) + "%");
                            holder.trustBadge.setVisibility(View.VISIBLE);

                            GradientDrawable badgeBg = new GradientDrawable();
                            badgeBg.setCornerRadius(10f);
                            badgeBg.setColor(getTrustBadgeColor(trustScore));
                            holder.trustBadge.setBackground(badgeBg);

                            // FIXED: Pass both report AND holder
                            holder.trustBadge.setOnClickListener(v -> showUserStatsDialog(report, holder));
                        } else {
                            holder.trustBadge.setVisibility(View.GONE);
                        }
                    } else {
                        holder.trustBadge.setVisibility(View.GONE);
                    }

                    // Load profile image
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
                    if (holder.trustBadge != null) {
                        holder.trustBadge.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                holder.usernameView.setText("Unknown User");
                if (holder.trustBadge != null) {
                    holder.trustBadge.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showUserStatsDialog(Report report, ReportViewHolder holder) {
        String userName = holder.usernameView.getText().toString();
        double trustScore = report.getTrustScore();
        int totalReports = report.getTotalReports();
        int approvedReports = report.getApprovedReports();
        double successRate = report.getSuccessRate();
        long joinDate = report.getJoinDate();

        String joinDateString = joinDate > 0 ? formatJoinDate(joinDate) : "Unknown";

        String message = "📊 User Statistics\n\n" +
                "👤 " + userName + "\n" +
                "⭐ Trust Score: " + (int)Math.round(trustScore) + "%\n" +
                "📝 Total Reports: " + totalReports + "\n" +
                "✅ Approved Reports: " + approvedReports + "\n" +
                "📈 Success Rate: " + String.format("%.1f", successRate) + "%\n" +
                "📅 Joined: " + joinDateString;

        new AlertDialog.Builder(context)
                .setTitle("User Reputation")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private String formatJoinDate(long joinDate) {
        long now = System.currentTimeMillis();
        long diff = now - joinDate;

        long days = diff / (1000 * 60 * 60 * 24);
        long months = days / 30;
        long years = days / 365;

        if (years > 0) {
            return years + " year" + (years > 1 ? "s" : "") + " ago";
        } else if (months > 0) {
            return months + " month" + (months > 1 ? "s" : "") + " ago";
        } else if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            return "Today";
        }
    }

    private void loadUserVote(String reportId, ImageView upvoteButton, ImageView downvoteButton) {
        if (currentUserId == null || currentUserId.isEmpty() || reportId == null || reportId.isEmpty()) {
            upvoteButton.setImageResource(R.drawable.upvote_blank);
            downvoteButton.setImageResource(R.drawable.downvote_blank);
            return;
        }

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
        if (currentUserId == null || currentUserId.isEmpty() || report == null || report.getReportId() == null) {
            return;
        }

        reportRef.child(report.getReportId()).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Integer upvotesValue = snapshot.child("upvotes").getValue(Integer.class);
                Integer downvotesValue = snapshot.child("downvotes").getValue(Integer.class);
                int upvotes = upvotesValue != null ? upvotesValue : 0;
                int downvotes = downvotesValue != null ? downvotesValue : 0;

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
        TextView trustBadge;
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

        FlexboxLayout badgesContainer;

        public ReportViewHolder(View itemView) {
            super(itemView);

            usernameView = itemView.findViewById(R.id.reportUsername);
            trustBadge = itemView.findViewById(R.id.trustBadge);
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

            badgesContainer = itemView.findViewById(R.id.badges_container);
        }
    }
}
package com.example.bacoorconnect.Report;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reportList;
    private Context context;
    private DatabaseReference reportRef, auditRef;
    private String currentUserId;

    public ReportAdapter(Context context, List<Report> reportList) {
        this.context = context;
        this.reportList = reportList;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.reportRef = FirebaseDatabase.getInstance().getReference("Report");
        this.auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
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

        holder.descriptionView.setText(report.getDescription());
        holder.upvoteCountView.setText(String.valueOf(report.getUpvotes()));
        holder.downvoteCountView.setText(String.valueOf(report.getDownvotes()));

        if (report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(report.getImageUrl())
                    .into(holder.reportImage);
            holder.reportImage.setVisibility(View.VISIBLE);
        } else {
            holder.reportImage.setVisibility(View.GONE);
        }

        updateCategoryIcon(report.getCategory(), holder.categoryImageView);

        updateVoteIcons(report, holder.upvoteButton, holder.downvoteButton);

        holder.upvoteButton.setOnClickListener(v -> modifyVote(report, true, holder));
        holder.downvoteButton.setOnClickListener(v -> modifyVote(report, false, holder));

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
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    private void modifyVote(Report report, boolean isUpvote, ReportViewHolder holder) {
        reportRef.child(report.getReportId()).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                int upvotes = snapshot.child("upvotes").getValue(Integer.class);
                int downvotes = snapshot.child("downvotes").getValue(Integer.class);

                String previousVote = snapshot.child("voters").child(currentUserId).getValue(String.class);
                HashMap<String, Object> updateData = new HashMap<>();
                String action = isUpvote ? "upvote" : "downvote";

                // Handle voting changes
                if (previousVote != null) {
                    if (previousVote.equals("upvote") && isUpvote) {
                        upvotes--;
                        updateData.put("voters/" + currentUserId, null);
                        logActivity(currentUserId, "Vote", "Removed upvote", report.getReportId(), "Success", "User removed their upvote", "Upvotes: " + upvotes);
                    } else if (previousVote.equals("downvote") && !isUpvote) {
                        downvotes--;
                        updateData.put("voters/" + currentUserId, null);
                        logActivity(currentUserId, "Vote", "Removed downvote", report.getReportId(), "Success", "User removed their downvote", "Downvotes: " + downvotes);
                    } else if (previousVote.equals("upvote") && !isUpvote) {
                        upvotes--;
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                        logActivity(currentUserId, "Vote", "Changed to downvote", report.getReportId(), "Success", "User changed their vote to downvote", "Upvotes: " + upvotes + ", Downvotes: " + downvotes);
                    } else if (previousVote.equals("downvote") && isUpvote) {
                        downvotes--;
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                        logActivity(currentUserId, "Vote", "Changed to upvote", report.getReportId(), "Success", "User changed their vote to upvote", "Upvotes: " + upvotes + ", Downvotes: " + downvotes);
                    }
                } else {
                    if (isUpvote) {
                        upvotes++;
                        updateData.put("voters/" + currentUserId, "upvote");
                        logActivity(currentUserId, "Vote", "Upvoted report", report.getReportId(), "Success", "User upvoted the report", "Upvotes: " + upvotes);
                    } else {
                        downvotes++;
                        updateData.put("voters/" + currentUserId, "downvote");
                        logActivity(currentUserId, "Vote", "Downvoted report", report.getReportId(), "Success", "User downvoted the report", "Downvotes: " + downvotes);
                    }
                }

                updateData.put("upvotes", upvotes);
                updateData.put("downvotes", downvotes);

                // Update the vote counts
                holder.upvoteCountView.setText(String.valueOf(upvotes));
                holder.downvoteCountView.setText(String.valueOf(downvotes));

                reportRef.child(report.getReportId()).updateChildren(updateData);
            }
        });
    }


    private void deleteReport(Report report) {
        reportRef.child(report.getReportId()).removeValue().addOnSuccessListener(aVoid -> {
            logActivity(currentUserId, "Delete", "Deleted Report", report.getReportId(), "Success", "User deleted the report", "N/A");
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

    private void updateVoteIcons(Report report, ImageView upvoteButton, ImageView downvoteButton) {
        String previousVote = report.getUserVote();

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

    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView descriptionView, upvoteCountView, downvoteCountView;
        ImageView upvoteButton, downvoteButton, categoryImageView, optionsButton, reportImage;

        public ReportViewHolder(View itemView) {
            super(itemView);
            reportImage = itemView.findViewById(R.id.reportImage);
            descriptionView = itemView.findViewById(R.id.reportDescription);
            upvoteCountView = itemView.findViewById(R.id.upvotecount);
            downvoteCountView = itemView.findViewById(R.id.downvotecount);
            upvoteButton = itemView.findViewById(R.id.upvote);
            downvoteButton = itemView.findViewById(R.id.downvote);
            categoryImageView = itemView.findViewById(R.id.reporttypelabel);
            optionsButton = itemView.findViewById(R.id.threedots);
        }
    }
}


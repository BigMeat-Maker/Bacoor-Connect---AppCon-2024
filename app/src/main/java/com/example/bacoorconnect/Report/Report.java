package com.example.bacoorconnect.Report;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Report {
    private double latitude;
    private double longitude;
    private String category;
    private String description;
    private String reportMessage;
    private String reportID;
    private String userId;
    private String imageUrl;


    private int upvotes = 0;
    private int downvotes = 0;
    private VoteState userVote = VoteState.NONE;

    private DatabaseReference auditRef;



    public enum VoteState {
        NONE, UPVOTED, DOWNVOTED
    }

    public String getUserVote() {
        switch (userVote) {
            case UPVOTED:
                return "upvote";
            case DOWNVOTED:
                return "downvote";
            default:
                return "none";
        }
    }


    public Report() {
    }

    public Report(double lat, double lon, String category, String description, String reportMessage, String reportId, String imageUrl, String userId) {
        this.latitude = lat;
        this.longitude = lon;
        this.category = category;
        this.description = description;
        this.reportMessage = reportMessage;
        this.reportID = reportId;
        this.userId = userId;
        this.imageUrl = imageUrl;
        this.auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
    }

    // Getters
    public double getLat() {
        return latitude;
    }

    public double getLon() {
        return longitude;
    }

    public String getImageUrl() {
        return imageUrl;  // This will return the image URL (or null if not set)
    }
    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getReportMessage() {
        return reportMessage;
    }

    public String getReportId() {
        return reportID;
    }

    public String getUserId() {
        return userId;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public int getDownvotes() {
        return downvotes;
    }

    public boolean hasUpvoted() {
        return userVote == VoteState.UPVOTED;
    }

    public boolean hasDownvoted() {
        return userVote == VoteState.DOWNVOTED;
    }

    public void toggleUpvote() {
        if (userVote == VoteState.UPVOTED) {
            upvotes--;
            userVote = VoteState.NONE;
            logActivity("Upvote Removed", "Removed upvote on report", "Upvotes: " + upvotes);
        } else {
            if (userVote == VoteState.DOWNVOTED) {
                downvotes--;
            }
            upvotes++;
            userVote = VoteState.UPVOTED;
            logActivity("Upvote", "Upvoted report", "Upvotes: " + upvotes);
        }
    }

    public void toggleDownvote() {
        if (userVote == VoteState.DOWNVOTED) {
            downvotes--;
            userVote = VoteState.NONE;
            logActivity("Downvote Removed", "Removed downvote on report", "Downvotes: " + downvotes);
        } else {
            if (userVote == VoteState.UPVOTED) {
                upvotes--;
            }
            downvotes++;
            userVote = VoteState.DOWNVOTED;
            logActivity("Downvote", "Downvoted report", "Downvotes: " + downvotes);
        }
    }

    public void setReportId(String reportID) {
        this.reportID = reportID;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    private void logActivity(String action, String notes, String changes) {
        String logId = auditRef.push().getKey();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("dateTime", dateTime);
        logData.put("userId", userId);
        logData.put("reportId", reportID);
        logData.put("action", action);
        logData.put("notes", notes);
        logData.put("changes", changes);



        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }

}

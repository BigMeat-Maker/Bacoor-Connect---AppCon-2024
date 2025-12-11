package com.example.bacoorconnect.Report;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Report {
    // Firebase fields
    private String reportId;
    private String userId;
    private String description;
    private String category;
    private String imageUrl;
    private int upvotes = 0;
    private int downvotes = 0;
    private long timestamp;

    // Additional fields from your Firebase
    private double latitude;
    private double longitude;
    private String location; // This is the "location" field in Firebase
    private String addressPrecision;
    private String reportMessage; // If you still want this

    // Constructors
    public Report() {
        // Default constructor required for Firebase
    }

    public Report(double lat, double lon, String category, String description,
                  String reportMessage, String reportId, String userId, String imageUrl) {
        this.latitude = lat;
        this.longitude = lon;
        this.category = category;
        this.description = description;
        this.reportId = reportId;
        this.userId = userId;
        this.imageUrl = imageUrl;
        // Set other fields as needed
        this.location = "Lat: " + lat + ", Lon: " + lon;
        this.timestamp = System.currentTimeMillis();
        this.upvotes = 0;
        this.downvotes = 0;
    }


    // Getters and Setters
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }

    public int getDownvotes() { return downvotes; }
    public void setDownvotes(int downvotes) { this.downvotes = downvotes; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAddressPrecision() { return addressPrecision; }
    public void setAddressPrecision(String addressPrecision) { this.addressPrecision = addressPrecision; }

    // For compatibility with old code
    public double getLat() { return latitude; }
    public double getLon() { return longitude; }
    public String getReportMessage() { return description; } // Use description as reportMessage

    // Helper methods
    public String getFormattedTime() {
        if (timestamp == 0) return "";

        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + "m ago";
        } else if (hours < 24) {
            return hours + "h ago";
        } else if (days < 7) {
            return days + "d ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    // Parse coordinates from location string if needed
    public void parseCoordinatesFromLocation() {
        if (location != null && location.contains("Lat:") && location.contains("Lon:")) {
            try {
                String[] parts = location.split(",");
                if (parts.length >= 2) {
                    String latPart = parts[0].replace("Lat:", "").trim();
                    String lonPart = parts[1].replace("Lon:", "").trim();
                    this.latitude = Double.parseDouble(latPart);
                    this.longitude = Double.parseDouble(lonPart);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }
}
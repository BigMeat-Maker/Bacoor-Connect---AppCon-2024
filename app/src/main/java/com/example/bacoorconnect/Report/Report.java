package com.example.bacoorconnect.Report;

import com.example.bacoorconnect.Helpers.ReportFlag;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Report {
    private String reportId;
    private String userId;
    private String description;
    private String category;
    private String imageUrl;
    private int upvotes = 0;
    private int downvotes = 0;
    private long timestamp;

    private double latitude;
    private double longitude;
    private String location;
    private String addressPrecision;

    private double trustScore;
    private String trustLevel;
    private int totalReports;
    private int approvedReports;
    private long joinDate;
    private Map<String, Object> scanResults;

    private int flagCount = 0;
    private Map<String, ReportFlag> flags = new HashMap<>();

    public Report() {
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
        this.location = "Lat: " + lat + ", Lon: " + lon;
        this.timestamp = System.currentTimeMillis();
        this.upvotes = 0;
        this.downvotes = 0;
        this.flagCount = 0;
        this.flags = new HashMap<>();
    }

    public double getTrustScore() { return trustScore; }
    public void setTrustScore(double trustScore) { this.trustScore = trustScore; }

    public int getTotalReports() { return totalReports; }
    public void setTotalReports(int totalReports) { this.totalReports = totalReports; }

    public long getJoinDate() { return joinDate; }
    public void setJoinDate(long joinDate) { this.joinDate = joinDate; }
    public String getTrustLevel() { return trustLevel; }
    public void setTrustLevel(String trustLevel) { this.trustLevel = trustLevel; }
    public double getSuccessRate() {
        return totalReports > 0 ? (double) approvedReports / totalReports * 100 : 0;}
    public int getApprovedReports() { return approvedReports; }
    public void setApprovedReports(int approvedReports) { this.approvedReports = approvedReports; }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public Map<String, Object> getScanResults() { return scanResults; }
    public void setScanResults(Map<String, Object> scanResults) { this.scanResults = scanResults; }

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

    public int getFlagCount() { return flagCount; }
    public void setFlagCount(int flagCount) { this.flagCount = flagCount; }

    public Map<String, ReportFlag> getFlags() { return flags; }
    public void setFlags(Map<String, ReportFlag> flags) { this.flags = flags != null ? flags : new HashMap<>(); }

    public String getAddressPrecision() { return addressPrecision; }
    public void setAddressPrecision(String addressPrecision) { this.addressPrecision = addressPrecision; }

    public double getLat() { return latitude; }
    public double getLon() { return longitude; }
    public String getReportMessage() { return description; }

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
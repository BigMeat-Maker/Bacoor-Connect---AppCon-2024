package com.example.bacoorconnect.Helpers;

import java.util.Map;

public class ReportFlag {
    private String userId;
    private String reason;
    private long timestamp;

    public ReportFlag() {}

    public ReportFlag(String userId, String reason, long timestamp) {
        this.userId = userId;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
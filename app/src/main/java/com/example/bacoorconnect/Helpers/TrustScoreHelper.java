package com.example.bacoorconnect.Helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

// TODO: Consider moving trust score calculation to a backend service or admin module to reduce client-side processing.
// Optimization Note: A background worker could potentially handle these updates by periodically checking for matching user IDs.
// Note: Trust score calculation currently only accounts for reports created after this feature was implemented.

public class TrustScoreHelper {
    private static final String TAG = "TrustScoreHelper";
    private static DatabaseReference scanLogsRef = FirebaseDatabase.getInstance().getReference("ScanLogs");
    private static DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");

    public interface TrustScoreCallback {
        void onScoreCalculated(double trustScore, int totalReports, int approvedReports);
        void onError(String error);
    }

    public static void calculateAndUpdateTrustScore(String userId, TrustScoreCallback callback) {
        final String finalUserId = userId;

        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                if (!userSnapshot.hasChild("joinDate")) {
                    usersRef.child(userId).child("joinDate").setValue(System.currentTimeMillis());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check join date", error.toException());
            }
        });

        scanLogsRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        int totalReports = 0;
                        int approvedReports = 0;

                        for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                            String status = logSnapshot.child("status").getValue(String.class);
                            String verdict = logSnapshot.child("verdict").getValue(String.class);
                            String errorDetails = logSnapshot.child("errorDetails").getValue(String.class);
                            String reportId = logSnapshot.child("reportId").getValue(String.class);
                            String type = logSnapshot.child("type").getValue(String.class);

                            Log.d(TAG, "Processing log - ReportId: " + reportId +
                                    ", Status: " + status +
                                    ", Verdict: " + verdict +
                                    ", Type: " + type +
                                    ", HasErrorDetails: " + (errorDetails != null));

                            if ("edit".equals(type)) {
                                Log.d(TAG, "  -> Skipping edit log (trust score not affected)");
                                continue;
                            }

                            if (status != null || errorDetails != null) {
                                totalReports++;
                                Log.d(TAG, "  -> Counted toward totalReports. New total: " + totalReports);
                            }

                            if ("SUCCESS".equals(status) && "APPROVED".equals(verdict)) {
                                approvedReports++;
                                Log.d(TAG, "  -> Counted toward approvedReports. New approved: " + approvedReports);
                            }
                        }

                        double trustScore = totalReports > 0 ?
                                (double) approvedReports / totalReports * 100 : 100.0;

                        trustScore = Math.round(trustScore * 10.0) / 10.0;

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("trustScore", trustScore);
                        updates.put("totalReports", totalReports);
                        updates.put("approvedReports", approvedReports);

                        double finalTrustScore = trustScore;
                        int finalTotalReports = totalReports;
                        int finalApprovedReports = approvedReports;
                        usersRef.child(finalUserId).updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Trust score updated for user " + finalUserId + ": " + finalTrustScore + "%");
                                    if (callback != null) {
                                        callback.onScoreCalculated(finalTrustScore, finalTotalReports, finalApprovedReports);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update trust score", e);
                                    if (callback != null) {
                                        callback.onError("Failed to save trust score: " + e.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Failed to calculate trust score", error.toException());
                        if (callback != null) {
                            callback.onError("Failed to calculate: " + error.getMessage());
                        }
                    }
                });
    }


    public static void getTrustScore(String userId, TrustScoreCallback callback) {
        final String finalUserId = userId;

        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double trustScore = snapshot.child("trustScore").getValue(Double.class);
                Integer totalReports = snapshot.child("totalReports").getValue(Integer.class);
                Integer approvedReports = snapshot.child("approvedReports").getValue(Integer.class);

                if (trustScore == null) trustScore = 100.0;
                if (totalReports == null) totalReports = 0;
                if (approvedReports == null) approvedReports = 0;

                callback.onScoreCalculated(trustScore, totalReports, approvedReports);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError("Failed to get trust score for user " + finalUserId + ": " + error.getMessage());
            }
        });
    }

    public static int getTrustColor(double trustScore) {
        if (trustScore >= 80) {
            return R.color.badge_high;
        } else if (trustScore >= 50) {
            return R.color.badge_medium;
        } else {
            return R.color.badge_low;
        }
    }

    public static String getTrustLevel(double trustScore) {
        if (trustScore >= 80) {
            return "High Trust";
        } else if (trustScore >= 50) {
            return "Medium Trust";
        } else {
            return "Low Trust";
        }
    }
}
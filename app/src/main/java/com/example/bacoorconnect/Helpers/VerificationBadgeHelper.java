package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.bacoorconnect.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VerificationBadgeHelper {

    public enum BadgeType {
        TEXT("Text Safety", "Checks if the report description contains inappropriate content"),
        IMAGE("Image Safety", "Checks if the uploaded image contains inappropriate content (NSFW, violence, etc.)"),
        REVERSE_SEARCH("Originality", "Checks if the image exists elsewhere online (prevents fake reports)"),
        CATEGORY("Category Match", "Checks if the image content matches the selected category"),
        AI_DETECTION("AI Detection", "Checks if the image was AI-generated (not allowed in reports)");

        private final String title;
        private final String description;

        BadgeType(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }

    public enum ConfidenceLevel {
        HIGH(R.color.badge_high, "✓", "Good - High confidence"),
        MEDIUM(R.color.badge_medium, "!", "Medium - Needs attention"),
        LOW(R.color.badge_low, "⚠", "Poor - Failed verification");

        private final int colorRes;
        private final String icon;
        private final String description;

        ConfidenceLevel(int colorRes, String icon, String description) {
            this.colorRes = colorRes;
            this.icon = icon;
            this.description = description;
        }

        public int getColorRes() { return colorRes; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }

    public static class BadgeData {
        public final BadgeType type;
        public final ConfidenceLevel level;
        public final String scoreText;

        public BadgeData(BadgeType type, ConfidenceLevel level, String scoreText) {
            this.type = type;
            this.level = level;
            this.scoreText = scoreText;
        }
    }

    /**
     * Parse scanResults and generate badge data
     */
    public static List<BadgeData> parseBadges(Map<String, Object> scanResults) {
        List<BadgeData> badges = new ArrayList<>();

        if (scanResults == null) {
            return badges;
        }

        // Text Analysis Badge
        if (scanResults.containsKey("textScan")) {
            boolean isSafe = !scanResults.containsKey("textScanError") &&
                    !(scanResults.get("textScan") instanceof String &&
                            ((String) scanResults.get("textScan")).contains("inappropriate"));
            badges.add(new BadgeData(
                    BadgeType.TEXT,
                    isSafe ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW,
                    isSafe ? "Safe" : "Violation detected"
            ));
        } else {
            badges.add(new BadgeData(BadgeType.TEXT, ConfidenceLevel.MEDIUM, "Not checked"));
        }

        // Image Analysis Badge
        if (scanResults.containsKey("imageScan")) {
            boolean isSafe = !scanResults.containsKey("imageScanError") &&
                    !(Boolean.TRUE.equals(scanResults.get("imageScan_racy")) ||
                            (scanResults.get("imageScan") instanceof String &&
                                    ((String) scanResults.get("imageScan")).contains("racy")));
            badges.add(new BadgeData(
                    BadgeType.IMAGE,
                    isSafe ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW,
                    isSafe ? "Safe" : "Inappropriate content"
            ));
        } else {
            badges.add(new BadgeData(BadgeType.IMAGE, ConfidenceLevel.MEDIUM, "Not checked"));
        }

        // Reverse Image Search Badge
        if (scanResults.containsKey("reverseImageSearch_resultType")) {
            String resultType = (String) scanResults.get("reverseImageSearch_resultType");
            switch (resultType) {
                case "NO_MATCHES":
                    badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, ConfidenceLevel.HIGH, "Original photo"));
                    break;
                case "FEW_MATCHES":
                    badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, ConfidenceLevel.MEDIUM, "Found online (1-3 matches)"));
                    break;
                case "MANY_MATCHES":
                    badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, ConfidenceLevel.LOW, "Widely available online"));
                    break;
                default:
                    badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, ConfidenceLevel.MEDIUM, "Unknown"));
            }
        } else {
            badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, ConfidenceLevel.MEDIUM, "Not checked"));
        }

        // Category Verification Badge
        if (scanResults.containsKey("categoryVerification")) {
            Object catObj = scanResults.get("categoryVerification");
            boolean matches = false;
            if (catObj instanceof Map) {
                Map<?, ?> catMap = (Map<?, ?>) catObj;
                Object matchesObj = catMap.get("matchesCategory");
                matches = matchesObj instanceof Boolean && (Boolean) matchesObj;
            }
            badges.add(new BadgeData(
                    BadgeType.CATEGORY,
                    matches ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW,
                    matches ? "Matches category" : "Category mismatch"
            ));
        } else if (scanResults.containsKey("categoryVerificationError")) {
            badges.add(new BadgeData(BadgeType.CATEGORY, ConfidenceLevel.MEDIUM, "Verification failed"));
        } else {
            badges.add(new BadgeData(BadgeType.CATEGORY, ConfidenceLevel.MEDIUM, "Not checked"));
        }

        // AI Detection Badge
        if (scanResults.containsKey("aiDetection")) {
            Object aiObj = scanResults.get("aiDetection");
            boolean isAI = false;
            if (aiObj instanceof String && ((String) aiObj).contains("AI-generated")) {
                isAI = true;
            }
            badges.add(new BadgeData(
                    BadgeType.AI_DETECTION,
                    isAI ? ConfidenceLevel.LOW : ConfidenceLevel.HIGH,
                    isAI ? "AI-generated detected" : "Real image"
            ));
        } else if (scanResults.containsKey("aiDetectionError")) {
            badges.add(new BadgeData(BadgeType.AI_DETECTION, ConfidenceLevel.MEDIUM, "Check failed"));
        } else {
            badges.add(new BadgeData(BadgeType.AI_DETECTION, ConfidenceLevel.MEDIUM, "Not checked"));
        }

        return badges;
    }

    /**
     * Create badge views and add them to a container
     */
    public static void addBadgesToContainer(Context context, ViewGroup container, Map<String, Object> scanResults) {
        if (container == null) return;

        container.removeAllViews();
        List<BadgeData> badges = parseBadges(scanResults);

        for (BadgeData badge : badges) {
            View badgeView = createBadgeView(context, badge);
            container.addView(badgeView);
        }
    }

    /**
     * Create a single badge view
     */
    private static View createBadgeView(Context context, BadgeData badge) {
        View badgeView = LayoutInflater.from(context).inflate(R.layout.item_verification_badge, null);

        TextView iconText = badgeView.findViewById(R.id.badge_icon);
        TextView titleText = badgeView.findViewById(R.id.badge_title);
        TextView scoreText = badgeView.findViewById(R.id.badge_score);

        iconText.setText(badge.level.getIcon());
        titleText.setText(badge.type.getTitle());
        scoreText.setText(badge.scoreText);

        // Set background color based on confidence level
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(16f);
        background.setColor(ContextCompat.getColor(context, badge.level.getColorRes()));
        badgeView.setBackground(background);

        // Make badge clickable to show explanation
        badgeView.setOnClickListener(v -> showBadgeExplanationDialog(context, badge));

        return badgeView;
    }

    /**
     * Show explanation dialog when badge is clicked
     */
    private static void showBadgeExplanationDialog(Context context, BadgeData badge) {
        new AlertDialog.Builder(context)
                .setTitle(badge.type.getTitle())
                .setMessage(badge.type.getDescription() + "\n\n" +
                        "Status: " + badge.level.getDescription() + "\n" +
                        "Details: " + badge.scoreText)
                .setPositiveButton("Got it", null)
                .show();
    }
}
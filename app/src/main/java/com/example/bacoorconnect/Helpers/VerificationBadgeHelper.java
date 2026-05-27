package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.bacoorconnect.R;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VerificationBadgeHelper {

    private static final DecimalFormat df = new DecimalFormat("0");

    public enum BadgeType {
        TEXT("Text", "📝", "Checks if the report description contains inappropriate content"),
        IMAGE("Image", "🖼️", "Checks if the uploaded image contains inappropriate content"),
        REVERSE_SEARCH("Originality", "🔍", "Checks if the image exists elsewhere online"),
        AI_DETECTION("AI", "🤖", "Checks if the image was AI-generated");

        private final String shortTitle;
        private final String icon;
        private final String description;

        BadgeType(String shortTitle, String icon, String description) {
            this.shortTitle = shortTitle;
            this.icon = icon;
            this.description = description;
        }

        public String getShortTitle() { return shortTitle; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }

    public static class BadgeData {
        public final BadgeType type;
        public final int percentage;
        public final String statusText;
        public final int colorRes;

        public BadgeData(BadgeType type, int percentage, String statusText, int colorRes) {
            this.type = type;
            this.percentage = percentage;
            this.statusText = statusText;
            this.colorRes = colorRes;
        }
    }

    public static List<BadgeData> parseBadges(Map<String, Object> scanResults) {
        List<BadgeData> badges = new ArrayList<>();

        if (scanResults == null) {
            return badges;
        }

        int textPercentage = 100;
        String textStatus = "Clean";
        int textColor = R.color.badge_high;

        if (scanResults.containsKey("textScanError")) {
            textPercentage = 0;
            textStatus = "Error";
            textColor = R.color.badge_low;
        } else if (scanResults.containsKey("textScan") && scanResults.get("textScan") != null) {
            String textScan = scanResults.get("textScan").toString();
            if (textScan.contains("inappropriate") || textScan.contains("violation")) {
                textPercentage = 20;
                textStatus = "Violation";
                textColor = R.color.badge_low;
            } else {
                textPercentage = 95;
                textStatus = "Clean";
                textColor = R.color.badge_high;
            }
        } else {
            textPercentage = 50;
            textStatus = "Not checked";
            textColor = R.color.badge_neutral;
        }
        badges.add(new BadgeData(BadgeType.TEXT, textPercentage, textStatus, textColor));

        int imagePercentage = 100;
        String imageStatus = "Clean";
        int imageColor = R.color.badge_high;

        if (scanResults.containsKey("imageScanError")) {
            imagePercentage = 0;
            imageStatus = "Error";
            imageColor = R.color.badge_low;
        } else if (scanResults.containsKey("imageScan") && scanResults.get("imageScan") != null) {
            String imageScan = scanResults.get("imageScan").toString();
            if (imageScan.contains("racy") || imageScan.contains("inappropriate")) {
                imagePercentage = 15;
                imageStatus = "Inappropriate";
                imageColor = R.color.badge_low;
            } else {
                imagePercentage = 95;
                imageStatus = "Clean";
                imageColor = R.color.badge_high;
            }
        } else {
            imagePercentage = 50;
            imageStatus = "Not checked";
            imageColor = R.color.badge_neutral;
        }
        badges.add(new BadgeData(BadgeType.IMAGE, imagePercentage, imageStatus, imageColor));

        int originalityPercentage = 100;
        String originalityStatus = "Original";
        int originalityColor = R.color.badge_high;

        if (scanResults.containsKey("reverseImageSearch_resultType")) {
            String resultType = (String) scanResults.get("reverseImageSearch_resultType");
            int matches = 0;
            Object matchCountObj = scanResults.get("reverseImageSearch_matchCount");
            if (matchCountObj instanceof Integer) {
                matches = (Integer) matchCountObj;
            } else if (matchCountObj instanceof Long) {
                matches = ((Long) matchCountObj).intValue();
            } else if (matchCountObj instanceof String) {
                try {
                    matches = Integer.parseInt((String) matchCountObj);
                } catch (NumberFormatException e) {
                    matches = 0;
                }
            }

            switch (resultType) {
                case "NO_MATCHES":
                    originalityPercentage = 95;
                    originalityStatus = "Original";
                    originalityColor = R.color.badge_high;
                    break;
                case "FEW_MATCHES":
                    originalityPercentage = Math.max(30, 80 - (matches * 15));
                    originalityStatus = matches + " match" + (matches > 1 ? "es" : "") + " found";
                    originalityColor = R.color.badge_medium;
                    break;
                case "MANY_MATCHES":
                    originalityPercentage = Math.max(5, 80 - (matches * 10));
                    originalityStatus = matches + " matches found";
                    originalityColor = R.color.badge_low;
                    break;
                case "EXACT_MATCH":
                    originalityPercentage = 5;
                    originalityStatus = "Exact match online";
                    originalityColor = R.color.badge_low;
                    break;
                default:
                    originalityPercentage = 50;
                    originalityStatus = "Unknown";
                    originalityColor = R.color.badge_neutral;
            }
        } else if (scanResults.containsKey("reverseImageSearchError")) {
            originalityPercentage = 30;
            originalityStatus = "Check failed";
            originalityColor = R.color.badge_medium;
        } else {
            originalityPercentage = 50;
            originalityStatus = "Not checked";
            originalityColor = R.color.badge_neutral;
        }
        badges.add(new BadgeData(BadgeType.REVERSE_SEARCH, originalityPercentage, originalityStatus, originalityColor));

        int aiPercentage = 100;
        String aiStatus = "Real";
        int aiColor = R.color.badge_high;

        if (scanResults.containsKey("aiDetection")) {
            Object aiObj = scanResults.get("aiDetection");

            String aiResponse = aiObj != null ? aiObj.toString() : "";

            if (aiResponse.contains("AI-generated") || aiResponse.toLowerCase().contains("ai generated")) {
                int confidence = 85;
                if (aiResponse.contains("Confidence:")) {
                    try {
                        int startIdx = aiResponse.indexOf("Confidence:") + 11;
                        int endIdx = aiResponse.indexOf("%", startIdx);
                        if (endIdx > startIdx) {
                            confidence = Integer.parseInt(aiResponse.substring(startIdx, endIdx).trim());
                        }
                    } catch (Exception e) {
                        confidence = 85;
                    }
                }
                aiPercentage = 100 - confidence;
                aiStatus = (100 - confidence) + "% Real";
                aiColor = aiPercentage > 60 ? R.color.badge_high :
                        (aiPercentage > 30 ? R.color.badge_medium : R.color.badge_low);
            } else {
                aiPercentage = 95;
                aiStatus = "Real";
                aiColor = R.color.badge_high;
            }
        } else if (scanResults.containsKey("aiDetectionError")) {
            aiPercentage = 40;
            aiStatus = "Check failed";
            aiColor = R.color.badge_medium;
        } else {
            aiPercentage = 50;
            aiStatus = "Not checked";
            aiColor = R.color.badge_neutral;
        }
        badges.add(new BadgeData(BadgeType.AI_DETECTION, aiPercentage, aiStatus, aiColor));

        return badges;
    }

    public static void addBadgesToContainer(Context context, ViewGroup container, Map<String, Object> scanResults) {
        if (container == null) return;

        container.removeAllViews();
        List<BadgeData> badges = parseBadges(scanResults);

        for (BadgeData badge : badges) {
            View badgeView = createBadgeView(context, badge);
            container.addView(badgeView);
        }
    }

    private static View createBadgeView(Context context, BadgeData badge) {
        View badgeView = LayoutInflater.from(context).inflate(R.layout.item_verification_badge, null);

        TextView iconText = badgeView.findViewById(R.id.badge_icon);
        TextView titleText = badgeView.findViewById(R.id.badge_title);
        TextView percentageText = badgeView.findViewById(R.id.badge_percentage);
        RelativeLayout circle = badgeView.findViewById(R.id.badge_circle);

        iconText.setText(badge.type.getIcon());
        titleText.setText(badge.type.getShortTitle());

        percentageText.setText(badge.percentage + "%");

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(ContextCompat.getColor(context, badge.colorRes));
        circle.setBackground(background);

        badgeView.setOnClickListener(v -> showBadgeExplanationDialog(context, badge));

        return badgeView;
    }

    private static void showBadgeExplanationDialog(Context context, BadgeData badge) {
        String statusMessage;
        switch (badge.type) {
            case TEXT:
                statusMessage = "Text content analysis shows " + badge.percentage + "% confidence that the description is appropriate.\n\nStatus: " + badge.statusText;
                break;
            case IMAGE:
                statusMessage = "Image content analysis shows " + badge.percentage + "% confidence that the image is appropriate.\n\nStatus: " + badge.statusText;
                break;
            case REVERSE_SEARCH:
                statusMessage = "Reverse image search shows " + badge.percentage + "% confidence this is an original photo.\n\n" +
                        (badge.percentage > 70 ? "This appears to be an original photo." :
                                badge.percentage > 40 ? "This image may exist elsewhere online." :
                                        "This image appears widely available online.");
                break;
            case AI_DETECTION:
                statusMessage = "AI detection shows " + badge.percentage + "% confidence this is a real photo.\n\n" +
                        (badge.percentage > 70 ? "This appears to be a real photograph." :
                                badge.percentage > 40 ? "This may be AI-generated." :
                                        "This is likely AI-generated.");
                break;
            default:
                statusMessage = badge.type.getDescription();
        }

        new AlertDialog.Builder(context)
                .setTitle(badge.type.getShortTitle() + " Verification (" + badge.percentage + "%)")
                .setMessage(badge.type.getDescription() + "\n\n" + statusMessage)
                .setPositiveButton("Got it", null)
                .show();
    }
}
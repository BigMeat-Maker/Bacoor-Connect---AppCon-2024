package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SightengineAIDetector {
    private static final String TAG = "SightengineAI";

    private final OkHttpClient client;
    private final Context context;
    private String apiUser;
    private String apiSecret;
    private float confidenceThreshold;
    private boolean isInitialized = false;

    public SightengineAIDetector(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        loadCredentials();
    }

    private void loadCredentials() {
        apiUser = SightengineConfig.getApiUser(context);
        apiSecret = SightengineConfig.getApiSecret(context);
        confidenceThreshold = SightengineConfig.getConfidenceThreshold(context);
        isInitialized = (apiUser != null && !apiUser.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty());

        if (isInitialized) {
            Log.d(TAG, "Sightengine detector initialized with credentials");
            Log.d(TAG, "Confidence threshold: " + confidenceThreshold);
        } else {
            Log.w(TAG, "Sightengine credentials not found");
        }
    }

    public void refreshCredentials() {
        loadCredentials();
        Log.d(TAG, "Credentials refreshed: " + (isInitialized ? "Available" : "Missing"));
    }

    public boolean isReady() {
        return isInitialized;
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    /**
     * Check if an image is AI-generated using Sightengine
     * @param imageUri The URI of the image to check
     * @param callback Callback for the result
     */
    public void detectAIGeneratedImage(Uri imageUri, final AIDetectionCallback callback) {
        if (!isInitialized) {
            Log.e(TAG, "Sightengine not initialized - missing credentials");
            callback.onDetectionFailed("AI detection service not configured");
            return;
        }

        try {
            byte[] imageBytes = getImageBytes(imageUri);
            if (imageBytes == null) {
                callback.onDetectionFailed("Failed to read image data");
                return;
            }

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("models", "ai-generated")
                    .addFormDataPart("api_user", apiUser)
                    .addFormDataPart("api_secret", apiSecret)
                    .addFormDataPart("media", "image.jpg",
                            RequestBody.create(MediaType.parse("image/jpeg"), imageBytes))
                    .build();

            Request request = new Request.Builder()
                    .url(SightengineConfig.getBaseUrl())
                    .post(requestBody)
                    .build();

            // Execute async
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    Log.e(TAG, "API call failed", e);
                    callback.onDetectionFailed("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "API Response: " + jsonResponse);

                        AIDetectionResult result = parseResponse(jsonResponse);
                        if (result != null) {
                            // Check against threshold
                            result.setThreshold(confidenceThreshold);
                            callback.onDetectionComplete(result);
                        } else {
                            callback.onDetectionFailed("Failed to parse API response");
                        }
                    } else {
                        callback.onDetectionFailed("API error: " + response.code());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Detection error", e);
            callback.onDetectionFailed("Error: " + e.getMessage());
        }
    }

    private byte[] getImageBytes(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return inputStream.readAllBytes();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read image", e);
        }
        return null;
    }

    private AIDetectionResult parseResponse(String json) {
        try {
            Gson gson = new Gson();
            ApiResponse response = gson.fromJson(json, ApiResponse.class);

            if (response.status != null && response.status.equals("failure")) {
                Log.e(TAG, "API returned failure: " + response.error);
                return null;
            }

            boolean isAIGenerated = false;
            double confidence = 0.0;
            String detectionType = "unknown";

            if (response.type != null) {
                detectionType = response.type;
                isAIGenerated = "ai-generated".equalsIgnoreCase(response.type);
                confidence = response.prob != null ? response.prob : 0.0;
            }

            // Alternative: check for ai_generated field
            if (response.aiGenerated != null) {
                isAIGenerated = response.aiGenerated;
                if (response.aiGeneratedProb != null) {
                    confidence = response.aiGeneratedProb;
                }
            }

            return new AIDetectionResult(isAIGenerated, confidence, detectionType, json);

        } catch (Exception e) {
            Log.e(TAG, "JSON parsing error", e);
            return null;
        }
    }

    private static class ApiResponse {
        String status;
        String error;
        String type;
        Double prob;
        @SerializedName("ai-generated")
        Boolean aiGenerated;
        @SerializedName("ai-generated_prob")
        Double aiGeneratedProb;
    }

    public static class AIDetectionResult {
        public final boolean isAIGenerated;
        public final double confidence;
        public final String detectionType;
        public final String rawResponse;
        private float threshold = 0.5f;

        public AIDetectionResult(boolean isAIGenerated, double confidence,
                                 String detectionType, String rawResponse) {
            this.isAIGenerated = isAIGenerated;
            this.confidence = confidence;
            this.detectionType = detectionType;
            this.rawResponse = rawResponse;
        }

        public void setThreshold(float threshold) {
            this.threshold = threshold;
        }

        public boolean isAboveThreshold() {
            return isAIGenerated && confidence > threshold;
        }

        public boolean isHighlyConfident() {
            return confidence > 0.8;
        }

        public boolean isMediumConfidence() {
            return confidence > 0.6 && confidence <= 0.8;
        }

        public String getFormattedResult() {
            return String.format(Locale.US, "AI Generated: %s (%.1f%% confidence, Threshold: %.1f%%)",
                    isAIGenerated ? "YES" : "NO", confidence * 100, threshold * 100);
        }
    }

    public interface AIDetectionCallback {
        void onDetectionComplete(AIDetectionResult result);
        void onDetectionFailed(String error);
    }
}
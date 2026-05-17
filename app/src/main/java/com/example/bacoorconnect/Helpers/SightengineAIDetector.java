package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
            Log.d(TAG, "Sightengine detector initialized");
            Log.d(TAG, "API User: " + (apiUser.length() > 8 ? apiUser.substring(0, 8) + "..." : apiUser));
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

            String url = SightengineConfig.getBaseUrl();

            Log.d(TAG, "Calling Sightengine API with model: genai");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("media", "image.jpg",
                            RequestBody.create(MediaType.parse("image/jpeg"), imageBytes))
                    .addFormDataPart("models", "genai")
                    .addFormDataPart("api_user", apiUser)
                    .addFormDataPart("api_secret", apiSecret)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    Log.e(TAG, "API call failed", e);
                    callback.onDetectionFailed("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        Log.d(TAG, "API Response: " + responseBody);
                        AIDetectionResult result = parseResponse(responseBody);
                        if (result != null) {
                            result.setThreshold(confidenceThreshold);
                            callback.onDetectionComplete(result);
                        } else {
                            callback.onDetectionFailed("Failed to parse API response");
                        }
                    } else {
                        String errorMsg = "API error: " + response.code();
                        try {
                            Gson gson = new Gson();
                            ErrorResponse errorResp = gson.fromJson(responseBody, ErrorResponse.class);
                            if (errorResp.error != null) {
                                errorMsg += " - " + errorResp.error.message;
                                Log.e(TAG, "Sightengine error: " + errorResp.error.type + " - " + errorResp.error.message);
                            }
                        } catch (Exception e) {
                            errorMsg += " - " + responseBody;
                        }
                        Log.e(TAG, errorMsg);
                        callback.onDetectionFailed(errorMsg);
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
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = inputStream.read(data)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                return buffer.toByteArray();
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
                Log.e(TAG, "API returned failure");
                return null;
            }

            boolean isAIGenerated = false;
            double confidence = 0.0;
            String detectionType = "unknown";

            if (response.type != null && response.type.ai_generated != null) {
                confidence = response.type.ai_generated;
                isAIGenerated = confidence > confidenceThreshold;
                detectionType = "ai_generated";
                Log.d(TAG, "Found ai_generated score: " + confidence);
            }

            if (response.type != null && response.type.ai_generators != null) {
                Log.d(TAG, "Per-generator scores available");
            }

            Log.d(TAG, String.format(Locale.US, "Result - AI Generated: %s, Confidence: %.3f%%, Type: %s",
                    isAIGenerated, confidence * 100, detectionType));

            return new AIDetectionResult(isAIGenerated, confidence, detectionType, json);

        } catch (Exception e) {
            Log.e(TAG, "JSON parsing error", e);
            Log.e(TAG, "Raw JSON: " + json);
            return null;
        }
    }

    private static class ApiResponse {
        String status;
        RequestInfo request;
        TypeResult type;
        MediaInfo media;
    }

    private static class RequestInfo {
        String id;
        float timestamp;
        int operations;
    }

    private static class TypeResult {
        @SerializedName("ai_generated")
        Double ai_generated;

        @SerializedName("ai_generators")
        AiGenerators ai_generators;
    }

    private static class AiGenerators {
        Double dalle;
        Double firefly;
        Double flux;
        Double gan;
        Double gpt;
        Double midjourney;
        @SerializedName("stable_diffusion")
        Double stable_diffusion;
    }

    private static class MediaInfo {
        String id;
        String uri;
    }

    private static class ErrorResponse {
        ErrorDetail error;
    }

    private static class ErrorDetail {
        String type;
        int code;
        String message;
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
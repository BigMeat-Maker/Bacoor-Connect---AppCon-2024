package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageContentAnalyzer {

    private static final String TAG = "ImageContentAnalyzer";

    public interface ImageAnalysisCallback {
        void onImageContentChecked(boolean isRacy, double score, String debugJson);
        void onContentCheckFailed(String error);
    }

    public static void analyzeImage(Context context, Uri imageUri, ImageAnalysisCallback callback) {
        Log.d(TAG, "Starting image analysis");

        String apiKey = ContentSafetyConfig.getContentSafetyKey(context);

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onContentCheckFailed("Content Safety service not configured");
            return;
        }

        try {
            InputStream imageStream = context.getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = getBytesFromInputStream(imageStream);
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

            String jsonBody = "{\n" +
                    "  \"image\": {\n" +
                    "    \"content\": \"" + base64Image + "\"\n" +
                    "  },\n" +
                    "  \"categories\": [\"Sexual\"]\n" +
                    "}";

            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url("https://japaneast.api.cognitive.microsoft.com/contentsafety/image:analyze?api-version=2023-10-01")
                    .post(requestBody)
                    .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API call failed", e);
                    callback.onContentCheckFailed("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d(TAG, "API Response: " + responseBody);

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        callback.onContentCheckFailed("API error: " + response.code());
                        return;
                    }

                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);

                        boolean isRacy = false;
                        double maxSeverity = 0;
                        boolean contentIsSafe = true;

                        if (jsonObject.has("categoriesAnalysis")) {
                            JSONArray categoriesAnalysis = jsonObject.getJSONArray("categoriesAnalysis");

                            for (int i = 0; i < categoriesAnalysis.length(); i++) {
                                JSONObject category = categoriesAnalysis.getJSONObject(i);
                                String categoryName = category.getString("category");
                                double severity = category.getDouble("severity");
                                maxSeverity = Math.max(maxSeverity, severity);

                                if ("Sexual".equals(categoryName)) {
                                    isRacy = severity > 0;
                                    contentIsSafe = severity == 0;
                                }
                            }
                        }

                        JSONObject debugJson = new JSONObject();
                        debugJson.put("isRacy", isRacy);
                        debugJson.put("isSafe", contentIsSafe);
                        debugJson.put("maxSeverity", maxSeverity);
                        debugJson.put("fullResponse", jsonObject);

                        String debugString = debugJson.toString();
                        Log.d(TAG, "Image analysis result - isRacy: " + isRacy + ", isSafe: " + contentIsSafe + ", maxSeverity: " + maxSeverity);

                        callback.onImageContentChecked(isRacy, maxSeverity, debugString);

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error", e);

                        JSONObject errorJson = new JSONObject();
                        try {
                            errorJson.put("error", "Failed to parse API response");
                            errorJson.put("rawResponse", responseBody);
                        } catch (JSONException ignored) {}

                        callback.onImageContentChecked(false, 0, errorJson.toString());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Image processing error", e);
            callback.onContentCheckFailed("Image processing error: " + e.getMessage());
        }
    }

    private static byte[] getBytesFromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
}
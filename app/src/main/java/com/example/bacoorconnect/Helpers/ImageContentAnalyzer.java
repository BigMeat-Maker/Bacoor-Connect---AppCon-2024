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

    public interface ImageAnalysisCallback {
        void onImageContentChecked(boolean isRacy, double score, String debugJson);
        void onContentCheckFailed(String error);
    }

    public static void analyzeImage(Context context, Uri imageUri, ImageAnalysisCallback callback) {
        Log.d("ImageContentAnalyzer", "Starting image analysis");

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
                    .addHeader("Ocp-Apim-Subscription-Key", "CbSF1NDTzzTa1ZAUAHxfOBM7VW3QNwWiE6gLheiXeUdUlrQ8xoKQJQQJ99BDACi0881XJ3w3AAAHACOGLPIj")
                    .addHeader("Content-Type", "application/json")
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onContentCheckFailed(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onContentCheckFailed("API error: " + response.code());
                        return;
                    }

                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if (jsonObject.has("categoriesAnalysis")) {
                            JSONArray categoriesAnalysis = jsonObject.getJSONArray("categoriesAnalysis");
                            boolean contentIsSafe = true;
                            double maxSeverity = 0;

                            for (int i = 0; i < categoriesAnalysis.length(); i++) {
                                JSONObject category = categoriesAnalysis.getJSONObject(i);
                                String categoryName = category.getString("category");
                                double severity = category.getDouble("severity");
                                maxSeverity = Math.max(maxSeverity, severity);

                                if ("Sexual".equals(categoryName)) {
                                    contentIsSafe = severity == 0;
                                }
                            }

                            callback.onImageContentChecked(!contentIsSafe, maxSeverity, jsonObject.toString());
                        } else {
                            callback.onContentCheckFailed("No categoriesAnalysis in response");
                        }
                    } catch (JSONException e) {
                        callback.onContentCheckFailed("JSON parsing error");
                    }
                }
            });
        } catch (Exception e) {
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
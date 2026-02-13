package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TextContentAnalyzer {

    public interface TextAnalysisCallback {
        void onTextContentChecked(boolean isSafe, String debugJson);
        void onContentCheckFailed(String error);
    }

    public static void analyzeText(Context context, String description, TextAnalysisCallback callback) {
        Log.d("TextContentAnalyzer", "Starting text analysis");

        String apiKey = ContentSafetyConfig.getContentSafetyKey(context);

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onContentCheckFailed("Content Safety service not configured");
            return;
        }

        OkHttpClient client = new OkHttpClient();

        String jsonBody = "{\n" +
                "  \"text\": \"" + description + "\",\n" +
                "  \"categories\": [\"SelfHarm\", \"Sexual\", \"Hate\"]\n" +
                "}";

        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url("https://japaneast.api.cognitive.microsoft.com/contentsafety/text:analyze?api-version=2023-10-01")
                .post(requestBody)
                .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("TextContentAnalyzer", "API call failed", e);
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

                        for (int i = 0; i < categoriesAnalysis.length(); i++) {
                            JSONObject category = categoriesAnalysis.getJSONObject(i);
                            String categoryName = category.getString("category");
                            double severity = category.getDouble("severity");

                            if ((categoryName.equals("SelfHarm") || categoryName.equals("Sexual") ||
                                    categoryName.equals("Hate")) && severity > 0) {
                                contentIsSafe = false;
                                break;
                            }
                        }

                        callback.onTextContentChecked(contentIsSafe, jsonObject.toString());
                    } else {
                        callback.onContentCheckFailed("No categoriesAnalysis in response");
                    }
                } catch (JSONException e) {
                    callback.onContentCheckFailed("JSON parsing error");
                }
            }
        });
    }
}
package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ReverseImageSearchV2 {
    private static final String TAG = "ReverseImageSearchV2";
    private static final String SERPAPI_BASE_URL = "https://serpapi.com/search";

    // Treshhold is 4+matches is a straight up block and 1-3 is logged as well for smthng
    private static final int SUSPICIOUS_RESULT_COUNT = 4;

    public interface SearchCallback {
        void onSearchComplete(SearchResult result);
        void onSearchFailed(String error);
    }

    public static class SearchResult {
        public final String resultType;
        public final int matchCount;
        public final boolean shouldBlock;
        public final String debugInfo;
        public final String summary;

        public SearchResult(String resultType, int matchCount, boolean shouldBlock,
                            String debugInfo, String summary) {
            this.resultType = resultType;
            this.matchCount = matchCount;
            this.shouldBlock = shouldBlock;
            this.debugInfo = debugInfo;
            this.summary = summary;
        }
    }

    public static void searchImage(Context context, Uri imageUri, SearchCallback callback) {
        String apiKey = SerpApiConfig.getApiKey(context);
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onSearchFailed("SERPAPI key not configured");
            return;
        }

        ImageUploader.uploadImage(context, imageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                performReverseSearch(imageUrl, apiKey, callback);
            }

            @Override
            public void onUploadFailed(String error) {
                callback.onSearchFailed("Image upload failed: " + error);
            }
        });
    }

    private static void performReverseSearch(String imageUrl, String apiKey, SearchCallback callback) {
        String searchUrl = SERPAPI_BASE_URL +
                "?engine=google_reverse_image" +
                "&image_url=" + Uri.encode(imageUrl) +
                "&api_key=" + apiKey;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(searchUrl)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onSearchFailed("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();

                    if (!response.isSuccessful()) {
                        callback.onSearchFailed("API error: " + response.code());
                        return;
                    }

                    SearchResult result = analyzeResults(responseBody);
                    callback.onSearchComplete(result);

                } catch (JSONException e) {
                    callback.onSearchFailed("JSON parsing error: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private static SearchResult analyzeResults(String jsonResponse) throws JSONException {
        JSONObject json = new JSONObject(jsonResponse);

        int matchCount = 0;
        if (json.has("image_results")) {
            JSONArray results = json.getJSONArray("image_results");
            matchCount = results.length();
        }

        String resultType;
        boolean shouldBlock;
        String summary;

        if (matchCount == 0) {
            resultType = "NO_MATCHES";
            shouldBlock = false;
            summary = "No matching images found online";

        } else if (matchCount >= 1 && matchCount <= 3) {
            resultType = "FEW_MATCHES";
            shouldBlock = false;
            summary = String.format("Found %d matching image(s) online", matchCount);

        } else {
            resultType = "MANY_MATCHES";
            shouldBlock = true;
            summary = String.format("Found %d matching images online - likely fake", matchCount);
        }

        JSONObject debug = new JSONObject();
        debug.put("matchCount", matchCount);
        debug.put("resultType", resultType);
        debug.put("summary", summary);
        debug.put("fullResponse", new JSONObject(jsonResponse));

        return new SearchResult(
                resultType,
                matchCount,
                shouldBlock,
                debug.toString(),
                summary
        );
    }
}
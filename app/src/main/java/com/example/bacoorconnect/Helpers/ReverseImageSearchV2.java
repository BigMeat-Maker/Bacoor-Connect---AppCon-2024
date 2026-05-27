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

    // TESTING BOOLEAN HERE
    // Set to true to disable blocking in testing
    // Set to false to enable actual blocking in production
    private static final boolean TESTING_MODE = true;
    // ========================

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
                performGoogleLensSearch(imageUrl, apiKey, callback);
            }

            @Override
            public void onUploadFailed(String error) {
                callback.onSearchFailed("Image upload failed: " + error);
            }
        });
    }

    private static void performGoogleLensSearch(String imageUrl, String apiKey, SearchCallback callback) {
        String searchUrl = SERPAPI_BASE_URL +
                "?engine=google_lens" +
                "&url=" + Uri.encode(imageUrl) +
                "&api_key=" + apiKey;

        Log.d(TAG, "Performing Google Lens search for: " + imageUrl);

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

                    SearchResult result = analyzeLensResults(responseBody);
                    callback.onSearchComplete(result);

                } catch (JSONException e) {
                    callback.onSearchFailed("JSON parsing error: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private static SearchResult analyzeLensResults(String jsonResponse) throws JSONException {
        JSONObject json = new JSONObject(jsonResponse);

        Log.d(TAG, "Google Lens Response: " + jsonResponse);

        if (json.has("search_metadata")) {
            JSONObject metadata = json.getJSONObject("search_metadata");
            String status = metadata.optString("status", "");
            if (!"Success".equals(status)) {
                return new SearchResult(
                        "ERROR",
                        0,
                        false,
                        jsonResponse,
                        "Search failed: " + status
                );
            }
        }

        int matchCount = 0;
        if (json.has("visual_matches")) {
            JSONArray visualMatches = json.getJSONArray("visual_matches");
            matchCount = visualMatches.length();
            Log.d(TAG, "Found " + matchCount + " visual matches");
        }

        if (matchCount == 0 && json.has("image_results")) {
            JSONArray imageResults = json.getJSONArray("image_results");
            matchCount = imageResults.length();
            Log.d(TAG, "Found " + matchCount + " image results");
        }

        if (matchCount == 0 && json.has("inline_images")) {
            JSONArray inlineImages = json.getJSONArray("inline_images");
            matchCount = inlineImages.length();
            Log.d(TAG, "Found " + matchCount + " inline images");
        }

        boolean hasExactMatch = false;
        if (json.has("visual_matches")) {
            JSONArray visualMatches = json.getJSONArray("visual_matches");
            for (int i = 0; i < visualMatches.length(); i++) {
                JSONObject match = visualMatches.getJSONObject(i);
                if (match.optBoolean("exact_matches", false)) {
                    hasExactMatch = true;
                    Log.d(TAG, "Found exact match at position " + (i + 1));
                    break;
                }
            }
        }

        boolean hasKnowledgeGraph = json.has("knowledge_graph");
        if (hasKnowledgeGraph) {
            JSONObject kg = json.getJSONObject("knowledge_graph");
            String title = kg.optString("title", "Unknown");
            Log.d(TAG, "Has knowledge graph - Image recognized as: " + title);
        }

        String resultType;
        boolean shouldBlock;
        String summary;

        if (matchCount == 0 && !hasKnowledgeGraph) {
            resultType = "NO_MATCHES";
            summary = "No matching images found online";
        } else if (matchCount == 0 && hasKnowledgeGraph) {
            resultType = "KNOWLEDGE_GRAPH";
            summary = "Image recognized by Google Lens but no visual matches";
        } else if (matchCount >= 1 && matchCount <= 3) {
            resultType = "FEW_MATCHES";
            summary = String.format("Found %d matching image(s) online", matchCount);
        } else {
            resultType = "MANY_MATCHES";
            summary = String.format("Found %d matching images online", matchCount);
        }

        if (hasExactMatch) {
            summary += " (Exact match detected)";
            resultType = "EXACT_MATCH";
        }

        // TESTING TESTING NOTHING IS GETTING BLOCKED HERE
        if (TESTING_MODE) {
            shouldBlock = false;  // NOT BLOCKING IN TESTING
            summary = "[TEST MODE] " + summary + " - Would " +
                    (matchCount > 0 ? "BLOCK" : "ALLOW") + " in production";
            Log.w(TAG, "TESTING MODE: " + summary);
        } else {
            // THIS IS THE PRODUCTION VALUE
            shouldBlock = matchCount >= 4 || hasExactMatch;
        }


        JSONObject debug = new JSONObject();
        debug.put("matchCount", matchCount);
        debug.put("resultType", resultType);
        debug.put("hasExactMatch", hasExactMatch);
        debug.put("hasKnowledgeGraph", hasKnowledgeGraph);
        debug.put("summary", summary);
        debug.put("fullResponse", new JSONObject(jsonResponse));

        Log.d(TAG, "Analysis result - Type: " + resultType + ", Matches: " + matchCount +
                ", ShouldBlock: " + shouldBlock + ", TestingMode: " + TESTING_MODE);

        return new SearchResult(
                resultType,
                matchCount,
                shouldBlock,
                debug.toString(),
                summary
        );
    }
}
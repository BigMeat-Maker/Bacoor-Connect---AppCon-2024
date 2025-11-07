package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ReverseImageSearch {
    private static final String TAG = "ReverseImageSearch";

    public interface SearchCallback {
        void onSearchComplete(boolean isSuspicious, String debugInfo);
        void onSearchFailed(String error);
    }

    public static void searchImage(Context context, Uri imageUri, String apiKey, String cseId, SearchCallback callback) {
        Log.d(TAG, "Starting reverse image search for URI: " + imageUri);

        ImageUploader.uploadImage(context, imageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                Log.d(TAG, "Image uploaded successfully. URL: " + imageUrl);
                performReverseSearch(imageUrl, apiKey, cseId, callback);
            }

            @Override
            public void onUploadFailed(String error) {
                Log.e(TAG, "Image upload failed: " + error);
                callback.onSearchFailed("Image upload failed: " + error);
            }
        });
    }

    private static void performReverseSearch(String imageUrl, String apiKey, String cseId, SearchCallback callback) {
        Log.d(TAG, "Building search API URL for image: " + imageUrl);

        String apiUrl = "https://www.googleapis.com/customsearch/v1" +
                "?q=" + Uri.encode(imageUrl) +
                "&searchType=image" +
                "&imgSize=large" +
                "&key=" + apiKey +
                "&cx=" + cseId;

        Log.d(TAG, "Final API URL: " + apiUrl.replace(apiKey, "***REDACTED***"));

        new OkHttpClient().newCall(new Request.Builder().url(apiUrl).build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        String errorMsg = "Network error during search: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        callback.onSearchFailed(errorMsg);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Received API response. Code: " + response.code() +
                                    ", Length: " + responseBody.length() + " chars");

                            JSONObject json = new JSONObject(responseBody);
                            boolean hasItems = json.has("items");
                            int itemCount = hasItems ? json.getJSONArray("items").length() : 0;

                            Log.d(TAG, "Search results - hasItems: " + hasItems +
                                    ", itemCount: " + itemCount);

                            boolean isSuspicious = hasItems && itemCount > 0;
                            Log.d(TAG, "Suspicious status: " + isSuspicious);

                            if (isSuspicious) {
                                Log.w(TAG, "Suspicious image detected! Found " + itemCount + " matches");
                            }

                            callback.onSearchComplete(isSuspicious, responseBody);
                        } catch (JSONException e) {
                            String errorMsg = "JSON parsing error: " + e.getMessage();
                            Log.e(TAG, errorMsg, e);
                            callback.onSearchFailed(errorMsg);
                        } finally {
                            response.close();
                        }
                    }
                });
    }
}

//I need to remove these ugly ass LOGS but im still checking
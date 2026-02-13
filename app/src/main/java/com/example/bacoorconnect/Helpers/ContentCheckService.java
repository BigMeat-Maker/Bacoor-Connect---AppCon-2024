package com.example.bacoorconnect.Helpers;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ContentCheckService extends IntentService {
    private static final String TAG = "ContentCheckService";
    private static final String API_URL = "https://japaneast.api.cognitive.microsoft.com/contentsafety/";

    public static final String ACTION_CHECK_RESULT = "com.example.bacoorconnect.CONTENT_CHECK_RESULT";
    public static final String EXTRA_IS_SAFE = "is_safe";
    public static final String EXTRA_CONTENT_TYPE = "content_type";
    public static final String EXTRA_ERROR = "error";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";

    public ContentCheckService() {
        super("ContentCheckService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                sendErrorBroadcast("User not authenticated");
                return;
            }

            String userId = user.getUid();

            if (intent.hasExtra("imageUri")) {
                Uri imageUri = Uri.parse(intent.getStringExtra("imageUri"));
                checkImageContent(imageUri, userId);
            } else if (intent.hasExtra("textData")) {
                String textData = intent.getStringExtra("textData");
                checkTextContent(textData, userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Content check failed", e);
            sendErrorBroadcast(e.getMessage());
        }
    }

    private void checkImageContent(Uri imageUri, String userId) {
        try (InputStream imageStream = getContentResolver().openInputStream(imageUri)) {
            byte[] imageBytes = new byte[imageStream.available()];
            imageStream.read(imageBytes);

            JSONObject requestBody = new JSONObject()
                    .put("image", new JSONObject()
                            .put("content", Base64.encodeToString(imageBytes, Base64.NO_WRAP)))
                    .put("categories", new JSONArray().put("Sexual"));

            makeAzureRequest(
                    "image:analyze?api-version=2023-10-01",
                    requestBody.toString(),
                    result -> handleImageResult(result, userId),
                    error -> handleContentError(TYPE_IMAGE, error)
            );
        } catch (Exception e) {
            handleContentError(TYPE_IMAGE, "Image processing error: " + e.getMessage());
        }
    }

    private void checkTextContent(String textData, String userId) {
        try {
            JSONObject requestBody = new JSONObject()
                    .put("text", textData)
                    .put("categories", new JSONArray().put("Sexual"));

            makeAzureRequest(
                    "text:analyze?api-version=2023-10-01",
                    requestBody.toString(),
                    result -> handleTextResult(result, textData, userId),
                    error -> handleContentError(TYPE_TEXT, error)
            );
        } catch (JSONException e) {
            handleContentError(TYPE_TEXT, "JSON creation error: " + e.getMessage());
        }
    }

    private void makeAzureRequest(String endpoint, String jsonBody,
                                  ResultHandler successHandler, ErrorHandler errorHandler) {

        String apiKey = ContentSafetyConfig.getContentSafetyKey(this);

        if (apiKey == null || apiKey.isEmpty()) {
            errorHandler.handle("Content Safety service not configured");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(API_URL + endpoint)
                .post(body)
                .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errorHandler.handle("API request failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorHandler.handle("API error: " + response.code() + " - " + response.message());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    successHandler.handle(new JSONObject(responseBody));
                } catch (JSONException e) {
                    errorHandler.handle("JSON parse error: " + e.getMessage());
                }
            }
        });
    }

    private void handleImageResult(JSONObject result, String userId) throws JSONException {
        boolean isSafe = true;
        if (result.has("categoriesAnalysis")) {
            JSONArray analysis = result.getJSONArray("categoriesAnalysis");
            for (int i = 0; i < analysis.length(); i++) {
                JSONObject category = analysis.getJSONObject(i);
                if ("Sexual".equals(category.getString("category"))) {
                    isSafe = category.getDouble("severity") <= 0;
                    break;
                }
            }
        }

        if (!isSafe) {
            addContentStrike(userId, "Inappropriate image content", null);
        }
        sendResultBroadcast(isSafe, TYPE_IMAGE);
    }

    private void handleTextResult(JSONObject result, String textData, String userId) throws JSONException {
        boolean isSafe = true;
        if (result.has("categoriesAnalysis")) {
            JSONArray analysis = result.getJSONArray("categoriesAnalysis");
            for (int i = 0; i < analysis.length(); i++) {
                JSONObject category = analysis.getJSONObject(i);
                if ("Sexual".equals(category.getString("category"))) {
                    isSafe = category.getDouble("severity") <= 0;
                    break;
                }
            }
        }

        if (!isSafe) {
            addContentStrike(userId, "Inappropriate text content", textData);
        }
        sendResultBroadcast(isSafe, TYPE_TEXT);
    }

    private void addContentStrike(String userId, String reason, String textContent) {
        DatabaseReference strikesRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("strikes");

        Map<String, Object> strike = new HashMap<>();
        strike.put("reason", reason);
        strike.put("timestamp", System.currentTimeMillis());

        if (!TextUtils.isEmpty(textContent)) {
            strike.put("content", textContent);
        }

        strikesRef.push().setValue(strike)
                .addOnSuccessListener(task -> Log.d(TAG, "Strike recorded"))
                .addOnFailureListener(e -> Log.e(TAG, "Strike recording failed", e));
    }

    private void sendResultBroadcast(boolean isSafe, String contentType) {
        Intent result = new Intent(ACTION_CHECK_RESULT)
                .putExtra(EXTRA_IS_SAFE, isSafe)
                .putExtra(EXTRA_CONTENT_TYPE, contentType);
        sendBroadcast(result);
    }

    private void sendErrorBroadcast(String error) {
        Intent result = new Intent(ACTION_CHECK_RESULT)
                .putExtra(EXTRA_ERROR, error);
        sendBroadcast(result);
    }

    private void handleContentError(String contentType, String error) {
        Log.e(TAG, "Content check error (" + contentType + "): " + error);
        sendErrorBroadcast(contentType + " check failed: " + error);
    }

    interface ResultHandler {
        void handle(JSONObject result) throws JSONException;
    }

    interface ErrorHandler {
        void handle(String error);
    }
}
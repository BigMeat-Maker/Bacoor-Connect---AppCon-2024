package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class CategoryVerifier {

    private static final String TAG = "CategoryVerifier";

    public interface VerificationCallback {
        void onCategoryVerified(boolean matchesCategory, List<String> tags, String caption);
        void onVerificationFailed(String error);
    }

    public static void verifyImageCategory(Context context, Uri imageUri, String expectedCategory, VerificationCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = getBytes(inputStream);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            RequestBody requestBody = RequestBody.create(imageBytes, MediaType.parse("application/octet-stream"));

            String apiKey = AzureVisionConfig.getVisionKey(context);

            if (apiKey == null || apiKey.isEmpty()) {
                callback.onVerificationFailed("Azure Vision service not configured");
                return;
            }

            String features = "visualFeatures=Tags,Description,Objects,Adult,Categories";

            Request request = new Request.Builder()
                    .url("https://southeastasia.api.cognitive.microsoft.com/vision/v3.2/analyze?" + features)
                    .post(requestBody)
                    .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                    .addHeader("Content-Type", "application/octet-stream")
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onVerificationFailed("Failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        Log.d(TAG, "Azure Vision Response: " + json);

                        Gson gson = new Gson();
                        VisionResponse vr = gson.fromJson(json, VisionResponse.class);

                        List<String> tagList = new ArrayList<>();
                        if (vr.tags != null) {
                            for (VisionResponse.Tag tag : vr.tags) {
                                if (tag.confidence > 0.5f) {
                                    tagList.add(tag.name.toLowerCase());
                                }
                            }
                        }

                        String caption = "";
                        if (vr.description != null && vr.description.captions != null && !vr.description.captions.isEmpty()) {
                            VisionResponse.Caption firstCaption = vr.description.captions.get(0);
                            // Might be bad as low confidence is still getting accepted
                            if (firstCaption.text != null && !firstCaption.text.isEmpty()) {
                                caption = firstCaption.text.toLowerCase();
                                Log.d(TAG, "Caption extracted: '" + caption + "' (confidence: " + firstCaption.confidence + ")");
                            }
                        }

                        List<String> objectNames = new ArrayList<>();
                        if (vr.objects != null) {
                            for (VisionResponse.Object obj : vr.objects) {
                                if (obj.object != null && obj.confidence > 0.5f) {
                                    objectNames.add(obj.object.toLowerCase());
                                    tagList.add(obj.object.toLowerCase());
                                }
                            }
                        }

                        Log.d(TAG, "Tags: " + tagList);
                        Log.d(TAG, "Caption: '" + caption + "'");
                        Log.d(TAG, "Objects: " + objectNames);
                        Log.d(TAG, "Expected category: " + expectedCategory);

                        if (expectedCategory.equalsIgnoreCase("accident")) {
                            boolean isAccident = false;
                            String reason = "";

                            if (caption != null && !caption.isEmpty()) {
                                String[] accidentKeywords = {"crashed", "crash", "collision", "accident", "wreck", "damage", "overturned", "flipped"};
                                for (String keyword : accidentKeywords) {
                                    if (caption.contains(keyword)) {
                                        isAccident = true;
                                        reason = "Caption indicates accident: '" + caption + "'";
                                        Log.d(TAG, "✓ " + reason);
                                        break;
                                    }
                                }
                            }

                            if (!isAccident) {
                                String[] accidentTags = {"accident", "crash", "collision", "wreck", "damaged", "crashed", "emergency"};
                                for (String tag : tagList) {
                                    for (String accidentTag : accidentTags) {
                                        if (tag.contains(accidentTag)) {
                                            isAccident = true;
                                            reason = "Tag indicates accident: '" + tag + "'";
                                            Log.d(TAG, "✓ " + reason);
                                            break;
                                        }
                                    }
                                    if (isAccident) break;
                                }
                            }

                            Log.d(TAG, "Final determination: " + (isAccident ? "ACCIDENT DETECTED" : "NO ACCIDENT"));
                            callback.onCategoryVerified(isAccident, tagList, caption);
                        } else {
                            boolean matches = checkCategoryMatch(expectedCategory, tagList, caption, objectNames);
                            callback.onCategoryVerified(matches, tagList, caption);
                        }
                    } else {
                        callback.onVerificationFailed("Response error: " + response.code());
                    }
                }
            });

        } catch (Exception e) {
            callback.onVerificationFailed("Exception: " + e.getMessage());
        }
    }

    private static byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static boolean checkCategoryMatch(String expectedCategory, List<String> tags, String caption, List<String> objects) {
        Map<String, Set<String>> keywords = buildCategoryKeywords();
        Set<String> expectedKeywords = keywords.get(expectedCategory.toLowerCase());
        if (expectedKeywords == null) return false;

        for (String tag : tags) {
            if (expectedKeywords.contains(tag)) return true;
        }

        for (String obj : objects) {
            if (expectedKeywords.contains(obj)) return true;
        }

        for (String keyword : expectedKeywords) {
            if (caption != null && caption.contains(keyword)) return true;
        }

        return false;
    }

    private static Map<String, Set<String>> buildCategoryKeywords() {
        Map<String, Set<String>> map = new HashMap<>();

        map.put("fire", new HashSet<>(Arrays.asList(
                "fire", "flame", "flames", "smoke", "blaze", "burning", "inferno",
                "ember", "burn", "firefighter", "fire truck", "fire engine"
        )));

        map.put("accident", new HashSet<>(Arrays.asList(
                "accident", "crash", "collision", "wreck", "damaged", "overturned",
                "flipped", "ambulance", "emergency", "debris", "skid mark"
        )));

        map.put("traffic", new HashSet<>(Arrays.asList(
                "traffic", "congestion", "traffic jam", "gridlock", "heavy traffic",
                "queue", "backed up", "slow moving"
        )));

        map.put("naturaldisaster", new HashSet<>(Arrays.asList(
                "flood", "flooded", "flooding", "submerged", "typhoon", "storm",
                "hurricane", "heavy rain", "landslide", "earthquake"
        )));

        return map;
    }

    public static class VisionResponse {
        public List<Tag> tags;
        public Description description;
        public List<Object> objects;
        public Adult adult;

        public static class Tag {
            public String name;
            public float confidence;
        }

        public static class Description {
            public List<Caption> captions;
        }

        public static class Caption {
            public String text;
            public float confidence;
        }

        public static class Object {
            public String object;
            public float confidence;
            public Rectangle rectangle;
        }

        public static class Rectangle {
            public int x;
            public int y;
            public int w;
            public int h;
        }

        public static class Adult {
            public boolean isAdultContent;
            public boolean isRacyContent;
            public float adultScore;
            public float racyScore;
        }
    }
}
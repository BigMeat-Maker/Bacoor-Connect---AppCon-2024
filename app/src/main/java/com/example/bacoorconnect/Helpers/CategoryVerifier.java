package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import okhttp3.*;

public class CategoryVerifier {

    public interface VerificationCallback {
        void onCategoryVerified(boolean matchesCategory, List<String> tags, String caption);
        void onVerificationFailed(String error);
    }

    public static void verifyImageCategory(Context context, Uri imageUri, String expectedCategory, VerificationCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = getBytes(inputStream);

            OkHttpClient client = new OkHttpClient();

            RequestBody requestBody = RequestBody.create(imageBytes, MediaType.parse("application/octet-stream"));

            Request request = new Request.Builder()
                    .url("https://southeastasia.api.cognitive.microsoft.com/vision/v3.2/analyze?visualFeatures=Tags,Description")
                    .post(requestBody)
                    .addHeader("Ocp-Apim-Subscription-Key", "C6yDeKezjgqt3NGoeoe3oO0ehz2ex65NYI13ipSUrcREyFFOKNV6JQQJ99BDACqBBLyXJ3w3AAAFACOG3Lis")
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
                        Gson gson = new Gson();
                        VisionResponse vr = gson.fromJson(json, VisionResponse.class);

                        List<String> tagList = new ArrayList<>();
                        if (vr.tags != null) {
                            for (VisionResponse.Tag tag : vr.tags) {
                                if (tag.confidence > 0.65f)
                                    tagList.add(tag.name.toLowerCase());
                            }
                        }

                        String caption = "";
                        if (vr.description != null && vr.description.captions != null && !vr.description.captions.isEmpty()) {
                            VisionResponse.Caption firstCaption = vr.description.captions.get(0);
                            if (firstCaption.confidence > 0.65f)
                                caption = firstCaption.text.toLowerCase();
                        }

                        boolean matches = checkCategoryMatch(expectedCategory, tagList, caption);
                        callback.onCategoryVerified(matches, tagList, caption);
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

    private static boolean checkCategoryMatch(String expectedCategory, List<String> tags, String caption) {
        Map<String, Set<String>> keywords = buildCategoryKeywords();
        Set<String> expectedKeywords = keywords.get(expectedCategory.toLowerCase());
        if (expectedKeywords == null) return false;

        for (String tag : tags) {
            if (expectedKeywords.contains(tag)) return true;
        }

        for (String keyword : expectedKeywords) {
            if (caption.contains(keyword)) return true;
        }

        return false;
    }

    private static Map<String, Set<String>> buildCategoryKeywords() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("fire", new HashSet<>(Arrays.asList("fire", "flame", "smoke", "blaze", "burning", "inferno", "ember", "burn")));
        map.put("accident", new HashSet<>(Arrays.asList("accident", "crash", "collision", "wreck", "ambulance", "injury", "emergency")));
        map.put("traffic", new HashSet<>(Arrays.asList("traffic", "congestion", "car", "vehicle", "jam", "intersection")));
        map.put("naturaldisaster", new HashSet<>(Arrays.asList("flood", "earthquake", "storm", "hurricane", "landslide", "wildfire")));
        return map;
    }

    // VisionResponse is an inner class, same as before
    public static class VisionResponse {
        public List<Tag> tags;
        public Description description;

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
    }
}

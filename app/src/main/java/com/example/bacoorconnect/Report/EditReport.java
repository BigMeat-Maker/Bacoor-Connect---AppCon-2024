package com.example.bacoorconnect.Report;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.example.bacoorconnect.Helpers.CategoryVerifier;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.example.bacoorconnect.Helpers.ImageUploader;
import com.example.bacoorconnect.Helpers.ReverseImageSearchV2;
import com.example.bacoorconnect.Helpers.SightengineAIDetector;
import com.example.bacoorconnect.Helpers.TextContentAnalyzer;
import com.example.bacoorconnect.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditReport extends AppCompatActivity {

    private ProgressDialog verificationProgress;
    private TextView locationText;
    private double lat = 14.4597;
    private double lon = 120.9333;
    private double userLat = 14.4450;
    private double userLon = 120.9405;
    private TextInputEditText descriptionEditText;
    private RadioButton roadAccidentRadioButton, disasterAccidentRadioButton, fireAccidentRadioButton, trafficRadioButton;
    private ImageView roadAccidentImage, fireAccidentImage, disasterAccidentImage, trafficImage;
    private DatabaseReference reportRef;
    private String reportId, currentUserId;
    private DatabaseReference auditRef;
    private ImageView userUploadedImage1;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri selectedImageUri;
    private Uri originalImageUri;
    private boolean isImageChanged = false;
    private String originalImageUrl = "";
    private String selectedCategory = "";
    private boolean contentChecksPassed = false;
    private Map<String, Object> currentScanResults = new HashMap<>();
    private SightengineAIDetector aiDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editpost);

        initializeViews();
        setupImagePicker();
        setupButtonListeners();
        loadReportDetails();

        aiDetector = new SightengineAIDetector(this);
        if (!aiDetector.isReady()) {
            Log.w("EditReport", "Sightengine AI detector not ready - credentials missing");
        } else {
            Log.d("EditReport", "Sightengine AI detector initialized with threshold: " + aiDetector.getConfidenceThreshold());
        }

        selectedCategory = "";
        verificationProgress = new ProgressDialog(this);
        verificationProgress.setCancelable(false);
    }

    private void initializeViews() {
        currentUserId = getCurrentUserID();
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        locationText = findViewById(R.id.LocationText);
        descriptionEditText = findViewById(R.id.description_edit_text);

        roadAccidentRadioButton = findViewById(R.id.RoadAccRadio);
        disasterAccidentRadioButton = findViewById(R.id.DisasterRadio);
        fireAccidentRadioButton = findViewById(R.id.FireRadio);
        trafficRadioButton = findViewById(R.id.TrafficRadio);

        roadAccidentImage = findViewById(R.id.RoadAccident);
        fireAccidentImage = findViewById(R.id.Fire);
        disasterAccidentImage = findViewById(R.id.NaturalDisaster);
        trafficImage = findViewById(R.id.TrafficReport);

        userUploadedImage1 = findViewById(R.id.Useruploadedimage1);

        reportId = getIntent().getStringExtra("reportId");
        userLat = getIntent().getDoubleExtra("userLat", 14.4450);
        userLon = getIntent().getDoubleExtra("userLon", 120.9405);

        Log.d("EditReport", "Initialized with userLat: " + userLat + ", userLon: " + userLon);

        if (reportId == null) {
            Toast.makeText(this, "Error: Report ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        reportRef = FirebaseDatabase.getInstance().getReference("Report").child(reportId);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getExtras() != null && result.getData().getExtras().get("data") != null) {
                            Bitmap imageBitmap = (Bitmap) result.getData().getExtras().get("data");
                            if (imageBitmap != null) {
                                selectedImageUri = saveBitmapToFile(imageBitmap);
                                userUploadedImage1.setImageBitmap(imageBitmap);
                                userUploadedImage1.setVisibility(View.VISIBLE);
                                isImageChanged = true;
                            }
                        }
                        else if (result.getData().getData() != null) {
                            selectedImageUri = result.getData().getData();
                            userUploadedImage1.setImageURI(selectedImageUri);
                            userUploadedImage1.setVisibility(View.VISIBLE);
                            isImageChanged = true;
                        }
                    }
                });
    }

    private Uri saveBitmapToFile(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }

            String filename = "camera_" + System.currentTimeMillis() + ".jpg";
            File imageFile = new File(cachePath, filename);

            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();

            return Uri.fromFile(imageFile);

        } catch (IOException e) {
            Log.e("EditReport", "Error saving bitmap", e);
            return null;
        }
    }

    private void setupButtonListeners() {
        findViewById(R.id.Addimage).setOnClickListener(v -> showImageSelectionDialog());
        findViewById(R.id.exit_edit).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.Updatepost).setOnClickListener(v -> updateReport());

        userUploadedImage1.setOnClickListener(v -> showDeleteImageDialog());

        roadAccidentRadioButton.setOnClickListener(v -> {
            updateCategoryUI("accident");
            selectedCategory = "accident";
        });
        disasterAccidentRadioButton.setOnClickListener(v -> {
            updateCategoryUI("disaster");
            selectedCategory = "disaster";
        });
        fireAccidentRadioButton.setOnClickListener(v -> {
            updateCategoryUI("fire");
            selectedCategory = "fire";
        });
        trafficRadioButton.setOnClickListener(v -> {
            updateCategoryUI("traffic");
            selectedCategory = "traffic";
        });
    }

    private void showImageSelectionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Add Image")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                })
                .show();
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        imagePickerLauncher.launch(cameraIntent);
    }

    private void openGallery() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(pickPhoto);
    }

    private void showDeleteImageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this image?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    userUploadedImage1.setImageDrawable(null);
                    userUploadedImage1.setVisibility(View.GONE);
                    selectedImageUri = null;
                    isImageChanged = true;

                    if (originalImageUrl != null && !originalImageUrl.isEmpty()) {
                        originalImageUrl = "";
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadReportDetails() {
        reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String location = snapshot.child("location").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String category = snapshot.child("category").getValue(String.class);
                    originalImageUrl = snapshot.child("imageUrl").getValue(String.class);

                    Double firebaseLat = snapshot.child("latitude").getValue(Double.class);
                    if (firebaseLat == null) firebaseLat = snapshot.child("lat").getValue(Double.class);

                    Double firebaseLon = snapshot.child("longitude").getValue(Double.class);
                    if (firebaseLon == null) firebaseLon = snapshot.child("lon").getValue(Double.class);

                    if (firebaseLat != null) lat = firebaseLat;
                    if (firebaseLon != null) lon = firebaseLon;

                    if (location != null && !location.isEmpty()) {
                        locationText.setText(location);
                    } else if (firebaseLat != null && firebaseLon != null) {
                        String locationStr = String.format(Locale.getDefault(),
                                "Lat: %.6f, Lon: %.6f", lat, lon);
                        locationText.setText(locationStr);
                    }

                    descriptionEditText.setText(description);

                    if (originalImageUrl != null && !originalImageUrl.isEmpty()) {
                        originalImageUri = Uri.parse(originalImageUrl);
                        userUploadedImage1.setVisibility(View.VISIBLE);
                        Glide.with(EditReport.this).load(originalImageUrl).into(userUploadedImage1);
                    } else {
                        userUploadedImage1.setVisibility(View.GONE);
                    }

                    if (category != null) {
                        updateCategoryUI(category.toLowerCase());
                        selectedCategory = category.toLowerCase();
                    }
                } else {
                    Toast.makeText(EditReport.this, "Report not found.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditReport.this, "Failed to load report.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCategoryUI(String selectedCategory) {
        roadAccidentRadioButton.setChecked(false);
        disasterAccidentRadioButton.setChecked(false);
        fireAccidentRadioButton.setChecked(false);
        trafficRadioButton.setChecked(false);

        roadAccidentImage.setAlpha(0.5f);
        fireAccidentImage.setAlpha(0.5f);
        disasterAccidentImage.setAlpha(0.5f);
        trafficImage.setAlpha(0.5f);

        switch (selectedCategory) {
            case "accident":
                roadAccidentRadioButton.setChecked(true);
                roadAccidentImage.setAlpha(1.0f);
                break;
            case "disaster":
                disasterAccidentRadioButton.setChecked(true);
                disasterAccidentImage.setAlpha(1.0f);
                break;
            case "fire":
                fireAccidentRadioButton.setChecked(true);
                fireAccidentImage.setAlpha(1.0f);
                break;
            case "traffic":
                trafficRadioButton.setChecked(true);
                trafficImage.setAlpha(1.0f);
                break;
        }
    }

    private void updateReport() {
        String updatedDescription = descriptionEditText.getText().toString().trim();
        selectedCategory = getSelectedCategory();

        if (updatedDescription.isEmpty() || selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please provide description and select a category.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentScanResults.clear();
        contentChecksPassed = false;
        verificationProgress.setMessage("Verifying content...");
        verificationProgress.show();

        TextContentAnalyzer.analyzeText(this, updatedDescription,
                new TextContentAnalyzer.TextAnalysisCallback() {
                    @Override
                    public void onTextContentChecked(boolean isSafe, String debugJson) {
                        currentScanResults.put("textScan", debugJson);
                        if (!isSafe) {
                            verificationProgress.dismiss();
                            uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_OFFENSIVE_TEXT", debugJson);
                            handleContentViolation("Inappropriate text content",
                                    null, updatedDescription, debugJson);
                            return;
                        }

                        if (isImageChanged && selectedImageUri != null) {
                            verifyImageContent(updatedDescription);
                        } else {
                            verificationProgress.dismiss();
                            contentChecksPassed = true;
                            proceedWithUpdate(updatedDescription, selectedCategory, originalImageUrl);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        verificationProgress.dismiss();
                        currentScanResults.put("textScanError", error);
                        uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Text scan failed: " + error);
                        Toast.makeText(EditReport.this,
                                "Content verification service unavailable. Please try again later.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Text analysis failed", error);
                    }
                });
    }

    private void verifyImageContent(String description) {
        ImageContentAnalyzer.analyzeImage(EditReport.this, selectedImageUri,
                new ImageContentAnalyzer.ImageAnalysisCallback() {
                    @Override
                    public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                        currentScanResults.put("imageScan", debugJson);
                        if (isRacy) {
                            uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE", debugJson);
                            handleContentViolation("Inappropriate image content",
                                    selectedImageUri, description, debugJson);
                        } else {
                            performReverseImageSearch(description);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        currentScanResults.put("imageScanError", error);
                        uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Image scan failed: " + error);
                        Toast.makeText(EditReport.this,
                                "Image verification service unavailable. Cannot update report.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Image analysis failed", error);
                    }
                });
    }

    private void performReverseImageSearch(String description) {
        verificationProgress.setMessage("Verifying image...");

        ReverseImageSearchV2.searchImage(this, selectedImageUri,
                new ReverseImageSearchV2.SearchCallback() {
                    @Override
                    public void onSearchComplete(ReverseImageSearchV2.SearchResult result) {
                        currentScanResults.put("reverseImageSearch", result.debugInfo);
                        currentScanResults.put("reverseImageSearch_matchCount", result.matchCount);
                        currentScanResults.put("reverseImageSearch_resultType", result.resultType);
                        currentScanResults.put("reverseImageSearch_summary", result.summary);

                        Log.d("EditReport", "Reverse search - Type: " + result.resultType +
                                ", Matches: " + result.matchCount);

                        if (result.shouldBlock) {
                            verificationProgress.dismiss();
                            uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_ONLINE_IMAGE", result.summary + " | " + result.debugInfo);
                            handleContentViolation(result.summary, selectedImageUri, description, result.debugInfo);
                        } else {
                            verifyImageCategory(description);
                        }
                    }

                    @Override
                    public void onSearchFailed(String error) {
                        Log.e("EditReport", "Reverse search failed: " + error);
                        currentScanResults.put("reverseImageSearchError", error);
                        verifyImageCategory(description);
                    }
                });
    }

    private void verifyImageCategory(String description) {
        CategoryVerifier.verifyImageCategory(EditReport.this, selectedImageUri, selectedCategory,
                new CategoryVerifier.VerificationCallback() {
                    @Override
                    public void onCategoryVerified(boolean matchesCategory, List<String> tags, String caption) {
                        Map<String, Object> categoryData = new HashMap<>();
                        categoryData.put("matchesCategory", matchesCategory);
                        categoryData.put("tags", tags);
                        categoryData.put("caption", caption);
                        currentScanResults.put("categoryVerification", categoryData);

                        if (matchesCategory) {
                            performAIDetection(description, tags, caption);
                        } else {
                            verificationProgress.dismiss();
                            String reason = String.format("Image doesn't match %s category. Detected: %s",
                                    selectedCategory, tags != null ? tags.toString() : "unknown");
                            uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_CATEGORY", reason);
                            handleContentViolation(reason, selectedImageUri, description,
                                    "Tags: " + tags + ", Caption: " + caption);
                        }
                    }

                    @Override
                    public void onVerificationFailed(String error) {
                        currentScanResults.put("categoryVerificationError", error);
                        performAIDetection(description, null, null);
                    }
                });
    }

    private void performAIDetection(String description, List<String> tags, String caption) {
        if (!aiDetector.isReady()) {
            Log.w("EditReport", "AI detector not ready, skipping AI check");
            currentScanResults.put("aiDetection", "Detector not ready");
            verificationProgress.dismiss();
            contentChecksPassed = true;
            uploadImageAndUpdateReport(description, selectedCategory);
            return;
        }

        verificationProgress.setMessage("Final AI verification...");

        aiDetector.detectAIGeneratedImage(selectedImageUri, new SightengineAIDetector.AIDetectionCallback() {
            @Override
            public void onDetectionComplete(SightengineAIDetector.AIDetectionResult result) {
                verificationProgress.dismiss();
                currentScanResults.put("aiDetection", result.rawResponse);

                Log.d("EditReport", "AI Detection Result: " + result.getFormattedResult());

                if (result.isAboveThreshold()) {
                    String strikeReason = String.format(Locale.getDefault(),
                            "AI-generated image detected (Confidence: %.1f%%, Threshold: %.1f%%)",
                            result.confidence * 100, aiDetector.getConfidenceThreshold() * 100);
                    uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_AI", strikeReason);
                    handleContentViolation(strikeReason, selectedImageUri, description, result.rawResponse);
                } else if (result.isAIGenerated && result.confidence > aiDetector.getConfidenceThreshold() - 0.1) {
                    Log.w("EditReport", "Possible AI image below threshold: " + result.confidence);
                    uploadScanResultToFirebase(reportId, "WARNING", "POSSIBLE_AI", "Possible AI image - " + result.getFormattedResult());
                    contentChecksPassed = true;
                    uploadImageAndUpdateReport(description, selectedCategory);
                } else {
                    contentChecksPassed = true;
                    uploadImageAndUpdateReport(description, selectedCategory);
                }
            }

            @Override
            public void onDetectionFailed(String error) {
                verificationProgress.dismiss();
                Log.e("EditReport", "AI detection failed: " + error);
                currentScanResults.put("aiDetectionError", error);
                uploadScanResultToFirebase(reportId, "WARNING", "AI_CHECK_FAILED", error);
                Toast.makeText(EditReport.this,
                        "AI verification unavailable, continuing...", Toast.LENGTH_SHORT).show();
                contentChecksPassed = true;
                uploadImageAndUpdateReport(description, selectedCategory);
            }
        });
    }

    private void handleContentViolation(String reason, Uri imageUri, String text, String debugInfo) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(EditReport.this)
                    .setTitle("Content Issue Detected")
                    .setMessage(reason + "\n\nReport cannot be updated with this content.")
                    .setPositiveButton("OK", null)
                    .show();
        });
        addStrikeToUser(reason, imageUri, text);
        // Note: uploadScanResultToFirebase is already called before this method
    }

    private void addStrikeToUser(String reason, Uri imageInQuestion, String textInQuestion) {
        String userId = getCurrentUserID();
        if (userId == null) return;

        DatabaseReference userStrikesRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId)
                .child("strikes");

        String strikeId = userStrikesRef.push().getKey();

        Map<String, Object> strikeData = new HashMap<>();
        strikeData.put("time", System.currentTimeMillis());
        strikeData.put("reason", reason);
        strikeData.put("textInQuestion", textInQuestion);
        strikeData.put("reportId", reportId);

        if (imageInQuestion != null) {
            ImageUploader.uploadImage(EditReport.this, imageInQuestion, new ImageUploader.UploadCallback() {
                @Override
                public void onUploadSuccess(String imageUrl) {
                    strikeData.put("imageInQuestion", imageUrl);
                    if (strikeId != null) {
                        userStrikesRef.child(strikeId).setValue(strikeData);
                    }
                }

                @Override
                public void onUploadFailed(String error) {
                    Log.e("EditReport", "Image upload failed for strike: " + error);
                    strikeData.put("imageInQuestion", null);
                    if (strikeId != null) {
                        userStrikesRef.child(strikeId).setValue(strikeData);
                    }
                }
            });
        } else {
            strikeData.put("imageInQuestion", null);
            if (strikeId != null) {
                userStrikesRef.child(strikeId).setValue(strikeData);
            }
        }
    }

    private void uploadImageAndUpdateReport(String description, String category) {
        if (!contentChecksPassed) {
            Toast.makeText(this, "Content verification failed. Report not updated.", Toast.LENGTH_LONG).show();
            return;
        }

        verificationProgress.setMessage("Uploading image...");
        verificationProgress.show();

        ImageUploader.uploadImage(this, selectedImageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                verificationProgress.dismiss();
                proceedWithUpdate(description, category, imageUrl);
            }

            @Override
            public void onUploadFailed(String error) {
                verificationProgress.dismiss();
                uploadScanResultToFirebase(reportId, "FAILED", "UPLOAD_FAILED", error);
                Toast.makeText(EditReport.this,
                        "Failed to upload image. Report not updated.",
                        Toast.LENGTH_LONG).show();
                logFailedVerification("Image upload failed", error);
            }
        });
    }

    private void proceedWithUpdate(String description, String category, String imageUrl) {
        if (!contentChecksPassed) {
            Toast.makeText(this, "Content verification failed. Report not updated.", Toast.LENGTH_LONG).show();
            return;
        }

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("description", description);
        updates.put("category", category);
        updates.put("latitude", lat);
        updates.put("longitude", lon);
        updates.put("location", locationText.getText().toString());
        updates.put("scanResults", currentScanResults);

        if (isImageChanged) {
            updates.put("imageUrl", imageUrl != null ? imageUrl : "");
        }

        reportRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    uploadScanResultToFirebase(reportId, "SUCCESS", "APPROVED", null);
                    Toast.makeText(this, "Report updated successfully.", Toast.LENGTH_SHORT).show();
                    logActivity("Report updated", reportId);
                    finish();
                })
                .addOnFailureListener(e -> {
                    uploadScanResultToFirebase(reportId, "FAILED", "DATABASE_ERROR", e.getMessage());
                    Toast.makeText(this, "Failed to update report.", Toast.LENGTH_SHORT).show();
                    logActivity("Failed to update report: " + e.getMessage(), reportId);
                });
    }

    private void uploadScanResultToFirebase(String reportId, String status, String verdict, String errorDetails) {
        String userId = getCurrentUserID();
        String logId = FirebaseDatabase.getInstance().getReference("ScanLogs").push().getKey();

        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("reportId", reportId);
        log.put("timestamp", System.currentTimeMillis());
        log.put("status", status);
        log.put("verdict", verdict);
        log.put("scanResults", currentScanResults);
        log.put("errorDetails", errorDetails != null ? errorDetails : "");

        // Build summary for quick viewing
        StringBuilder summary = new StringBuilder();
        summary.append("Text: ").append(currentScanResults.containsKey("textScan") ? "✓" : "✗");
        summary.append(" | Image: ").append(currentScanResults.containsKey("imageScan") ? "✓" : "✗");
        summary.append(" | Reverse: ").append(currentScanResults.containsKey("reverseImageSearch_resultType") ?
                currentScanResults.get("reverseImageSearch_resultType") : "✗");
        summary.append(" | Category: ").append(currentScanResults.containsKey("categoryVerification") ?
                (currentScanResults.get("categoryVerification") instanceof Map ?
                        ((Map<?, ?>) currentScanResults.get("categoryVerification")).get("matchesCategory") : "✓") : "✗");
        summary.append(" | AI: ").append(currentScanResults.containsKey("aiDetection") ? "✓" : "✗");
        log.put("summary", summary.toString());

        if (logId != null) {
            FirebaseDatabase.getInstance().getReference("ScanLogs").child(logId).setValue(log)
                    .addOnSuccessListener(aVoid -> Log.d("ScanLogs", "Scan log saved for report: " + reportId))
                    .addOnFailureListener(e -> Log.e("ScanLogs", "Failed to save scan log", e));
        }
    }

    private void logFailedVerification(String type, String error) {
        HashMap<String, Object> log = new HashMap<>();
        log.put("timestamp", System.currentTimeMillis());
        log.put("userId", currentUserId);
        log.put("type", type);
        log.put("error", error);
        log.put("reportId", reportId);
        auditRef.push().setValue(log);
    }

    private String getSelectedCategory() {
        if (roadAccidentRadioButton.isChecked()) return "accident";
        if (disasterAccidentRadioButton.isChecked()) return "disaster";
        if (fireAccidentRadioButton.isChecked()) return "fire";
        if (trafficRadioButton.isChecked()) return "traffic";
        return "";
    }

    private String getCurrentUserID() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private void logActivity(String action, String reportId) {
        if (currentUserId == null) return;

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("timestamp", System.currentTimeMillis());
        logData.put("userId", currentUserId);
        logData.put("action", action);
        logData.put("reportId", reportId);

        auditRef.push().setValue(logData);
    }
}
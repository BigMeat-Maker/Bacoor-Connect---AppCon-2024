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
import com.example.bacoorconnect.Helpers.TrustScoreHelper;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditReport extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;

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
    private ProgressDialog mainProgressDialog;
    private boolean isFinishing = false;
    private boolean updateSuccessful = false;

    private String originalDescription;
    private String originalCategory;
    private String originalImageUrlBeforeEdit;
    private double originalLat;
    private double originalLon;
    private String originalLocation;
    private Map<String, Object> originalScanResults;

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

        mainProgressDialog = new ProgressDialog(this);
        mainProgressDialog.setCancelable(false);
        mainProgressDialog.setCanceledOnTouchOutside(false);
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (mainProgressDialog != null && !mainProgressDialog.isShowing()) {
                mainProgressDialog.setMessage(message);
                mainProgressDialog.show();
            } else if (mainProgressDialog != null && mainProgressDialog.isShowing()) {
                mainProgressDialog.setMessage(message);
            }
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            if (mainProgressDialog != null && mainProgressDialog.isShowing()) {
                mainProgressDialog.dismiss();
            }
        });
    }

    private void finishToFrontpage() {
        if (!isFinishing) {
            isFinishing = true;
            hideLoading();

            Intent intent = new Intent();
            setResult(updateSuccessful ? RESULT_OK : RESULT_CANCELED, intent);
            finish();
        }
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
            finishToFrontpage();
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
        findViewById(R.id.exit_edit).setOnClickListener(v -> finishToFrontpage());
        findViewById(R.id.Updatepost).setOnClickListener(v -> {
            v.setEnabled(false);
            updateReport();
        });

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
        showLoading("Loading report...");

        reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    originalDescription = snapshot.child("description").getValue(String.class);
                    originalCategory = snapshot.child("category").getValue(String.class);
                    originalImageUrlBeforeEdit = snapshot.child("imageUrl").getValue(String.class);
                    originalLat = lat;
                    originalLon = lon;
                    originalLocation = snapshot.child("location").getValue(String.class);

                    Object originalScanObj = snapshot.child("scanResults").getValue();
                    if (originalScanObj instanceof Map) {
                        originalScanResults = new HashMap<>((Map<String, Object>) originalScanObj);
                    }

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

                    hideLoading();
                } else {
                    hideLoading();
                    Toast.makeText(EditReport.this, "Report not found.", Toast.LENGTH_SHORT).show();
                    finishToFrontpage();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                Toast.makeText(EditReport.this, "Failed to load report.", Toast.LENGTH_SHORT).show();
                finishToFrontpage();
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

    private void updateTrustScoreForFailure(String reportId, String status, String verdict) {
        String currentUserId = getCurrentUserID();
        if (currentUserId != null) {
            Log.d("EditReport", "Updating trust score due to content violation - Status: " + status + ", Verdict: " + verdict);
            TrustScoreHelper.calculateAndUpdateTrustScore(currentUserId, new TrustScoreHelper.TrustScoreCallback() {
                @Override
                public void onScoreCalculated(double trustScore, int totalReports, int approvedReports) {
                    Log.d("EditReport", "Trust score updated after violation: " + trustScore + "% (Total: " + totalReports + ", Approved: " + approvedReports + ")");
                }

                @Override
                public void onError(String error) {
                    Log.e("EditReport", "Failed to update trust score: " + error);
                }
            });
        }
    }

    private void uploadFailureScanResult(String reportId, String status, String verdict, String errorDetails) {
        String userId = getCurrentUserID();
        String logId = FirebaseDatabase.getInstance().getReference("ScanLogs").push().getKey();

        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("reportId", reportId);
        log.put("timestamp", System.currentTimeMillis());
        log.put("status", status);
        log.put("verdict", verdict);
        log.put("type", "edit");
        log.put("scanResults", currentScanResults);
        log.put("errorDetails", errorDetails != null ? errorDetails : "");

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
            FirebaseDatabase.getInstance().getReference("ScanLogs").child(logId).setValue(log);
        }
    }

    private void updateReport() {
        String updatedDescription = descriptionEditText.getText().toString().trim();
        selectedCategory = getSelectedCategory();

        if (updatedDescription.isEmpty() || selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please provide description and select a category.", Toast.LENGTH_SHORT).show();
            findViewById(R.id.Updatepost).setEnabled(true);
            return;
        }

        currentScanResults.clear();
        contentChecksPassed = false;

        showLoading("Verifying content...");

        TextContentAnalyzer.analyzeText(this, updatedDescription,
                new TextContentAnalyzer.TextAnalysisCallback() {
                    @Override
                    public void onTextContentChecked(boolean isSafe, String debugJson) {
                        currentScanResults.put("textScan", debugJson);
                        if (!isSafe) {
                            uploadFailureScanResult(reportId, "BLOCKED", "REJECTED_OFFENSIVE_TEXT", debugJson);
                            handleContentViolation("Inappropriate text content",
                                    null, updatedDescription, debugJson);
                            updateTrustScoreForFailure(reportId, "BLOCKED", "REJECTED_OFFENSIVE_TEXT");
                            finishToFrontpage();
                            return;
                        }

                        if (isImageChanged && selectedImageUri != null) {
                            verifyImageContent(updatedDescription);
                        } else {
                            hideLoading();
                            contentChecksPassed = true;
                            proceedWithUpdate(updatedDescription, selectedCategory, originalImageUrl);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        currentScanResults.put("textScanError", error);
                        uploadFailureScanResult(reportId, "FAILED", "SCAN_ERROR", "Text scan failed: " + error);
                        updateTrustScoreForFailure(reportId, "FAILED", "SCAN_ERROR");
                        Toast.makeText(EditReport.this,
                                "Content verification service unavailable. Please try again later.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Text analysis failed", error);
                        finishToFrontpage();
                    }
                });
    }

    private void verifyImageContent(String description) {
        showLoading("Checking image content...");

        ImageContentAnalyzer.analyzeImage(EditReport.this, selectedImageUri,
                new ImageContentAnalyzer.ImageAnalysisCallback() {
                    @Override
                    public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                        currentScanResults.put("imageScan", debugJson);
                        if (isRacy) {
                            uploadFailureScanResult(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE", debugJson);
                            handleContentViolation("Inappropriate image content",
                                    selectedImageUri, description, debugJson);
                            updateTrustScoreForFailure(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE");
                            finishToFrontpage();
                        } else {
                            performReverseImageSearch(description);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        currentScanResults.put("imageScanError", error);
                        uploadFailureScanResult(reportId, "FAILED", "SCAN_ERROR", "Image scan failed: " + error);
                        updateTrustScoreForFailure(reportId, "FAILED", "SCAN_ERROR");
                        Toast.makeText(EditReport.this,
                                "Image verification service unavailable. Cannot update report.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Image analysis failed", error);
                        finishToFrontpage();
                    }
                });
    }

    private void performReverseImageSearch(String description) {
        showLoading("Verifying image online...");

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
                            uploadFailureScanResult(reportId, "BLOCKED", "REJECTED_ONLINE_IMAGE", result.summary + " | " + result.debugInfo);
                            handleContentViolation(result.summary, selectedImageUri, description, result.debugInfo);
                            updateTrustScoreForFailure(reportId, "BLOCKED", "REJECTED_ONLINE_IMAGE");
                            finishToFrontpage();
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
        showLoading("Verifying category match...");

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
                            String reason = String.format("Image doesn't match %s category. Detected: %s",
                                    selectedCategory, tags != null ? tags.toString() : "unknown");
                            uploadFailureScanResult(reportId, "BLOCKED", "REJECTED_CATEGORY", reason);
                            handleContentViolation(reason, selectedImageUri, description,
                                    "Tags: " + tags + ", Caption: " + caption);
                            updateTrustScoreForFailure(reportId, "BLOCKED", "REJECTED_CATEGORY");
                            finishToFrontpage();
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
            hideLoading();
            contentChecksPassed = true;
            uploadImageAndUpdateReport(description, selectedCategory);
            return;
        }

        showLoading("Final AI verification...");

        aiDetector.detectAIGeneratedImage(selectedImageUri, new SightengineAIDetector.AIDetectionCallback() {
            @Override
            public void onDetectionComplete(SightengineAIDetector.AIDetectionResult result) {
                currentScanResults.put("aiDetection", result.rawResponse);

                Log.d("EditReport", "AI Detection Result: " + result.getFormattedResult());

                if (result.isAboveThreshold()) {
                    String strikeReason = String.format(Locale.getDefault(),
                            "AI-generated image detected (Confidence: %.1f%%, Threshold: %.1f%%)",
                            result.confidence * 100, aiDetector.getConfidenceThreshold() * 100);
                    uploadFailureScanResult(reportId, "BLOCKED", "REJECTED_AI", strikeReason);
                    handleContentViolation(strikeReason, selectedImageUri, description, result.rawResponse);
                    updateTrustScoreForFailure(reportId, "BLOCKED", "REJECTED_AI");
                    finishToFrontpage();
                } else if (result.isAIGenerated && result.confidence > aiDetector.getConfidenceThreshold() - 0.1) {
                    Log.w("EditReport", "Possible AI image below threshold: " + result.confidence);
                    uploadFailureScanResult(reportId, "WARNING", "POSSIBLE_AI", "Possible AI image - " + result.getFormattedResult());
                    hideLoading();
                    contentChecksPassed = true;
                    uploadImageAndUpdateReport(description, selectedCategory);
                } else {
                    hideLoading();
                    contentChecksPassed = true;
                    uploadImageAndUpdateReport(description, selectedCategory);
                }
            }

            @Override
            public void onDetectionFailed(String error) {
                Log.e("EditReport", "AI detection failed: " + error);
                currentScanResults.put("aiDetectionError", error);
                uploadFailureScanResult(reportId, "WARNING", "AI_CHECK_FAILED", error);
                Toast.makeText(EditReport.this,
                        "AI verification unavailable, continuing...", Toast.LENGTH_SHORT).show();
                hideLoading();
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
                    .setPositiveButton("OK", (dialog, which) -> finishToFrontpage())
                    .show();
        });
        addStrikeToUser(reason, imageUri, text);
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
            finishToFrontpage();
            return;
        }

        showLoading("Uploading image...");

        ImageUploader.uploadImage(this, selectedImageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                proceedWithUpdate(description, category, imageUrl);
            }

            @Override
            public void onUploadFailed(String error) {
                uploadFailureScanResult(reportId, "FAILED", "UPLOAD_FAILED", error);
                updateTrustScoreForFailure(reportId, "FAILED", "UPLOAD_FAILED");
                Toast.makeText(EditReport.this,
                        "Failed to upload image. Report not updated.",
                        Toast.LENGTH_LONG).show();
                logFailedVerification("Image upload failed", error);
                finishToFrontpage();
            }
        });
    }

    private void proceedWithUpdate(String description, String category, String imageUrl) {
        if (!contentChecksPassed) {
            Toast.makeText(this, "Content verification failed. Report not updated.", Toast.LENGTH_LONG).show();
            finishToFrontpage();
            return;
        }

        showLoading("Updating report...");

        reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    finishToFrontpage();
                    return;
                }

                Map<String, Object> existingScanResults = new HashMap<>();
                Object existingScanObj = snapshot.child("scanResults").getValue();
                if (existingScanObj instanceof Map) {
                    existingScanResults.putAll((Map<String, Object>) existingScanObj);
                }

                int currentEditCount = snapshot.child("editCount").getValue(Integer.class) != null ?
                        snapshot.child("editCount").getValue(Integer.class) : 0;

                boolean descriptionChanged = !description.equals(originalDescription);
                boolean categoryChanged = !category.equals(originalCategory);
                boolean locationChanged = !locationText.getText().toString().equals(originalLocation);
                boolean imageChanged = isImageChanged && (imageUrl != null ? !imageUrl.equals(originalImageUrlBeforeEdit) : originalImageUrlBeforeEdit != null);

                Map<String, Object> scanResultsChanges = new HashMap<>();
                if (currentScanResults.containsKey("textScan") && originalScanResults != null) {
                    String newTextScan = currentScanResults.get("textScan") != null ? currentScanResults.get("textScan").toString() : "";
                    String oldTextScan = originalScanResults.get("textScan") != null ? originalScanResults.get("textScan").toString() : "";
                    if (!newTextScan.equals(oldTextScan)) {
                        scanResultsChanges.put("textScan", Map.of("old", oldTextScan, "new", newTextScan));
                    }
                }
                if (currentScanResults.containsKey("imageScan") && originalScanResults != null) {
                    String newImageScan = currentScanResults.get("imageScan") != null ? currentScanResults.get("imageScan").toString() : "";
                    String oldImageScan = originalScanResults.get("imageScan") != null ? originalScanResults.get("imageScan").toString() : "";
                    if (!newImageScan.equals(oldImageScan)) {
                        scanResultsChanges.put("imageScan", Map.of("old", oldImageScan, "new", newImageScan));
                    }
                }
                if (currentScanResults.containsKey("reverseImageSearch_resultType") && originalScanResults != null) {
                    String newReverseType = currentScanResults.get("reverseImageSearch_resultType") != null ? currentScanResults.get("reverseImageSearch_resultType").toString() : "";
                    String oldReverseType = originalScanResults.get("reverseImageSearch_resultType") != null ? originalScanResults.get("reverseImageSearch_resultType").toString() : "";
                    if (!newReverseType.equals(oldReverseType)) {
                        scanResultsChanges.put("reverseImageSearch_resultType", Map.of("old", oldReverseType, "new", newReverseType));
                    }
                }
                if (currentScanResults.containsKey("aiDetection") && originalScanResults != null) {
                    String newAiDetection = currentScanResults.get("aiDetection") != null ? currentScanResults.get("aiDetection").toString() : "";
                    String oldAiDetection = originalScanResults.get("aiDetection") != null ? originalScanResults.get("aiDetection").toString() : "";
                    if (!newAiDetection.equals(oldAiDetection)) {
                        scanResultsChanges.put("aiDetection", Map.of("old", oldAiDetection, "new", newAiDetection));
                    }
                }

                Map<String, Object> editEntry = new HashMap<>();
                String editId = "edit_" + System.currentTimeMillis();
                editEntry.put("timestamp", System.currentTimeMillis());

                List<String> changedFields = new ArrayList<>();

                if (descriptionChanged) {
                    editEntry.put("previousDescription", originalDescription);
                    editEntry.put("newDescription", description);
                    changedFields.add("description");
                }
                if (categoryChanged) {
                    editEntry.put("previousCategory", originalCategory);
                    editEntry.put("newCategory", category);
                    changedFields.add("category");
                }
                if (locationChanged) {
                    editEntry.put("previousLocation", originalLocation);
                    editEntry.put("previousLatitude", originalLat);
                    editEntry.put("previousLongitude", originalLon);
                    editEntry.put("newLocation", locationText.getText().toString());
                    editEntry.put("newLatitude", lat);
                    editEntry.put("newLongitude", lon);
                    changedFields.add("location");
                }
                if (imageChanged) {
                    editEntry.put("previousImageUrl", originalImageUrlBeforeEdit);
                    editEntry.put("newImageUrl", imageUrl);
                    changedFields.add("image");
                }
                if (!scanResultsChanges.isEmpty()) {
                    editEntry.put("scanResultsChanges", scanResultsChanges);
                    changedFields.add("scanResults");
                }

                editEntry.put("changedFields", changedFields);

                Map<String, Object> mergedScanResults = new HashMap<>();
                if (originalScanResults != null) {
                    mergedScanResults.putAll(originalScanResults);
                }
                mergedScanResults.putAll(currentScanResults);

                int newEditCount = currentEditCount + 1;

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("description", description);
                updates.put("category", category);
                updates.put("latitude", lat);
                updates.put("longitude", lon);
                updates.put("location", locationText.getText().toString());
                updates.put("scanResults", mergedScanResults);
                updates.put("editCount", newEditCount);
                updates.put("lastEdited", System.currentTimeMillis());

                if (!changedFields.isEmpty()) {
                    updates.put("editHistory/" + editId, editEntry);
                    Log.d("EditReport", "Edit #" + newEditCount + " - Changed fields: " + changedFields);
                } else {
                    Log.d("EditReport", "No changes detected, skipping edit history");
                }

                if (imageChanged) {
                    updates.put("imageUrl", imageUrl != null ? imageUrl : "");
                }

                reportRef.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            logActivity("Report Updated", reportId);
                            Log.d("EditReport", "✅ Report updated successfully - Edit #" + newEditCount);

                            updateSuccessful = true;
                            Toast.makeText(EditReport.this, "Report updated successfully.", Toast.LENGTH_SHORT).show();
                            finishToFrontpage();
                        })
                        .addOnFailureListener(e -> {
                            Log.e("EditReport", "Failed to update report", e);
                            logActivity("Failed to update report: " + e.getMessage(), reportId);
                            uploadFailureScanResult(reportId, "FAILED", "DATABASE_ERROR", e.getMessage());
                            updateTrustScoreForFailure(reportId, "FAILED", "DATABASE_ERROR");
                            Toast.makeText(EditReport.this, "Failed to update report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finishToFrontpage();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("EditReport", "Failed to load existing report data", error.toException());
                finishToFrontpage();
            }
        });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainProgressDialog != null && mainProgressDialog.isShowing()) {
            mainProgressDialog.dismiss();
        }
    }
}
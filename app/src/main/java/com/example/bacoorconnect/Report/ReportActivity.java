package com.example.bacoorconnect.Report;

import android.app.ProgressDialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.bacoorconnect.Helpers.CategoryVerifier;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.example.bacoorconnect.Helpers.ImageUploader;
import com.example.bacoorconnect.Helpers.ReverseImageSearchV2;
import com.example.bacoorconnect.Helpers.TrustScoreHelper;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.SightengineAIDetector;
import com.example.bacoorconnect.Helpers.TextContentAnalyzer;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private Uri imageUri;
    private String selectedCategory = "";
    private RadioButton preciseRadioButton, generalRadioButton;
    private EditText descriptionEditText;
    private TextView locationText;
    private ImageView selectImageButton, backButton;
    private NavigationView navigationView;
    private ImageView imagePreview;
    private double lon;
    private double lat;
    private double userLat;
    private double userLon;
    private ImageView DashNotif;
    private DatabaseReference auditRef;
    private DrawerLayout drawerLayout;
    private Map<String, Object> currentScanResults = new HashMap<>();

    private SightengineAIDetector aiDetector;

    // Single ProgressDialog for all operations
    private ProgressDialog mainProgressDialog;
    private boolean isFinishing = false;
    private boolean submissionSuccessful = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        initializeViews();
        setupCategorySelection();
        setupLocationHandling();
        setupImageSelection();
        setupSubmitButton();

        aiDetector = new SightengineAIDetector(this);
        if (!aiDetector.isReady()) {
            Log.w("ReportActivity", "Sightengine AI detector not ready - credentials missing");
        } else {
            Log.d("ReportActivity", "Sightengine AI detector initialized with threshold: " + aiDetector.getConfidenceThreshold());
        }

        handleIntentExtras();

        if (lat != 0.0 && lon != 0.0 && locationText != null) {
            String currentText = locationText.getText().toString();
            if (currentText.isEmpty() || currentText.contains(String.valueOf(lat))) {
                updateLocationFromLatLon(lat, lon);
            }
        }

        // Initialize main progress dialog
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
            setResult(submissionSuccessful ? RESULT_OK : RESULT_CANCELED, intent);
            finish();
        }
    }

    private boolean isCoordinateString(String text) {
        return text.matches("-?\\d+\\.\\d+,\\s*-?\\d+\\.\\d+");
    }

    private void initializeViews() {
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        preciseRadioButton = findViewById(R.id.Precise);
        generalRadioButton = findViewById(R.id.General);
        descriptionEditText = findViewById(R.id.description_edit_text);
        locationText = findViewById(R.id.location_text_view);
        imagePreview = findViewById(R.id.image_preview);
        selectImageButton = findViewById(R.id.select_image_button);
        backButton = findViewById(R.id.back_button);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        DashNotif = findViewById(R.id.notification);

        backButton.setOnClickListener(v -> finishToFrontpage());
    }

    private void updateLocationFromLatLon(double latitude, double longitude) {
        String coordText = String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude);
        locationText.setText(coordText + " (Getting address...)");

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locationDetails = address.getAddressLine(0);
                locationText.setText(locationDetails);
            } else {
                locationText.setText(coordText + " (Address not found)");
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationText.setText(coordText + " (Error fetching address)");
            Toast.makeText(this, "Error fetching address", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCategorySelection() {
        findViewById(R.id.Accident).setOnClickListener(v -> setSelectedCategory("accident", (ImageView) v));
        findViewById(R.id.Fire).setOnClickListener(v -> setSelectedCategory("fire", (ImageView) v));
        findViewById(R.id.Traffic).setOnClickListener(v -> setSelectedCategory("traffic", (ImageView) v));
        findViewById(R.id.NaturalDisaster).setOnClickListener(v -> setSelectedCategory("naturaldisaster", (ImageView) v));
    }

    private void setupLocationHandling() {
    }

    private void setupImageSelection() {
        selectImageButton.setOnClickListener(v -> openFileChooser());
    }

    private void setupSubmitButton() {
        findViewById(R.id.submit_report_button).setOnClickListener(v -> {
            v.setEnabled(false);

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");
            String reportId = reportsRef.push().getKey();

            checkUserStrikesAndSubmitReport(reportId, null, canSubmit -> {
                runOnUiThread(() -> {
                    if (canSubmit) {
                        uploadImageAndSubmitReport(reportId);
                    } else {
                        Toast.makeText(ReportActivity.this,
                                "You have exceeded the maximum allowed strikes. You cannot submit a report.",
                                Toast.LENGTH_LONG).show();
                        finishToFrontpage();
                    }
                    v.setEnabled(true);
                });
            });
        });
    }

    private void handleIntentExtras() {
        Bundle arguments = getIntent().getExtras();
        if (arguments != null) {
            String location = arguments.getString("location");
            lat = arguments.getDouble("lat", 0.0);
            lon = arguments.getDouble("lon", 0.0);
            userLat = arguments.getDouble("userLat", 14.4450);
            userLon = arguments.getDouble("userLon", 120.9405);

            if (location != null && !location.isEmpty() && locationText != null) {
                locationText.setText(location);
            } else if (lat != 0.0 && lon != 0.0) {
                locationText.setText(String.format(Locale.getDefault(), "%.6f, %.6f", lat, lon));
                updateLocationFromLatLon(lat, lon);
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = createImageFile();

        if (photoFile != null) {
            imageUri = FileProvider.getUriForFile(this, "com.example.bacoorconnect.fileprovider", photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
        }
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "IMG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            Log.e("ReportActivity", "Error creating image file", e);
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("IMAGE", "Activity result received. Code: " + resultCode);

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
                imageUri = data.getData();
                Log.d("IMAGE", "Gallery image selected: " + imageUri);
            }
            else if (requestCode == CAPTURE_IMAGE_REQUEST) {
                Log.d("IMAGE", "Camera image captured: " + imageUri);
            }

            if (imageUri != null) {
                Log.d("IMAGE", "Setting image preview");
                imagePreview.setImageURI(imageUri);
                imagePreview.setVisibility(View.VISIBLE);
            } else {
                Log.e("IMAGE", "Image URI is null after selection");
            }
        } else {
            Log.e("IMAGE", "Activity result not OK: " + resultCode);
        }
    }

    private void updateTrustScore(String reportId, String status, String verdict) {
        String currentUserId = getCurrentUserID();
        if (currentUserId != null) {
            Log.d("ReportActivity", "Updating trust score - Status: " + status + ", Verdict: " + verdict);
            TrustScoreHelper.calculateAndUpdateTrustScore(currentUserId, new TrustScoreHelper.TrustScoreCallback() {
                @Override
                public void onScoreCalculated(double trustScore, int totalReports, int approvedReports) {
                    Log.d("ReportActivity", "Trust score updated: " + trustScore + "% (Total: " + totalReports + ", Approved: " + approvedReports + ")");
                }

                @Override
                public void onError(String error) {
                    Log.e("ReportActivity", "Failed to update trust score: " + error);
                }
            });
        }
    }

    private void uploadImageAndSubmitReport(String reportId) {
        String description = descriptionEditText.getText().toString();
        currentScanResults.clear();

        showLoading("Checking text content...");

        TextContentAnalyzer.analyzeText(this, description, new TextContentAnalyzer.TextAnalysisCallback() {
            @Override
            public void onTextContentChecked(boolean isSafe, String debugJson) {
                currentScanResults.put("textScan", debugJson);
                if (!isSafe) {
                    uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_OFFENSIVE_TEXT", debugJson);
                    handleInappropriateContent(1, "Inappropriate text content", null, description, debugJson, reportId);
                    updateTrustScore(reportId, "BLOCKED", "REJECTED_OFFENSIVE_TEXT");
                    finishToFrontpage();
                    return;
                }

                if (imageUri != null) {
                    showLoading("Checking image content...");
                    ImageContentAnalyzer.analyzeImage(ReportActivity.this, imageUri,
                            new ImageContentAnalyzer.ImageAnalysisCallback() {
                                @Override
                                public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                    currentScanResults.put("imageScan", debugJson);
                                    if (isRacy) {
                                        uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE", debugJson);
                                        handleInappropriateContent(2, "Inappropriate image content", imageUri, description, debugJson, reportId);
                                        updateTrustScore(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE");
                                        finishToFrontpage();
                                    } else {
                                        runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                                                "Content is safe. Verifying image...",
                                                Toast.LENGTH_SHORT).show());
                                        performReverseImageSearch(imageUri, reportId);
                                    }
                                }

                                @Override
                                public void onContentCheckFailed(String error) {
                                    Log.e("IMAGE_SCAN", "Image scan failed: " + error);
                                    currentScanResults.put("imageScanError", error);
                                    runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                                            "Image verification failed. Uploading anyway.",
                                            Toast.LENGTH_SHORT).show());
                                    uploadImageToStorage(reportId);
                                }
                            });
                } else {
                    runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                            "Content is safe. Submitting report...",
                            Toast.LENGTH_SHORT).show());
                    submitReport(reportId, null);
                }
            }

            @Override
            public void onContentCheckFailed(String error) {
                Log.e("TEXT_SCAN", "Text scan failed: " + error);
                currentScanResults.put("textScanError", error);
                if (imageUri != null) {
                    ImageContentAnalyzer.analyzeImage(ReportActivity.this, imageUri,
                            new ImageContentAnalyzer.ImageAnalysisCallback() {

                                @Override
                                public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                    currentScanResults.put("imageScan", debugJson);
                                    if (isRacy) {
                                        uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE", debugJson);
                                        handleInappropriateContent(2, "Inappropriate image content", imageUri, description, debugJson, reportId);
                                        updateTrustScore(reportId, "BLOCKED", "REJECTED_OFFENSIVE_IMAGE");
                                        finishToFrontpage();
                                    } else {
                                        performReverseImageSearch(imageUri, reportId);
                                    }
                                }

                                @Override
                                public void onContentCheckFailed(String error) {
                                    currentScanResults.put("imageScanError", error);
                                    runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                                            "Content verification failed completely. Report blocked.",
                                            Toast.LENGTH_LONG).show());
                                    uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Text scan: " + error + ", Image scan: " + error);
                                    updateTrustScore(reportId, "FAILED", "SCAN_ERROR");
                                    finishToFrontpage();
                                }
                            });
                } else {
                    runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                            "Content verification failed. Report blocked.",
                            Toast.LENGTH_LONG).show());
                    uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Text scan failed: " + error);
                    updateTrustScore(reportId, "FAILED", "SCAN_ERROR");
                    finishToFrontpage();
                }
            }
        });
    }

    private void performReverseImageSearch(Uri imageUri, String reportId) {
        showLoading("Searching for image online...");

        ReverseImageSearchV2.searchImage(this, imageUri,
                new ReverseImageSearchV2.SearchCallback() {
                    @Override
                    public void onSearchComplete(ReverseImageSearchV2.SearchResult result) {
                        runOnUiThread(() -> {
                            currentScanResults.put("reverseImageSearch", result.debugInfo);
                            currentScanResults.put("reverseImageSearch_matchCount", result.matchCount);
                            currentScanResults.put("reverseImageSearch_resultType", result.resultType);
                            currentScanResults.put("reverseImageSearch_summary", result.summary);

                            Log.d("ReportActivity", "Reverse search - Type: " + result.resultType +
                                    ", Matches: " + result.matchCount);

                            if (result.shouldBlock) {
                                uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_ONLINE_IMAGE", result.summary + " | " + result.debugInfo);
                                handleInappropriateContent(3, result.summary, imageUri,
                                        getCurrentDescription(), result.debugInfo, reportId);
                                updateTrustScore(reportId, "BLOCKED", "REJECTED_ONLINE_IMAGE");
                                finishToFrontpage();
                            } else {
                                verifyImageCategory(imageUri, reportId, result.debugInfo);
                            }
                        });
                    }

                    @Override
                    public void onSearchFailed(String error) {
                        runOnUiThread(() -> {
                            Log.e("ReverseImageSearch", "Search failed: " + error);
                            currentScanResults.put("reverseImageSearchError", error);
                            verifyImageCategory(imageUri, reportId, "Search failed: " + error);
                        });
                    }
                });
    }

    private String getCurrentDescription() {
        return descriptionEditText.getText().toString().trim();
    }

    private void verifyImageCategory(Uri imageUri, String reportId, String debugInfo) {
        showLoading("Verifying category match...");

        CategoryVerifier.verifyImageCategory(this, imageUri, selectedCategory,
                new CategoryVerifier.VerificationCallback() {
                    @Override
                    public void onCategoryVerified(boolean matchesCategory,
                                                   List<String> tags, String caption) {
                        Map<String, Object> categoryData = new HashMap<>();
                        categoryData.put("matchesCategory", matchesCategory);
                        categoryData.put("tags", tags);
                        categoryData.put("caption", caption);
                        currentScanResults.put("categoryVerification", categoryData);

                        if (matchesCategory) {
                            performAIDetection(imageUri, reportId, debugInfo, tags, caption);
                        } else {
                            String strikeReason = String.format(
                                    "Category mismatch. Expected %s, found tags: %s",
                                    selectedCategory, tags);
                            uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_CATEGORY", strikeReason);
                            handleInappropriateContent(3, strikeReason,
                                    imageUri, caption, debugInfo, reportId);
                            updateTrustScore(reportId, "BLOCKED", "REJECTED_CATEGORY");
                            finishToFrontpage();
                        }
                    }

                    @Override
                    public void onVerificationFailed(String error) {
                        Log.e("CategoryCheck", "Verification failed: " + error);
                        currentScanResults.put("categoryVerificationError", error);
                        Toast.makeText(ReportActivity.this,
                                "Image verification incomplete. Report will be reviewed.",
                                Toast.LENGTH_LONG).show();
                        performAIDetection(imageUri, reportId, debugInfo, null, null);
                    }
                });
    }

    private void performAIDetection(Uri imageUri, String reportId, String debugInfo,
                                    List<String> tags, String caption) {

        if (!aiDetector.isReady()) {
            Log.w("ReportActivity", "AI detector not ready, skipping AI check");
            currentScanResults.put("aiDetection", "Detector not ready");
            uploadImageToStorage(reportId);
            return;
        }

        showLoading("Final AI verification...");

        aiDetector.detectAIGeneratedImage(imageUri, new SightengineAIDetector.AIDetectionCallback() {
            @Override
            public void onDetectionComplete(SightengineAIDetector.AIDetectionResult result) {
                runOnUiThread(() -> {
                    currentScanResults.put("aiDetection", result.rawResponse);

                    Log.d("ReportActivity", "AI Detection Result: " + result.getFormattedResult());

                    if (result.isAboveThreshold()) {
                        String strikeReason = String.format(Locale.getDefault(),
                                "AI-generated image detected (Confidence: %.1f%%, Threshold: %.1f%%)",
                                result.confidence * 100, aiDetector.getConfidenceThreshold() * 100
                        );

                        String additionalInfo = String.format(Locale.getDefault(),
                                "Detection Type: %s\nTags: %s\nCaption: %s\nDebug: %s",
                                result.detectionType, tags != null ? tags : "N/A",
                                caption != null ? caption : "N/A", debugInfo
                        );

                        uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_AI", strikeReason + " | " + additionalInfo);
                        handleInappropriateContent(3, strikeReason, imageUri,
                                getCurrentDescription(), result.rawResponse + "\n" + additionalInfo, reportId);
                        updateTrustScore(reportId, "BLOCKED", "REJECTED_AI");
                        finishToFrontpage();

                    } else if (result.isAIGenerated && result.confidence > aiDetector.getConfidenceThreshold() - 0.1) {
                        Log.w("ReportActivity", "Possible AI image below threshold: " + result.confidence);
                        uploadScanResultToFirebase(reportId, "WARNING", "POSSIBLE_AI", "Possible AI image - " + result.getFormattedResult());
                        uploadImageToStorage(reportId);
                    } else {
                        uploadImageToStorage(reportId);
                    }
                });
            }

            @Override
            public void onDetectionFailed(String error) {
                runOnUiThread(() -> {
                    Log.e("ReportActivity", "AI detection failed: " + error);
                    currentScanResults.put("aiDetectionError", error);
                    Toast.makeText(ReportActivity.this,
                            "AI verification unavailable, continuing...", Toast.LENGTH_SHORT).show();
                    uploadImageToStorage(reportId);
                });
            }
        });
    }

    private void handleInappropriateContent(int strikeCount, String reason, Uri imageUri, String text, String debugJson, String reportId) {
        String verdict;
        if (reason.contains("AI-generated")) {
            verdict = "REJECTED_AI";
        } else if (reason.contains("Category mismatch")) {
            verdict = "REJECTED_CATEGORY";
        } else if (reason.contains("online")) {
            verdict = "REJECTED_ONLINE_IMAGE";
        } else {
            verdict = "REJECTED_OFFENSIVE";
        }

        String message;
        if (reason.contains("AI-generated")) {
            message = "AI-generated images are not allowed in reports!";
        } else if (reason.contains("Category mismatch")) {
            message = "Image content doesn't match selected category!";
        } else if (reason.contains("online")) {
            message = "Image found online! Please use original photos only.";
        } else {
            message = "Inappropriate content detected!";
        }

        runOnUiThread(() -> Toast.makeText(ReportActivity.this, message, Toast.LENGTH_LONG).show());

        addStrikeToUser(strikeCount, reason, imageUri, text);
    }

    private void uploadImageToStorage(String reportId) {
        showLoading("Uploading image...");

        ImageUploader.uploadImage(this, imageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                submitReport(reportId, imageUrl);
            }

            @Override
            public void onUploadFailed(String error) {
                Log.e("ReportActivity", "Image upload failed: " + error);
                uploadScanResultToFirebase(reportId, "FAILED", "UPLOAD_FAILED", error);
                updateTrustScore(reportId, "FAILED", "UPLOAD_FAILED");
                submitReport(reportId, null);
            }
        });
    }

    private void checkUserStrikesAndSubmitReport(String reportId, String imageUrl, OnStrikeCheckCompleteListener listener) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userStrikesRef = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("strikes");

        userStrikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                long strikeCount = dataSnapshot.getChildrenCount();

                if (strikeCount > 5) {
                    runOnUiThread(() -> {
                        Toast.makeText(ReportActivity.this, "You have exceeded the maximum allowed strikes.", Toast.LENGTH_LONG).show();

                    });
                    uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_STRIKE_LIMIT", "User has exceeded strike limit");
                    updateTrustScore(reportId, "BLOCKED", "REJECTED_STRIKE_LIMIT");
                    listener.onStrikeCheckComplete(false);
                } else {
                    listener.onStrikeCheckComplete(true);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("StrikeCheck", "Failed to check user strikes", databaseError.toException());

                listener.onStrikeCheckComplete(true);
            }
        });
    }

    private void addStrikeToUser(int strikeCount, String reason, Uri imageInQuestion, String textInQuestion) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userStrikesRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId)
                .child("strikes");

        String strikeId = userStrikesRef.push().getKey();

        Map<String, Object> strikeData = new HashMap<>();
        strikeData.put("time", System.currentTimeMillis());
        strikeData.put("reason", reason);
        strikeData.put("textInQuestion", textInQuestion);

        if (imageInQuestion != null) {
            ImageUploader.uploadImage(ReportActivity.this, imageInQuestion, new ImageUploader.UploadCallback() {
                @Override
                public void onUploadSuccess(String imageUrl) {
                    strikeData.put("imageInQuestion", imageUrl);
                    saveStrikeToDatabase(userStrikesRef, strikeId, strikeData, reason);
                }

                @Override
                public void onUploadFailed(String error) {
                    Log.e("ImageUpload", "Image upload failed: " + error);
                    strikeData.put("imageInQuestion", null);
                    saveStrikeToDatabase(userStrikesRef, strikeId, strikeData, reason);
                }
            });
        }else {
            strikeData.put("imageInQuestion", null);
            saveStrikeToDatabase(userStrikesRef, strikeId, strikeData, reason);
        }
    }

    private void saveStrikeToDatabase(DatabaseReference userStrikesRef, String strikeId, Map<String, Object> strikeData, String reason) {
        if (strikeId != null) {
            userStrikesRef.child(strikeId).setValue(strikeData).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("StrikeSystem", "Strike added: " + reason);
                } else {
                    Log.e("StrikeSystem", "Failed to add strike", task.getException());
                }
            });
        }
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

    private void submitReport(String reportId, String imageUrl) {
        if (selectedCategory.isEmpty() && !preciseRadioButton.isChecked() && !generalRadioButton.isChecked()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
            });
            finishToFrontpage();
            return;
        }

        showLoading("Submitting report...");

        checkUserStrikesAndSubmitReport(reportId, imageUrl, new OnStrikeCheckCompleteListener() {
            @Override
            public void onStrikeCheckComplete(boolean canSubmit) {
                if (!canSubmit) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(ReportActivity.this)
                                .setTitle("Submission Blocked")
                                .setMessage("You have been deemed an unsafe user. You cannot submit a report.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                    uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_STRIKE_LIMIT", "User has exceeded strike limit");
                    updateTrustScore(reportId, "BLOCKED", "REJECTED_STRIKE_LIMIT");
                    finishToFrontpage();
                    return;
                }

                String description = descriptionEditText.getText().toString();
                String addressPrecision = preciseRadioButton.isChecked() ? "precise" : "general";
                long timestamp = System.currentTimeMillis();

                String currentUserId = getCurrentUserID();

                HashMap<String, Object> reportData = new HashMap<>();
                reportData.put("addressPrecision", addressPrecision);
                reportData.put("category", selectedCategory);
                reportData.put("description", description);
                reportData.put("location", locationText.getText().toString());
                reportData.put("latitude", lat);
                reportData.put("longitude", lon);
                reportData.put("upvotes", 0);
                reportData.put("downvotes", 0);
                reportData.put("comments", new HashMap<>());
                reportData.put("timestamp", timestamp);
                reportData.put("userId", currentUserId);
                reportData.put("scanResults", currentScanResults);

                if (imageUrl != null) {
                    reportData.put("imageUrl", imageUrl);
                }

                DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");

                if (reportId != null) {
                    reportsRef.child(reportId).setValue(reportData)
                            .addOnSuccessListener(aVoid -> {
                                logActivity("Submit Report", "Report Submitted", currentUserId, "Success");

                                uploadScanResultToFirebase(reportId, "SUCCESS", "APPROVED", null);
                                updateTrustScore(reportId, "SUCCESS", "APPROVED");

                                submissionSuccessful = true;
                                runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                                        "Report submitted successfully!", Toast.LENGTH_SHORT).show());

                                finishToFrontpage();
                            })
                            .addOnFailureListener(e -> {
                                logActivity("Submit Report", "Report Submission Failed", currentUserId, "Failure");
                                uploadScanResultToFirebase(reportId, "FAILED", "DATABASE_ERROR", e.getMessage());
                                updateTrustScore(reportId, "FAILED", "DATABASE_ERROR");

                                runOnUiThread(() -> Toast.makeText(ReportActivity.this,
                                        "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show());
                                finishToFrontpage();
                            });
                }
            }
        });
    }

    private void logActivity(String action, String description, String userId, String status) {
        if (userId == null) return;

        String logId = auditRef.push().getKey();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("dateTime", dateTime);
        logData.put("action", action);
        logData.put("description", description);
        logData.put("userId", userId);
        logData.put("status", status);

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }

    private String getCurrentUserID() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private void setSelectedCategory(String category, ImageView selectedImage) {
        selectedCategory = category;

        clearCategoryHighlights();
        selectedImage.setAlpha(1.0f);
    }

    private void clearCategoryHighlights() {
        findViewById(R.id.Accident).setAlpha(0.5f);
        findViewById(R.id.Fire).setAlpha(0.5f);
        findViewById(R.id.Traffic).setAlpha(0.5f);
        findViewById(R.id.NaturalDisaster).setAlpha(0.5f);
    }

    private void openFileChooser() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image Source")
                .setItems(new CharSequence[]{"Take a Photo", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    public interface OnStrikeCheckCompleteListener {
        void onStrikeCheckComplete(boolean canSubmit);
    }

    public interface OnImageUploadedListener {
        void onImageUploaded(String imageUrl);
    }

    interface OnImageUploadListener {
        void onImageUploadSuccess(String imageUrl);

        void onImageUploadFailure(String error);
    }

    interface OnContentCheckListener {
        void onTextContentChecked(boolean isSafe, String debugJson);

        void onImageContentChecked(boolean isRacy, double score, String debugJson);

        void onContentCheckFailed(String error);
    }

    interface OnCategoryVerificationListener {
        void onCategoryVerified(boolean matchesCategory, List<String> tags, String caption);
        void onVerificationFailed(String error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainProgressDialog != null && mainProgressDialog.isShowing()) {
            mainProgressDialog.dismiss();
        }
    }
}
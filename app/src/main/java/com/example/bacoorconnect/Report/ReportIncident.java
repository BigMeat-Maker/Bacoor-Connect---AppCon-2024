package com.example.bacoorconnect.Report;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import com.example.bacoorconnect.General.BottomNavHelper;
import com.example.bacoorconnect.Helpers.CategoryVerifier;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.example.bacoorconnect.Helpers.ImageUploader;
import com.example.bacoorconnect.Helpers.TrustScoreHelper;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.ReverseImageSearchV2;
import com.example.bacoorconnect.Helpers.SightengineAIDetector;
import com.example.bacoorconnect.Helpers.TextContentAnalyzer;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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

public class ReportIncident extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int LOCATION_PERMISSION_REQUEST = 100;

    private Uri imageUri;
    private String selectedCategory = "";
    private RadioButton preciseRadioButton, generalRadioButton;
    private EditText descriptionEditText;
    private TextView locationText;
    private ImageView selectImageButton;
    private ImageView imagePreview;
    private double lon;
    private double lat;
    private double userLat;
    private double userLon;
    private BottomNavigationView bottomNavigationView;
    private DatabaseReference auditRef;
    private FusedLocationProviderClient fusedLocationClient;
    private SightengineAIDetector aiDetector;
    private Map<String, Object> currentScanResults = new HashMap<>();

    // Single ProgressDialog for all operations
    private ProgressDialog mainProgressDialog;
    private boolean isFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        initializeViews();
        setupNavigation();
        setupCategorySelection();
        setupLocationHandling();
        setupImageSelection();
        setupSubmitButton();

        aiDetector = new SightengineAIDetector(this);
        if (!aiDetector.isReady()) {
            Log.w("ReportIncident", "Sightengine AI detector not ready - credentials missing");
        } else {
            Log.d("ReportIncident", "Sightengine AI detector initialized with threshold: " + aiDetector.getConfidenceThreshold());
        }

        handleIntentExtras();

        if (lat == 0.0 && lon == 0.0) {
            getCurrentLocation();
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
            setResult(RESULT_OK, intent);
            finish();
        }
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

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            lat = location.getLatitude();
                            lon = location.getLongitude();
                            userLat = lat;
                            userLon = lon;
                            updateLocationFromLatLon(lat, lon);
                            Log.d("ReportIncident", "Got location: " + lat + ", " + lon);
                        } else {
                            Log.e("ReportIncident", "Location is null");
                            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ReportIncident", "Failed to get location: " + e.getMessage());
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show();
                    });
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Using default location.", Toast.LENGTH_SHORT).show();
                lat = 14.4450;
                lon = 120.9405;
                updateLocationFromLatLon(lat, lon);
            }
        }
    }

    private void initializeViews() {
        preciseRadioButton = findViewById(R.id.Precise);
        generalRadioButton = findViewById(R.id.General);
        descriptionEditText = findViewById(R.id.description_edit_text);
        locationText = findViewById(R.id.location_text_view);
        imagePreview = findViewById(R.id.image_preview);
        selectImageButton = findViewById(R.id.select_image_button);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }

    private void setupNavigation() {
        BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.nav_ri);
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
            // Prevent multiple clicks
            v.setEnabled(false);

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");
            String reportId = reportsRef.push().getKey();

            checkUserStrikesAndSubmitReport(reportId, null, canSubmit -> {
                runOnUiThread(() -> {
                    if (canSubmit) {
                        uploadImageAndSubmitReport(reportId);
                    } else {
                        Toast.makeText(ReportIncident.this,
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
            userLat = arguments.getDouble("userLat", lat != 0 ? lat : 14.4450);
            userLon = arguments.getDouble("userLon", lon != 0 ? lon : 120.9405);

            Log.d("ReportIncident", "Intent extras - Lat: " + lat + ", Lon: " + lon);

            if (location != null && !location.isEmpty() && locationText != null) {
                locationText.setText(location);
            } else if (lat != 0.0 && lon != 0.0) {
                String coordText = String.format(Locale.getDefault(), "%.6f, %.6f", lat, lon);
                locationText.setText(coordText);
                updateLocationFromLatLon(lat, lon);
            }
        } else {
            Log.d("ReportIncident", "No intent extras found");
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
            Log.e("ReportIncident", "Error creating image file", e);
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
            } else if (requestCode == CAPTURE_IMAGE_REQUEST) {
                Log.d("IMAGE", "Camera image captured: " + imageUri);
            }

            if (imageUri != null) {
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
            Log.d("ReportIncident", "Updating trust score - Status: " + status + ", Verdict: " + verdict);
            TrustScoreHelper.calculateAndUpdateTrustScore(currentUserId, new TrustScoreHelper.TrustScoreCallback() {
                @Override
                public void onScoreCalculated(double trustScore, int totalReports, int approvedReports) {
                    Log.d("ReportIncident", "Trust score updated: " + trustScore + "% (Total: " + totalReports + ", Approved: " + approvedReports + ")");
                }

                @Override
                public void onError(String error) {
                    Log.e("ReportIncident", "Failed to update trust score: " + error);
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
                    ImageContentAnalyzer.analyzeImage(ReportIncident.this, imageUri,
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
                                        runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                                                "Content is safe. Verifying image...",
                                                Toast.LENGTH_SHORT).show());
                                        performReverseImageSearch(imageUri, reportId);
                                    }
                                }

                                @Override
                                public void onContentCheckFailed(String error) {
                                    Log.e("IMAGE_SCAN", "Image scan failed: " + error);
                                    currentScanResults.put("imageScanError", error);
                                    uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Image scan failed: " + error);
                                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                                            "Image verification failed. Uploading anyway.",
                                            Toast.LENGTH_SHORT).show());
                                    uploadImageToStorage(reportId);
                                }
                            });
                } else {
                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
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
                    ImageContentAnalyzer.analyzeImage(ReportIncident.this, imageUri,
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
                                    uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Text scan: " + error + ", Image scan: " + error);
                                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                                            "Content verification failed completely. Report blocked.",
                                            Toast.LENGTH_LONG).show());
                                    updateTrustScore(reportId, "FAILED", "SCAN_ERROR");
                                    finishToFrontpage();
                                }
                            });
                } else {
                    uploadScanResultToFirebase(reportId, "FAILED", "SCAN_ERROR", "Text scan failed: " + error);
                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                            "Content verification failed. Report blocked.",
                            Toast.LENGTH_LONG).show());
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

                            Log.d("ReportIncident", "Reverse search - Type: " + result.resultType +
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
                        Toast.makeText(ReportIncident.this,
                                "Image verification incomplete. Report will be reviewed.",
                                Toast.LENGTH_LONG).show();
                        performAIDetection(imageUri, reportId, debugInfo, null, null);
                    }
                });
    }

    private void performAIDetection(Uri imageUri, String reportId, String debugInfo,
                                    List<String> tags, String caption) {
        if (!aiDetector.isReady()) {
            Log.w("ReportIncident", "AI detector not ready, skipping AI check");
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

                    Log.d("ReportIncident", "AI Detection Result: " + result.getFormattedResult());

                    if (result.isAboveThreshold()) {
                        String strikeReason = String.format(Locale.getDefault(),
                                "AI-generated image detected (Confidence: %.1f%%, Threshold: %.1f%%)",
                                result.confidence * 100, aiDetector.getConfidenceThreshold() * 100);
                        uploadScanResultToFirebase(reportId, "BLOCKED", "REJECTED_AI", strikeReason);
                        handleInappropriateContent(3, strikeReason, imageUri,
                                getCurrentDescription(), result.rawResponse, reportId);
                        updateTrustScore(reportId, "BLOCKED", "REJECTED_AI");
                        finishToFrontpage();
                    } else if (result.isAIGenerated && result.confidence > aiDetector.getConfidenceThreshold() - 0.1) {
                        Log.w("ReportIncident", "Possible AI image below threshold: " + result.confidence);
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
                    Log.e("ReportIncident", "AI detection failed: " + error);
                    currentScanResults.put("aiDetectionError", error);
                    uploadScanResultToFirebase(reportId, "WARNING", "AI_CHECK_FAILED", error);
                    Toast.makeText(ReportIncident.this,
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

        runOnUiThread(() -> Toast.makeText(ReportIncident.this, message, Toast.LENGTH_LONG).show());

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
                Log.e("ReportIncident", "Image upload failed: " + error);
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
                        Toast.makeText(ReportIncident.this, "You have exceeded the maximum allowed strikes.", Toast.LENGTH_LONG).show();
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
            ImageUploader.uploadImage(ReportIncident.this, imageInQuestion, new ImageUploader.UploadCallback() {
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
        } else {
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
        if (selectedCategory.isEmpty()) {
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
                        new AlertDialog.Builder(ReportIncident.this)
                                .setTitle("Submission Blocked")
                                .setMessage("You have been deemed an unsafe user. You cannot submit a report.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
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

                                runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                                        "Report submitted successfully!", Toast.LENGTH_SHORT).show());

                                finishToFrontpage();
                            })
                            .addOnFailureListener(e -> {
                                uploadScanResultToFirebase(reportId, "FAILED", "DATABASE_ERROR", e.getMessage());
                                updateTrustScore(reportId, "FAILED", "DATABASE_ERROR");
                                logActivity("Submit Report", "Report Submission Failed", currentUserId, "Failure");

                                runOnUiThread(() -> Toast.makeText(ReportIncident.this,
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

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_ri);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainProgressDialog != null && mainProgressDialog.isShowing()) {
            mainProgressDialog.dismiss();
        }
    }
}
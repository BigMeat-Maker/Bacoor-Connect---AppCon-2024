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
import com.example.bacoorconnect.Helpers.AdjustLocationFragment;
import com.example.bacoorconnect.Helpers.CategoryVerifier;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.example.bacoorconnect.Helpers.ImageUploader;
import com.example.bacoorconnect.Helpers.TextContentAnalyzer;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.ReverseImageSearch;
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

public class EditReport extends AppCompatActivity {

    private ProgressDialog verificationProgress;
    private TextView locationText;
    private double lat = 14.4597;
    private double lon = 120.9333;
    private TextView changeMapLocation;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editpost);

        initializeViews();
        setupImagePicker();
        setupButtonListeners();
        loadReportDetails();

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

        changeMapLocation = findViewById(R.id.ChangeMapLocation);

        userUploadedImage1 = findViewById(R.id.Useruploadedimage1);

        reportId = getIntent().getStringExtra("reportId");
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

        changeMapLocation.setOnClickListener(v -> openLocationAdjuster());
    }

    private void openLocationAdjuster() {
        AdjustLocationFragment fragment = new AdjustLocationFragment();
        Bundle bundle = new Bundle();
        bundle.putDouble("lat", lat);
        bundle.putDouble("lon", lon);
        fragment.setArguments(bundle);

        getSupportFragmentManager().setFragmentResultListener("locationResult", this,
                (requestKey, result) -> {
                    if (requestKey.equals("locationResult")) {
                        double newLat = result.getDouble("lat", lat);
                        double newLon = result.getDouble("lon", lon);
                        String locationDetails = result.getString("locationDetails", "");

                        lat = newLat;
                        lon = newLon;

                        if (locationDetails != null && !locationDetails.isEmpty()) {
                            locationText.setText(locationDetails);
                        } else {
                            String locationStr = String.format(Locale.getDefault(),
                                    "Lat: %.6f, Lon: %.6f", lat, lon);
                            locationText.setText(locationStr);
                        }

                        Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
                    }
                });

        fragment.show(getSupportFragmentManager(), fragment.getTag());
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

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        File imageFile = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        return imageFile;
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
                    Double firebaseLon = snapshot.child("longitude").getValue(Double.class);

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

        contentChecksPassed = false;
        verificationProgress.setMessage("Verifying content...");
        verificationProgress.show();

        TextContentAnalyzer.analyzeText(this, updatedDescription,
                new TextContentAnalyzer.TextAnalysisCallback() {
                    @Override
                    public void onTextContentChecked(boolean isSafe, String debugJson) {
                        if (!isSafe) {
                            verificationProgress.dismiss();
                            handleContentViolation("Inappropriate text content",
                                    null, updatedDescription, debugJson);
                            return;
                        }

                        if (isImageChanged && selectedImageUri != null) {
                            verifyImageContent(updatedDescription, selectedCategory);
                        } else {
                            verificationProgress.dismiss();
                            contentChecksPassed = true;
                            proceedWithUpdate(updatedDescription, selectedCategory, originalImageUrl);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        verificationProgress.dismiss();
                        Toast.makeText(EditReport.this,
                                "Content verification service unavailable. Please try again later.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Text analysis failed", error);
                    }
                });
    }

    private void verifyImageContent(String description, String category) {
        ImageContentAnalyzer.analyzeImage(EditReport.this, selectedImageUri,
                new ImageContentAnalyzer.ImageAnalysisCallback() {
                    @Override
                    public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                        if (isRacy) {
                            handleContentViolation("Inappropriate image content",
                                    selectedImageUri, description, debugJson);
                        } else {
                            performReverseImageSearch(description, category);
                        }
                    }

                    @Override
                    public void onContentCheckFailed(String error) {
                        Toast.makeText(EditReport.this,
                                "Image verification service unavailable. Cannot update report.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Image analysis failed", error);
                    }
                });
    }

    private void performReverseImageSearch(String description, String category) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Verifying image...");
        progress.setCancelable(false);
        progress.show();

        String apiKey = "AIzaSyC9Fox3bylVocReIelel79lUzEkkR0smhU";
        String cseId = "257c747f0be954590";

        ReverseImageSearch.searchImage(EditReport.this, selectedImageUri, apiKey, cseId,
                new ReverseImageSearch.SearchCallback() {
                    @Override
                    public void onSearchComplete(boolean isSuspicious, String debugInfo) {
                        progress.dismiss();

                        if (isSuspicious) {
                            handleContentViolation("Suspicious image content",
                                    selectedImageUri, description, debugInfo);
                        } else {
                            verifyImageCategory(description, category);
                        }
                    }

                    @Override
                    public void onSearchFailed(String error) {
                        progress.dismiss();
                        Toast.makeText(EditReport.this,
                                "Image verification service unavailable. Cannot update report.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Reverse image search failed", error);
                    }
                });
    }

    private void verifyImageCategory(String description, String category) {
        CategoryVerifier.verifyImageCategory(EditReport.this, selectedImageUri, category,
                new CategoryVerifier.VerificationCallback() {
                    @Override
                    public void onCategoryVerified(boolean matchesCategory, List<String> tags, String caption) {
                        if (matchesCategory) {
                            contentChecksPassed = true;
                            uploadImageAndUpdateReport(description, category);
                        } else {
                            String reason = String.format("Image doesn't match %s category. Detected: %s",
                                    category, tags != null ? tags.toString() : "unknown");
                            handleContentViolation(reason, selectedImageUri, description,
                                    "Tags: " + tags + ", Caption: " + caption);
                        }
                    }

                    @Override
                    public void onVerificationFailed(String error) {
                        Toast.makeText(EditReport.this,
                                "Category verification service unavailable. Cannot update report.",
                                Toast.LENGTH_LONG).show();
                        logFailedVerification("Category verification failed", error);
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

        uploadScanResultToFirebase(debugInfo);
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

        if (isImageChanged) {
            updates.put("imageUrl", imageUrl != null ? imageUrl : "");
        }

        reportRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Report updated successfully.", Toast.LENGTH_SHORT).show();
                    logActivity("Report updated", reportId);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update report.", Toast.LENGTH_SHORT).show();
                    logActivity("Failed to update report: " + e.getMessage(), reportId);
                });
    }

    private void uploadScanResultToFirebase(String debugJson) {
        String logId = FirebaseDatabase.getInstance().getReference("ScanLogs").push().getKey();
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", currentUserId);
        log.put("scanResult", debugJson);
        log.put("timestamp", System.currentTimeMillis());
        log.put("reportId", reportId);

        if (logId != null) {
            FirebaseDatabase.getInstance().getReference("ScanLogs").child(logId).setValue(log);
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
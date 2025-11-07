package com.example.bacoorconnect.Report;

import android.app.ProgressDialog;
import android.content.Intent;
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

import com.example.bacoorconnect.Helpers.AdjustLocationFragment;
import com.example.bacoorconnect.General.BottomNavHelper;
import com.example.bacoorconnect.Helpers.CategoryVerifier;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.example.bacoorconnect.Helpers.ImageUploader;
import com.example.bacoorconnect.General.NavigationHandler;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.General.NotificationCenter;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.ReverseImageSearch;
import com.example.bacoorconnect.Helpers.TextContentAnalyzer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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

public class ReportIncident extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private Uri imageUri;
    private String selectedCategory = "";
    private RadioButton preciseRadioButton, generalRadioButton;
    private EditText descriptionEditText;
    private TextView locationText;
    private ImageView selectImageButton, menuIcon;
    private NavigationView navigationView;
    private ImageView imagePreview;
    private double lon;
    private double lat;
    private BottomNavigationView bottomNavigationView;
    private ImageView DashNotif;
    private DatabaseReference auditRef;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);

        initializeViews();
        setupNavigation();
        setupCategorySelection();
        setupLocationHandling();
        setupImageSelection();
        setupSubmitButton();

        handleIntentExtras();
    }

    private void initializeViews() {
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        preciseRadioButton = findViewById(R.id.Precise);
        generalRadioButton = findViewById(R.id.General);
        descriptionEditText = findViewById(R.id.description_edit_text);
        locationText = findViewById(R.id.location_text_view);
        imagePreview = findViewById(R.id.image_preview);
        selectImageButton = findViewById(R.id.select_image_button);
        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        DashNotif = findViewById(R.id.notification);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }

    private void setupNavigation() {
        NavigationHeader.setupNavigationHeader(this, navigationView);
        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            if (isGuest && (item.getItemId() == R.id.nav_history || item.getItemId() == R.id.nav_profile)) {
                Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                return false;
            }
            NavigationHandler navigationHandler = new NavigationHandler(this, drawerLayout);
            navigationHandler.handleMenuSelection(item);
            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        DashNotif.setOnClickListener(v -> {
            startActivity(new Intent(ReportIncident.this, NotificationCenter.class));
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.Nav_RI);
    }

    private void setupCategorySelection() {
        findViewById(R.id.Accident).setOnClickListener(v -> setSelectedCategory("accident", (ImageView) v));
        findViewById(R.id.Fire).setOnClickListener(v -> setSelectedCategory("fire", (ImageView) v));
        findViewById(R.id.Traffic).setOnClickListener(v -> setSelectedCategory("traffic", (ImageView) v));
        findViewById(R.id.NaturalDisaster).setOnClickListener(v -> setSelectedCategory("naturaldisaster", (ImageView) v));
    }

    private void setupLocationHandling() {
        getSupportFragmentManager().setFragmentResultListener("locationResult", this, (requestKey, bundle) -> {
            String locationDetails = bundle.getString("locationDetails");
            lat = bundle.getDouble("lat", lat);
            lon = bundle.getDouble("lon", lon);

            if (locationDetails != null) {
                locationText.setText(locationDetails);
            } else {
                locationText.setText("Location not found");
                runOnUiThread(() -> Toast.makeText(this, "Failed to get location details.", Toast.LENGTH_SHORT).show());
            }
        });

        findViewById(R.id.changelocation).setOnClickListener(v -> openLocationAdjuster());
    }

    private void setupImageSelection() {
        selectImageButton.setOnClickListener(v -> openFileChooser());
    }

    private void setupSubmitButton() {
        findViewById(R.id.submit_report_button).setOnClickListener(v -> {
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
                    }
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

            if (location != null && locationText != null) {
                locationText.setText(location);
            }
        }
    }

    private void openLocationAdjuster() {
        AdjustLocationFragment fragment = new AdjustLocationFragment();
        Bundle bundle = new Bundle();
        bundle.putDouble("lat", lat);
        bundle.putDouble("lon", lon);
        fragment.setArguments(bundle);
        fragment.show(getSupportFragmentManager(), fragment.getTag());
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

    private void uploadImageAndSubmitReport(String reportId) {
        String description = descriptionEditText.getText().toString();


        TextContentAnalyzer.analyzeText(this, description, new TextContentAnalyzer.TextAnalysisCallback() {
            @Override
            public void onTextContentChecked(boolean isSafe, String debugJson) {
                if (!isSafe) {
                    handleInappropriateContent(1, "Inappropriate text content", null, description, debugJson);
                    return;
                }

                if (imageUri != null) {

                    ImageContentAnalyzer.analyzeImage(ReportIncident.this, imageUri,
                            new ImageContentAnalyzer.ImageAnalysisCallback() {
                                @Override
                                public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                    if (isRacy) {
                                        handleInappropriateContent(2, "Inappropriate image content", imageUri, description, debugJson);
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
                if (imageUri != null) {
                    ImageContentAnalyzer.analyzeImage(ReportIncident.this, imageUri,
                            new ImageContentAnalyzer.ImageAnalysisCallback() {

                                @Override
                                public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                    if (isRacy) {
                                        handleInappropriateContent(2, "Inappropriate image content", imageUri, description, debugJson);
                                    } else {
                                        performReverseImageSearch(imageUri, reportId);
                                    }
                                }

                                @Override
                                public void onContentCheckFailed(String error) {
                                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                                            "Content verification failed completely. Report blocked.",
                                            Toast.LENGTH_LONG).show());
                                    uploadScanResultToFirebase("Text scan: " + error + ", Image scan: " + error);
                                }
                            });
                } else {
                    runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                            "Content verification failed. Report blocked.",
                            Toast.LENGTH_LONG).show());
                    uploadScanResultToFirebase("Text scan failed: " + error);
                }
            }
        });
    }

    private void performReverseImageSearch(Uri imageUri, String reportId) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Verifying image...");
        progress.setCancelable(false);
        progress.show();

        String apiKey = "AIzaSyC9Fox3bylVocReIelel79lUzEkkR0smhU";
        String cseId = "257c747f0be954590";

        ReverseImageSearch.searchImage(this, imageUri, apiKey, cseId,
                new ReverseImageSearch.SearchCallback() {
                    @Override
                    public void onSearchComplete(boolean isSuspicious, String debugInfo) {
                        progress.dismiss();

                        if (isSuspicious) {
                            handleInappropriateContent(3, "Suspicious image content",
                                    imageUri, getCurrentDescription(), debugInfo);
                        } else {
                            verifyImageCategory(imageUri, reportId, debugInfo);
                        }
                    }

                    @Override
                    public void onSearchFailed(String error) {
                        progress.dismiss();
                        Log.e("ReverseImageSearch", "Search failed: " + error);
                        verifyImageCategory(imageUri, reportId, "Search failed: " + error);
                    }
                });
    }

    private String getCurrentDescription() {
        return descriptionEditText.getText().toString().trim();
    }

    private void verifyImageCategory(Uri imageUri, String reportId, String debugInfo) {
        CategoryVerifier.verifyImageCategory(this, imageUri, selectedCategory,
                new CategoryVerifier.VerificationCallback() {
                    @Override
                    public void onCategoryVerified(boolean matchesCategory,
                                                   List<String> tags, String caption) {
                        if (matchesCategory) {
                            uploadImageToStorage(reportId);
                        } else {
                            String strikeReason = String.format(
                                    "Category mismatch. Expected %s, found tags: %s",
                                    selectedCategory, tags);
                            handleInappropriateContent(3, strikeReason,
                                    imageUri, caption, debugInfo);
                        }
                    }

                    @Override
                    public void onVerificationFailed(String error) {
                        Log.e("CategoryCheck", "Verification failed: " + error);
                        Toast.makeText(ReportIncident.this,
                                "Image verification incomplete. Report will be reviewed.",
                                Toast.LENGTH_LONG).show();
                        uploadImageToStorage(reportId);
                    }
                });
    }

    private void handleInappropriateContent(int strikeCount, String reason, Uri imageUri, String text, String debugJson) {
        runOnUiThread(() -> Toast.makeText(ReportIncident.this,
                "Inappropriate content detected!",
                Toast.LENGTH_LONG).show());
        addStrikeToUser(strikeCount, reason, imageUri, text);
        uploadScanResultToFirebase(debugJson);
    }

    private void uploadImageToStorage(String reportId) {

        ImageUploader.uploadImage(this, imageUri, new ImageUploader.UploadCallback() {
            @Override
            public void onUploadSuccess(String imageUrl) {
                submitReport(reportId, imageUrl);
            }

            @Override
            public void onUploadFailed(String error) {
                Log.e("ReportIncident", "Image upload failed: " + error);
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

    private void uploadScanResultToFirebase(String debugJson) {
        String userId = getCurrentUserID();
        String logId = FirebaseDatabase.getInstance().getReference("ScanLogs").push().getKey();
        HashMap<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("scanResult", debugJson);
        log.put("timestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference("ScanLogs").child(logId).setValue(log);
    }

    private void submitReport(String reportId, String imageUrl) {
        if (selectedCategory.isEmpty() && !preciseRadioButton.isChecked() && !generalRadioButton.isChecked()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();});
            return;
        }

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

                if (imageUrl != null) {
                    reportData.put("imageUrl", imageUrl);
                }

                DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("Report");

                if (reportId != null) {
                    reportsRef.child(reportId).setValue(reportData)
                            .addOnSuccessListener(aVoid -> {
                                logActivity("Submit Report", "Report Submitted", currentUserId, "Success");

                                Intent resultIntent = new Intent();
                                setResult(RESULT_OK, resultIntent);

                                finish();
                            })
                            .addOnFailureListener(e -> {
                                logActivity("Submit Report", "Report Submission Failed", currentUserId, "Failure");
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
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.Nav_RI);
        }
    }

}
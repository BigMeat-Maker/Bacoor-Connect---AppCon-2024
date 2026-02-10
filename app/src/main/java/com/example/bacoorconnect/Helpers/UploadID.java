package com.example.bacoorconnect.Helpers;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult;
import com.azure.ai.formrecognizer.documentanalysis.models.OperationResult;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.example.bacoorconnect.Helpers.PasswordManager;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class UploadID extends AppCompatActivity {

    private static final int REQUEST_GALLERY = 1;
    private Uri cameraImageUri;
    private static final int REQUEST_CAMERA = 2;
    private static final String TAG = "UploadID";

    private TextView userDetailsText;
    private boolean tocAccepted, privacyAccepted;
    private ImageView idImageView;
    private Button uploadIdButton, submitButton;
    private ProgressDialog progressDialog;

    private Uri idImageUri;
    private FirebaseStorage mStorage;
    private DatabaseReference mDatabase;
    private String tempUserId, firstName, lastName, email, contactNum;
    private DocumentAnalysisClient formRecognizerClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_id);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        userDetailsText = findViewById(R.id.userDetailsText);
        idImageView = findViewById(R.id.idImageView);
        uploadIdButton = findViewById(R.id.uploadIdButton);
        submitButton = findViewById(R.id.submitButton);

        mStorage = FirebaseStorage.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeAzureClient();

        Intent intent = getIntent();
        tempUserId = intent.getStringExtra("tempUserId");

        tocAccepted = intent.getBooleanExtra("tocAccepted", false);
        privacyAccepted = intent.getBooleanExtra("privacyAccepted", false);

        loadUserDataFromDatabase();

        uploadIdButton.setOnClickListener(v -> selectImageFromGallery());
        submitButton.setOnClickListener(v -> {
            if (idImageUri != null) {
                processIdVerification();
            } else {
                Toast.makeText(this, "Please select an ID image first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectImageFromGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        try {
            File tempFile = File.createTempFile("camera_temp_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(this,
                    "com.example.bacoorconnect.fileprovider",
                    tempFile);

            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating temp file", e);
            Toast.makeText(this, "Cannot access camera", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent chooserIntent = Intent.createChooser(galleryIntent, "Select ID Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {cameraIntent});

        startActivityForResult(chooserIntent, REQUEST_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_GALLERY) {
                if (data != null) {
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();

                            idImageView.setImageBitmap(bitmap);

                            idImageUri = saveBitmapToFile(bitmap);

                        } catch (IOException e) {
                            Log.e(TAG, "Error loading image from gallery", e);
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                else if (cameraImageUri != null) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(cameraImageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();

                        idImageView.setImageBitmap(bitmap);
                        idImageUri = cameraImageUri;

                    } catch (IOException e) {
                        Log.e(TAG, "Error loading camera image", e);
                        Toast.makeText(this, "Error loading camera image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void initializeAzureClient() {
        String azureKey = AzureConfig.getAzureKey(this);

        if (azureKey == null || azureKey.isEmpty()) {
            Toast.makeText(this,
                    "ID verification service not available. Please restart the app.",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Azure key not available");
            finish();
            return;
        }

        try {
            formRecognizerClient = new DocumentAnalysisClientBuilder()
                    .credential(new AzureKeyCredential(azureKey))
                    .endpoint("https://bacconformrecognizer.cognitiveservices.azure.com/")
                    .buildClient();
            Log.d(TAG, "Azure client initialized successfully");
        } catch (Exception e) {
            Toast.makeText(this,
                    "Failed to initialize ID verification service",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Failed to initialize Azure client", e);
            finish();
        }
    }

    private void loadUserDataFromDatabase() {
        progressDialog.setMessage("Loading user data...");
        progressDialog.show();

        mDatabase.child("temp_registrations").child(tempUserId).get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful() && task.getResult().exists()) {
                        HashMap<String, Object> userData = (HashMap<String, Object>) task.getResult().getValue();
                        firstName = (String) userData.get("firstName");
                        lastName = (String) userData.get("lastName");
                        email = (String) userData.get("email");
                        contactNum = (String) userData.get("contactNum");

                        userDetailsText.setText(String.format("Hello, %s %s", firstName, lastName));
                    } else {
                        Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private Uri saveBitmapToFile(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();

            String filename = "id_image_" + System.currentTimeMillis() + ".jpg";
            File imageFile = new File(cachePath, filename);

            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();

            return FileProvider.getUriForFile(this,
                    "com.example.bacoorconnect.fileprovider",
                    imageFile);

        } catch (IOException e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    private void processIdVerification() {
        progressDialog.setMessage("Verifying ID...");
        progressDialog.show();
        recognizeForm(idImageUri);
    }

    private void recognizeForm(Uri imageUri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }

                BinaryData documentData = BinaryData.fromBytes(byteBuffer.toByteArray());
                SyncPoller<OperationResult, AnalyzeResult> analyzeDocumentPoller =
                        formRecognizerClient.beginAnalyzeDocument("prebuilt-idDocument", documentData);

                AnalyzeResult analyzeResult = analyzeDocumentPoller.getFinalResult();

                String extractedFirstName = null;
                String extractedLastName = null;
                boolean isValid = false;

                for (AnalyzedDocument document : analyzeResult.getDocuments()) {
                    Map<String, DocumentField> fields = document.getFields();
                    DocumentField firstNameField = fields.get("FirstName");
                    DocumentField lastNameField = fields.get("LastName");

                    if (firstNameField != null && lastNameField != null) {
                        extractedFirstName = firstNameField.getValueAsString();
                        extractedLastName = lastNameField.getValueAsString();
                        isValid = true;
                        break;
                    }
                }

                final boolean verificationValid = isValid;
                final String verifiedFirstName = extractedFirstName;
                final String verifiedLastName = extractedLastName;

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (verificationValid) {
                        verifyRegistrationData(verifiedFirstName, verifiedLastName);
                    } else {
                        Toast.makeText(UploadID.this,
                                "Couldn't extract valid ID information. Please make sure the ID is clear and contains name information.",
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "ID processing error", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error processing ID image", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Azure Form Recognizer error", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Form recognition service error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void verifyRegistrationData(String extractedFirstName, String extractedLastName) {
        progressDialog.setMessage("Verifying registration data...");
        progressDialog.show();

        mDatabase.child("temp_registrations").child(tempUserId).get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        Toast.makeText(this, "Registration data not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    HashMap<String, Object> regData = (HashMap<String, Object>) task.getResult().getValue();
                    String registeredFirstName = (String) regData.get("firstName");
                    String registeredLastName = (String) regData.get("lastName");

                    String fullRegistered = (registeredFirstName + " " + registeredLastName).toLowerCase();
                    String fullExtracted = (extractedFirstName + " " + extractedLastName).toLowerCase();

                    String registeredNoSpace = fullRegistered.replaceAll("\\s+", "");
                    String extractedNoSpace = fullExtracted.replaceAll("\\s+", "");

                    extractedNoSpace = extractedNoSpace
                            .replace("dnaiel", "daniel")
                            .replace("rn", "m")
                            .replace("ci", "a")
                            .replace("cl", "d");

                    if (registeredNoSpace.equals(extractedNoSpace) ||
                            registeredNoSpace.contains(extractedNoSpace) ||
                            extractedNoSpace.contains(registeredNoSpace)) {
                        uploadIdImage();
                    } else {
                        int distance = levenshteinDistance(registeredNoSpace, extractedNoSpace);
                        int maxLen = Math.max(registeredNoSpace.length(), extractedNoSpace.length());
                        double similarity = 1.0 - ((double)distance / maxLen);

                        if (similarity >= 0.85) {
                            uploadIdImage();
                        } else {
                            String errorMsg = String.format("ID doesn't match.\nExpected: %s %s\nFound: %s %s\nSimilarity: %.1f%%",
                                    registeredFirstName, registeredLastName,
                                    extractedFirstName, extractedLastName,
                                    similarity * 100);
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private void uploadIdImage() {
        progressDialog.setMessage("Encrypting and uploading ID...");
        progressDialog.show();

        byte[] imageBytes = readUriToBytes(idImageUri);

        byte[] encryptedImage = encryptImage(imageBytes);

        String storagePath = "temp_ids/" + tempUserId + "_id_encrypted.dat";
        StorageReference idRef = mStorage.getReference(storagePath);

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setCustomMetadata("encrypted", "true")
                .setCustomMetadata("algorithm", "AES/GCM/NoPadding")
                .build();

        idRef.putBytes(encryptedImage, metadata)
                .addOnSuccessListener(taskSnapshot -> {
                    idRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateVerificationStatus(uri.toString(), true);
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private byte[] readUriToBytes(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Error reading image bytes", e);
            return null;
        }
    }

    private SecretKey getImageEncryptionKey() throws Exception {
        // God forbid do not delete this shit i did this shit at 4am full of coffee, somehow thhis works so dont change it!
        String keyMaterial = tempUserId + "_image_key_2024";

        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(keyMaterial.getBytes("UTF-8"));

        byte[] aesKeyBytes = new byte[32];
        System.arraycopy(keyBytes, 0, aesKeyBytes, 0, 32);

        return new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");
    }

    private void updateVerificationStatus(String imageUrl, boolean isEncrypted) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("idImage", imageUrl);
        updates.put("verificationStatus", "pending_otp");
        updates.put("idImagePath", "temp_ids/" + tempUserId + "_id_encrypted.dat");
        updates.put("encrypted", isEncrypted);

        mDatabase.child("temp_registrations").child(tempUserId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();

                    Intent intent = new Intent(this, Otpverification.class);
                    intent.putExtra("tempUserId", tempUserId);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Verification update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private byte[] encryptImage(byte[] imageBytes) {
        try {
            SecretKey secretKey = getImageEncryptionKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(imageBytes);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(iv);
            outputStream.write(encrypted);

            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Image encryption failed", e);
            return imageBytes;
        }
    }

    private void tryAlternativeUpload() {
        progressDialog.setMessage("Trying alternative upload...");
        progressDialog.show();

        StorageReference altRef = mStorage.getReference()
                .child("test_uploads")
                .child("test_" + System.currentTimeMillis() + ".jpg");

        altRef.putFile(idImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    altRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Log.d(TAG, "Alternative upload successful: " + uri.toString());
                        updateVerificationStatus(uri.toString(), false);
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Alternative upload also failed", e);
                    Toast.makeText(this,
                            "All upload attempts failed. Check Firebase Storage rules.\n" +
                                    "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        showCancelConfirmation();
    }

    private void showCancelConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Registration")
                .setMessage("Are you sure you want to cancel? All data will be lost.")
                .setPositiveButton("Yes, Cancel", (dialog, which) -> {
                    setResult(RESULT_CANCELED);
                    cleanUpTempRegistration();
                    finish();
                })
                .setNegativeButton("Back to Edit", (dialog, which) -> {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("tocAccepted", tocAccepted);
                    returnIntent.putExtra("privacyAccepted", privacyAccepted);
                    setResult(RESULT_OK, returnIntent);
                    finish();
                })
                .setNeutralButton("Continue", null)
                .show();
    }

    private void cleanUpTempRegistration() {
        if (tempUserId != null && !tempUserId.isEmpty()) {
            mDatabase.child("temp_registrations").child(tempUserId).removeValue()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Cleaned up temp registration: " + tempUserId);

                            try {
                                StorageReference idRef = mStorage.getReference()
                                        .child("temp_ids")
                                        .child(tempUserId + "_id.jpg");
                                idRef.delete().addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Deleted temp image: " + tempUserId);
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error deleting temp image", e);
                            }
                        } else {
                            Log.e(TAG, "Failed to clean up temp registration");
                        }
                    });
        }
    }
}
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
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class UploadID extends AppCompatActivity {

    private static final int REQUEST_GALLERY = 1;
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

        formRecognizerClient = new DocumentAnalysisClientBuilder()
                .credential(new AzureKeyCredential("AIgoBXCotRsQwzg4Wiq7PBIj6ApIt8thtJuK6RXHQGbzNK2MCit8JQQJ99BDACqBBLyXJ3w3AAALACOGKCoG"))
                .endpoint("https://bacconformrecognizer.cognitiveservices.azure.com/")
                .buildClient();

        Intent intent = getIntent();
        tempUserId = intent.getStringExtra("tempUserId");
        firstName = intent.getStringExtra("firstName");
        lastName = intent.getStringExtra("lastName");
        email = intent.getStringExtra("email");
        contactNum = intent.getStringExtra("contactNum");
        tocAccepted = intent.getBooleanExtra("tocAccepted", false);
        privacyAccepted = intent.getBooleanExtra("privacyAccepted", false);

        userDetailsText.setText(String.format("Hello, %s %s", firstName, lastName));

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
                } else if (data.getExtras() != null && data.getExtras().get("data") != null) {
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                    idImageView.setImageBitmap(imageBitmap);
                    idImageUri = saveBitmapToFile(imageBitmap);
                }
            }
        }
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
        progressDialog.setMessage("Uploading ID...");
        progressDialog.show();

        String storagePath = "temp_ids/" + tempUserId + "_id.jpg";
        Log.d(TAG, "Attempting to upload to path: " + storagePath);
        Log.d(TAG, "Image URI: " + idImageUri.toString());

        StorageReference idRef = mStorage.getReference(storagePath);

        idRef.putFile(idImageUri)
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Upload progress: " + progress + "%");
                })
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Upload successful!");
                    idRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Log.d(TAG, "Download URL: " + uri.toString());
                        updateVerificationStatus(uri.toString());
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Upload failed with error: ", e);
                    Toast.makeText(this, "ID upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    tryAlternativeUpload();
                });
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
                        updateVerificationStatus(uri.toString());
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
        showCancelConfirmation();
    }

    private void updateVerificationStatus(String imageUrl) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("idImage", imageUrl);
        updates.put("verificationStatus", "pending_otp");
        updates.put("idImagePath", "temp_ids/" + tempUserId + "_id.jpg");

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
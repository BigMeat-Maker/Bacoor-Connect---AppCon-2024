package com.example.bacoorconnect.Helpers;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class UploadID extends AppCompatActivity {

    private static final int REQUEST_ID_CAPTURE = 1;
    private static final String TAG = "UploadID";

    private TextView userDetailsText;
    private ImageView idImageView;
    private Button uploadIdButton, submitButton;
    private ProgressDialog progressDialog;

    private Uri idImageUri;
    private FirebaseStorage mStorage;
    private DatabaseReference mDatabase;
    private String userID, firstName, lastName, email, address;
    private DocumentAnalysisClient formRecognizerClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_id);

        // Initialize ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        // Initialize UI components
        userDetailsText = findViewById(R.id.userDetailsText);
        idImageView = findViewById(R.id.idImageView);
        uploadIdButton = findViewById(R.id.uploadIdButton);
        submitButton = findViewById(R.id.submitButton);

        // Initialize Firebase components
        mStorage = FirebaseStorage.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize Azure Form Recognizer
        formRecognizerClient = new DocumentAnalysisClientBuilder()
                .credential(new AzureKeyCredential("AIgoBXCotRsQwzg4Wiq7PBIj6ApIt8thtJuK6RXHQGbzNK2MCit8JQQJ99BDACqBBLyXJ3w3AAALACOGKCoG"))
                .endpoint("https://bacconformrecognizer.cognitiveservices.azure.com/")
                .buildClient();

        // Get user data from intent
        Intent intent = getIntent();
        firstName = intent.getStringExtra("firstName");
        lastName = intent.getStringExtra("lastName");
        email = intent.getStringExtra("email");
        address = intent.getStringExtra("address");

        // Verify current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userID = currentUser.getUid();

        // Set welcome message
        userDetailsText.setText(String.format("Hello, %s %s", firstName, lastName));

        // Button click listeners
        uploadIdButton.setOnClickListener(v -> captureIdImage());
        submitButton.setOnClickListener(v -> {
            if (idImageUri != null) {
                processIdVerification();
            } else {
                Toast.makeText(this, "Please upload ID first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void captureIdImage() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_ID_CAPTURE);
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }

    private void processIdVerification() {
        progressDialog.setMessage("Verifying ID...");
        progressDialog.show();
        recognizeForm(idImageUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_ID_CAPTURE && data != null) {
            Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
            idImageView.setImageBitmap(imageBitmap);
            idImageUri = getImageUri(imageBitmap);
        }
    }

    private Uri getImageUri(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "ID_Image", null);
        return Uri.parse(path);
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

                // Process extracted data
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
                                "Couldn't extract valid ID information",
                                Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "ID processing error", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error processing ID image", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void verifyRegistrationData(String extractedFirstName, String extractedLastName) {
        progressDialog.setMessage("Verifying registration data...");
        progressDialog.show();

        mDatabase.child("temp_registrations").child(userID).get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        Toast.makeText(this, "Registration data not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    HashMap<String, Object> regData = (HashMap<String, Object>) task.getResult().getValue();
                    String registeredFirstName = (String) regData.get("firstName");
                    String registeredLastName = (String) regData.get("lastName");

                    if (extractedFirstName.equalsIgnoreCase(registeredFirstName.trim()) &&
                            extractedLastName.equalsIgnoreCase(registeredLastName.trim())) {
                        uploadIdImage();
                    } else {
                        Toast.makeText(this, "ID doesn't match registration info", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadIdImage() {
        progressDialog.setMessage("Uploading ID...");
        progressDialog.show();

        StorageReference idRef = mStorage.getReference()
                .child("user_ids")
                .child(userID + "_id.jpg");

        idRef.putFile(idImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    idRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateVerificationStatus(uri.toString());
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "ID upload failed", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "ID upload error", e);
                });
    }

    private void updateVerificationStatus(String imageUrl) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("idImage", imageUrl);
        updates.put("verificationStatus", "pending_face_verification");

        updates.put("idImagePath", "user_ids/" + userID + "_id.jpg");

        mDatabase.child("users").child(userID).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    startActivity(new Intent(this, Otpverification.class)
                            .putExtra("userID", userID));
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Verification update failed", Toast.LENGTH_SHORT).show();
                });
    }
}
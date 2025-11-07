package com.example.bacoorconnect.Helpers;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.azure.ai.vision.face.models.*;
import com.azure.ai.vision.face.FaceClient;
import com.azure.ai.vision.face.FaceClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.example.bacoorconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class FaceVerification extends AppCompatActivity {

    private static final int REQUEST_SELFIE_CAPTURE = 2;

    private ImageView selfieImageView;
    private Button uploadSelfieButton, submitButton;

    private Uri selfieImageUri;
    private Bitmap selfieBitmap;

    private FaceClient faceClient;
    private String userId = "sampleUserId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_verification);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        }
        else{Toast.makeText(this,"User is not logged in",Toast.LENGTH_SHORT).show();
            finish();
            return;}


        selfieImageView = findViewById(R.id.selfieImageView);
        uploadSelfieButton = findViewById(R.id.uploadSelfieButton);
        submitButton = findViewById(R.id.submitButton);

        String endpoint = "https://bacconfaceai.cognitiveservices.azure.com/";
        String apiKey = "908TZByLbT3nbvs1tuQleI9ghNCSWUFeNw2zHLFFeI0QQK4I2JdTJQQJ99BDACqBBLyXJ3w3AAAKACOG6C4n";
        faceClient = new FaceClientBuilder()
                .credential(new AzureKeyCredential(apiKey))
                .endpoint(endpoint)
                .buildClient();

        uploadSelfieButton.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(cameraIntent, REQUEST_SELFIE_CAPTURE);
            } else {
                Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show();
            }
        });

        submitButton.setOnClickListener(v -> {
            if (selfieBitmap != null) {
                // detectFaceInSelfie(selfieBitmap); THIS IS THE CORRECT REMOVE THE SECOND ONCE ITS CONFIRMED

                uploadSelfieToFirebase(selfieBitmap);
            } else {
                Toast.makeText(this, "Please upload a selfie", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
            if (requestCode == REQUEST_SELFIE_CAPTURE) {
                selfieImageView.setImageBitmap(imageBitmap);
                selfieBitmap = imageBitmap;
            }
        }
    }

    private void uploadSelfieToFirebase(Bitmap bitmap) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        StorageReference selfieRef = storageRef.child("temp_registrations/" + userId + "/selfieImage.jpg");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        selfieRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    selfieRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        saveSelfieUrlToDatabase(downloadUrl);
                        Toast.makeText(this, "Selfie uploaded successfully", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUploadError", "Failed to upload selfie", e);
                    showErrorDialog("Selfie Upload Error", e.getMessage());
                });
    }

    private void saveSelfieUrlToDatabase(String url) {
        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("temp_registrations")
                .child(userId)
                .child("selfieUrl")
                .setValue(url);
    }


    private void detectFaceInSelfie(Bitmap bitmap) {
        try {
            BinaryData binaryData = BinaryData.fromBytes(bitmapToByteArray(bitmap));
            DetectOptions options = new DetectOptions(
                    FaceDetectionModel.fromString("detection_03"),
                    FaceRecognitionModel.fromString("recognition_04"),
                    true
            );
            List<FaceDetectionResult> results = faceClient.detect(binaryData, options);
            if (!results.isEmpty()) {
                String selfieFaceId = results.get(0).getFaceId();
                uploadSelfieToFirebase(bitmap);
                detectFaceInIdImage(selfieFaceId);
            } else {
                Toast.makeText(this, "No face detected in selfie.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("AzureFaceError", "Error detecting face in selfie", e); // logs full stack trace

            new AlertDialog.Builder(this)
                    .setTitle("Face Detection Error")
                    .setMessage("Something went wrong while processing the selfie:\n\n" + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }
    private void detectFaceInIdImage(String selfieFaceId) {
        StorageReference idImageRef = FirebaseStorage.getInstance().getReference("temp_registrations/" + userId + "/idImage");

        final long ONE_MB = 5 * 1024 * 1024;
        idImageRef.getBytes(ONE_MB).addOnSuccessListener(bytes -> {
            try {
                BinaryData binaryData = BinaryData.fromBytes(bytes);

                DetectOptions options = new DetectOptions(
                        FaceDetectionModel.fromString("detection_03"),
                        FaceRecognitionModel.fromString("recognition_04"),
                        true
                );

                List<FaceDetectionResult> idResults = faceClient.detect(binaryData, options);
                if (!idResults.isEmpty()) {
                    String idFaceId = idResults.get(0).getFaceId();
                    compareFaces(selfieFaceId, idFaceId);
                } else {
                    Toast.makeText(this, "No face detected in ID image.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("AzureFaceError", "Error detecting face in ID image", e);
                showErrorDialog("ID Face Detection Error", e.getMessage());
            }
        }).addOnFailureListener(e -> {
            Log.e("FirebaseDownloadError", "Failed to load ID image", e);
            showErrorDialog("Failed to Load ID Image", e.getMessage());
        });
    }

    private void compareFaces(String selfieFaceId, String idFaceId) {
        try {
            FaceVerificationResult result = faceClient.verifyFaceToFace(selfieFaceId, idFaceId);
            if (result.isIdentical()) {
                Toast.makeText(this, "Face verification successful!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Face verification failed.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("AzureFaceCompareError", "Error comparing faces", e);
            showErrorDialog("Face Comparison Error", e.getMessage());
        }
    }
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message != null ? message : "An unknown error occurred.")
                .setPositiveButton("OK", null)
                .show();
    }

    private byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }
}

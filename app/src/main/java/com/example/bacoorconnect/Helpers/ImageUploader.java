package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

public class ImageUploader {

    public interface UploadCallback {
        void onUploadSuccess(String imageUrl);
        void onUploadFailed(String error);
    }

    public static void uploadImage(Context context, Uri imageUri, UploadCallback callback) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String imageName = "report_images/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference imageRef = storageRef.child(imageName);

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        callback.onUploadSuccess(uri.toString());
                    }).addOnFailureListener(e -> {
                        callback.onUploadFailed("Failed to get download URL");
                    });
                })
                .addOnFailureListener(e -> {
                    callback.onUploadFailed(e.getMessage());
                });
    }
}
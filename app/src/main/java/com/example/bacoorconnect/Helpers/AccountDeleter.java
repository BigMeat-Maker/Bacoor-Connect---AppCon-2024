package com.example.bacoorconnect.Helpers;

import android.content.Context;
import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class AccountDeleter {
    public interface DeletionCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public static void deleteUserAccount(Context context, DeletionCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onFailure("No user logged in");
            return;
        }

        String userId = currentUser.getUid();

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);

        userRef.get().addOnSuccessListener(dataSnapshot -> {
            if (dataSnapshot.exists()) {
                String profileImagePath = getStoragePathFromUrl(dataSnapshot.child("profileImage").getValue(String.class));
                String idImagePath = dataSnapshot.child("idImagePath").getValue(String.class);

                userRef.removeValue().addOnSuccessListener(aVoid -> {
                    deleteStorageFiles(userId, profileImagePath, idImagePath, () -> {
                        deleteAuthAccount(currentUser, callback);
                    });
                }).addOnFailureListener(e -> {
                    callback.onFailure("Failed to delete user data: " + e.getMessage());
                });
            } else {
                deleteAuthAccount(currentUser, callback);
            }
        }).addOnFailureListener(e -> {
            callback.onFailure("Failed to fetch user data: " + e.getMessage());
        });
    }

    private static void deleteStorageFiles(String userId, String profileImagePath, String idImagePath, Runnable onComplete) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference profileRef = profileImagePath != null ?
                storage.getReferenceFromUrl(profileImagePath) :
                storage.getReference("profile_images/" + userId);

        StorageReference idRef = idImagePath != null ?
                storage.getReference(idImagePath) :
                storage.getReference("user_ids/" + userId + "_id.jpg");

        profileRef.delete().addOnFailureListener(e -> {
            if (e.getMessage().contains("Object does not exist")) {
            }
        });

        idRef.delete().addOnFailureListener(e -> {
            if (e.getMessage().contains("Object does not exist")) {
            }
        }).addOnCompleteListener(task -> {
            onComplete.run();
        });
    }

    private static void deleteAuthAccount(FirebaseUser user, DeletionCallback callback) {
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess();
            } else {
                callback.onFailure("Failed to delete auth account: " + task.getException().getMessage());
            }
        });
    }

    private static String getStoragePathFromUrl(String url) {
        if (url == null) return null;
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getLastPathSegment();
            return "gs://" + uri.getHost() + "/" + path;
        } catch (Exception e) {
            return null;
        }
    }
}
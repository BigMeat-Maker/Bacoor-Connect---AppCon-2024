package com.example.bacoorconnect;

import static android.app.PendingIntent.getActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.General.BottomNavHelper;
import com.example.bacoorconnect.General.FrontpageActivity;
import com.example.bacoorconnect.Helpers.AccountDeleter;
import com.example.bacoorconnect.Helpers.ImageContentAnalyzer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.text.InputFilter;
import android.text.Spanned;
import android.text.InputType;

public class UserProfile extends AppCompatActivity {

    private EditText fname, lname, email, contactno;
    private TextView EditPFP, editProfileTitle, UserProfileName, UserProfileEmail;
    private ImageView UserPFP;
    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri imageUri;
    private ImageView editDetailsBtn;
    private Button saveChangesBtn, cancelEditBtn, logoutBtn;
    public boolean isEditing = false;
    private BottomNavigationView bottomNavigationView;
    private String originalEmail, originalPhone, originalImageUrl;
    private LinearLayout editProfileImageLayout, EditContainer;
    private TableLayout userDetailsTable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.userprofile);

        EditContainer = findViewById(R.id.editcontainer);
        UserProfileEmail = findViewById(R.id.email_profile);
        UserProfileName = findViewById(R.id.fname_profile);
        EditPFP = findViewById(R.id.editpfp);
        UserPFP = findViewById(R.id.userpfp);
        fname = findViewById(R.id.fname);
        lname = findViewById(R.id.lname);
        email = findViewById(R.id.email);
        contactno = findViewById(R.id.contactno);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        editProfileTitle = findViewById(R.id.edit_profile_title);
        editProfileImageLayout = findViewById(R.id.edit_profile_image_layout);
        userDetailsTable = findViewById(R.id.user_details_table);

        logoutBtn = findViewById(R.id.logout_btn);
        logoutBtn.setOnClickListener(v -> {
            logoutUser();
        });

        loadProfileImage();
        loadUserDetails();

        if (!canEditProfile()) {
            Toast.makeText(this, "You can only edit your profile once a week", Toast.LENGTH_SHORT).show();
        }

        editDetailsBtn = findViewById(R.id.edituserdetails);
        saveChangesBtn = findViewById(R.id.save_changes_btn);
        cancelEditBtn = findViewById(R.id.cancel_editing_btn);

        editDetailsBtn.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(UserProfile.this, v);
            popupMenu.getMenuInflater().inflate(R.menu.account_more_popup, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    enableEditing();
                    return true;
                } else if (id == R.id.action_delete) {
                    showDeleteConfirmationDialog();
                    return true;
                }
                return false;
            });

            popupMenu.show();
        });

        if (bottomNavigationView != null) {
            BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.nav_profile);
        } else {
            android.util.Log.e("ServicesActivity", "BottomNavigationView not found. Check layout ID.");
        }

    }

    private void logoutUser() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(uid)
                    .child("status")
                    .setValue("offline");
        }

        SharedPreferences preferences = getSharedPreferences("UserProfilePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("keepLoggedIn", false);
        editor.apply();

        SharedPreferences defaultPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor defaultEditor = defaultPrefs.edit();
        defaultEditor.remove("userFirstName");
        defaultEditor.remove("userLastName");
        defaultEditor.remove("userEmail");
        defaultEditor.remove("userPhone");
        defaultEditor.remove("userProfileImage");
        defaultEditor.apply();

        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(UserProfile.this, FrontpageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Deleting account...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        AccountDeleter.deleteUserAccount(this, new AccountDeleter.DeletionCallback() {
            @Override
            public void onSuccess() {
                progressDialog.dismiss();
                Toast.makeText(UserProfile.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();

                // Navigate to FrontpageActivity instead of Login
                Intent intent = new Intent(UserProfile.this, FrontpageActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
            @Override
            public void onFailure(String errorMessage) {
                progressDialog.dismiss();
                Toast.makeText(UserProfile.this, "Failed to delete account: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void validateEmailAndPhone() {
        String newEmail = email.getText().toString().trim();
        String newPhone = contactno.getText().toString().trim();

        if (!isValidEmail(newEmail)) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidPhone(newPhone)) {
            Toast.makeText(this, "Please enter a valid phone number (10–15 digits).", Toast.LENGTH_SHORT).show();
            return;
        }

        String combinedText = newEmail + " " + newPhone;

        Log.d("validateEmailAndPhone", "Combined Text: " + combinedText);

        proceedWithProfileUpdate(newEmail, newPhone);
    }

    private void loadProfileImage() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(FirebaseAuth.getInstance().getUid());

        userRef.child("profileImage").get().addOnSuccessListener(dataSnapshot -> {
            if (dataSnapshot.exists()) {
                String imageUrl = dataSnapshot.getValue(String.class);
                if (imageUrl != null) {
                    originalImageUrl = imageUrl;
                    Glide.with(this).load(imageUrl).circleCrop().into(UserPFP);
                }
            }
        });
    }

    private void loadUserDetails() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(FirebaseAuth.getInstance().getUid());

        userRef.get().addOnSuccessListener(dataSnapshot -> {
            if (dataSnapshot.exists()) {
                String firstName = dataSnapshot.child("firstName").getValue(String.class);
                String lastName = dataSnapshot.child("lastName").getValue(String.class);
                String userEmail = dataSnapshot.child("email").getValue(String.class);

                // Get phone number - check different field names
                String phone = null;

                if (dataSnapshot.hasChild("phone")) {
                    phone = dataSnapshot.child("phone").getValue(String.class);
                } else if (dataSnapshot.hasChild("phoneNumber")) {
                    phone = dataSnapshot.child("phoneNumber").getValue(String.class);
                } else if (dataSnapshot.hasChild("contactNum")) {
                    phone = dataSnapshot.child("contactNum").getValue(String.class);
                } else if (dataSnapshot.hasChild("contactno")) {
                    phone = dataSnapshot.child("contactno").getValue(String.class);
                }

                Log.d("UserProfile", "Phone number from Firebase: " + phone);

                if (firstName != null && !firstName.isEmpty()) fname.setText(firstName);
                if (lastName != null && !lastName.isEmpty()) lname.setText(lastName);
                if (userEmail != null && !userEmail.isEmpty()) email.setText(userEmail);

                if (phone != null && !phone.isEmpty()) {
                    String formattedPhone = formatPhoneNumber(phone);
                    contactno.setText(phone);

                    TextView contactNumberInfo = findViewById(R.id.contactNumberInfo);
                    if (contactNumberInfo != null) {
                        contactNumberInfo.setText(formattedPhone);
                    }
                } else {
                    contactno.setText("");
                    TextView contactNumberInfo = findViewById(R.id.contactNumberInfo);
                    if (contactNumberInfo != null) {
                        contactNumberInfo.setText("No phone number set");
                    }
                }

                if (firstName != null && !firstName.isEmpty()) {
                    if (lastName != null && !lastName.isEmpty()) {
                        UserProfileName.setText(firstName + " " + lastName);
                    } else {
                        UserProfileName.setText(firstName);
                    }
                }

                if (userEmail != null && !userEmail.isEmpty()) {
                    UserProfileEmail.setText(userEmail);
                }

            } else {
                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("UserProfile", "Error loading user details", e);
        });
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        String digitsOnly = phone.replaceAll("[^0-9]", "");

        if (digitsOnly.length() == 11) {
            return digitsOnly.substring(0, 4) + "-" +
                    digitsOnly.substring(4, 7) + "-" +
                    digitsOnly.substring(7);
        } else if (digitsOnly.length() == 10) {
            return digitsOnly.substring(0, 3) + "-" +
                    digitsOnly.substring(3, 6) + "-" +
                    digitsOnly.substring(6);
        } else {
            return phone;
        }
    }


    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            UserPFP.setImageURI(imageUri);
            checkForChanges();
        }
    }

    private void uploadImageToFirebase(Uri filePath) {
        if (filePath != null) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                    .child("profile_images/" + FirebaseAuth.getInstance().getUid());

            storageRef.putFile(filePath)
                    .addOnSuccessListener(taskSnapshot -> {
                        taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(uri -> {
                            saveImageUrlToRealtimeDatabase(uri.toString());
                            saveLastProfileEditTime();
                            finishSave();
                        });
                    })
                    .addOnFailureListener(e -> {
                        // Better error handling
                        if (e instanceof StorageException) {
                            StorageException storageException = (StorageException) e;
                            int errorCode = storageException.getErrorCode();
                            String errorMessage = storageException.getMessage();

                            Log.e("FirebaseUpload", "Error Code: " + errorCode + ", Message: " + errorMessage);

                            if (errorCode == StorageException.ERROR_NOT_AUTHENTICATED) {
                                Toast.makeText(this, "Not authenticated. Please sign in again.", Toast.LENGTH_SHORT).show();
                            } else if (errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                                Toast.makeText(this, "Permission denied. Check security rules.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Upload failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void saveImageUrlToRealtimeDatabase(String imageUrl) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(FirebaseAuth.getInstance().getUid());

        userRef.child("profileImage").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile picture has been updated.", Toast.LENGTH_SHORT).show();
                    logActivity("UserID", "Profile Edit", "Edited Profile", "User Profile", "Success",
                            "User updated profile picture", "Profile Picture");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update image, please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean canEditProfile() {
        SharedPreferences prefs = getSharedPreferences("UserProfilePrefs", MODE_PRIVATE);
        long lastEditTime = prefs.getLong("lastProfileEditTime", 0);

        long currentTimeMillis = System.currentTimeMillis();
        long weekInMillis = 7 * 24 * 60 * 60 * 1000;

        return (currentTimeMillis - lastEditTime) >= weekInMillis;
    }

    private void saveLastProfileEditTime() {
        SharedPreferences prefs = getSharedPreferences("UserProfilePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("lastProfileEditTime", System.currentTimeMillis());
        editor.apply();
    }

    private void enableEditing() {
        isEditing = true;

        // Hide logout button when editing
        logoutBtn.setVisibility(View.GONE);

        // Load fresh data from Firebase when entering edit mode
        refreshUserDetailsForEditing();

        EditContainer.setVisibility(View.VISIBLE);
        editProfileTitle.setVisibility(View.VISIBLE);
        editProfileImageLayout.setVisibility(View.VISIBLE);
        userDetailsTable.setVisibility(View.VISIBLE);
        EditPFP.setVisibility(View.VISIBLE);
        saveChangesBtn.setVisibility(View.VISIBLE);
        cancelEditBtn.setVisibility(View.VISIBLE);

        // Save original values AFTER refreshing from Firebase
        originalEmail = email.getText().toString();
        originalPhone = contactno.getText().toString();

        EditText[] fields = { email, contactno };

        for (EditText field : fields) {
            field.setEnabled(true);
            field.setFocusableInTouchMode(true);
            setupSelectAllOnFocus(field);
        }

        // Add phone number input filtering
        InputFilter[] phoneFilters = new InputFilter[] {
                new InputFilter.LengthFilter(15), // Maximum 15 characters
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                                               Spanned dest, int dstart, int dend) {
                        // Allow only digits and plus sign at the beginning
                        for (int i = start; i < end; i++) {
                            char c = source.charAt(i);
                            if (!Character.isDigit(c) && c != '+') {
                                return "";
                            }
                            // Plus sign only allowed at position 0
                            if (c == '+' && dstart != 0) {
                                return "";
                            }
                        }
                        return null;
                    }
                }
        };
        contactno.setFilters(phoneFilters);

        // Add input type for phone
        contactno.setInputType(InputType.TYPE_CLASS_PHONE);

        EditPFP.setOnClickListener(v -> openImageChooser());
        saveChangesBtn.setOnClickListener(v -> validateEmailAndPhone());
        cancelEditBtn.setOnClickListener(v -> disableEditing());
        Toast.makeText(this, "You can now edit your details", Toast.LENGTH_SHORT).show();

        setupChangeWatcher();
    }

    // New method to refresh data specifically for editing
    private void refreshUserDetailsForEditing() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(FirebaseAuth.getInstance().getUid());

        userRef.get().addOnSuccessListener(dataSnapshot -> {
            if (dataSnapshot.exists()) {
                String firstName = dataSnapshot.child("firstName").getValue(String.class);
                String lastName = dataSnapshot.child("lastName").getValue(String.class);
                String userEmail = dataSnapshot.child("email").getValue(String.class);

                // Get phone number - check different field names
                String phone = null;

                if (dataSnapshot.hasChild("phone")) {
                    phone = dataSnapshot.child("phone").getValue(String.class);
                } else if (dataSnapshot.hasChild("phoneNumber")) {
                    phone = dataSnapshot.child("phoneNumber").getValue(String.class);
                } else if (dataSnapshot.hasChild("contactNum")) {
                    phone = dataSnapshot.child("contactNum").getValue(String.class);
                } else if (dataSnapshot.hasChild("contactno")) {
                    phone = dataSnapshot.child("contactno").getValue(String.class);
                }

                if (firstName != null && !firstName.isEmpty()) fname.setText(firstName);
                if (lastName != null && !lastName.isEmpty()) lname.setText(lastName);
                if (userEmail != null && !userEmail.isEmpty()) email.setText(userEmail);

                if (phone != null && !phone.isEmpty()) {
                    contactno.setText(phone);
                } else {
                    contactno.setText("");
                }

            } else {
                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("UserProfile", "Error loading user details for editing", e);
        });
    }

    private void disableEditing() {
        isEditing = false;

        logoutBtn.setVisibility(View.VISIBLE);

        email.setText(originalEmail);
        contactno.setText(originalPhone);

        email.setEnabled(false);
        contactno.setEnabled(false);

        editProfileTitle.setVisibility(View.GONE);
        editProfileImageLayout.setVisibility(View.GONE);
        userDetailsTable.setVisibility(View.GONE);
        EditPFP.setVisibility(View.GONE);
        saveChangesBtn.setVisibility(View.GONE);
        cancelEditBtn.setVisibility(View.GONE);

        if (imageUri != null) {
            imageUri = null;
            if (originalImageUrl != null) {
                Glide.with(this).load(originalImageUrl).circleCrop().into(UserPFP);
            }
        }

        saveChangesBtn.setVisibility(View.GONE);
    }

    private void finishSave() {
        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
        saveChangesBtn.setVisibility(View.GONE);

        // Show logout button after saving
        logoutBtn.setVisibility(View.VISIBLE);

        // Reload user details to update the displayed values
        loadUserDetails();
        loadProfileImage();

        // Disable editing mode
        isEditing = false;
        email.setEnabled(false);
        contactno.setEnabled(false);

        editProfileTitle.setVisibility(View.GONE);
        editProfileImageLayout.setVisibility(View.GONE);
        userDetailsTable.setVisibility(View.GONE);
        EditPFP.setVisibility(View.GONE);
        saveChangesBtn.setVisibility(View.GONE);
        cancelEditBtn.setVisibility(View.GONE);

        saveLastProfileEditTime();
    }

    @Override
    public void onBackPressed() {
        if (isEditing) {
            disableEditing();
        } else {
            super.onBackPressed();
        }
    }

    private void setupSelectAllOnFocus(EditText editText) {
        editText.setSelectAllOnFocus(true);
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) ((EditText)v).selectAll();
        });
    }

    private void setupChangeWatcher() {
        EditText[] fields = { email, fname, lname, contactno };
        for (EditText field : fields) {
            field.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    checkForChanges();
                }
                @Override
                public void afterTextChanged(android.text.Editable s) { }
            });
        }
    }

    private void checkForChanges() {
        boolean textChanged =
                !email.getText().toString().equals(originalEmail) ||
                        !contactno.getText().toString().equals(originalPhone);

        boolean imageChanged = imageUri != null;

        saveChangesBtn.setVisibility((textChanged || imageChanged) ? View.VISIBLE : View.GONE);
    }

    private void proceedWithProfileUpdate(String newEmail, String newPhone) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Clean the phone number (remove any dashes or spaces)
        String cleanPhone = newPhone.replaceAll("[^0-9+]", "");

        currentUser.updateEmail(newEmail)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users")
                                .child(currentUser.getUid());

                        userRef.child("email").setValue(newEmail);
                        userRef.child("phone").setValue(cleanPhone); // Save cleaned number

                        saveLastProfileEditTime();

                        // Update the contactNumberInfo TextView with formatted number
                        TextView contactNumberInfo = findViewById(R.id.contactNumberInfo);
                        if (contactNumberInfo != null) {
                            contactNumberInfo.setText(formatPhoneNumber(cleanPhone));
                        }

                        if (imageUri != null) {
                            // COMPRESS IMAGE BEFORE AZURE SCAN
                            Uri compressedUri = compressAndResizeImage(imageUri, 1024, 1024); // Max 1024px, 1024KB

                            // Use compressed image for Azure scan
                            ImageContentAnalyzer.analyzeImage(UserProfile.this, compressedUri,
                                    new ImageContentAnalyzer.ImageAnalysisCallback() {
                                        @Override
                                        public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                            runOnUiThread(() -> {
                                                if (isRacy) {
                                                    Toast.makeText(UserProfile.this,
                                                            "❌ Inappropriate content detected.",
                                                            Toast.LENGTH_LONG).show();
                                                    addStrikeToUser("Inappropriate image content", debugJson);
                                                    if (originalImageUrl != null) {
                                                        Glide.with(UserProfile.this).load(originalImageUrl).circleCrop().into(UserPFP);
                                                    }
                                                    imageUri = null;
                                                    finishSaveWithoutImage();
                                                } else {
                                                    Toast.makeText(UserProfile.this,
                                                            "✓ Image verified. Uploading...",
                                                            Toast.LENGTH_SHORT).show();
                                                    // Upload ORIGINAL image to Firebase, not compressed
                                                    uploadImageToFirebase(imageUri);
                                                }
                                            });
                                        }

                                        @Override
                                        public void onContentCheckFailed(String error) {
                                            Log.e("ProfileImageScan", "Image scan failed: " + error);

                                            runOnUiThread(() -> {
                                                // Try with original image as fallback
                                                Toast.makeText(UserProfile.this,
                                                        "⚠️ Retrying with original image...",
                                                        Toast.LENGTH_SHORT).show();

                                                ImageContentAnalyzer.analyzeImage(UserProfile.this, imageUri,
                                                        new ImageContentAnalyzer.ImageAnalysisCallback() {
                                                            @Override
                                                            public void onImageContentChecked(boolean isRacy, double score, String debugJson) {
                                                                runOnUiThread(() -> {
                                                                    if (isRacy) {
                                                                        Toast.makeText(UserProfile.this,
                                                                                "❌ Inappropriate content detected.",
                                                                                Toast.LENGTH_LONG).show();
                                                                        addStrikeToUser("Inappropriate image content", debugJson);
                                                                        if (originalImageUrl != null) {
                                                                            Glide.with(UserProfile.this).load(originalImageUrl).circleCrop().into(UserPFP);
                                                                        }
                                                                        imageUri = null;
                                                                        finishSaveWithoutImage();
                                                                    } else {
                                                                        Toast.makeText(UserProfile.this,
                                                                                "✓ Image verified. Uploading...",
                                                                                Toast.LENGTH_SHORT).show();
                                                                        uploadImageToFirebase(imageUri);
                                                                    }
                                                                });
                                                            }

                                                            @Override
                                                            public void onContentCheckFailed(String error2) {
                                                                runOnUiThread(() -> {
                                                                    Toast.makeText(UserProfile.this,
                                                                            "⚠️ Image scan failed. Uploading without scan.",
                                                                            Toast.LENGTH_LONG).show();
                                                                    uploadImageToFirebase(imageUri);
                                                                });
                                                            }
                                                        });
                                            });
                                        }
                                    });
                        } else {
                            finishSave();
                        }
                    } else {
                        Toast.makeText(this, "Failed to update email in Auth.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Uri compressAndResizeImage(Uri imageUri, int maxDimension, int maxSizeKB) {
        try {
            // Get image dimensions without loading full bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream boundsStream = getContentResolver().openInputStream(imageUri);
            BitmapFactory.decodeStream(boundsStream, null, options);
            boundsStream.close();

            int width = options.outWidth;
            int height = options.outHeight;
            Log.d("ImageInfo", "Original dimensions: " + width + "x" + height);

            // Calculate sampling to reduce memory usage
            int sampleSize = 1;
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2;
            }

            // Load bitmap with sampling
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
            imageStream.close();

            if (bitmap == null) {
                Log.e("ImageCompression", "Failed to decode bitmap");
                return imageUri;
            }

            // Further resize if still too large
            if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
                float scale = Math.min(
                        (float) maxDimension / bitmap.getWidth(),
                        (float) maxDimension / bitmap.getHeight()
                );

                int newWidth = (int) (bitmap.getWidth() * scale);
                int newHeight = (int) (bitmap.getHeight() * scale);

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                bitmap.recycle();
                bitmap = resizedBitmap;
            }

            // Compress to target size
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 90; // Start with 90% quality

            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

            // Reduce quality if still too large
            while (baos.toByteArray().length > maxSizeKB * 1024 && quality > 40) {
                baos.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }

            Log.d("ImageCompression",
                    "Compressed: " + bitmap.getWidth() + "x" + bitmap.getHeight() +
                            ", Quality=" + quality + "%, Size=" + (baos.toByteArray().length / 1024) + "KB");

            // Save to cache
            File cacheFile = new File(getCacheDir(), "compressed_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(baos.toByteArray());
            fos.flush();
            fos.close();
            bitmap.recycle();

            return Uri.fromFile(cacheFile);

        } catch (Exception e) {
            Log.e("ImageCompression", "Error: " + e.getMessage());
            return imageUri;
        }
    }

    private void finishSaveWithoutImage() {
        Toast.makeText(this, "Profile updated (image blocked)", Toast.LENGTH_SHORT).show();
        saveChangesBtn.setVisibility(View.GONE);

        // Show logout button
        logoutBtn.setVisibility(View.VISIBLE);

        loadUserDetails();
        loadProfileImage();

        isEditing = false;
        email.setEnabled(false);
        contactno.setEnabled(false);

        editProfileTitle.setVisibility(View.GONE);
        editProfileImageLayout.setVisibility(View.GONE);
        userDetailsTable.setVisibility(View.GONE);
        EditPFP.setVisibility(View.GONE);
        saveChangesBtn.setVisibility(View.GONE);
        cancelEditBtn.setVisibility(View.GONE);

        saveLastProfileEditTime();
    }

    private void addStrikeToUser(String reason, String debugJson) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userStrikesRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId)
                .child("strikes");

        String strikeId = userStrikesRef.push().getKey();

        Map<String, Object> strikeData = new HashMap<>();
        strikeData.put("time", System.currentTimeMillis());
        strikeData.put("reason", reason);
        strikeData.put("debugInfo", debugJson);
        strikeData.put("type", "profile_image");
        strikeData.put("userId", userId);
        strikeData.put("imageUri", imageUri != null ? imageUri.toString() : "N/A");

        // Log the strike for auditing
        Log.w("SECURITY", "Adding strike for user " + userId + ": " + reason);

        if (strikeId != null) {
            userStrikesRef.child(strikeId).setValue(strikeData)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("ProfileEdit", "Strike added successfully with reason: " + reason);

                            // Check if user should be blocked after this strike
                            checkUserStrikesAndWarn();
                        } else {
                            Log.e("ProfileEdit", "Failed to add strike: " + task.getException());
                        }
                    });
        }
    }

    private void checkUserStrikesAndWarn() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userStrikesRef = FirebaseDatabase.getInstance()
                .getReference("Users").child(userId).child("strikes");

        userStrikesRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot dataSnapshot) {
                long strikeCount = dataSnapshot.getChildrenCount();

                if (strikeCount >= 3) {
                    new AlertDialog.Builder(UserProfile.this)
                            .setTitle("⚠️ Warning: Multiple Violations")
                            .setMessage("You have received " + strikeCount + " strikes for inappropriate content. " +
                                    "Further violations may result in account restrictions.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError databaseError) {
                Log.e("StrikeCheck", "Failed to check strikes", databaseError.toException());
            }
        });
    }


    private void logActivity(String userId, String type, String action, String target, String status, String notes, String changes) {
        DatabaseReference auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        String logId = auditRef.push().getKey();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("dateTime", dateTime);
        logData.put("userId", userId);
        logData.put("type", type);
        logData.put("action", action);
        logData.put("target", target);
        logData.put("status", status);
        logData.put("notes", notes);
        logData.put("changes", changes);

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }

        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        return cleanPhone.matches("^\\+?[0-9]{10,13}$");
    }

}
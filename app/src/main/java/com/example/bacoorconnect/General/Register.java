package com.example.bacoorconnect.General;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bacoorconnect.Helpers.PasswordManager;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.UploadID;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Random;

public class Register extends AppCompatActivity {

    private EditText editTextFirstName, editTextLastName, editTextEmail, editTextContactNum, editTextPassword, editTextConfirmPassword;
    private Button register;
    private TextView loginText;
    private ProgressDialog progressDialog;
    private DatabaseReference mDatabase;
    private TextView termsLink, privacyLink;
    private static final int TOC_REQUEST_CODE = 1;
    private static final int PRIVACY_REQUEST_CODE = 2;
    private boolean tocAccepted = false;
    private boolean privacyAccepted = false;
    private static final int REQUEST_ID_VERIFICATION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Restore state if available
        if (savedInstanceState != null) {
            tocAccepted = savedInstanceState.getBoolean("tocAccepted", false);
            privacyAccepted = savedInstanceState.getBoolean("privacyAccepted", false);
        }

        CheckBox tocCheckbox = findViewById(R.id.TOCCheckBox);
        CheckBox privacyCheckbox = findViewById(R.id.PrivacyCheckBox);

        // Set initial states
        if (tocCheckbox != null) {
            tocCheckbox.setChecked(tocAccepted);
            tocCheckbox.setOnClickListener(v -> {
                tocAccepted = tocCheckbox.isChecked();
            });
        }

        if (privacyCheckbox != null) {
            privacyCheckbox.setChecked(privacyAccepted);
            privacyCheckbox.setOnClickListener(v -> {
                privacyAccepted = privacyCheckbox.isChecked();
            });
        }

        mDatabase = FirebaseDatabase.getInstance().getReference();

        editTextFirstName = findViewById(R.id.editTextFirstName);
        editTextLastName = findViewById(R.id.editTextLastName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextContactNum = findViewById(R.id.editTextContactNum);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        register = findViewById(R.id.RegisterButton);
        loginText = findViewById(R.id.LoginText);
        termsLink = findViewById(R.id.termsLink);
        privacyLink = findViewById(R.id.privacyLink);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);

        termsLink.setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, TOC.class);
            startActivityForResult(intent, TOC_REQUEST_CODE);
        });

        privacyLink.setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, PrivacyPolicy.class);
            startActivityForResult(intent, PRIVACY_REQUEST_CODE);
        });

        register.setOnClickListener(v -> {
            String firstName = editTextFirstName.getText().toString().trim();
            String lastName = editTextLastName.getText().toString().trim();
            String email = editTextEmail.getText().toString().trim();
            String contactNum = editTextContactNum.getText().toString().trim();
            String password = editTextPassword.getText().toString();
            String confirmPassword = editTextConfirmPassword.getText().toString();

            if (!tocAccepted || !privacyAccepted) {
                Toast.makeText(Register.this, "You must accept both the Terms and Privacy Policy to continue.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (validateRegistration(firstName, lastName, email, contactNum, password, confirmPassword)) {
                // Check email availability directly in Users node
                checkEmailAvailability(email, new EmailCheckCallback() {
                    @Override
                    public void onResult(boolean isAvailable, String errorMessage) {
                        if (isAvailable) {
                            sendToIDVerification(firstName, lastName, email, contactNum, password);
                        } else {
                            Toast.makeText(Register.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });

        loginText.setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, FrontpageActivity.class);
            intent.putExtra("OPEN_LOGIN_FRAGMENT", true);
            startActivity(intent);
            finish();
        });

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    // Callback interface for email check
    interface EmailCheckCallback {
        void onResult(boolean isAvailable, String errorMessage);
    }

    // Check email directly in Users node
    private void checkEmailAvailability(String email, EmailCheckCallback callback) {
        progressDialog.setMessage("Checking email availability...");
        progressDialog.show();

        // Search directly in Users node
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        usersRef.orderByChild("email").equalTo(email).get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        if (task.getResult().exists()) {
                            callback.onResult(false, "Email already registered. Please login or use a different email.");
                        } else {
                            checkTempRegistrations(email, callback);
                        }
                    } else {
                        callback.onResult(false, "Error checking email. Please try again.");
                    }
                });
    }

    private void checkTempRegistrations(String email, EmailCheckCallback callback) {
        DatabaseReference tempRef = FirebaseDatabase.getInstance().getReference("temp_registrations");
        tempRef.orderByChild("email").equalTo(email).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().exists()) {
                            long now = System.currentTimeMillis();
                            for (DataSnapshot snap : task.getResult().getChildren()) {
                                Long ts = snap.child("timestamp").getValue(Long.class);
                                if (ts != null && (now - ts) < 24 * 60 * 60 * 1000) {
                                    callback.onResult(false, "This email has a pending registration. Please complete it or wait 24 hours.");
                                    return;
                                }
                            }
                            // Old temp registration, allow new registration
                            callback.onResult(true, null);
                        } else {
                            // Email is available
                            callback.onResult(true, null);
                        }
                    } else {
                        callback.onResult(false, "Error checking email. Please try again.");
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("tocAccepted", tocAccepted);
        outState.putBoolean("privacyAccepted", privacyAccepted);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void togglePasswordVisibility(EditText editText, ImageView toggleIcon) {
        if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleIcon.setImageResource(R.drawable.baseline_remove_red_eye_24);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleIcon.setImageResource(R.drawable.baseline_disabled_visible_24);
        }
        editText.setSelection(editText.getText().length());
    }

    private boolean validateRegistration(String firstName, String lastName, String email, String contactNum, String password, String confirmPassword) {
        if (firstName.isEmpty()) {
            editTextFirstName.setError("First name is required");
            editTextFirstName.requestFocus();
            return false;
        }
        if (lastName.isEmpty()) {
            editTextLastName.setError("Last name is required");
            editTextLastName.requestFocus();
            return false;
        }
        if (email.isEmpty()) {
            editTextEmail.setError("Email is required");
            editTextEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Enter a valid email address");
            editTextEmail.requestFocus();
            return false;
        }

        if (!(email.endsWith("@gmail.com") || email.endsWith("@sdca.edu.ph"))) {
            editTextEmail.setError("Please use a valid email address (@gmail.com or @sdca.edu.ph)");
            editTextEmail.requestFocus();
            return false;
        }

        if (!contactNum.matches("^(09|\\+639)\\d{9}$")) {
            editTextContactNum.setError("Enter valid PH number (09XXXXXXXXX or +639XXXXXXXXX)");
            editTextContactNum.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            editTextPassword.setError("Password is required");
            editTextPassword.requestFocus();
            return false;
        }

        if (!isValidPassword(password)) {
            editTextPassword.setError("Password must be at least 6 characters, include an uppercase, lowercase, number, and special character");
            editTextPassword.requestFocus();
            return false;
        }

        if (confirmPassword.isEmpty()) {
            editTextConfirmPassword.setError("Confirm password is required");
            editTextConfirmPassword.requestFocus();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            editTextConfirmPassword.setError("Passwords do not match");
            editTextConfirmPassword.requestFocus();
            return false;
        }

        return true;
    }

    private boolean isValidPassword(String password) {
        if (password.length() < 6) return false;
        if (!password.matches(".*[A-Z].*")) return false;
        if (!password.matches(".*[a-z].*")) return false;
        if (!password.matches(".*\\d.*")) return false;
        return password.matches(".*[!@#$%^&*].*");
    }

    private void sendToIDVerification(String firstName, String lastName, String email,
                                      String contactNum, String password) {
        progressDialog.show();

        String tempUserId = "temp_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);

        HashMap<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("email", email);
        userData.put("contactNum", contactNum);
        userData.put("timestamp", System.currentTimeMillis());

        mDatabase.child("temp_registrations").child(tempUserId).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    savePasswordTemporarily(tempUserId, password);

                    Intent intent = new Intent(Register.this, UploadID.class);
                    intent.putExtra("tempUserId", tempUserId);
                    startActivityForResult(intent, REQUEST_ID_VERIFICATION);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(Register.this,
                            "Failed to save registration: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e("Registration", "Database error", e);
                });
    }

    private void savePasswordTemporarily(String tempUserId, String password) {
        PasswordManager.saveTempPassword(this, tempUserId, password);
    }

    private void updateCheckboxUI() {
        CheckBox tocCheckbox = findViewById(R.id.TOCCheckBox);
        CheckBox privacyCheckbox = findViewById(R.id.PrivacyCheckBox);

        if (tocCheckbox != null) tocCheckbox.setChecked(tocAccepted);
        if (privacyCheckbox != null) privacyCheckbox.setChecked(privacyAccepted);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ID_VERIFICATION) {
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Registration cancelled", Toast.LENGTH_SHORT).show();
                if (data != null && data.hasExtra("tempUserId")) {
                    String cancelledUserId = data.getStringExtra("tempUserId");
                    PasswordManager.clearTempPassword(this, cancelledUserId);
                }
            } else if (resultCode == RESULT_OK && data != null) {
                Toast.makeText(this, "You can edit your information", Toast.LENGTH_SHORT).show();
            }
        }

        if (resultCode == RESULT_OK) {
            if (requestCode == TOC_REQUEST_CODE && data != null) {
                if (data.hasExtra("tocAccepted")) {
                    tocAccepted = data.getBooleanExtra("tocAccepted", false);
                } else {
                    tocAccepted = true;
                }
                updateCheckboxUI();
            } else if (requestCode == PRIVACY_REQUEST_CODE && data != null) {
                if (data.hasExtra("privacyAccepted")) {
                    privacyAccepted = data.getBooleanExtra("privacyAccepted", false);
                } else {
                    privacyAccepted = true;
                }
                updateCheckboxUI();
            }
        }
    }
}
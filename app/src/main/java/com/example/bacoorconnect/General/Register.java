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

import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Helpers.UploadID;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class Register extends AppCompatActivity {

    private EditText editTextFirstName, editTextLastName, editTextEmail, editTextContactNum, editTextPassword, editTextConfirmPassword;
    private Button register, backButton;
    private TextView loginText;
    private ProgressDialog progressDialog;
    private DatabaseReference mDatabase;
    private CheckBox agreeToTermsCheckbox;
    private TextView termsLink, privacyLink;
    private static final int TOC_REQUEST_CODE = 1;
    private static final int PRIVACY_REQUEST_CODE = 2;
    private boolean tocAccepted = false;
    private boolean privacyAccepted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

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

        termsLink = findViewById(R.id.termsLink);
        privacyLink = findViewById(R.id.privacyLink);

        termsLink.setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, TOC.class);
            startActivityForResult(intent, TOC_REQUEST_CODE);
        });

        privacyLink.setOnClickListener(v -> {
            Intent intent = new Intent(Register.this, PrivacyPolicy.class);
            startActivityForResult(intent, PRIVACY_REQUEST_CODE);
        });

        register.setOnClickListener(v -> {
            String firstName = editTextFirstName.getText().toString();
            String lastName = editTextLastName.getText().toString();
            String email = editTextEmail.getText().toString();
            String contactNum = editTextContactNum.getText().toString();
            String password = editTextPassword.getText().toString();
            String confirmPassword = editTextConfirmPassword.getText().toString();

            if (!tocAccepted || !privacyAccepted) {
                Toast.makeText(Register.this, "You must accept both the Terms and Privacy Policy to continue.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (validateRegistration(firstName, lastName, email, contactNum, password, confirmPassword)) {
                sendToIDVerification(firstName, lastName, email, contactNum, password);
            }
        });

        loginText.setOnClickListener(v -> startActivity(new Intent(Register.this, Login.class)));

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Register.this, FrontpageActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == TOC_REQUEST_CODE) {
                tocAccepted = data.getBooleanExtra("tocAccepted", false);
            } else if (requestCode == PRIVACY_REQUEST_CODE) {
                privacyAccepted = data.getBooleanExtra("privacyAccepted", false);
            }

        }
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
            editTextEmail.setError("Please use a valid email address");
            editTextEmail.requestFocus();
            return false;
        }
        if (!contactNum.matches("^(09|\\+639)\\d{9}$")) {
            editTextContactNum.setError("Enter valid PH number (09XXXXXXXXX)");
            return false;
        }
        if (contactNum.length() < 11) {
            editTextContactNum.setError("Enter a valid contact number");
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

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userID = mAuth.getCurrentUser().getUid();

                        HashMap<String, Object> userData = new HashMap<>();
                        userData.put("firstName", firstName);
                        userData.put("lastName", lastName);
                        userData.put("email", email);
                        userData.put("contactNum", contactNum);
                        userData.put("timestamp", System.currentTimeMillis());

                        mDatabase.child("temp_registrations").child(userID).setValue(userData)
                                .addOnSuccessListener(aVoid -> {
                                    progressDialog.dismiss();
                                    Intent intent = new Intent(Register.this, UploadID.class);
                                    intent.putExtra("userID", userID);
                                    intent.putExtra("firstName", firstName);
                                    intent.putExtra("lastName", lastName);
                                    intent.putExtra("email", email);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressDialog.dismiss();
                                    Toast.makeText(Register.this,
                                            "Failed to save registration: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                    Log.e("Registration", "Database error", e);
                                });
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(Register.this,
                                "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e("Registration", "Auth error", task.getException());
                    }
                });
    }

}

package com.example.bacoorconnect.General;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.preference.PreferenceManager;

import com.example.bacoorconnect.Helpers.LocationTrackingService;
import com.example.bacoorconnect.MainActivity;
import com.example.bacoorconnect.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Login extends AppCompatActivity {

    private CheckBox keepLoggedInCheckbox;
    private EditText inputEmail, inputPassword;
    private Button loginButton;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase, auditRef;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.bacoorconnect.R.layout.activity_login);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        requestAppPermissions();

        inputEmail = findViewById(com.example.bacoorconnect.R.id.inputEmail);
        inputPassword = findViewById(com.example.bacoorconnect.R.id.inputPassword);
        loginButton = findViewById(com.example.bacoorconnect.R.id.LoginButton);
        keepLoggedInCheckbox = findViewById(com.example.bacoorconnect.R.id.keepLogIn);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, FrontpageActivity.class);
            startActivity(intent);
        });

        loginButton.setOnClickListener(v -> loginUser());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isLoggedIn = preferences.getBoolean("keepLoggedIn", false);

        if (isLoggedIn) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                navigateToDashboard(user.getUid());
                Intent serviceIntent = new Intent(Login.this, LocationTrackingService.class);
                ContextCompat.startForegroundService(Login.this, serviceIntent);
            }
        }
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissionsToRequest = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };

            for (String permission : permissionsToRequest) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, permissionsToRequest, 100);
                    break;
                }
            }
        }
    }

    private void loginUser() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            inputEmail.setError("Email is required");
            inputEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            inputPassword.setError("Password is required");
            inputPassword.requestFocus();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    String userID = user.getUid();

                    DatabaseReference statusRef = FirebaseDatabase.getInstance()
                            .getReference("Users")
                            .child(userID)
                            .child("status");

                    statusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String status = snapshot.getValue(String.class);
                            if ("online".equals(status)) {
                                FirebaseAuth.getInstance().signOut();
                                Toast.makeText(Login.this, "Account is still marked online. Please wait or logout from previous session.", Toast.LENGTH_LONG).show();
                            } else {
                                // If status is missing (null), initialize it
                                if (status == null) {
                                    snapshot.getRef().setValue("offline");
                                }
                                proceedWithFinalLogin(userID);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(Login.this, "Error checking user status", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                Toast.makeText(Login.this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithFinalLogin(String userID) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(Login.this);
        preferences.edit().putBoolean("keepLoggedIn", keepLoggedInCheckbox.isChecked()).apply();


        logActivity(userID, "Authentication", "User Login", "User", "Success", "User logged in", "N/A", false);
        navigateToDashboard(userID);
    }


    private void navigateToDashboard(String userId) {
        Intent intent = new Intent(Login.this, FrontpageActivity.class);
        intent.putExtra("LOAD_DASHBOARD", true);
        startActivity(intent);
        finish();
    }

    private void logActivity(String userId, String type, String action, String target, String status, String notes, String changes, boolean isAdmin) {
        if (userId == null) return;

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
        logData.put("isAdmin", isAdmin);

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }
}

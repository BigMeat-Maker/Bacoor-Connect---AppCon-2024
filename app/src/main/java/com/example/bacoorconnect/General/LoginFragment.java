package com.example.bacoorconnect.General;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.bacoorconnect.Helpers.LocationTrackingService;
import com.example.bacoorconnect.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class LoginFragment extends Fragment {

    private CheckBox keepLoggedInCheckbox;
    private EditText inputEmail, inputPassword;
    private Button loginButton;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase, auditRef;
    private ImageView backButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        FirebaseApp.initializeApp(requireContext());
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        requestAppPermissions();

        inputEmail = view.findViewById(R.id.inputEmail);
        inputPassword = view.findViewById(R.id.inputPassword);
        loginButton = view.findViewById(R.id.LoginButton);
        keepLoggedInCheckbox = view.findViewById(R.id.keepLogIn);
        backButton = view.findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            if (requireActivity() instanceof FrontpageActivity) {
                ((FrontpageActivity) requireActivity()).showWelcomeScreen();
            }
        });

        loginButton.setOnClickListener(v -> loginUser());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireView().setFocusableInTouchMode(true);
        requireView().requestFocus();
        requireView().setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (requireActivity() instanceof FrontpageActivity) {
                    ((FrontpageActivity) requireActivity()).showWelcomeScreen();
                }
                return true;
            }
            return false;
        });
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissionsToRequest = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };

            for (String permission : permissionsToRequest) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest, 100);
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
                                Toast.makeText(requireContext(), "Account is still marked online. Please wait or logout from previous session.", Toast.LENGTH_LONG).show();
                            } else {
                                if (status == null) {
                                    snapshot.getRef().setValue("online");
                                }
                                else if ("offline".equals(status)) {
                                    snapshot.getRef().setValue("online");
                                }

                                proceedWithFinalLogin(userID);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(requireContext(), "Error checking user status", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                Toast.makeText(requireContext(), "Login failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithFinalLogin(String userID) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        preferences.edit().putBoolean("keepLoggedIn", keepLoggedInCheckbox.isChecked()).apply();

        mDatabase.child(userID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String firstName = snapshot.child("firstName").getValue(String.class);
                    String lastName = snapshot.child("lastName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("userFirstName", firstName != null ? firstName : "");
                    editor.putString("userLastName", lastName != null ? lastName : "");
                    editor.putString("userEmail", email != null ? email : "");
                    editor.apply();

                    logActivity(userID, "Authentication", "User Login", "User", "Success", "User logged in", "N/A", false);

                    navigateToDashboard();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                logActivity(userID, "Authentication", "User Login", "User", "Success", "User logged in", "N/A", false);
                navigateToDashboard();
            }
        });
    }

    private void navigateToDashboard() {
        if (getActivity() instanceof FrontpageActivity) {
            ((FrontpageActivity) getActivity()).loadDashboardFragment();
        }
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

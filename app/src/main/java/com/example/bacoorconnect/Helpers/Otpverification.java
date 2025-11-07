package com.example.bacoorconnect.Helpers;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bacoorconnect.General.Login;
import com.example.bacoorconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.HashMap;
import java.util.Random;

public class Otpverification extends AppCompatActivity {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TIMEOUT_MS = 180000; // 3 minutes
    private static final long RESEND_DELAY_MS = 15000; // 15 seconds

    private final EditText[] otpInputs = new EditText[OTP_LENGTH];
    private Button verifyButton;
    private TextView resendButton, otpErrorText, timerText;
    private String generatedOTP, email, firstName, lastName;
    private DatabaseReference databaseRef, auditRef;
    private CountDownTimer countDownTimer;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otpverification);

        databaseRef = FirebaseDatabase.getInstance().getReference();
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        initializeFromIntent();

        initializeViews();

        setupOtpAutoMove();

        startOtpTimer();

        setupButtonListeners();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Verifying, please wait...");
        progressDialog.setCancelable(false);
    }

    private void initializeFromIntent() {
        Intent intent = getIntent();
        email = intent.getStringExtra("email");
        firstName = intent.getStringExtra("firstName");
        lastName = intent.getStringExtra("lastName");
        generatedOTP = intent.getStringExtra("otp");
    }

    private void initializeViews() {
        otpInputs[0] = findViewById(R.id.otp_1);
        otpInputs[1] = findViewById(R.id.otp_2);
        otpInputs[2] = findViewById(R.id.otp_3);
        otpInputs[3] = findViewById(R.id.otp_4);
        otpInputs[4] = findViewById(R.id.otp_5);
        otpInputs[5] = findViewById(R.id.otp_6);

        verifyButton = findViewById(R.id.btn_verify);
        resendButton = findViewById(R.id.btn_resend);
        otpErrorText = findViewById(R.id.otp_error);
        timerText = findViewById(R.id.timer_display);
    }

    private void setupButtonListeners() {
        verifyButton.setOnClickListener(v -> verifyOtp());
        resendButton.setOnClickListener(v -> resendOTP());
        resendButton.setEnabled(false);
        new android.os.Handler().postDelayed(() -> resendButton.setEnabled(true), RESEND_DELAY_MS);
    }

    private void verifyOtp() {
        String enteredOTP = getEnteredOTP();

        if (TextUtils.isEmpty(enteredOTP) || enteredOTP.length() != OTP_LENGTH) {
            otpErrorText.setText("Please enter a complete OTP.");
            return;
        }

        progressDialog.show();

        String formattedEmail = email.replace(".", ",");
        DatabaseReference otpRef = databaseRef.child("otp_requests").child(formattedEmail);

        otpRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !task.getResult().exists()) {
                progressDialog.dismiss();
                handleOtpError("No OTP found. Please request a new one.", "No OTP found");
                return;
            }

            String savedOTP = task.getResult().child("otp").getValue(String.class);
            Long timestamp = task.getResult().child("timestamp").getValue(Long.class);

            if (timestamp == null) {
                progressDialog.dismiss();
                handleOtpError("Timestamp missing. Request a new OTP.", "Timestamp missing");
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > OTP_TIMEOUT_MS) {
                progressDialog.dismiss();
                handleOtpError("OTP expired. Please request a new one.", "OTP expired");
                verifyButton.setEnabled(false);
                return;
            }

            if (!enteredOTP.equals(savedOTP)) {
                progressDialog.dismiss();
                handleOtpError("Invalid OTP. Please try again.", "Invalid OTP entered");
                return;
            }

            // OTP is valid
            Toast.makeText(Otpverification.this, "OTP Verified!", Toast.LENGTH_SHORT).show();
            logActivity("OTP Verification", "Success", "OTP Verified successfully");
            saveUserToFirebase(email, firstName, lastName, savedOTP, otpRef);
        });
    }


    private void handleOtpError(String message, String logMessage) {
        otpErrorText.setText(message);
        logActivity("OTP Verification", "Failed", logMessage);
    }

    private String getEnteredOTP() {
        StringBuilder otp = new StringBuilder();
        for (EditText otpInput : otpInputs) {
            otp.append(otpInput.getText().toString().trim());
        }
        return otp.toString();
    }

    private void saveUserToFirebase(String email, String firstName, String lastName,
                                    String otp, DatabaseReference otpRef) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User authentication failed.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        HashMap<String, Object> userData = new HashMap<>();
        userData.put("userID", userId);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("email", email);
        userData.put("timestamp", System.currentTimeMillis());

        databaseRef.child("Users").child(userId).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    otpRef.removeValue();
                    logActivity("User Registration", "Success", "User registered");
                    navigateToLogin();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show();
                    logActivity("User Registration", "Failed", "Database error");
                });

    }

    private void navigateToLogin() {
        startActivity(new Intent(this, Login.class));
        finish();
    }

    private void startOtpTimer() {
        countDownTimer = new CountDownTimer(OTP_TIMEOUT_MS, 1000) {
            public void onTick(long millisUntilFinished) {
                updateTimerDisplay(millisUntilFinished);
            }

            public void onFinish() {
                timerText.setText("OTP expired");
                verifyButton.setEnabled(false);
                resendButton.setEnabled(true);
            }
        }.start();
    }

    private void updateTimerDisplay(long millisUntilFinished) {
        int minutes = (int) (millisUntilFinished / 1000) / 60;
        int seconds = (int) (millisUntilFinished / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "Time left: %02d:%02d", minutes, seconds));
    }

    private void resendOTP() {
        String formattedEmail = email.replace(".", ",");
        DatabaseReference otpRef = databaseRef.child("otp_requests").child(formattedEmail);
        otpRef.removeValue();

        generatedOTP = String.valueOf(new Random().nextInt(899999) + 100000);
        long newTimestamp = System.currentTimeMillis();

        sendOtpEmail(generatedOTP);

        HashMap<String, Object> otpData = new HashMap<>();
        otpData.put("otp", generatedOTP);
        otpData.put("timestamp", newTimestamp);
        otpRef.setValue(otpData);

        resetOtpInputs();
        startOtpTimer();
        verifyButton.setEnabled(true);
        resendButton.setEnabled(false);
        new android.os.Handler().postDelayed(() -> resendButton.setEnabled(true), RESEND_DELAY_MS);
    }

    private void sendOtpEmail(String otp) {
        String subject = "Your New OTP for Bacoor Connect";
        String message = String.format(Locale.getDefault(),
                "Dear %s,\n\nYour new OTP is: %s\nValid for 3 minutes.\n\nRegards,\nBacoor Connect Team",
                firstName, otp);

        new JavaMailAPI(
                "bacoorconnect@gmail.com",
                "epzh fnit cvyw vnen",
                email,
                subject,
                message,
                new JavaMailAPI.MailCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(Otpverification.this, "New OTP sent!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure() {
                        Toast.makeText(Otpverification.this, "Failed to send OTP", Toast.LENGTH_SHORT).show();
                    }
                }
        ).execute();
    }

    private void resetOtpInputs() {
        for (EditText otpInput : otpInputs) {
            otpInput.setText("");
        }
        otpInputs[0].requestFocus();
        otpErrorText.setText("");
    }

    private void setupOtpAutoMove() {
        for (int i = 0; i < otpInputs.length; i++) {
            final int currentIndex = i;
            otpInputs[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && currentIndex < otpInputs.length - 1) {
                        otpInputs[currentIndex + 1].requestFocus();
                    } else if (s.length() == 0 && currentIndex > 0) {
                        otpInputs[currentIndex - 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void logActivity(String action, String status, String notes) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String logId = auditRef.push().getKey();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date());

        HashMap<String, Object> logData = new HashMap<>();
        logData.put("dateTime", dateTime);
        logData.put("userId", user.getUid());
        logData.put("action", action);
        logData.put("status", status);
        logData.put("notes", notes);

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }

}
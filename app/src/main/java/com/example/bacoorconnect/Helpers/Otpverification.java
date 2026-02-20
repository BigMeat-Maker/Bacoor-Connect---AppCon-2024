package com.example.bacoorconnect.Helpers;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bacoorconnect.General.FrontpageActivity;
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
    private static final long OTP_TIMEOUT_MS = 180000;
    private static final long RESEND_DELAY_MS = 15000;

    private final EditText[] otpInputs = new EditText[OTP_LENGTH];
    private Button verifyButton;
    private TextView resendButton, otpErrorText, timerText;
    private String generatedOTP, email, firstName, lastName, tempUserId, contactNum,password;
    private DatabaseReference databaseRef, auditRef;
    private CountDownTimer countDownTimer;
    private ProgressDialog progressDialog;
    private boolean isOtpGenerated = false;
    private String otpId;
    private DatabaseReference otpRef;
    private int verificationAttempts = 0;
    private long lastVerificationAttempt = 0;
    private static final int MAX_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MS = 300000;
    private int resendAttempts = 0;
    private long lastResendAttempt = 0;
    private static final int MAX_RESEND_ATTEMPTS = 3;
    private static final long RESEND_WINDOW_MS = 300000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otpverification);

        databaseRef = FirebaseDatabase.getInstance().getReference();
        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");

        initializeFromIntent();

        initializeViews();

        setupOtpAutoMove();

        setupButtonListeners();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Verifying, please wait...");
        progressDialog.setCancelable(false);

        loadUserDataAndGenerateOTP();

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }



    private void initializeFromIntent() {
        Intent intent = getIntent();
        tempUserId = intent.getStringExtra("tempUserId");
    }

    private void loadUserDataAndGenerateOTP() {
        progressDialog.setMessage("Loading data...");
        progressDialog.show();

        databaseRef.child("temp_registrations").child(tempUserId).get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (!task.isSuccessful() || !task.getResult().exists()) {
                        Toast.makeText(this, "Registration data not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    HashMap<String, Object> tempData = (HashMap<String, Object>) task.getResult().getValue();
                    firstName = (String) tempData.get("firstName");
                    lastName = (String) tempData.get("lastName");
                    email = (String) tempData.get("email");
                    contactNum = (String) tempData.get("contactNum");

                    password = PasswordManager.getTempPassword(this, tempUserId);

                    if (password == null) {
                        Toast.makeText(this, "Registration expired. Please start over.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    generateAndSendOTP();
                });
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

        if (!checkRateLimit()) {
            otpErrorText.setText("Too many attempts. Please wait 5 minutes.");
            return;
        }

        verificationAttempts++;
        lastVerificationAttempt = System.currentTimeMillis();

        if (TextUtils.isEmpty(enteredOTP) || enteredOTP.length() != OTP_LENGTH) {
            otpErrorText.setText("Please enter a complete OTP.");
            return;
        }

        progressDialog.show();

        if (otpRef == null) {
            progressDialog.dismiss();
            handleOtpError("OTP session expired. Request a new OTP.", "No OTP session");
            return;
        }

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

            Toast.makeText(Otpverification.this, "OTP Verified!", Toast.LENGTH_SHORT).show();
            saveUserToFirebase(otpRef);
        });
    }

    private boolean checkRateLimit() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastVerificationAttempt > ATTEMPT_WINDOW_MS) {
            verificationAttempts = 0;
        }

        if (verificationAttempts >= MAX_ATTEMPTS) {
            return false;
        }

        if (verificationAttempts > 0) {
            long delay = Math.min(1000 * (long)Math.pow(2, verificationAttempts - 1), 30000);
            if (currentTime - lastVerificationAttempt < delay) {
                return false;
            }
        }

        return true;
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

    private void saveUserToFirebase(DatabaseReference otpRef) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            String userId = user.getUid();

                            HashMap<String, Object> userData = new HashMap<>();
                            userData.put("firstName", firstName);
                            userData.put("lastName", lastName);
                            userData.put("email", email);
                            userData.put("contactNum", contactNum);
                            userData.put("timestamp", System.currentTimeMillis());
                            userData.put("admin", 0);
                            userData.put("status", "active");

                            databaseRef.child("Users").child(userId).setValue(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        databaseRef.child("temp_registrations")
                                                .child(tempUserId).removeValue();
                                        otpRef.removeValue();

                                        PasswordManager.clearTempPassword(this, tempUserId);

                                        progressDialog.dismiss();
                                        logActivity("User Registration", "Success", "User registered");
                                        navigateToLogin();
                                    })
                                    .addOnFailureListener(e -> {
                                        progressDialog.dismiss();
                                        Toast.makeText(this,
                                                "Failed to save user data.",
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(this,
                                "Authentication failed: " + authTask.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, FrontpageActivity.class);
        intent.putExtra("OPEN_LOGIN_FRAGMENT", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
        if (!checkResendLimit()) {
            Toast.makeText(this, "Too many resend requests. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Increment resend attempts
        resendAttempts++;
        lastResendAttempt = System.currentTimeMillis();

        // Clean up old OTP
        if (otpRef != null) {
            otpRef.removeValue();
        }

        generatedOTP = generateSecureOTP();

        otpId = "otp_" + System.currentTimeMillis() + "_" + new Random().nextInt(10000);
        otpRef = databaseRef.child("otp_requests").child(otpId);

        long newTimestamp = System.currentTimeMillis();

        HashMap<String, Object> otpData = new HashMap<>();
        otpData.put("otp", generatedOTP);
        otpData.put("timestamp", newTimestamp);
        otpData.put("tempUserId", tempUserId);
        otpData.put("email", email);

        otpRef.setValue(otpData)
                .addOnSuccessListener(aVoid -> {
                    sendOtpEmail(generatedOTP);
                    resetOtpInputs();
                    startOtpTimer();
                    verifyButton.setEnabled(true);

                    resendButton.setEnabled(false);
                    new android.os.Handler().postDelayed(() -> resendButton.setEnabled(true), RESEND_DELAY_MS);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to resend OTP", Toast.LENGTH_SHORT).show();
                });
    }


    private boolean checkResendLimit() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastResendAttempt > RESEND_WINDOW_MS) {
            resendAttempts = 0;
        }

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            long timeLeft = (RESEND_WINDOW_MS - (currentTime - lastResendAttempt)) / 1000;
            if (timeLeft > 0) {
                timerText.setText("Resend limit reached. Try again in " + timeLeft + " seconds");
            }
            return false;
        }

        if (resendAttempts > 0 && (currentTime - lastResendAttempt < 30000)) {
            long timeLeft = (30000 - (currentTime - lastResendAttempt)) / 1000;
            timerText.setText("Wait " + timeLeft + " seconds before resending");
            return false;
        }

        return true;
    }

    private void generateAndSendOTP() {
        if (isOtpGenerated) return;
        generatedOTP = generateSecureOTP();

        otpId = "otp_" + System.currentTimeMillis() + "_" + new Random().nextInt(10000);
        otpRef = databaseRef.child("otp_requests").child(otpId);

        HashMap<String, Object> otpData = new HashMap<>();
        otpData.put("otp", generatedOTP);
        otpData.put("timestamp", System.currentTimeMillis());
        otpData.put("tempUserId", tempUserId);
        otpData.put("email", email);

        otpRef.setValue(otpData)
                .addOnSuccessListener(aVoid -> {
                    sendOtpEmail(generatedOTP);
                    isOtpGenerated = true;
                    startOtpTimer();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to generate OTP", Toast.LENGTH_SHORT).show();
                });
    }

    private String generateSecureOTP() {
        try {
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            int num = secureRandom.nextInt(1000000);
            return String.format("%06d", num);
        } catch (Exception e) {
            // Fallback to Random if SecureRandom fails
            return String.valueOf(new Random().nextInt(899999) + 100000);
        }
    }

    private void sendOtpEmail(String otp) {
        String emailAddress = EmailConfig.getEmailAddress(this);
        String emailPassword = EmailConfig.getEmailPassword(this);

        if (emailAddress == null || emailPassword == null) {
            Toast.makeText(this, "Email configuration error", Toast.LENGTH_SHORT).show();
            return;
        }

        String subject = "Your OTP for Bacoor Connect Registration";
        String message = String.format(Locale.getDefault(),
                "Dear %s %s,\n\n" +
                        "Thank you for registering with Bacoor Connect!\n\n" +
                        "Your OTP is: %s\n" +
                        "Valid for 3 minutes.\n\n" +
                        "Please enter this code to complete your registration.\n\n" +
                        "Regards,\nBacoor Connect Team",
                firstName, lastName, otp);

        new JavaMailAPI(
                emailAddress,
                emailPassword,
                email,
                subject,
                message,
                new JavaMailAPI.MailCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(Otpverification.this, "OTP sent to your email!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure() {
                        Toast.makeText(Otpverification.this, "Failed to send OTP email", Toast.LENGTH_SHORT).show();
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
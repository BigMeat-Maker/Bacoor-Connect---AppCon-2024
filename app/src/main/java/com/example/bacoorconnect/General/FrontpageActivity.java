package com.example.bacoorconnect.General;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.Helpers.LocationTrackingService;
import com.example.bacoorconnect.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

public class FrontpageActivity extends AppCompatActivity {

    private Button Logindirect, Registerdirect, Guestdirect;
    private View welcomeLayout;
    private View loggedInLayout;
    private View fragmentContainer;
    private View headerLayout;
    private View bottomNavigation;
    private TextView greetingText;
    private ImageView profileIcon; // Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_frontpage);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        welcomeLayout = findViewById(R.id.welcomeLayout);
        loggedInLayout = findViewById(R.id.loggedInLayout);
        fragmentContainer = findViewById(R.id.fragment_container);
        headerLayout = findViewById(R.id.headerLayout);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        greetingText = findViewById(R.id.greetingText);
        profileIcon = findViewById(R.id.profileIcon); // Initialize this

        Logindirect = findViewById(R.id.Logindirect);
        Registerdirect = findViewById(R.id.RegisterButton);
        Guestdirect = findViewById(R.id.LoginGuest);

        // Add click listener for profile icon
        profileIcon.setOnClickListener(v -> {
            // Navigate to profile or show profile menu
            // You can add your profile navigation logic here
        });

        Logindirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToActivity(1);
            }
        });

        Registerdirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToActivity(2);
            }
        });

        Guestdirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToActivity(3);
            }
        });

        // Check if we should load dashboard fragment
        if (getIntent().getBooleanExtra("LOAD_DASHBOARD", false)) {
            loadDashboardFragment();
        } else {
            // Check if user is already logged in
            checkAutoLogin();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            BottomNavHelper.setupBottomNavigation(this, bottomNav, R.id.nav_home);
        }
    }

    private void checkAutoLogin() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isLoggedIn = preferences.getBoolean("keepLoggedIn", false);

        if (isLoggedIn) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                // Fetch user details from Firebase before loading dashboard
                String userID = user.getUid();
                DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference("Users");

                mDatabase.child(userID).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Get user details from Firebase
                            String firstName = snapshot.child("firstName").getValue(String.class);
                            String lastName = snapshot.child("lastName").getValue(String.class);
                            String email = snapshot.child("email").getValue(String.class);
                            String phoneNumber = snapshot.child("phoneNumber").getValue(String.class);

                            // Store in SharedPreferences for easy access
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("userFirstName", firstName != null ? firstName : "");
                            editor.putString("userLastName", lastName != null ? lastName : "");
                            editor.putString("userEmail", email != null ? email : "");
                            editor.putString("userPhone", phoneNumber != null ? phoneNumber : "");
                            editor.apply();

                            // Load profile image if exists
                            if (snapshot.hasChild("profileImage")) {
                                String profileImageUrl = snapshot.child("profileImage").getValue(String.class);
                                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                    // Store profile image URL
                                    editor.putString("userProfileImage", profileImageUrl);
                                    editor.apply();

                                    // Load profile image using Glide
                                    runOnUiThread(() -> {
                                        Glide.with(FrontpageActivity.this)
                                                .load(profileImageUrl)
                                                .circleCrop()
                                                .placeholder(R.drawable.profile) // Default profile icon
                                                .into(profileIcon);
                                    });
                                }
                            }

                            // Update greeting text
                            runOnUiThread(() -> {
                                updateGreetingText(firstName, lastName, email);
                            });
                        }
                        loadDashboardFragment();
                        Intent serviceIntent = new Intent(FrontpageActivity.this, LocationTrackingService.class);
                        ContextCompat.startForegroundService(FrontpageActivity.this, serviceIntent);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Still load dashboard even if user details fetch fails
                        loadDashboardFragment();
                        Intent serviceIntent = new Intent(FrontpageActivity.this, LocationTrackingService.class);
                        ContextCompat.startForegroundService(FrontpageActivity.this, serviceIntent);
                    }
                });
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If logged in layout is visible, handle back navigation
        if (loggedInLayout.getVisibility() == View.VISIBLE) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                // Pop the back stack (will go back to welcome screen)
                getSupportFragmentManager().popBackStack();
                welcomeLayout.setVisibility(View.VISIBLE);
                loggedInLayout.setVisibility(View.GONE);
            } else {
                // If dashboard is showing, exit the app
                super.onBackPressed();
            }
        } else {
            // If welcome screen is showing, exit the app
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh user data when returning to this activity
        refreshUserData();
    }

    private void refreshUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String firstName = snapshot.child("firstName").getValue(String.class);
                        String lastName = snapshot.child("lastName").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);

                        // Update greeting
                        updateGreetingText(firstName, lastName, email);

                        // Update profile picture
                        if (snapshot.hasChild("profileImage")) {
                            String profileImageUrl = snapshot.child("profileImage").getValue(String.class);
                            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                Glide.with(FrontpageActivity.this)
                                        .load(profileImageUrl)
                                        .circleCrop()
                                        .placeholder(R.drawable.profile)
                                        .into(profileIcon);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });
        }
    }

    private void navigateToActivity(int selection) {
        switch (selection) {
            case 1:
                loadLoginFragment();
                break;
            case 2:
                Intent intent = new Intent(FrontpageActivity.this, Register.class);
                startActivity(intent);
                break;
            case 3:
                FirebaseAuth.getInstance().signOut();
                loadDashboardFragment();
                break;
            default:
                break;
        }
    }

    public void loadLoginFragment() {
        welcomeLayout.setVisibility(View.GONE);
        loggedInLayout.setVisibility(View.VISIBLE);

        // Hide header and bottom navigation during login
        headerLayout.setVisibility(View.GONE);
        bottomNavigation.setVisibility(View.GONE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new LoginFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    public void showWelcomeScreen() {
        welcomeLayout.setVisibility(View.VISIBLE);
        loggedInLayout.setVisibility(View.GONE);

        // Clear the back stack
        getSupportFragmentManager().popBackStack();

        // Reset profile icon to default
        profileIcon.setImageResource(R.drawable.profile);
    }

    public void loadDashboardFragment() {
        welcomeLayout.setVisibility(View.GONE);
        loggedInLayout.setVisibility(View.VISIBLE);

        // Show header and bottom navigation when dashboard loads
        headerLayout.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);

        // Update greeting text with user's name
        updateGreetingTextFromPrefs();

        // Clear the back stack before loading dashboard
        getSupportFragmentManager().popBackStack(null, getSupportFragmentManager().POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new Dashboard());
        transaction.commit();
    }

    private void updateGreetingTextFromPrefs() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String firstName = preferences.getString("userFirstName", "");
        String lastName = preferences.getString("userLastName", "");
        String email = preferences.getString("userEmail", "");

        updateGreetingText(firstName, lastName, email);
    }

    private void updateGreetingText(String firstName, String lastName, String email) {
        String greeting = "Hello, ";

        // Build the greeting with available user information
        if (firstName != null && !firstName.isEmpty()) {
            greeting += firstName;
            if (lastName != null && !lastName.isEmpty()) {
                greeting += " " + lastName;
            }
            greeting += "!";
        } else if (email != null && !email.isEmpty()) {
            // If no name is available, use email
            greeting += email + "!";
        } else {
            // Fallback to generic greeting
            greeting += "User!";
        }

        greetingText.setText(greeting);
    }
}
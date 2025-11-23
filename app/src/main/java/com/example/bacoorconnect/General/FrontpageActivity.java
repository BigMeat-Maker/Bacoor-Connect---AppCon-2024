package com.example.bacoorconnect.General;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import com.example.bacoorconnect.Helpers.LocationTrackingService;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.User;
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

        Logindirect = findViewById(R.id.Logindirect);
        Registerdirect = findViewById(R.id.RegisterButton);
        Guestdirect = findViewById(R.id.LoginGuest);

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
                            User.firstName = snapshot.child("firstName").getValue(String.class);
                            User.lastName = snapshot.child("lastName").getValue(String.class);
                            User.email = snapshot.child("email").getValue(String.class);
                            User.phoneNumber = snapshot.child("phoneNumber").getValue(String.class);

                            // Ensure values are not null
                            if (User.firstName == null) User.firstName = "";
                            if (User.lastName == null) User.lastName = "";
                            if (User.email == null) User.email = "";
                            if (User.phoneNumber == null) User.phoneNumber = "";
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
    }

    public void loadDashboardFragment() {
        welcomeLayout.setVisibility(View.GONE);
        loggedInLayout.setVisibility(View.VISIBLE);

        // Show header and bottom navigation when dashboard loads
        headerLayout.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);

        // Update greeting text with user's name
        updateGreetingText();

        // Clear the back stack before loading dashboard
        getSupportFragmentManager().popBackStack(null, getSupportFragmentManager().POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new Dashboard());
        transaction.commit();
    }

    private void updateGreetingText() {
        String greeting = "Hello, ";

        // Build the greeting with available user information
        if (User.firstName != null && !User.firstName.isEmpty()) {
            greeting += User.firstName;
            if (User.lastName != null && !User.lastName.isEmpty()) {
                greeting += " " + User.lastName;
            }
            greeting += "!";
        } else if (User.email != null && !User.email.isEmpty()) {
            // If no name is available, use email
            greeting += User.email + "!";
        } else {
            // Fallback to generic greeting
            greeting += "User!";
        }

        greetingText.setText(greeting);
    }

}
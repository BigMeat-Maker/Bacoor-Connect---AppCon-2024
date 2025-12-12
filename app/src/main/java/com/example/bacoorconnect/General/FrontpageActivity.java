package com.example.bacoorconnect.General;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.Emergency.EmergencyGuides;
import com.example.bacoorconnect.Emergency.EmergencyHospitals;
import com.example.bacoorconnect.Helpers.LocationTrackingService;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.Report.ReportIncident;
import com.example.bacoorconnect.UserProfile;
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
    private ImageView profileIcon;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_frontpage);

        backButton = findViewById(R.id.backButton);
        welcomeLayout = findViewById(R.id.welcomeLayout);
        loggedInLayout = findViewById(R.id.loggedInLayout);
        fragmentContainer = findViewById(R.id.fragment_container);
        headerLayout = findViewById(R.id.headerLayout);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        greetingText = findViewById(R.id.greetingText);
        profileIcon = findViewById(R.id.profileIcon);

        Logindirect = findViewById(R.id.Logindirect);
        Registerdirect = findViewById(R.id.RegisterButton);
        Guestdirect = findViewById(R.id.LoginGuest);

        profileIcon.setOnClickListener(v -> {
        });

        backButton.setOnClickListener(v -> {
            onBackPressed();
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

        if (getIntent().getBooleanExtra("LOAD_DASHBOARD", false)) {
            loadDashboardFragment();
        } else {
            checkAutoLogin();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            setupCustomBottomNavigation(bottomNav);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            updateUIForCurrentFragment();
        });

        updateGreetingTextFromPrefs();

    }

    private void setupCustomBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                new Handler().postDelayed(() -> {
                    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (!(currentFragment instanceof Dashboard)) {
                        loadDashboardFragment();
                    }
                }, 50);
                return true;

            } else if (itemId == R.id.nav_service) {
                Intent intent = new Intent(FrontpageActivity.this, services.class);
                startActivity(intent);
                return true;

            } else if (itemId == R.id.nav_ri) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Intent intent = new Intent(FrontpageActivity.this, ReportIncident.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Please login to report incidents", Toast.LENGTH_SHORT).show();
                }
                return true;

            } else if (itemId == R.id.nav_map) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Intent intent = new Intent(FrontpageActivity.this, MapDash.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                }
                return true;

            } else if (itemId == R.id.nav_history) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Intent intent = new Intent(FrontpageActivity.this, ReportHistoryActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                }
                return true;

            } else if (itemId == R.id.nav_profile) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Intent intent = new Intent(FrontpageActivity.this, UserProfile.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            return false;
        });
    }

    private void checkAutoLogin() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isLoggedIn = preferences.getBoolean("keepLoggedIn", false);

        if (isLoggedIn) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String userID = user.getUid();
                DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference("Users");

                mDatabase.child(userID).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String firstName = snapshot.child("firstName").getValue(String.class);
                            String lastName = snapshot.child("lastName").getValue(String.class);
                            String email = snapshot.child("email").getValue(String.class);
                            String phoneNumber = snapshot.child("phoneNumber").getValue(String.class);

                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("userFirstName", firstName != null ? firstName : "");
                            editor.putString("userLastName", lastName != null ? lastName : "");
                            editor.putString("userEmail", email != null ? email : "");
                            editor.putString("userPhone", phoneNumber != null ? phoneNumber : "");
                            editor.apply();

                            if (snapshot.hasChild("profileImage")) {
                                String profileImageUrl = snapshot.child("profileImage").getValue(String.class);
                                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                    editor.putString("userProfileImage", profileImageUrl);
                                    editor.apply();

                                    runOnUiThread(() -> {
                                        Glide.with(FrontpageActivity.this)
                                                .load(profileImageUrl)
                                                .circleCrop()
                                                .placeholder(R.drawable.profile)
                                                .into(profileIcon);
                                    });
                                }
                            }

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
        if (loggedInLayout.getVisibility() == View.VISIBLE) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof Dashboard) {
                super.onBackPressed();
            } else {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStackImmediate();

                    updateUIForCurrentFragment();
                } else {
                    loadDashboardFragment();
                }
            }
        } else {
            super.onBackPressed();
        }
    }

    private void updateUIForCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof Dashboard) {
            backButton.setVisibility(View.GONE);
            updateGreetingTextFromPrefs();
        } else if (currentFragment != null) {
            backButton.setVisibility(View.VISIBLE);

            if (currentFragment instanceof EmergencyGuides) {
                greetingText.setText("Emergency Guides");
            } else if (currentFragment instanceof EmergencyHospitals) {
                greetingText.setText("Emergency Hospitals");
            } else if (currentFragment instanceof LoginFragment) {
                greetingText.setText("Login");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserData();

        checkCurrentFragment();
    }

    private void checkCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof Dashboard) {
            backButton.setVisibility(View.GONE);
            updateGreetingTextFromPrefs();
        } else if (currentFragment != null) {
            backButton.setVisibility(View.VISIBLE);
        }
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

                        updateGreetingText(firstName, lastName, email);

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
        getSupportFragmentManager().popBackStack();
        profileIcon.setImageResource(R.drawable.profile);
    }
    public void loadDashboardFragment() {
        welcomeLayout.setVisibility(View.GONE);
        loggedInLayout.setVisibility(View.VISIBLE);

        headerLayout.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);

        backButton.setVisibility(View.GONE);

        updateGreetingTextFromPrefs();

        getSupportFragmentManager().popBackStack(null, getSupportFragmentManager().POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new Dashboard());
        transaction.commit();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
    public void loadEmergencyFragment(Fragment fragment, String title) {
        welcomeLayout.setVisibility(View.GONE);
        loggedInLayout.setVisibility(View.VISIBLE);
        headerLayout.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
        greetingText.setText(title);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack("emergency_fragment");
        transaction.commit();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.getMenu().findItem(R.id.nav_home).setChecked(false);
        }
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

        if (firstName != null && !firstName.isEmpty()) {
            greeting += firstName;
            if (lastName != null && !lastName.isEmpty()) {
                greeting += " " + lastName;
            }
            greeting += "!";
        } else if (email != null && !email.isEmpty()) {
            greeting += email + "!";
        } else {
            greeting += "User!";
        }

        greetingText.setText(greeting);
    }
}
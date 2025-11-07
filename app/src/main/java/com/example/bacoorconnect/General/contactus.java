package com.example.bacoorconnect.General;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.bacoorconnect.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

public class contactus extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private DrawerLayout drawerLayout;
    private ImageView menuIcon;
    private NavigationView navigationView;

    private TextInputEditText fullnameInput, emailInput, numberInput, subjectInput, messageInput;
    private Button sendBtn;
    private ImageView DashNotif;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_contactus);

        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        NavigationHeader.setupNavigationHeader(this, navigationView);

        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            if (isGuest && (item.getItemId() == R.id.nav_history || item.getItemId() == R.id.nav_profile)) {
                Toast.makeText(this, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                return false;
            }
            NavigationHandler navigationHandler = new NavigationHandler(this, drawerLayout);
            navigationHandler.handleMenuSelection(item);
            drawerLayout.closeDrawer(navigationView);
            return true;
        });

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setupBottomNavigation(this, bottomNavigationView, R.id.Nav_Service);

        DashNotif = findViewById(R.id.notification);
        DashNotif.setOnClickListener(v -> {
            Intent intent = new Intent(contactus.this, NotificationCenter.class);
            startActivity(intent);
        });

        if (isGuest) {
            disableGuestFeatures();
        }

        fullnameInput = findViewById(R.id.contact_fullname);
        emailInput = findViewById(R.id.contact_email);
        numberInput = findViewById(R.id.contact_number);
        subjectInput = findViewById(R.id.contact_subject);
        messageInput = findViewById(R.id.contact_message);
        sendBtn = findViewById(R.id.send_button);

        sendBtn.setOnClickListener(v -> {
            String name = fullnameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String number = numberInput.getText().toString().trim();
            String subject = subjectInput.getText().toString().trim();
            String message = messageInput.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(number)
                    || TextUtils.isEmpty(subject) || TextUtils.isEmpty(message)) {
                Toast.makeText(contactus.this, "Please fill out all required fields.", Toast.LENGTH_SHORT).show();
            } else {
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("message/rfc822");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@bacoorconnect.com"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                emailIntent.putExtra(Intent.EXTRA_TEXT,
                        "Name: " + name + "\n" +
                                "Email: " + email + "\n" +
                                "Phone: " + number + "\n\n" +
                                "Message:\n" + message);

                try {
                    startActivity(Intent.createChooser(emailIntent, "Send email using..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(contactus.this, "No email clients installed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void disableGuestFeatures() {
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.nav_history).setVisible(false);
        menu.findItem(R.id.nav_profile).setVisible(false);

        bottomNavigationView.getMenu().findItem(R.id.Nav_RI).setEnabled(false);
        bottomNavigationView.getMenu().findItem(R.id.Nav_RI).setVisible(false);

        Toast.makeText(this, "Guest mode: Limited access", Toast.LENGTH_SHORT).show();
    }
}

package com.example.bacoorconnect.General;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.UserProfile;
import com.example.bacoorconnect.Weather.weatherDash;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class NavigationHandler {

    private final Context context;
    private final DrawerLayout drawerLayout;

    public NavigationHandler(Context context, DrawerLayout drawerLayout) {
        this.context = context;
        this.drawerLayout = drawerLayout;
    }

    public void handleMenuSelection(@NonNull MenuItem item) {
        boolean isGuest = isGuestMode();
        int itemId = item.getItemId();

        if (isGuest && (itemId == R.id.nav_history || itemId == R.id.nav_profile || itemId == R.id.nav_map)) {
            String blockedFeature = item.getTitle().toString();
            Toast.makeText(context, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
            AuditLogger.log(context, "Guest", "Access Attempt", "Blocked", blockedFeature, "Denied",
                    "Guest tried to access " + blockedFeature, "N/A");
            drawerLayout.closeDrawers();
            return;
        }

        Intent intent = null;

        if (itemId == R.id.nav_home) {
            intent = new Intent(context, Dashboard.class);
        } else if (itemId == R.id.nav_map) {
            intent = new Intent(context, MapDash.class);
        } else if (itemId == R.id.nav_service) {
            intent = new Intent(context, weatherDash.class);
        } else if (itemId == R.id.nav_history) {
            intent = new Intent(context, ReportHistoryActivity.class);
        } else if (itemId == R.id.nav_profile) {
            intent = new Intent(context, UserProfile.class);
        } else if (itemId == R.id.nav_about) {
            intent = new Intent(context, AboutUs.class);
        } else if (itemId == R.id.nav_feedback) {
            intent = new Intent(context, contactus.class);
        } else if (itemId == R.id.nav_logout) {
            logoutUser();
            return;
        } else {
            Toast.makeText(context, "Unknown Item Selected", Toast.LENGTH_SHORT).show();
        }

        if (intent != null) {
            intent.putExtra("isGuest", isGuest);
            context.startActivity(intent);
        }

        drawerLayout.closeDrawers();
    }

    private boolean isGuestMode() {
        return FirebaseAuth.getInstance().getCurrentUser() == null;
    }

    private void logoutUser() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("keepLoggedIn", false);
        editor.remove("savedEmail");
        editor.remove("savedPassword");
        editor.apply();

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "Guest";

        if (!isGuestMode()) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
            userRef.child("status").setValue("offline")
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("NavigationHandler", "User status updated to offline");
                        } else {
                            Log.e("NavigationHandler", "Failed to update user status: " + task.getException());
                        }

                        // Log the audit trail
                        AuditLogger.log(
                                context,
                                userId,
                                "Authentication",
                                "User Logout",
                                "User",
                                "Success",
                                "User logged out",
                                "N/A"
                        );

                        // Sign out after updating status
                        FirebaseAuth.getInstance().signOut();
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show();

                        // Navigate to front page
                        Intent intent = new Intent(context, FrontpageActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                    });
        } else {
            Toast.makeText(context, "Exiting guest mode...", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(context, FrontpageActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
    }


}

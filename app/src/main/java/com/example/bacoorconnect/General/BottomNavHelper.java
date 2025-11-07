package com.example.bacoorconnect.General;

import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.Report.ReportIncident;
import com.example.bacoorconnect.UserProfile;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class BottomNavHelper {

    public static void setupBottomNavigation(
            AppCompatActivity activity,
            BottomNavigationView bottomNav,
            int selectedItemId
    ) {
        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Class<?> targetActivity = null;
            boolean isGuest = FirebaseAuth.getInstance().getCurrentUser() == null;

            if (isGuest && (itemId == R.id.Nav_RI || itemId == R.id.nav_map)) {
                String featureName = (itemId == R.id.Nav_RI) ? "Report Incident" : "Map";
                Toast.makeText(activity, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                AuditLogger.log(activity, "Guest", "Access Attempt", "Blocked", featureName, "Denied",
                        "Guest tried to access " + featureName, "N/A");
                return false;
            }

            if (itemId == R.id.Nav_Home) {
                targetActivity = Dashboard.class;
            } else if (itemId == R.id.Nav_Service) {
                targetActivity = services.class;
            } else if (itemId == R.id.Nav_RI) {
                targetActivity = ReportIncident.class;
            } else if (itemId == R.id.Nav_History) {
                targetActivity = ReportHistoryActivity.class;
            } else if (itemId == R.id.Nav_Profile) {
                targetActivity = UserProfile.class;
            }

            if (targetActivity != null && !activity.getClass().equals(targetActivity)) {
                Intent intent = new Intent(activity, targetActivity);
                intent.putExtra("isGuest", isGuest);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);

                return true;
            }

            return false;
        });
    }
}
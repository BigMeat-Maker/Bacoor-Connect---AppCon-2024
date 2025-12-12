package com.example.bacoorconnect.General;

import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportActivity;
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
        if (activity instanceof FrontpageActivity) {
            return;
        }

        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Class<?> targetActivity = null;
            boolean isGuest = FirebaseAuth.getInstance().getCurrentUser() == null;

            if (activity instanceof MapDash) {
                return false;
            }

            if (isGuest && (itemId == R.id.nav_ri || itemId == R.id.nav_map)) {
                String featureName = (itemId == R.id.nav_ri) ? "Report Incident" : "Map";
                Toast.makeText(activity, "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                AuditLogger.log(activity, "Guest", "Access Attempt", "Blocked", featureName, "Denied",
                        "Guest tried to access " + featureName, "N/A");
                return false;
            }

            if (itemId == R.id.nav_home) {
                targetActivity = FrontpageActivity.class;
            } else if (itemId == R.id.nav_service) {
                targetActivity = services.class;
            } else if (itemId == R.id.nav_ri) {
                targetActivity = ReportIncident.class;
            } else if (itemId == R.id.nav_map) {
                targetActivity = MapDash.class;
            } else if (itemId == R.id.nav_history) {
                targetActivity = ReportHistoryActivity.class;
            } else if (itemId == R.id.nav_profile) {
                targetActivity = UserProfile.class;
            }

            if (targetActivity != null && !activity.getClass().equals(targetActivity)) {
                Intent intent = new Intent(activity, targetActivity);
                intent.putExtra("isGuest", isGuest);
                if (targetActivity == FrontpageActivity.class) {
                    intent.putExtra("LOAD_DASHBOARD", true);
                }
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
    }
}
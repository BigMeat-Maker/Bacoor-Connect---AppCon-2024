package com.example.bacoorconnect.Emergency;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;

import com.example.bacoorconnect.General.AboutUs;
import com.example.bacoorconnect.General.Dashboard;
import com.example.bacoorconnect.General.Login;
import com.example.bacoorconnect.General.MapDash;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportIncident;
import com.example.bacoorconnect.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
public class EmergencyHotlines extends Fragment {

    private DatabaseReference auditRef;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency_hotlines, container, false);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hotlines", "Emergency Resources", "Success", "User accessed the emergency resources: hotlines page", "N/A");

        // Setup hotline click listeners
        setupHotlineClick(view, R.id.hotline_rer, "161", "RER 161");
        setupHotlineClick(view, R.id.hotline_bdrrmo, "(046)4170727", "BDRRMO");
        setupHotlineClick(view, R.id.hotline_pnp, "09777520819", "PNP Bacoor");
        setupHotlineClick(view, R.id.hotline_bfp, "09666959711", "BFP Bacoor");
        setupHotlineClick(view, R.id.hotline_medical, "(046)4814120", "City Information Office");

        // Setup quick access click listeners
        setupQuickAccessListeners(view);

        return view;
    }

    private void setupHotlineClick(View parentView, int layoutId, String phoneNumber, String serviceName) {
        LinearLayout layout = parentView.findViewById(layoutId);
        if (layout != null) {
            layout.setOnClickListener(v -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(callIntent);
                logActivity("Unknown", "Phone Call", "Dialed", serviceName + " - " + phoneNumber, "Success", "User initiated a phone call", "N/A");
            });
        }
    }

    private void setupQuickAccessListeners(View view) {
        // Home
        view.findViewById(R.id.quick_home).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), Dashboard.class);
            startActivity(intent);
        });

        // Traffic Map
        view.findViewById(R.id.quick_map).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Service
        view.findViewById(R.id.quick_service).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Report History
        view.findViewById(R.id.quick_history).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), ReportIncident.class);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
            }
        });

        // Profile
        view.findViewById(R.id.quick_profile).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), UserProfile.class);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
            }
        });

        // About
        view.findViewById(R.id.quick_about).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutUs.class);
            startActivity(intent);
        });

        // Feedback
        view.findViewById(R.id.quick_feedback).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutUs.class);
            startActivity(intent);
        });

        // Logout
        view.findViewById(R.id.quick_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void logActivity(String userId, String type, String action, String target, String status, String notes, String changes) {
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

        if (logId != null) {
            auditRef.child(logId).setValue(logData);
        }
    }
}

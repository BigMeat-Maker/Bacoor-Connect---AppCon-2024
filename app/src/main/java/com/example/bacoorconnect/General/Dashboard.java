package com.example.bacoorconnect.General;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;

import com.example.bacoorconnect.Emergency.EmergencyGuides;
import com.example.bacoorconnect.Emergency.EmergencyHospitals;
import com.example.bacoorconnect.General.AboutUs;
import com.example.bacoorconnect.General.Dashboard;
import com.example.bacoorconnect.General.Login;
import com.example.bacoorconnect.General.MapDash;
import com.example.bacoorconnect.R;
import com.example.bacoorconnect.Report.ReportHistoryActivity;
import com.example.bacoorconnect.Report.ReportIncident;
import com.example.bacoorconnect.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
public class Dashboard extends Fragment {

    private DatabaseReference auditRef;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_dashboard, container, false);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hotlines", "Emergency Resources", "Success", "User accessed the emergency resources: hotlines page", "N/A");

        // Setup hotline click listeners with detailed information
        setupHotlineClick(view, R.id.hotline_rer, "161", "RER 161",
                R.drawable.logo_alert161,
                "Rapid Emergency Response - 24/7 emergency assistance for all types of emergencies in Bacoor City.",
                "161",
                "City Hall Compound, Brgy. San Nicolas I, Bacoor City, Cavite");

        setupHotlineClick(view, R.id.hotline_bdrrmo, "(046)4170727", "BDRRMO",
                R.drawable.hotline_bdrrmo,
                "Bacoor Disaster Risk Reduction and Management Office - Disaster response, rescue operations, and risk reduction programs.",
                "(046) 417-0727",
                "2nd Floor, New City Hall Building, Brgy. San Nicolas I, Bacoor City, Cavite");

        setupHotlineClick(view, R.id.hotline_pnp, "09777520819", "PNP Bacoor",
                R.drawable.hotline_pnp,
                "Philippine National Police Bacoor - Law enforcement, crime prevention, and public safety services.",
                "0977-752-0819",
                "PNP Bacoor Station, Tirona Highway, Brgy. Talaba VI, Bacoor City, Cavite");

        setupHotlineClick(view, R.id.hotline_bfp, "09666959711", "BFP Bacoor",
                R.drawable.hotline_bfp,
                "Bureau of Fire Protection Bacoor - Fire prevention, suppression, and rescue services available 24/7.",
                "0966-695-9711",
                "BFP Bacoor Station, Aguinaldo Highway, Brgy. Niog II, Bacoor City, Cavite");

        setupHotlineClick(view, R.id.hotline_medical, "(046)4814120", "City Information Office",
                R.drawable.hotline_medical,
                "Bacoor City Information Office - General inquiries, information dissemination, and public assistance.",
                "(046) 481-4120",
                "Ground Floor, New City Hall Building, Brgy. San Nicolas I, Bacoor City, Cavite");

        setupEmergencyResourcesCards(view);
        setupQuickAccessListeners(view);
        setupBottomNavigation(view);

        return view;
    }

    private void setupBottomNavigation(View view) {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                view.findViewById(R.id.bottom_navigation);

        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);

            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.nav_home) {
                    // Stay on the Dashboard (Home)
                    return true;

                } else if (itemId == R.id.nav_service) {
                    // Navigate to Service Activity
                    Intent intent = new Intent(getActivity(), MapDash.class);
                    startActivity(intent);
                    return true;

                } else if (itemId == R.id.nav_ri) {
                    // Navigate to Report Incident Activity
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        Intent intent = new Intent(getActivity(), ReportIncident.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getContext(), "Please login to report incidents", Toast.LENGTH_SHORT).show();
                    }
                    return true;

                } else if (itemId == R.id.nav_history) {
                    // Navigate to Report History Activity
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        Intent intent = new Intent(getActivity(), ReportIncident.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                    }
                    return true;

                } else if (itemId == R.id.nav_profile) {
                    // Navigate to Profile Activity
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        Intent intent = new Intent(getActivity(), UserProfile.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getContext(), "Feature unavailable in guest mode", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                return false;
            });
        }
    }

    private void setupEmergencyResourcesCards(View view) {
        // Hospital Directory Card
        CardView hospitalCard = view.findViewById(R.id.card_hospitals);
        if (hospitalCard != null) {
            hospitalCard.setOnClickListener(v -> {
                navigateToFragment(new EmergencyHospitals());
                logActivity("Unknown", "Navigation", "Opened Hospital Directory", "Emergency Resources", "Success", "User accessed hospital directory", "N/A");
            });
        }

        // Emergency Guides Card
        CardView guidesCard = view.findViewById(R.id.card_emergency_guides);
        if (guidesCard != null) {
            guidesCard.setOnClickListener(v -> {
                navigateToFragment(new EmergencyGuides());
                logActivity("Unknown", "Navigation", "Opened Emergency Guides", "Emergency Resources", "Success", "User accessed emergency guides", "N/A");
            });
        }
    }

    private void navigateToFragment(Fragment fragment) {
        if (getActivity() != null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private void setupHotlineClick(View parentView, int layoutId, String phoneNumber, String serviceName,
                                   int iconResId, String description, String displayPhone, String address) {
        LinearLayout layout = parentView.findViewById(layoutId);
        if (layout != null) {
            layout.setOnClickListener(v -> {
                showHotlineInfoDialog(serviceName, iconResId, description, displayPhone, phoneNumber, address);
            });
        }
    }

    private void showHotlineInfoDialog(String serviceName, int iconResId, String description,
                                       String displayPhone, String dialPhone, String address) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_hotline_info);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Set dialog content
        ImageView icon = dialog.findViewById(R.id.dialog_icon);
        TextView name = dialog.findViewById(R.id.dialog_service_name);
        TextView desc = dialog.findViewById(R.id.dialog_description);
        TextView phone = dialog.findViewById(R.id.dialog_phone);
        TextView addr = dialog.findViewById(R.id.dialog_address);
        Button closeBtn = dialog.findViewById(R.id.dialog_btn_close);
        Button callBtn = dialog.findViewById(R.id.dialog_btn_call);

        icon.setImageResource(iconResId);
        name.setText(serviceName);
        desc.setText(description);
        phone.setText(displayPhone);
        addr.setText(address);

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        callBtn.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + dialPhone));
            startActivity(callIntent);
            logActivity("Unknown", "Phone Call", "Dialed", serviceName + " - " + displayPhone, "Success", "User initiated a phone call", "N/A");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupQuickAccessListeners(View view) {
        // Home

        // Traffic Map
        view.findViewById(R.id.quick_map).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapDash.class);
            startActivity(intent);
        });

        // Service
        view.findViewById(R.id.quick_service).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), services.class);
            startActivity(intent);
        });

        // Report History
        view.findViewById(R.id.quick_history).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                Intent intent = new Intent(getActivity(), ReportHistoryActivity.class);
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
            Intent intent = new Intent(getActivity(), contactus.class);
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
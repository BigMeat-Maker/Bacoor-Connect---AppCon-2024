package com.example.bacoorconnect.Emergency;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;

import com.example.bacoorconnect.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class EmergencyHospitals extends Fragment {

    private DatabaseReference auditRef;
    private FloatingActionButton scrolltotopBtn;
    private NestedScrollView nestedScrollView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency_hospitals, container, false);

        nestedScrollView = view.findViewById(R.id.nestedScrollView);
        scrolltotopBtn = view.findViewById(R.id.scrollToTopBtn);

        auditRef = FirebaseDatabase.getInstance().getReference("audit_trail");
        logActivity("Unknown", "Navigation", "Opened Emergency Hospitals", "Emergency Resources", "Success", "user accessed the emergency resources: hospitals page", "N/A");


        scrolltotopBtn.setOnClickListener(v -> {
            nestedScrollView.smoothScrollTo(0,0);
        });

        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > 600) {
                        scrolltotopBtn.show();
                    } else {
                        scrolltotopBtn.hide();
                    }
                }
        );

        nestedScrollView.post(() -> {
            if (nestedScrollView.getScrollY() > 600) {
                scrolltotopBtn.show();
            } else {
                scrolltotopBtn.hide();
            }
        });


        // hospitals call
        setUpPhoneCall(view, R.id.BDMC_Call, "(046)4166275");
        setUpPhoneCall(view, R.id.CGH_Call, "(046)4348239");
        setUpPhoneCall(view, R.id.MSMC_Call, "(046)4773087");
        setUpPhoneCall(view, R.id.MDH_Call, "(046)4770830");
        setUpPhoneCall(view, R.id.PGCMCI_Call, "(046)9705314");
        setUpPhoneCall(view, R.id.SCHMC_Call, "(02)82499100");
        setUpPhoneCall(view, R.id.SEAMC_Call, "09452259136");
        setUpPhoneCall(view, R.id.STRH_Call, "(046)4183644");
        setUpPhoneCall(view, R.id.SDMC_Call, "(046)4172520");
        setUpPhoneCall(view, R.id.SMMH_Call, "(046)4771707");


        // hospitals loc
        setUpLocation(view, R.id.BDMC_Location, "Bacoor Doctors Medical Center, Bacoor Boulevard");
        setUpLocation(view, R.id.CGH_Location, "CWXM+M8R, General Tirona Highway, Bacoor, 4102 Cavite");
        setUpLocation(view, R.id.MSMC_Location, "9XFH+GW Bacoor, Cavite");
        setUpLocation(view, R.id.MDH_Location, "201 Molino Rd, Bacoor, Cavite");
        setUpLocation(view, R.id.PGCMCI_Location, "9XXF+5Q Bacoor, Cavite");
        setUpLocation(view, R.id.SCHMC_Location, "115 Daang Hari Road, Molino, Bacoor, 4102 Cavite");
        setUpLocation(view, R.id.SEAMC_Location, "CX4G+9P Bacoor, Cavite");
        setUpLocation(view, R.id.STRH_Location, "CWRW+HW Bacoor, Cavite");
        setUpLocation(view, R.id.SDMC_Location, "FX56+M7X, Bacoor, Cavite");
        setUpLocation(view, R.id.SMMH_Location, "212 molino 2 proper, Bacoor, 4102 Cavite");


        return view;
    }

    private void setUpPhoneCall(View view, int buttonId, String phoneNumber) {
        Button button = view.findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(v -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(callIntent);
                logActivity("Unknown", "Phone Call", "Dialed", phoneNumber, "Success", "user initiated a phone call", "N/A");
            });
        }
    }

    private void setUpLocation(View view, int buttonId, String address) {
        Button button = view.findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(v -> {
                String url = "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                logActivity("Location", "Map Open", "Viewed", address, "Success", "user opened location", "N/A");
            });

        }
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

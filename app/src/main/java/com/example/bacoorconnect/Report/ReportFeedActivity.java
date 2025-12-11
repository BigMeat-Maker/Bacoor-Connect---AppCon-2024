package com.example.bacoorconnect.Report;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportFeedActivity extends Fragment {
    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<Report> reportList = new ArrayList<>();
    private DatabaseReference reportRef = FirebaseDatabase.getInstance().getReference("Report");
    private double currentLatitude = 14.4597;
    private double currentLongitude = 120.9333;

    public ReportFeedActivity() {
    }

    public static ReportFeedActivity newInstance() {
        return new ReportFeedActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_report_feed, container, false);

        recyclerView = view.findViewById(R.id.reportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        getCurrentLocation();

        // Initialize adapter with location
        adapter = new ReportAdapter(getContext(), reportList, currentLatitude, currentLongitude);
        recyclerView.setAdapter(adapter);

        loadReports();

        return view;
    }

    private void getCurrentLocation() {
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();
            }
        }
    }

    private void loadReports() {
        reportRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reportList.clear();
                for (DataSnapshot reportSnap : snapshot.getChildren()) {
                    Report report = reportSnap.getValue(Report.class);
                    if (report != null) {
                        report.setReportId(reportSnap.getKey());

                        // If coordinates are stored in location string, parse them
                        if ((report.getLatitude() == 0 || report.getLongitude() == 0)
                                && report.getLocation() != null) {
                            report.parseCoordinatesFromLocation();
                        }

                        reportList.add(report);
                    }
                }
                Collections.reverse(reportList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load reports.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
package com.example.bacoorconnect.Emergency;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.R;

import java.util.List;
import java.util.Locale;

public class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.ViewHolder> {

    private List<Hospital> hospitals;
    private Location userLocation;
    private final OnHospitalInteractionListener listener;

    public interface OnHospitalInteractionListener {
        void onCallClicked(Hospital hospital);
        void onLocationClicked(Hospital hospital);
    }

    public HospitalAdapter(List<Hospital> hospitals, Location userLocation, OnHospitalInteractionListener listener) {
        this.hospitals = hospitals;
        this.userLocation = userLocation;
        this.listener = listener;
    }

    public void updateData(List<Hospital> newHospitals, Location newLocation) {
        this.hospitals = newHospitals;
        this.userLocation = newLocation;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hospital, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Hospital hospital = hospitals.get(position);

        holder.hospitalName.setText(hospital.getName());
        holder.hospitalAddress.setText(hospital.getAddress());
        holder.hospitalContact.setText(hospital.getPhoneNumber());
        
        Glide.with(holder.itemView.getContext())
                .load(hospital.getImageUrl())
                .placeholder(R.drawable.drawer_hospital) // fallback placeholder
                .into(holder.hospitalImage);

        if (userLocation != null) {
            Location hospitalLoc = new Location("");
            hospitalLoc.setLatitude(hospital.getLatitude());
            hospitalLoc.setLongitude(hospital.getLongitude());
            
            float distanceInMeters = userLocation.distanceTo(hospitalLoc);
            float distanceInKm = distanceInMeters / 1000f;
            
            holder.hospitalDistance.setVisibility(View.VISIBLE);
            holder.hospitalDistance.setText(String.format(Locale.getDefault(), "%.1f km away", distanceInKm));
        } else {
            holder.hospitalDistance.setVisibility(View.GONE);
        }

        holder.hospitalCallBtn.setOnClickListener(v -> listener.onCallClicked(hospital));
        holder.hospitalLocationBtn.setOnClickListener(v -> listener.onLocationClicked(hospital));
    }

    @Override
    public int getItemCount() {
        return hospitals.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView hospitalName, hospitalAddress, hospitalContact, hospitalDistance;
        ImageView hospitalImage;
        Button hospitalCallBtn, hospitalLocationBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalName = itemView.findViewById(R.id.hospitalName);
            hospitalAddress = itemView.findViewById(R.id.hospitalAddress);
            hospitalContact = itemView.findViewById(R.id.hospitalContact);
            hospitalDistance = itemView.findViewById(R.id.hospitalDistance);
            hospitalImage = itemView.findViewById(R.id.hospitalImage);
            hospitalCallBtn = itemView.findViewById(R.id.hospitalCallBtn);
            hospitalLocationBtn = itemView.findViewById(R.id.hospitalLocationBtn);
        }
    }
}

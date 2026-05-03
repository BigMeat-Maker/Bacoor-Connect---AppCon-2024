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

public class HotlineAdapter extends RecyclerView.Adapter<HotlineAdapter.ViewHolder> {

    private List<Hotline> Hotlines;
    private Location userLocation;
    private final OnHotlineInteractionListener listener;

    public interface OnHotlineInteractionListener {
        void onCallClicked(Hotline Hotline);
        void onLocationClicked(Hotline Hotline);
    }

    public HotlineAdapter(List<Hotline> Hotlines, Location userLocation, OnHotlineInteractionListener listener) {
        this.Hotlines = Hotlines;
        this.userLocation = userLocation;
        this.listener = listener;
    }

    public void updateData(List<Hotline> newHotlines, Location newLocation) {
        this.Hotlines = newHotlines;
        this.userLocation = newLocation;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hotline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Hotline Hotline = Hotlines.get(position);

        holder.HotlineName.setText(Hotline.getName());
        holder.HotlineAddress.setText(Hotline.getAddress());
        holder.HotlineContact.setText(Hotline.getPhoneNumber());
        
        Glide.with(holder.itemView.getContext())
                .load(Hotline.getImageUrl())
                .into(holder.HotlineImage);

        if (userLocation != null) {
            Location HotlineLoc = new Location("");
            HotlineLoc.setLatitude(Hotline.getLatitude());
            HotlineLoc.setLongitude(Hotline.getLongitude());
            
            float distanceInMeters = userLocation.distanceTo(HotlineLoc);
            float distanceInKm = distanceInMeters / 1000f;
            
            holder.HotlineDistance.setVisibility(View.VISIBLE);
            holder.HotlineDistance.setText(String.format(Locale.getDefault(), "%.1f km away", distanceInKm));
        } else {
            holder.HotlineDistance.setVisibility(View.GONE);
        }

        holder.HotlineCallBtn.setOnClickListener(v -> listener.onCallClicked(Hotline));
        holder.HotlineLocationBtn.setOnClickListener(v -> listener.onLocationClicked(Hotline));
    }

    @Override
    public int getItemCount() {
        return Hotlines.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView HotlineName, HotlineAddress, HotlineContact, HotlineDistance;
        ImageView HotlineImage;
        Button HotlineCallBtn, HotlineLocationBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            HotlineName = itemView.findViewById(R.id.HotlineName);
            HotlineAddress = itemView.findViewById(R.id.HotlineAddress);
            HotlineContact = itemView.findViewById(R.id.HotlineContact);
            HotlineDistance = itemView.findViewById(R.id.HotlineDistance);
            HotlineImage = itemView.findViewById(R.id.HotlineImage);
            HotlineCallBtn = itemView.findViewById(R.id.HotlineCallBtn);
            HotlineLocationBtn = itemView.findViewById(R.id.HotlineLocationBtn);
        }
    }
}

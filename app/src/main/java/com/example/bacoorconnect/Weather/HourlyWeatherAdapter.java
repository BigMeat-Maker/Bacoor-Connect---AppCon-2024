package com.example.bacoorconnect.Weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;

import java.util.List;

public class HourlyWeatherAdapter extends RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder> {

    private final List<HourlyForecast> hourlyForecasts;

    public HourlyWeatherAdapter(List<HourlyForecast> hourlyForecasts) {
        this.hourlyForecasts = hourlyForecasts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather_hourly, parent, false);
        return new ViewHolder(view);
    }

    //will change cuz why not coconut
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HourlyForecast forecast = hourlyForecasts.get(position);

        holder.textTime.setText(forecast.getTime());
        holder.textTemperature.setText(String.format("%.1f°", (float) forecast.getTemperature()));

        String timeString = forecast.getTime();
        int hour = Integer.parseInt(timeString.split(":")[0]);


        holder.imageWeatherIconNight.setVisibility(View.GONE);
        holder.imageWeatherIconExtHot.setVisibility(View.GONE);
        holder.imageWeatherIconCloudy.setVisibility(View.GONE);
        holder.imageWeatherIconpartlycloudy.setVisibility(View.GONE);
        holder.imageWeatherIconpartlynightcloudy.setVisibility(View.GONE);

        if (hour >= 6 && hour < 9) { // Morning (6:00 AM - 9:00 AM)
            if (forecast.getTemperature() >= 22 && forecast.getTemperature() <= 29) {
                holder.imageWeatherIconCloudy.setVisibility(View.VISIBLE);
            }
        } else if (hour >= 9 && hour < 12) { // Late Morning (9:00 AM - 12:00 PM)
            if (forecast.getTemperature() >= 26 && forecast.getTemperature() <= 34) {
                holder.imageWeatherIconExtHot.setVisibility(View.VISIBLE);
            }
        } else if (hour >= 12 && hour < 15) { // Afternoon (12:00 PM - 3:00 PM)
            if (forecast.getTemperature() >= 28 && forecast.getTemperature() <= 40) {
                holder.imageWeatherIconExtHot.setVisibility(View.VISIBLE);
            }
        } else if (hour >= 15 && hour < 18) { // Late Afternoon (3:00 PM - 6:00 PM)
            if (forecast.getTemperature() >= 26 && forecast.getTemperature() <= 36) {
                holder.imageWeatherIconCloudy.setVisibility(View.VISIBLE);
            }
        } else if (hour >= 18 && hour < 21) { // Evening (6:00 PM - 9:00 PM)
            if (forecast.getTemperature() >= 24 && forecast.getTemperature() <= 32) {
                holder.imageWeatherIconpartlycloudy.setVisibility(View.VISIBLE);
            }
        } else if (hour >= 21 || hour < 6) { // Night (9:00 PM - 6:00 AM)
            if (forecast.getTemperature() >= 21 && forecast.getTemperature() <= 27) {
                holder.imageWeatherIconNight.setVisibility(View.VISIBLE);
            } else if (forecast.getTemperature() > 27) {
                holder.imageWeatherIconpartlynightcloudy.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return hourlyForecasts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTime, textTemperature;
        ImageView imageWeatherIconNight,
                imageWeatherIconCloudy, imageWeatherIconpartlycloudy,
                imageWeatherIconpartlynightcloudy, imageWeatherIconExtHot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textTime = itemView.findViewById(R.id.textHour);
            textTemperature = itemView.findViewById(R.id.textTemp);
            imageWeatherIconExtHot = itemView.findViewById(R.id.imageWeatherIconExtHot);
            imageWeatherIconNight = itemView.findViewById(R.id.imageWeatherIconNight);
            imageWeatherIconCloudy = itemView.findViewById(R.id.imageWeatherIconCloudy);
            imageWeatherIconpartlycloudy = itemView.findViewById(R.id.imageWeatherIconpartlycloudy);
            imageWeatherIconpartlynightcloudy = itemView.findViewById(R.id.imageWeatherIconpartlynightcloudy);
        }
    }
}
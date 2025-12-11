package com.example.bacoorconnect.Weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyWeatherAdapter extends RecyclerView.Adapter<DailyWeatherAdapter.ViewHolder> {

    private final List<DailyForecast> dailyForecasts;

    public DailyWeatherAdapter(List<DailyForecast> dailyForecasts) {
        this.dailyForecasts = dailyForecasts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather_daily, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyForecast forecast = dailyForecasts.get(position);

        holder.textDate.setText(forecast.getFormattedDateWithDayShort());

        String tempRange = formatTemperatureRange(forecast.getTemperatureMax(), forecast.getTemperatureMin());
        holder.textTemperatureRange.setText(tempRange);

        String condition = getConditionFromCode(forecast.getWeatherCode());

        updateWeatherIcons(holder, condition);
    }

    private String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEEE", Locale.getDefault()); // Full day name

            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return "Monday"; // Fallback
        }
    }

    private String formatTemperatureRange(double max, double min) {
        int maxInt = (int) Math.round(max);
        int minInt = (int) Math.round(min);
        return maxInt + "° / " + minInt + "°";
    }

    private String getConditionFromCode(int weatherCode) {
        switch (weatherCode) {
            case 0: return "Sunny";
            case 1: return "Sunny";
            case 2: return "Partly Cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Cloudy"; // Foggy
            case 51: case 53: case 55: return "Rainy";
            case 56: case 57: return "Rainy"; // Freezing Drizzle
            case 61: case 63: case 65: return "Rainy";
            case 66: case 67: return "Rainy"; // Freezing Rain
            case 71: case 73: case 75: return "Cloudy"; // Snow
            case 77: return "Cloudy"; // Snow Grains
            case 80: case 81: case 82: return "Rainy";
            case 85: case 86: return "Cloudy"; // Snow Showers
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm";
            default: return "Sunny";
        }
    }

    private void updateWeatherIcons(ViewHolder holder, String condition) {
        // Hide all icons first
        holder.imageWeatherIconDay.setVisibility(View.GONE);
        holder.imageWeatherIconCloudy.setVisibility(View.GONE);
        holder.imageWeatherIconrainy.setVisibility(View.GONE);
        holder.imageWeatherIconwindy.setVisibility(View.GONE);
        holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
        holder.imageWeatherIconExtHot.setVisibility(View.GONE);

        // Show only the correct icon
        switch (condition) {
            case "Sunny":
                holder.imageWeatherIconDay.setVisibility(View.VISIBLE);
                break;
            case "Partly Cloudy":
            case "Cloudy":
                holder.imageWeatherIconCloudy.setVisibility(View.VISIBLE);
                break;
            case "Rainy":
                holder.imageWeatherIconrainy.setVisibility(View.VISIBLE);
                break;
            case "Thunderstorm":
                holder.imageWeatherIconthunderstorm.setVisibility(View.VISIBLE);
                break;
            case "Windy":
                holder.imageWeatherIconwindy.setVisibility(View.VISIBLE);
                break;
            case "Extremely Hot":
                holder.imageWeatherIconExtHot.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return dailyForecasts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textDate, textTemperatureRange;
        ImageView imageWeatherIconDay, imageWeatherIconthunderstorm, imageWeatherIconrainy,
                imageWeatherIconwindy, imageWeatherIconCloudy, imageWeatherIconExtHot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textDate = itemView.findViewById(R.id.textDate);
            textTemperatureRange = itemView.findViewById(R.id.textTemperatureRange);
            imageWeatherIconDay = itemView.findViewById(R.id.imageWeatherIconDay);
            imageWeatherIconthunderstorm = itemView.findViewById(R.id.imageWeatherIconthunderstorm);
            imageWeatherIconrainy = itemView.findViewById(R.id.imageWeatherIconrainy);
            imageWeatherIconwindy = itemView.findViewById(R.id.imageWeatherIconwindy);
            imageWeatherIconCloudy = itemView.findViewById(R.id.imageWeatherIconCloudy);
            imageWeatherIconExtHot = itemView.findViewById(R.id.imageWeatherIconExtHot);
        }
    }
}
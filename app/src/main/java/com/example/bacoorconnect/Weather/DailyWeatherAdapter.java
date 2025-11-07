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

    //will change hehhee
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyForecast forecast = dailyForecasts.get(position);

        holder.textDate.setText(forecast.getDate());
        holder.textTemperatureRange.setText(
                String.format("%d° / %d°", forecast.getTemperatureMax(), forecast.getTemperatureMin())
        );

        switch (forecast.getCondition()) {
            case "Sunny":
                holder.imageWeatherIconDay.setVisibility(View.VISIBLE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Partly Cloudy":
                holder.imageWeatherIconCloudy.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Cloudy":
                holder.imageWeatherIconCloudy.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Rainy":
                holder.imageWeatherIconrainy.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Thunderstorm":
                holder.imageWeatherIconthunderstorm.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Windy":
                holder.imageWeatherIconwindy.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
                break;

            case "Extremely Hot":
                holder.imageWeatherIconExtHot.setVisibility(View.VISIBLE);
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                break;

            default:
                holder.imageWeatherIconDay.setVisibility(View.GONE);
                holder.imageWeatherIconCloudy.setVisibility(View.GONE);
                holder.imageWeatherIconrainy.setVisibility(View.GONE);
                holder.imageWeatherIconwindy.setVisibility(View.GONE);
                holder.imageWeatherIconthunderstorm.setVisibility(View.GONE);
                holder.imageWeatherIconExtHot.setVisibility(View.GONE);
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

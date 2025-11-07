package com.example.bacoorconnect.Weather;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;

import java.util.ArrayList;
import java.util.List;

public class WeatherHourly extends Fragment {

    private static final String ARG_HOURLY_LIST = "hourlyList";
    private List<HourlyForecast> hourlyForecasts;

    public WeatherHourly() {
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather_hourly, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewHourly);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        if (hourlyForecasts != null) {
            HourlyWeatherAdapter adapter = new HourlyWeatherAdapter(hourlyForecasts);
            recyclerView.setAdapter(adapter);
        }

        return view;
    }

    public static WeatherHourly newInstance(List<HourlyForecast> hourlyList) {
        WeatherHourly fragment = new WeatherHourly();
        Bundle args = new Bundle();
        args.putSerializable(ARG_HOURLY_LIST, new ArrayList<>(hourlyList));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            hourlyForecasts = (List<HourlyForecast>) getArguments().getSerializable(ARG_HOURLY_LIST);
        }
    }
}
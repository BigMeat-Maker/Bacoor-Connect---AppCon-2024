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

public class WeatherDaily extends Fragment {

    private static final String ARG_DAILY_LIST = "dailyList";
    private List<DailyForecast> dailyForecasts;

    public WeatherDaily() {

    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather_daily, container, false);


        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewDaily);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        if (dailyForecasts != null) {
            DailyWeatherAdapter adapter = new DailyWeatherAdapter(dailyForecasts);
            recyclerView.setAdapter(adapter);
        }

        return view;
    }

    public static WeatherDaily newInstance(List<DailyForecast> dailyList) {
        WeatherDaily fragment = new WeatherDaily();
        Bundle args = new Bundle();
        args.putSerializable(ARG_DAILY_LIST, new ArrayList<>(dailyList));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dailyForecasts = (List<DailyForecast>) getArguments().getSerializable(ARG_DAILY_LIST);
        }
    }
}
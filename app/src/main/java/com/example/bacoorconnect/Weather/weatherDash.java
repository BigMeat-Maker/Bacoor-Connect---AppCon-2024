package com.example.bacoorconnect.Weather;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class weatherDash extends Fragment {

    private final String TAG = "WeatherDebug";

    public weatherDash() {
    }

    public static weatherDash newInstance() {
        return new weatherDash();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_weather_dash, container, false);

        loadDailyWeatherFragment();
        loadHourlyWeatherFragment();

        return view;
    }

    private void loadDailyWeatherFragment() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("WeatherForecast");
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DailyForecast> dailyList = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    DailyForecast forecast = snap.getValue(DailyForecast.class);
                    if (forecast != null) dailyList.add(forecast);
                }
                if (!dailyList.isEmpty() && getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.DailyWeather, WeatherDaily.newInstance(dailyList))
                            .commit();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Daily forecast error: " + error.getMessage());
            }
        });
    }

    private void loadHourlyWeatherFragment() {
        DatabaseReference hourlyRef = FirebaseDatabase.getInstance().getReference("HourlyForecast");
        hourlyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<HourlyForecast> hourlyList = new ArrayList<>();
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    for (DataSnapshot timeSnapshot : dateSnapshot.getChildren()) {
                        HourlyForecast forecast = timeSnapshot.getValue(HourlyForecast.class);
                        if (forecast != null) hourlyList.add(forecast);
                    }
                }
                if (!hourlyList.isEmpty() && getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.HourlyWeather, WeatherHourly.newInstance(hourlyList))
                            .commit();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Hourly forecast error: " + error.getMessage());
            }
        });
    }
}
package com.example.bacoorconnect.Weather;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class weatherDash extends Fragment {

    private TextView weatherTemperature;
    private TextView weatherDate;
    private TextView weatherTime;
    private TextView weatherHeatIndex;
    private TextView weatherHumidity;
    private TextView weatherCondition;
    private ImageView weatherIcon;

    public weatherDash() {
    }

    public static weatherDash newInstance() {
        return new weatherDash();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_weather_dash, container, false);

        initializeViews(view);
        setCurrentDateTime();
        loadCurrentWeatherFromForecast();
        loadDailyWeatherFragment();
        loadHourlyWeatherFragment();

        return view;
    }

    private void initializeViews(View view) {
        weatherTemperature = view.findViewById(R.id.WeatherTemperature);
        weatherDate = view.findViewById(R.id.WeatherDate);
        weatherTime = view.findViewById(R.id.WeatherTime);
        weatherHeatIndex = view.findViewById(R.id.WeatherHeatIndex);
        weatherHumidity = view.findViewById(R.id.WeatherHumidity);
        weatherCondition = view.findViewById(R.id.WeatherCondition);
        weatherIcon = view.findViewById(R.id.WeatherIcon);
    }

    private void setCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
        weatherDate.setText(dateFormat.format(new Date()));

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        weatherTime.setText(timeFormat.format(new Date()));
    }

    private void loadCurrentWeatherFromForecast() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String currentHour = new SimpleDateFormat("HH", Locale.getDefault()).format(new Date());

        if (currentHour.length() == 1) currentHour = "0" + currentHour;
        String firebaseHourKey = currentHour + ":00";

        DatabaseReference hourlyRef = FirebaseDatabase.getInstance().getReference("HourlyForecast");
        String finalCurrentHour = currentHour;
        hourlyRef.child(todayDate).child(firebaseHourKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    updateWeatherData(snapshot);
                } else {
                    findClosestHour(todayDate, finalCurrentHour);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setDefaultWeatherValues();
            }
        });
    }

    private void findClosestHour(String todayDate, String targetHour) {
        int targetHourInt = Integer.parseInt(targetHour);

        DatabaseReference hourlyRef = FirebaseDatabase.getInstance().getReference("HourlyForecast");
        hourlyRef.child(todayDate).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dateSnapshot) {
                int minDiff = 24;
                DataSnapshot closestSnapshot = null;

                for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                    String hourKey = hourSnapshot.getKey();
                    String hourPart = hourKey.split(":")[0];

                    try {
                        int hourInt = Integer.parseInt(hourPart);
                        int diff = Math.abs(hourInt - targetHourInt);
                        if (diff < minDiff) {
                            minDiff = diff;
                            closestSnapshot = hourSnapshot;
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid formats
                    }
                }

                if (closestSnapshot != null) {
                    updateWeatherData(closestSnapshot);
                } else {
                    setDefaultWeatherValues();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setDefaultWeatherValues();
            }
        });
    }

    private void updateWeatherData(DataSnapshot snapshot) {
        Double temperature = snapshot.child("temperature").getValue(Double.class);
        Double apparentTemp = snapshot.child("temperatureApparent").getValue(Double.class);
        Double humidity = snapshot.child("humidity").getValue(Double.class);
        Double precipitation = snapshot.child("precipitation").getValue(Double.class);

        if (temperature != null) {
            weatherTemperature.setText(Math.round(temperature) + "°");
        }

        if (apparentTemp != null) {
            weatherHeatIndex.setText(Math.round(apparentTemp) + "°");
        }

        if (humidity != null) {
            weatherHumidity.setText(Math.round(humidity) + "%");
        }

        setWeatherCondition(precipitation);
    }

    private void setWeatherCondition(Double precipitation) {
        if (precipitation == null) {
            weatherCondition.setText("Unknown");
            weatherIcon.setImageResource(R.drawable.weather_partlycloudy);
            return;
        }

        if (precipitation > 0) {
            weatherCondition.setText("Rainy");
            weatherIcon.setImageResource(R.drawable.weather_rainy);
        } else {
            weatherCondition.setText("Partly Cloudy");
            weatherIcon.setImageResource(R.drawable.weather_partlycloudy);
        }
    }

    private void setDefaultWeatherValues() {
        weatherTemperature.setText("26°");
        weatherHeatIndex.setText("26°");
        weatherHumidity.setText("75%");
        weatherCondition.setText("Partly Cloudy");
        weatherIcon.setImageResource(R.drawable.weather_partlycloudy);
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
                // Handle error silently
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
            }
        });
    }
}
package com.example.bacoorconnect.Weather;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.bacoorconnect.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class weatherDash extends Fragment {

    private static final String TAG = "weatherDash";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2101;

    private TextView weatherTemperature, weatherDate, weatherTime, weatherHeatIndex, weatherHumidity, weatherCondition, weatherLocation;
    private ImageView weatherIcon;
    private FusedLocationProviderClient fusedLocationClient;
    private final OkHttpClient okHttpClient = new OkHttpClient();

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        setCurrentDateTime();
        loadWeatherFromCurrentLocation();
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
        weatherLocation = view.findViewById(R.id.WeatherLocation);
    }

    private void setCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
        weatherDate.setText(dateFormat.format(new Date()));

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        weatherTime.setText(timeFormat.format(new Date()));
    }

    private void loadWeatherFromCurrentLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            loadWeatherFromFirebaseFallback();
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            fetchWeatherForCoordinates(location.getLatitude(), location.getLongitude());
                        } else {
                            requestCurrentLocationFix();
                        }
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "Unable to get last location", error);
                        loadWeatherFromFirebaseFallback();
                    });
        } catch (SecurityException exception) {
            Log.w(TAG, "Location permission rejected at runtime", exception);
            loadWeatherFromFirebaseFallback();
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCurrentLocationFix() {
        if (!hasLocationPermission()) {
            loadWeatherFromFirebaseFallback();
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            fetchWeatherForCoordinates(location.getLatitude(), location.getLongitude());
                        } else {
                            loadWeatherFromFirebaseFallback();
                        }
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "Unable to get current location", error);
                        loadWeatherFromFirebaseFallback();
                    });
        } catch (SecurityException exception) {
            Log.w(TAG, "Location permission rejected at runtime", exception);
            loadWeatherFromFirebaseFallback();
        }
    }

    private void fetchWeatherForCoordinates(double latitude, double longitude) {
        weatherLocation.setText(String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude));
        updateLocationName(latitude, longitude);

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.open-meteo.com")
                .addPathSegment("v1")
                .addPathSegment("forecast")
                .addQueryParameter("latitude", String.valueOf(latitude))
                .addQueryParameter("longitude", String.valueOf(longitude))
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code")
                .addQueryParameter("hourly", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,wind_gusts_10m")
                .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,wind_gusts_10m_max,relative_humidity_2m_mean")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "7")
                .build();

        Request request = new Request.Builder().url(url).build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                Log.w(TAG, "Weather API request failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> loadWeatherFromFirebaseFallback());
                }
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> loadWeatherFromFirebaseFallback());
                    }
                    return;
                }

                try {
                    JSONObject root = new JSONObject(response.body().string());
                    JSONObject current = root.optJSONObject("current");
                    List<HourlyForecast> hourlyForecasts = parseHourlyForecasts(root.optJSONObject("hourly"));
                    List<DailyForecast> dailyForecasts = parseDailyForecasts(root.optJSONObject("daily"));

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            updateCurrentWeather(current, hourlyForecasts);
                            showHourlyWeather(hourlyForecasts);
                            showDailyWeather(dailyForecasts);
                        });
                    }
                } catch (Exception parseError) {
                    Log.w(TAG, "Failed parsing weather API response", parseError);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> loadWeatherFromFirebaseFallback());
                    }
                }
            }
        });
    }

    private void updateLocationName(double latitude, double longitude) {
        new Thread(() -> {
            String placeName = String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude);
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    if (address.getLocality() != null && !address.getLocality().isEmpty()) {
                        placeName = address.getLocality();
                    } else if (address.getSubAdminArea() != null && !address.getSubAdminArea().isEmpty()) {
                        placeName = address.getSubAdminArea();
                    }
                }
            } catch (Exception ignored) {
                // Keep coordinate fallback when geocoder is unavailable.
            }

            String finalPlaceName = placeName;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> weatherLocation.setText(finalPlaceName));
            }
        }).start();
    }

    private void updateCurrentWeather(JSONObject current, List<HourlyForecast> hourlyForecasts) {
        if (current == null && !hourlyForecasts.isEmpty()) {
            HourlyForecast first = hourlyForecasts.get(0);
            weatherTemperature.setText(Math.round(first.getTemperature()) + "°");
            weatherHeatIndex.setText(Math.round(first.getTemperatureApparent()) + "°");
            weatherHumidity.setText(first.getHumidity() + "%");
            setWeatherConditionByCode(-1, first.getPrecipitation());
            return;
        }

        double temperature = current != null ? current.optDouble("temperature_2m", Double.NaN) : Double.NaN;
        double apparent = current != null ? current.optDouble("apparent_temperature", Double.NaN) : Double.NaN;
        double humidity = current != null ? current.optDouble("relative_humidity_2m", Double.NaN) : Double.NaN;
        double precipitation = current != null ? current.optDouble("precipitation", 0d) : 0d;
        int weatherCode = current != null ? current.optInt("weather_code", -1) : -1;

        if (!Double.isNaN(temperature)) {
            weatherTemperature.setText(Math.round(temperature) + "°");
        }
        if (!Double.isNaN(apparent)) {
            weatherHeatIndex.setText(Math.round(apparent) + "°");
        }
        if (!Double.isNaN(humidity)) {
            weatherHumidity.setText(Math.round(humidity) + "%");
        }

        setWeatherConditionByCode(weatherCode, precipitation);
    }

    private List<DailyForecast> parseDailyForecasts(JSONObject daily) {
        List<DailyForecast> dailyList = new ArrayList<>();
        if (daily == null) {
            return dailyList;
        }

        JSONArray timeArray = daily.optJSONArray("time");
        JSONArray maxArray = daily.optJSONArray("temperature_2m_max");
        JSONArray minArray = daily.optJSONArray("temperature_2m_min");
        JSONArray precipArray = daily.optJSONArray("precipitation_probability_max");
        JSONArray codeArray = daily.optJSONArray("weather_code");
        JSONArray windArray = daily.optJSONArray("wind_gusts_10m_max");
        JSONArray humidityArray = daily.optJSONArray("relative_humidity_2m_mean");

        if (timeArray == null) {
            return dailyList;
        }

        for (int i = 0; i < timeArray.length(); i++) {
            String date = timeArray.optString(i, "");
            double precip = precipArray != null ? precipArray.optDouble(i, 0d) : 0d;
            double wind = windArray != null ? windArray.optDouble(i, 0d) : 0d;
            double max = maxArray != null ? maxArray.optDouble(i, 0d) : 0d;
            double min = minArray != null ? minArray.optDouble(i, 0d) : 0d;
            int code = codeArray != null ? codeArray.optInt(i, 0) : 0;
            double humidity = humidityArray != null ? humidityArray.optDouble(i, 0d) : 0d;

            dailyList.add(new DailyForecast(date + "T00:00:00Z", precip, wind, max, min, code, humidity));
        }
        return dailyList;
    }

    private List<HourlyForecast> parseHourlyForecasts(JSONObject hourly) {
        List<HourlyForecast> hourlyList = new ArrayList<>();
        if (hourly == null) {
            return hourlyList;
        }

        JSONArray timeArray = hourly.optJSONArray("time");
        JSONArray tempArray = hourly.optJSONArray("temperature_2m");
        JSONArray apparentArray = hourly.optJSONArray("apparent_temperature");
        JSONArray humidityArray = hourly.optJSONArray("relative_humidity_2m");
        JSONArray precipArray = hourly.optJSONArray("precipitation_probability");
        JSONArray windArray = hourly.optJSONArray("wind_gusts_10m");

        if (timeArray == null) {
            return hourlyList;
        }

        int limit = Math.min(timeArray.length(), 24);
        for (int i = 0; i < limit; i++) {
            HourlyForecast forecast = new HourlyForecast();
            String rawTime = timeArray.optString(i, "");
            forecast.setTime(formatHourlyLabel(rawTime));
            forecast.setTemperature(tempArray != null ? tempArray.optDouble(i, 0d) : 0d);
            forecast.setTemperatureApparent(apparentArray != null ? apparentArray.optDouble(i, 0d) : 0d);
            forecast.setHumidity(humidityArray != null ? humidityArray.optInt(i, 0) : 0);
            forecast.setPrecipitation(precipArray != null ? precipArray.optInt(i, 0) : 0);
            forecast.setWindGust(windArray != null ? windArray.optDouble(i, 0d) : 0d);
            hourlyList.add(forecast);
        }
        return hourlyList;
    }

    private String formatHourlyLabel(String rawTime) {
        if (rawTime == null || rawTime.isEmpty()) {
            return "00:00";
        }
        if (rawTime.contains("T")) {
            String[] parts = rawTime.split("T");
            if (parts.length > 1) {
                return parts[1].length() >= 5 ? parts[1].substring(0, 5) : parts[1];
            }
        }
        return rawTime.length() >= 5 ? rawTime.substring(0, 5) : rawTime;
    }

    private void showDailyWeather(List<DailyForecast> dailyList) {
        if (!dailyList.isEmpty() && getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.DailyWeather, WeatherDaily.newInstance(dailyList))
                    .commit();
        }
    }

    private void showHourlyWeather(List<HourlyForecast> hourlyList) {
        if (!hourlyList.isEmpty() && getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.HourlyWeather, WeatherHourly.newInstance(hourlyList))
                    .commit();
        }
    }

    private void setWeatherConditionByCode(int weatherCode, double precipitation) {
        if (weatherCode == 0 || weatherCode == 1) {
            weatherCondition.setText("Clear");
            weatherIcon.setImageResource(R.drawable.weather_sunny);
            return;
        }
        if (weatherCode == 2 || weatherCode == 3 || weatherCode == 45 || weatherCode == 48) {
            weatherCondition.setText("Partly Cloudy");
            weatherIcon.setImageResource(R.drawable.weather_partlycloudy);
            return;
        }
        if (weatherCode == 95 || weatherCode == 96 || weatherCode == 99) {
            weatherCondition.setText("Thunderstorm");
            weatherIcon.setImageResource(R.drawable.weather_thunderstorm);
            return;
        }
        if (weatherCode == 61 || weatherCode == 63 || weatherCode == 65 || weatherCode == 80 || weatherCode == 81 || weatherCode == 82 || precipitation > 0d) {
            weatherCondition.setText("Rainy");
            weatherIcon.setImageResource(R.drawable.weather_rainy);
            return;
        }

        weatherCondition.setText("Partly Cloudy");
        weatherIcon.setImageResource(R.drawable.weather_partlycloudy);
    }

    private void loadWeatherFromFirebaseFallback() {
        loadCurrentWeatherFromForecast();
        loadDailyWeatherFragment();
        loadHourlyWeatherFragment();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                loadWeatherFromCurrentLocation();
            } else {
                loadWeatherFromFirebaseFallback();
            }
        }
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
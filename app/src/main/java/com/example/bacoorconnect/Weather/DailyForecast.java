package com.example.bacoorconnect.Weather;

import com.example.bacoorconnect.R;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyForecast implements Serializable {
    private String date;
    private double precipitation;
    private double windGust;
    private double temperatureMax;
    private double temperatureMin;
    private int weatherCode;
    private double humidity;

    public DailyForecast() {}

    public DailyForecast(String date, double precipitation, double windGust,
                         double temperatureMax, double temperatureMin,
                         int weatherCode, double humidity) {
        this.date = date;
        this.precipitation = precipitation;
        this.windGust = windGust;
        this.temperatureMax = temperatureMax;
        this.temperatureMin = temperatureMin;
        this.weatherCode = weatherCode;
        this.humidity = humidity;
    }

    public String getDate() {
        return date;
    }

    public double getPrecipitation() {
        return precipitation;
    }

    public double getWindGust() {
        return windGust;
    }

    public double getTemperatureMax() {
        return temperatureMax;
    }

    public double getTemperatureMin() {
        return temperatureMin;
    }

    public int getWeatherCode() {
        return weatherCode;
    }

    public double getHumidity() {
        return humidity;
    }

    // Get formatted date: Day name only (e.g., "Friday")
    public String getFormattedDay() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

            Date parsedDate = inputFormat.parse(date);
            return dayFormat.format(parsedDate);
        } catch (ParseException e) {
            return "Friday";
        }
    }

    // Get formatted date: Month Day (e.g., "Oct 11")
    public String getFormattedMonthDay() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

            Date parsedDate = inputFormat.parse(date);
            return outputFormat.format(parsedDate);
        } catch (ParseException e) {
            return "Oct 11";
        }
    }

    // OPTION 1: Date then Day in parentheses (e.g., "Oct 11 (Fri)")
    public String getFormattedDateWithDayShort() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
            SimpleDateFormat dayFormat = new SimpleDateFormat("(EEE)", Locale.getDefault());

            Date parsedDate = inputFormat.parse(date);
            return dateFormat.format(parsedDate) + " " + dayFormat.format(parsedDate);
        } catch (ParseException e) {
            return "Oct 11 (Fri)";
        }
    }

    // OPTION 2: Day then Date (e.g., "Friday, Oct 11")
    public String getFormattedDayWithDate() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

            Date parsedDate = inputFormat.parse(date);
            return outputFormat.format(parsedDate);
        } catch (ParseException e) {
            return "Friday, Oct 11";
        }
    }

    // OPTION 3: Short format (e.g., "Fri, Oct 11")
    public String getFormattedShortDate() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

            Date parsedDate = inputFormat.parse(date);
            return outputFormat.format(parsedDate);
        } catch (ParseException e) {
            return "Fri, Oct 11";
        }
    }

    // Get weather condition based on weatherCode
    public String getCondition() {
        switch (weatherCode) {
            case 0: return "Clear";
            case 1: return "Mostly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Light Rain";
            case 56: case 57: return "Freezing Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing Rain";
            case 71: case 73: case 75: return "Snow";
            case 77: return "Snow Grains";
            case 80: case 81: case 82: return "Rain Showers";
            case 85: case 86: return "Snow Showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with Hail";
            default: return "Unknown";
        }
    }

    public int getWeatherIcon() {
        switch (weatherCode) {
            case 0: return R.drawable.weather_sunny;
            case 1: return R.drawable.weather_sunny;
            case 2: return R.drawable.weather_partlycloudy;
            case 3: return R.drawable.weathersunny;
            case 45: case 48: return R.drawable.weather_windy;
            case 51: case 53: case 55: return R.drawable.weather_rainy;
            case 56: case 57: return R.drawable.weather_rainy;
            case 61: case 63: case 65: return R.drawable.weather_rainy;
            case 66: case 67: return R.drawable.weather_rainy;
            case 71: case 73: case 75: return R.drawable.weather_windy;
            case 77: return R.drawable.weather_windy;
            case 80: case 81: case 82: return R.drawable.weather_rainy;
            case 85: case 86: return R.drawable.weather_windy;
            case 95: return R.drawable.weather_thunderstorm;
            case 96: case 99: return R.drawable.weather_thunderstorm;
            default: return R.drawable.weather_sunny;
        }
    }

    public String getFormattedTemperatureRange() {
        int max = (int) Math.round(temperatureMax);
        int min = (int) Math.round(temperatureMin);
        return max + "° / " + min + "°";
    }

    public String getFormattedPrecipitation() {
        int percent = (int) Math.round(precipitation);
        return percent + "%";
    }
}
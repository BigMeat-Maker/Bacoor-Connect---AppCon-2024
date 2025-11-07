package com.example.bacoorconnect.Weather;

import java.io.Serializable;

public class DailyForecast implements Serializable {
    private String date;
    private int precipitation;
    private int windGust;
    private int temperatureMax;
    private int temperatureMin;
    private int weatherCode;
    private int humidity;

    public DailyForecast() {}

    public DailyForecast(String date, int precipitation, int windGust, int temperatureMax, int temperatureMin, int weatherCode, int humidity) {
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

    public int getPrecipitation() {
        return precipitation;
    }

    public int getWindGust() {
        return windGust;
    }

    public int getTemperatureMax() {
        return temperatureMax;
    }

    public int getTemperatureMin() {
        return temperatureMin;
    }

    public int getWeatherCode() {
        return weatherCode;
    }

    public int getHumidity() {
        return humidity;
    }

    public String getCondition() {
        return "";
    }
}
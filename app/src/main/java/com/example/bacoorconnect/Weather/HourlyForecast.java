package com.example.bacoorconnect.Weather;

import android.os.Parcel;
import android.os.Parcelable;

public class HourlyForecast implements Parcelable {
    private int humidity;
    private int precipitation;
    private double temperature;
    private double temperatureApparent;
    private String time;
    private double windGust;

    public HourlyForecast() {
    }

    protected HourlyForecast(Parcel in) {
        humidity = in.readInt();
        precipitation = in.readInt();
        temperature = in.readDouble();
        temperatureApparent = in.readDouble();
        time = in.readString();
        windGust = in.readDouble();
    }

    public static final Creator<HourlyForecast> CREATOR = new Creator<HourlyForecast>() {
        @Override
        public HourlyForecast createFromParcel(Parcel in) {
            return new HourlyForecast(in);
        }

        @Override
        public HourlyForecast[] newArray(int size) {
            return new HourlyForecast[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(humidity);
        dest.writeInt(precipitation);
        dest.writeDouble(temperature);
        dest.writeDouble(temperatureApparent);
        dest.writeString(time);
        dest.writeDouble(windGust);
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public int getPrecipitation() {
        return precipitation;
    }

    public void setPrecipitation(int precipitation) {
        this.precipitation = precipitation;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTemperatureApparent() {
        return temperatureApparent;
    }

    public void setTemperatureApparent(double temperatureApparent) {
        this.temperatureApparent = temperatureApparent;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public double getWindGust() {
        return windGust;
    }

    public void setWindGust(double windGust) {
        this.windGust = windGust;
    }
}

package com.example.bacoorconnect.Emergency;

public class Hotline {
    private String name;
    private String address;
    private String phoneNumber;
    private double latitude;
    private double longitude;
    private String imageUrl;

    public Hotline() {
        // Required empty constructor for Firebase
    }

    public Hotline(String name, String address, String phoneNumber, double latitude, double longitude, String imageUrl) {
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}

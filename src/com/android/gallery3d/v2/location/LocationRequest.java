package com.android.gallery3d.v2.location;

public class LocationRequest {

    private long imageId;
    private double latitude;
    private double longitude;
    private long date;

    private LocationRequest(long imageId, double latitude, double longitude, long date) {
        this.imageId = imageId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = date;
    }

    public static LocationRequest genRequest(long imageId, double latitude, double longitude, long date) {
        return new LocationRequest(imageId, latitude, longitude, date);
    }

    public long getImageId() {
        return imageId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getDate() {
        return date;
    }
}

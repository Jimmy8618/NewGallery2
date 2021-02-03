package com.android.gallery3d.v2.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class LocationAchieverThread extends Thread {
    private static final String TAG = LocationAchieverThread.class.getSimpleName();

    private static LocationAchieverThread sLocationAchiever;

    private Geocoder mGeocoder;

    private ConnectivityManager mConnectivityManager;

    private final LinkedList<LocationRequest> mRequestList = new LinkedList<>();

    private final List<OnLocationParseListener> mOnLocationParseListeners = new ArrayList<>();

    public interface OnLocationParseListener {
        void onLocationParsed(LocationResult result);
    }

    public static LocationAchieverThread getDefault() {
        if (sLocationAchiever == null) {
            synchronized (LocationAchieverThread.class) {
                if (sLocationAchiever == null) {
                    sLocationAchiever = new LocationAchieverThread();
                }
            }
        }
        return sLocationAchiever;
    }

    private LocationAchieverThread() {
        mGeocoder = new Geocoder(GalleryAppImpl.getApplication(), Locale.US);
        mConnectivityManager = (ConnectivityManager) GalleryAppImpl.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        start();
    }

    public void registLocationParseListener(OnLocationParseListener l) {
        synchronized (mOnLocationParseListeners) {
            mOnLocationParseListeners.add(l);
        }
    }

    public void unregistLocationParseListener(OnLocationParseListener l) {
        synchronized (mOnLocationParseListeners) {
            mOnLocationParseListeners.remove(l);
        }
    }

    public void addRequest(LocationRequest request) {
        boolean needNotify = false;
        synchronized (mRequestList) {
            if (mRequestList.isEmpty()) {
                needNotify = true;
            }
            mRequestList.add(request);
        }
        if (needNotify) {
            onDirty();
        }
    }

    public void clearRequest() {
        synchronized (mRequestList) {
            mRequestList.clear();
        }
    }

    private LocationRequest post() {
        synchronized (mRequestList) {
            if (mRequestList.isEmpty()) {
                return null;
            } else {
                return mRequestList.remove();
            }
        }
    }

    private synchronized void onDirty() {
        notifyAll();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        LocationRequest request;
        while (true) {
            request = post();
            synchronized (this) {
                if (request == null) {
                    Log.d(TAG, "Task wait.");
                    Utils.waitWithoutInterrupt(this);
                    continue;
                }
            }
            LocationResult result = parseLocation(request);

            if (result != null) {
                synchronized (mOnLocationParseListeners) {
                    for (OnLocationParseListener l : mOnLocationParseListeners) {
                        l.onLocationParsed(result);
                    }
                }
            }
        }
    }

    private LocationResult parseLocation(LocationRequest request) {
        Log.d(TAG, "parseLocation location ...");
        LocationResult result = null;
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            Log.d(TAG, "parseLocation network not connect!!");
            return null;
        }
        try {
            List<Address> addresses = mGeocoder.getFromLocation(request.getLatitude(), request.getLongitude(), 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                result = new LocationResult();
                result.imageId = request.getImageId();
                result.latitude = request.getLatitude();
                result.longitude = request.getLongitude();
                result.countryCode = address.getCountryCode();
                result.countryName = address.getCountryName();
                result.adminArea = address.getAdminArea();
                result.locality = address.getLocality();
                result.subLocality = address.getSubLocality();
                result.thoroughfare = address.getThoroughfare();
                result.subThoroughfare = address.getSubThoroughfare();
                result.featureName = address.getFeatureName();
                result.date = request.getDate();
            }
        } catch (IOException e) {
            Log.e(TAG, "error in parse location", e);
            return null;
        }
        return result;
    }
}

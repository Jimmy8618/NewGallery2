package com.android.gallery3d.v2.location;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocationTask extends ContentObserver {
    private static final String TAG = LocationTask.class.getSimpleName();

    private LoadTask mLoadTask;

    private ConnectivityManager mConnectivityManager;

    /**
     * Creates a content observer.
     *
     * @param handler The handler to run {@link #onChange} on, or null if none.
     */
    public LocationTask(Handler handler) {
        super(handler);
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                DiscoverStore.LocationInfo.Media.CONTENT_URI,
                true,
                this
        );
        mConnectivityManager = (ConnectivityManager) GalleryAppImpl.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (mLoadTask != null) {
            mLoadTask.notifyDirty();
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        if (mLoadTask == null) {
            mLoadTask = new LoadTask();
            mLoadTask.start();
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        if (mLoadTask != null) {
            mLoadTask.terminate();
            mLoadTask = null;
        }
    }

    private class LoadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Log.d(TAG, "run start.");
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty) {
                        Log.d(TAG, "run wait.");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;

                //////////////////////////begin/////////////////////////////
                List<LocationGroupItem> group = getLocationGroups();
                if (!mActive || mDirty) {
                    continue;
                }

                multiLanguage(group);

                List<LocationInfoItem> items = getUnMatched();
                if (items.size() == 0 || !mActive || mDirty) {
                    continue;
                }

                if (group.size() == 0) {
                    LocationInfoItem locationInfo = items.get(0);
                    insertNewGroup(locationInfo);
                } else {
                    for (LocationInfoItem locationInfo : items) {
                        boolean matched = false;
                        for (LocationGroupItem groupItem : group) {
                            if (!mActive || mDirty) {
                                break;
                            }
                            if (locationInfo.isThisGroup(groupItem)) {
                                matched = true;
                                addToGroup(locationInfo, groupItem);
                                break;
                            }
                        }
                        if (!mActive || mDirty) {
                            break;
                        }
                        if (!matched) {
                            insertNewGroup(locationInfo);
                            break;
                        }
                    }
                }

                ///////////////////////////end//////////////////////////////
            }
            Log.d(TAG, "run end.");
        }

        synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        synchronized void terminate() {
            mActive = false;
            notifyAll();
        }

        private void multiLanguage(List<LocationGroupItem> group) {
            if (group.size() <= 0) {
                return;
            }

            NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) {
                Log.d(TAG, "multiLanguage network not connect!!");
                return;
            }

            String current = Locale.getDefault().getCountry();
            Geocoder geocoder = new Geocoder(GalleryAppImpl.getApplication());

            for (LocationGroupItem item : group) {
                if (!mActive || mDirty) {
                    break;
                }
                if (current == null || !current.equals(item.locale)) {
                    try {
                        List<Address> addresses = geocoder.getFromLocation(item.latitude, item.longitude, 1);
                        if (addresses != null && addresses.size() > 0) {
                            Address address = addresses.get(0);

                            ContentValues values = new ContentValues();
                            values.put(DiscoverStore.LocationGroup.Columns.LOCALE, current);
                            values.put(DiscoverStore.LocationGroup.Columns.LOCALE_COUNTRY_NAME, address.getCountryName());
                            values.put(DiscoverStore.LocationGroup.Columns.LOCALE_ADMIN_AREA, address.getAdminArea());
                            values.put(DiscoverStore.LocationGroup.Columns.LOCALE_LOCALITY, address.getLocality());

                            GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.LocationGroup.Media.CONTENT_URI,
                                    values, DiscoverStore.LocationGroup.Columns._ID + "=" + item.id, null);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "error in multiLanguage", e);
                    }
                }
            }
        }
    }

    private void addToGroup(LocationInfoItem infoItem, LocationGroupItem groupItem) {
        ContentValues matched = new ContentValues();
        matched.put(DiscoverStore.LocationInfo.Columns.LOCATION_GROUP, groupItem.id);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                matched, DiscoverStore.LocationInfo.Columns._ID + "=" + infoItem.id, null);
    }

    private void insertNewGroup(LocationInfoItem item) {
        ContentValues values = new ContentValues();
        values.put(DiscoverStore.LocationGroup.Columns.LOCALE, Locale.US.getCountry());
        values.put(DiscoverStore.LocationGroup.Columns.US_COUNTRY_CODE, item.usCountryCode);
        values.put(DiscoverStore.LocationGroup.Columns.US_COUNTRY_NAME, item.usCountryName);
        values.put(DiscoverStore.LocationGroup.Columns.US_ADMIN_AREA, item.usAdminArea);
        values.put(DiscoverStore.LocationGroup.Columns.US_LOCALITY, item.usLocality);
        values.put(DiscoverStore.LocationGroup.Columns.LOCALE_COUNTRY_NAME, item.usCountryName);
        values.put(DiscoverStore.LocationGroup.Columns.LOCALE_ADMIN_AREA, item.usAdminArea);
        values.put(DiscoverStore.LocationGroup.Columns.LOCALE_LOCALITY, item.usLocality);
        values.put(DiscoverStore.LocationGroup.Columns.LATITUDE, item.latitude);
        values.put(DiscoverStore.LocationGroup.Columns.LONGITUDE, item.longitude);

        Uri uri = GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.LocationGroup.Media.CONTENT_URI, values);
        int group_id = (int) ContentUris.parseId(uri);

        ContentValues matched = new ContentValues();
        matched.put(DiscoverStore.LocationInfo.Columns.LOCATION_GROUP, group_id);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                matched, DiscoverStore.LocationInfo.Columns._ID + "=" + item.id, null);
    }

    private static class LocationGroupItem {
        int id;
        String locale;
        String usCountryCode;
        String usCountryName;
        String usAdminArea;
        String usLocality;
        double latitude;
        double longitude;

        LocationGroupItem(Cursor cursor) {
            id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns._ID));
            locale = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.LOCALE));
            usCountryCode = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.US_COUNTRY_CODE));
            usCountryName = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.US_COUNTRY_NAME));
            usAdminArea = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.US_ADMIN_AREA));
            usLocality = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.US_LOCALITY));
            latitude = cursor.getDouble(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.LATITUDE));
            longitude = cursor.getDouble(cursor.getColumnIndex(DiscoverStore.LocationGroup.Columns.LONGITUDE));
        }
    }

    private ArrayList<LocationGroupItem> getLocationGroups() {
        ArrayList<LocationGroupItem> groups = new ArrayList<>();

        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.LocationGroup.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationGroup.Columns._ID,
                        DiscoverStore.LocationGroup.Columns.LOCALE,
                        DiscoverStore.LocationGroup.Columns.US_COUNTRY_CODE,
                        DiscoverStore.LocationGroup.Columns.US_COUNTRY_NAME,
                        DiscoverStore.LocationGroup.Columns.US_ADMIN_AREA,
                        DiscoverStore.LocationGroup.Columns.US_LOCALITY,
                        DiscoverStore.LocationGroup.Columns.LATITUDE,
                        DiscoverStore.LocationGroup.Columns.LONGITUDE
                }, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                groups.add(new LocationGroupItem(cursor));
            }
        }

        Utils.closeSilently(cursor);

        return groups;
    }

    private static class LocationInfoItem {
        int id;
        String usCountryCode;
        String usCountryName;
        String usAdminArea;
        String usLocality;
        double latitude;
        double longitude;

        LocationInfoItem(Cursor cursor) {
            id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns._ID));
            usCountryCode = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.COUNTRY_CODE));
            usCountryName = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.COUNTRY_NAME));
            usAdminArea = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.ADMIN_AREA));
            usLocality = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.LOCALITY));
            latitude = cursor.getDouble(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.LATITUDE));
            longitude = cursor.getDouble(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.LONGITUDE));
        }

        boolean isThisGroup(LocationGroupItem groupItem) {
            return usCountryCode != null && usCountryCode.equals(groupItem.usCountryCode)
                    && usCountryName != null && usCountryName.equals(groupItem.usCountryName)
                    && usAdminArea != null && usAdminArea.equals(groupItem.usAdminArea)
                    && usLocality != null && usLocality.equals(groupItem.usLocality);
        }
    }

    private ArrayList<LocationInfoItem> getUnMatched() {
        ArrayList<LocationInfoItem> itemArrayList = new ArrayList<>();

        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns._ID,
                        DiscoverStore.LocationInfo.Columns.COUNTRY_CODE,
                        DiscoverStore.LocationInfo.Columns.COUNTRY_NAME,
                        DiscoverStore.LocationInfo.Columns.ADMIN_AREA,
                        DiscoverStore.LocationInfo.Columns.LOCALITY,
                        DiscoverStore.LocationInfo.Columns.LATITUDE,
                        DiscoverStore.LocationInfo.Columns.LONGITUDE
                }, DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " < 0", null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                itemArrayList.add(new LocationInfoItem(cursor));
            }
        }

        Utils.closeSilently(cursor);

        return itemArrayList;
    }
}

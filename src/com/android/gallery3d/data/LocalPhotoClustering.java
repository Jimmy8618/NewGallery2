package com.android.gallery3d.data;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * Created by rui.li on 2016-12-1.
 */

public class LocalPhotoClustering extends TimeClustering {
    private final static String TAG = "LocalPhotoClustering";

    public LocalPhotoClustering(Context context) {
        super(context);
    }

    @Override
    public void run(MediaSet baseSet) {
        final double[] latLng = new double[2];
        final ArrayList<SmallItem> items = new ArrayList<SmallItem>();

        if (!SprdMediaLoader.RE_QUERY) {
            Log.d(TAG, "LocalPhotoClustering enumerateLocalPhoto B.");
            baseSet.enumerateLocalPhoto(new MediaSet.ItemConsumer() {

                @Override
                public void consume(int index, MediaItem item) {
                    if (index < 0 || item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
                        return;
                    }
                    SmallItem s = new SmallItem();
                    s.path = item.getPath();
                    s.dateInMs = item.getDateInMs();//item.getDateAddedInSec()*1000;
                    item.getLatLong(latLng);
                    s.lat = latLng[0];
                    s.lng = latLng[1];
                    s.name = mSimpleDateFormat.format(new Date(s.dateInMs));
                    items.add(s);
                }
            });
            Log.d(TAG, "LocalPhotoClustering enumerateLocalPhoto E.");
        } else {
            Log.d(TAG, "LocalPhotoClustering enumerateLocalImages B.");
            SprdMediaLoader.enumerateLocalImages(mContext, new SprdMediaLoader.MediaConsumer() {
                @Override
                public void consume(Path path, long dateInMs, long modifyDateInSec, double latitude, double longtitude) {
                    SmallItem s = new SmallItem();
                    s.path = path;
                    s.dateInMs = dateInMs;
                    if (dateInMs == 0) {
                        s.dateInMs = modifyDateInSec * 1000;
                    }
                    s.lat = latitude;
                    s.lng = longtitude;
                    s.name = mSimpleDateFormat.format(new Date(s.dateInMs));
                    items.add(s);
                }
            });
            Log.d(TAG, "LocalPhotoClustering enumerateLocalImages E.");
        }

        Collections.sort(items, sDateComparator);

        /*int j = 0;
        Log.d(TAG, "run after sort:");
        for (SmallItem item : items) {
            Log.d(TAG, (j++) +" : "+formatTimeInMillis(item.dateInMs));
        }*/

        int n = items.size();
        long minTime = 0;
        long maxTime = 0;
        for (int i = 0; i < n; i++) {
            long t = items.get(i).dateInMs;
            if (t == 0) {
                continue;
            }
            if (minTime == 0) {
                minTime = maxTime = t;
            } else {
                minTime = Math.min(minTime, t);
                maxTime = Math.max(maxTime, t);
            }
        }
        /*Log.d(TAG, "run n="+n+", minTime="+formatTimeInMillis(minTime)+", " +
                "maxTime="+formatTimeInMillis(maxTime));*/

        setTimeRange(maxTime - minTime, n);

        if (!SprdMediaLoader.RE_QUERY) {
            Log.d(TAG, "LocalPhotoClustering run compute B.");
            for (int i = 0; i < n; i++) {
                compute(items.get(i));
            }

            compute(null);
            Log.d(TAG, "LocalPhotoClustering run compute E.");
        } else {
            Log.d(TAG, "LocalPhotoClustering run compute B.");
            compute2(items);
            Log.d(TAG, "LocalPhotoClustering run compute E.");
        }

        int m = mClusters.size();
        mNames = new String[m];
        for (int i = 0; i < m; i++) {
            mNames[i] = mClusters.get(i).generateCaption(mContext);
        }
        mDates = new String[m];
        for (int i = 0; i < m; i++) {
            mDates[i] = mClusters.get(i).generateDate();
        }
    }
}

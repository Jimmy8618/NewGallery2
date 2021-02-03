/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.data;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;

public class TimeClustering extends Clustering {
    @SuppressWarnings("unused")
    private static final String TAG = "TimeClustering";

    // If 2 items are greater than 25 miles apart, they will be in different
    // clusters.
    private static final int GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES = 20;

    // Do not want to split based on anything under 1 min.
    private static final long MIN_CLUSTER_SPLIT_TIME_IN_MS = 0;//60000L;

    // Disregard a cluster split time of anything over 2 hours.
    private static final long MAX_CLUSTER_SPLIT_TIME_IN_MS = 86400000L;//7200000L;

    // Try and get around 9 clusters (best-effort for the common case).
    private static final int NUM_CLUSTERS_TARGETED = 9;

    // Try and merge 2 clusters if they are both smaller than min cluster size.
    // The min cluster size can range from 8 to 15.
    private static final int MIN_MIN_CLUSTER_SIZE = 8;
    private static final int MAX_MIN_CLUSTER_SIZE = 15;

    // Try and split a cluster if it is bigger than max cluster size.
    // The max cluster size can range from 20 to 50.
    private static final int MIN_MAX_CLUSTER_SIZE = 20;
    private static final int MAX_MAX_CLUSTER_SIZE = 50;

    // Initially put 2 items in the same cluster as long as they are within
    // 3 cluster frequencies of each other.
    private static int CLUSTER_SPLIT_MULTIPLIER = 3;

    // The minimum change factor in the time between items to consider a
    // partition.
    // Example: (Item 3 - Item 2) / (Item 2 - Item 1).
    private static final int MIN_PARTITION_CHANGE_FACTOR = 2;

    // Make the cluster split time of a large cluster half that of a regular
    // cluster.
    private static final int PARTITION_CLUSTER_SPLIT_TIME_FACTOR = 2;

    protected Context mContext;
    protected ArrayList<Cluster> mClusters;
    protected String[] mNames;

    protected String[] mDates;
    private Cluster mCurrCluster;

    private long mClusterSplitTime =
            (MIN_CLUSTER_SPLIT_TIME_IN_MS + MAX_CLUSTER_SPLIT_TIME_IN_MS) / 2;
    private long mLargeClusterSplitTime =
            mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;
    private int mMinClusterSize = (MIN_MIN_CLUSTER_SIZE + MAX_MIN_CLUSTER_SIZE) / 2;
    private int mMaxClusterSize = (MIN_MAX_CLUSTER_SIZE + MAX_MAX_CLUSTER_SIZE) / 2;

    protected static final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    protected static final Comparator<SmallItem> sDateComparator =
            new DateComparator();

    private static class DateComparator implements Comparator<SmallItem> {
        @Override
        public int compare(SmallItem item1, SmallItem item2) {
            return -Utils.compare(item1.dateInMs, item2.dateInMs);
        }
    }

    public TimeClustering(Context context) {
        mContext = context;
        mClusters = new ArrayList<Cluster>();
        mCurrCluster = new Cluster();
    }

    public static String formatTimeInMillis(long timeInMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeInMillis);
        Date date = cal.getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
        String fmt = dateFormat.format(date);

        return fmt;
    }

    @Override
    public void run(MediaSet baseSet) {
        Log.d(TAG, "TimeClustering run B.");
        final ArrayList<SmallItem> items;

        if (!SprdMediaLoader.RE_QUERY) {
            Log.d(TAG, "TimeClustering run enumerateTotalMediaItems B.");
            final int total = baseSet.getTotalMediaItemCount();
            final SmallItem[] buf = new SmallItem[total];
            final double[] latLng = new double[2];

            baseSet.enumerateTotalMediaItems(new MediaSet.ItemConsumer() {
                @Override
                public void consume(int index, MediaItem item) {
                    if (index < 0 || index >= total) {
                        return;
                    }
                    SmallItem s = new SmallItem();
                    s.path = item.getPath();
                    s.dateInMs = item.getDateInMs();//item.getDateAddedInSec()*1000;
                    item.getLatLong(latLng);
                    s.lat = latLng[0];
                    s.lng = latLng[1];
                    s.name = mSimpleDateFormat.format(new Date(s.dateInMs));
                    buf[index] = s;
                }
            });

            items = new ArrayList<SmallItem>(total);
            for (int i = 0; i < total; i++) {
                if (buf[i] != null) {
                    items.add(buf[i]);
                }
            }
            Log.d(TAG, "TimeClustering run enumerateTotalMediaItems E.");
        } else {
            Log.d(TAG, "TimeClustering run enumerateTotalMedias B.");
            items = new ArrayList<SmallItem>();
            SprdMediaLoader.enumerateTotalMedias(mContext, new SprdMediaLoader.MediaConsumer() {
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
                    // Log.d(TAG, "TimeClustering run dateInMs = " + dateInMs+ " s.path = "+ s.path + "  modifyDateInMs = " + modifyDateInMs);
                    items.add(s);
                }
            });
            Log.d(TAG, "TimeClustering run enumerateTotalMedias E.");
        }

        Collections.sort(items, sDateComparator);
        int j = 0;
//        for (SmallItem item : items) {
//            Log.d(TAG, "item "+ (j++) +" : "+formatTimeInMillis(item.dateInMs));
//        }

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
        Log.d(TAG, "run n=" + n + ", minTime=" + formatTimeInMillis(minTime) + ", " +
                "maxTime=" + formatTimeInMillis(maxTime));

        setTimeRange(maxTime - minTime, n);

        if (!SprdMediaLoader.RE_QUERY) {
            Log.d(TAG, "TimeClustering run compute B.");
            for (int i = 0; i < n; i++) {
                compute(items.get(i));
            }

            compute(null);
            Log.d(TAG, "TimeClustering run compute E.");
        } else {
            Log.d(TAG, "TimeClustering run compute B.");
            compute2(items);
            Log.d(TAG, "TimeClustering run compute E.");
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
        Log.d(TAG, "TimeClustering run E.");
    }

    protected void compute2(ArrayList<SmallItem> items) {
        mCurrCluster = new Cluster();
        if (items.size() == 1) {
            mCurrCluster.addItem(items.get(0));
            mClusters.add(mCurrCluster);
        } else if (items.size() > 1) {
            SmallItem item0 = items.get(0);
            mCurrCluster.addItem(item0);
            mClusters.add(mCurrCluster);

            SmallItem itemEnu;
            for (int i = 1; i < items.size(); i++) {
                itemEnu = items.get(i);
                if (itemEnu.name.equals(item0.name)) {
                    mCurrCluster.addItem(itemEnu);
                } else {
                    item0 = itemEnu;
                    mCurrCluster = new Cluster();
                    mCurrCluster.addItem(item0);
                    mClusters.add(mCurrCluster);
                }
            }
        }
    }

    @Override
    public int getNumberOfClusters() {
        return mClusters.size();
    }

    @Override
    public ArrayList<Path> getCluster(int index) {
        ArrayList<SmallItem> items = mClusters.get(index).getItems();
        ArrayList<Path> result = new ArrayList<Path>(items.size());
        for (int i = 0, n = items.size(); i < n; i++) {
            result.add(items.get(i).path);
        }
        return result;
    }

    @Override
    public String getClusterName(int index) {
        return mNames[index];
    }

    @Override
    public String getClusterDate(int index) {
        return mDates[index];
    }

    protected void setTimeRange(long timeRange, int numItems) {
        if (numItems != 0) {
            int meanItemsPerCluster = numItems / NUM_CLUSTERS_TARGETED;
            // Heuristic to get min and max cluster size - half and double the
            // desired items per cluster.
            mMinClusterSize = meanItemsPerCluster / 2;
            mMaxClusterSize = meanItemsPerCluster * 2;
            mClusterSplitTime = timeRange / numItems * CLUSTER_SPLIT_MULTIPLIER;
        }
        mClusterSplitTime = Utils.clamp(mClusterSplitTime, MIN_CLUSTER_SPLIT_TIME_IN_MS, MAX_CLUSTER_SPLIT_TIME_IN_MS);
        mLargeClusterSplitTime = mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;
        mMinClusterSize = Utils.clamp(mMinClusterSize, MIN_MIN_CLUSTER_SIZE, MAX_MIN_CLUSTER_SIZE);
        mMaxClusterSize = Utils.clamp(mMaxClusterSize, MIN_MAX_CLUSTER_SIZE, MAX_MAX_CLUSTER_SIZE);
    }

    protected void compute(SmallItem currentItem) {
        if (currentItem != null) {
            Log.d(TAG, "compute start currentItem=" + formatTimeInMillis(currentItem.dateInMs));
            int numClusters = mClusters.size();
            int numCurrClusterItems = mCurrCluster.size();
            boolean geographicallySeparateItem = false;
            boolean itemAddedToCurrentCluster = false;

            // Determine if this item should go in the current cluster or be the
            // start of a new cluster.
            if (numCurrClusterItems == 0) {
                mCurrCluster.addItem(currentItem);
            } else {
                SmallItem prevItem = mCurrCluster.getLastItem();
                /*if (isGeographicallySeparated(prevItem, currentItem)) {
                    mClusters.add(mCurrCluster);
                    geographicallySeparateItem = true;
                } else if (numCurrClusterItems > mMaxClusterSize) {
                    splitAndAddCurrentCluster();
                } else if (timeDistance(prevItem, currentItem) < mClusterSplitTime) {
                    mCurrCluster.addItem(currentItem);
                    itemAddedToCurrentCluster = true;
                } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                        && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                    mergeAndAddCurrentCluster();
                } else {
                    mClusters.add(mCurrCluster);
                }*/

                if (isSameDate(prevItem, currentItem)) {
//                    Log.d(TAG, "compute mCurrCluster.addItem 2 "+formatTimeInMillis(currentItem
//                            .dateInMs));
                    mCurrCluster.addItem(currentItem);
                    itemAddedToCurrentCluster = true;
                } else {
                    mClusters.add(mCurrCluster);
                }

                // Creating a new cluster and adding the current item to it.
                if (!itemAddedToCurrentCluster) {
                    mCurrCluster = new Cluster();
                    /*if (geographicallySeparateItem) {
                        mCurrCluster.mGeographicallySeparatedFromPrevCluster = true;
                    }*/
                    mCurrCluster.addItem(currentItem);
                }
            }
        } else {
            if (mCurrCluster.size() > 0) {
                int numClusters = mClusters.size();
                int numCurrClusterItems = mCurrCluster.size();

                // The last cluster may potentially be too big or too small.
                /*if (numCurrClusterItems > mMaxClusterSize) {
                    splitAndAddCurrentCluster();
                } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                        && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                    mergeAndAddCurrentCluster();
                } else {
                    mClusters.add(mCurrCluster);
                }*/
                mClusters.add(mCurrCluster);
                mCurrCluster = new Cluster();
            }
        }
    }

    private void splitAndAddCurrentCluster() {
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        int secondPartitionStartIndex = getPartitionIndexForCurrentCluster();
        if (secondPartitionStartIndex != -1) {
            Cluster partitionedCluster = new Cluster();
            for (int j = 0; j < secondPartitionStartIndex; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
            partitionedCluster = new Cluster();
            for (int j = secondPartitionStartIndex; j < numCurrClusterItems; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    private int getPartitionIndexForCurrentCluster() {
        int partitionIndex = -1;
        float largestChange = MIN_PARTITION_CHANGE_FACTOR;
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        int minClusterSize = mMinClusterSize;

        // Could be slightly more efficient here but this code seems cleaner.
        if (numCurrClusterItems > minClusterSize + 1) {
            for (int i = minClusterSize; i < numCurrClusterItems - minClusterSize; i++) {
                SmallItem prevItem = currClusterItems.get(i - 1);
                SmallItem currItem = currClusterItems.get(i);
                SmallItem nextItem = currClusterItems.get(i + 1);

                long timeNext = nextItem.dateInMs;
                long timeCurr = currItem.dateInMs;
                long timePrev = prevItem.dateInMs;

                if (timeNext == 0 || timeCurr == 0 || timePrev == 0) {
                    continue;
                }

                long diff1 = Math.abs(timeNext - timeCurr);
                long diff2 = Math.abs(timeCurr - timePrev);

                float change = Math.max(diff1 / (diff2 + 0.01f), diff2 / (diff1 + 0.01f));
                if (change > largestChange) {
                    if (timeDistance(currItem, prevItem) > mLargeClusterSplitTime) {
                        partitionIndex = i;
                        largestChange = change;
                    } else if (timeDistance(nextItem, currItem) > mLargeClusterSplitTime) {
                        partitionIndex = i + 1;
                        largestChange = change;
                    }
                }
            }
        }
        return partitionIndex;
    }

    private void mergeAndAddCurrentCluster() {
        int numClusters = mClusters.size();
        Cluster prevCluster = mClusters.get(numClusters - 1);
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        if (prevCluster.size() < mMinClusterSize) {
            for (int i = 0; i < numCurrClusterItems; i++) {
                prevCluster.addItem(currClusterItems.get(i));
            }
            mClusters.set(numClusters - 1, prevCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    // Returns true if a, b are sufficiently geographically separated.
    private static boolean isGeographicallySeparated(SmallItem itemA, SmallItem itemB) {
        if (!GalleryUtils.isValidLocation(itemA.lat, itemA.lng)
                || !GalleryUtils.isValidLocation(itemB.lat, itemB.lng)) {
            return false;
        }

        double distance = GalleryUtils.fastDistanceMeters(
                Math.toRadians(itemA.lat),
                Math.toRadians(itemA.lng),
                Math.toRadians(itemB.lat),
                Math.toRadians(itemB.lng));
        return (GalleryUtils.toMile(distance) > GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES);
    }

    // Returns the time interval between the two items in milliseconds.
    private static long timeDistance(SmallItem a, SmallItem b) {
        return Math.abs(a.dateInMs - b.dateInMs);
    }

    private boolean isSameDate(SmallItem a, SmallItem b) {
        return TimeUtil.isSameDayOfMillis(a.dateInMs, b.dateInMs);
    }

    private static class TimeUtil {
        public static final int SECONDS_IN_DAY = 60 * 60 * 24;
        public static final long MILLIS_IN_DAY = 1000L * SECONDS_IN_DAY;

        public static boolean isSameDayOfMillis(final long ms1, final long ms2) {
            final long interval = ms1 - ms2;
            return interval < MILLIS_IN_DAY
                    && interval > -1L * MILLIS_IN_DAY
                    && toDay(ms1) == toDay(ms2);
        }

        private static long toDay(long millis) {
            long l = (millis + TimeZone.getDefault().getOffset(millis)) / MILLIS_IN_DAY;
//            Log.d(TAG, "TimeUtil.toDay "+formatTimeInMillis(millis)+", toDay="+l);
            return l;
        }
    }
}

class SmallItem {
    Path path;
    long dateInMs;
    double lat, lng;
    String name;
}

class Cluster {
    @SuppressWarnings("unused")
    private static final String TAG = "Cluster";
    private static final String MMDDYY_FORMAT = "yyyy-MM-dd";//"MMddyy";

    // This is for TimeClustering only.
    public boolean mGeographicallySeparatedFromPrevCluster = false;

    private ArrayList<SmallItem> mItems = new ArrayList<SmallItem>();

    public Cluster() {
    }

    public void addItem(SmallItem item) {
        mItems.add(item);
    }

    public int size() {
        return mItems.size();
    }

    public SmallItem getLastItem() {
        int n = mItems.size();
        return (n == 0) ? null : mItems.get(n - 1);
    }

    public ArrayList<SmallItem> getItems() {
        return mItems;
    }

    public String generateCaption(Context context) {
        int n = mItems.size();
        long minTimestamp = 0;
        long maxTimestamp = 0;

        for (int i = 0; i < n; i++) {
            long t = mItems.get(i).dateInMs;
            if (t == 0) {
                continue;
            }
            if (minTimestamp == 0) {
                minTimestamp = maxTimestamp = t;
            } else {
                minTimestamp = Math.min(minTimestamp, t);
                maxTimestamp = Math.max(maxTimestamp, t);
            }
        }
        if (minTimestamp == 0) {
            return "";
        }

        String caption;
        String minDay = DateFormat.format(MMDDYY_FORMAT, minTimestamp)
                .toString();
        String maxDay = DateFormat.format(MMDDYY_FORMAT, maxTimestamp)
                .toString();
        Log.d(TAG, "generateCaption minDay=" + minDay + ", maxDay=" + maxDay);
        if (minDay.substring(4).equals(maxDay.substring(4))) {
            // The items are from the same year - show at least as
            // much granularity as abbrev_all allows.
            /*caption = DateUtils.formatDateRange(context, minTimestamp,
                    maxTimestamp, DateUtils.FORMAT_NUMERIC_DATE);

            // Get a more granular date range string if the min and
            // max timestamp are on the same day and from the
            // current year.
            if (minDay.equals(maxDay)) {
                int flags = DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE;
                // Contains the year only if the date does not
                // correspond to the current year.
                String dateRangeWithOptionalYear = DateUtils.formatDateTime(
                        context, minTimestamp, flags);
                String dateRangeWithYear = DateUtils.formatDateTime(
                        context, minTimestamp, flags | DateUtils.FORMAT_SHOW_YEAR);
                if (!dateRangeWithOptionalYear.equals(dateRangeWithYear)) {
                    // This means both dates are from the same year
                    // - show the time.
                    // Not enough room to display the time range.
                    // Pick the mid-point.
                    long midTimestamp = (minTimestamp + maxTimestamp) / 2;
                    caption = DateUtils.formatDateRange(context, midTimestamp,
                            midTimestamp, DateUtils.FORMAT_SHOW_TIME | flags);
                    Log.d(TAG, "generateCaption #2 caption="+caption);
                }
            }*/
            caption = com.android.gallery3d.util.DateUtils.timeStringWithDateInMs(context, minTimestamp);//new String(maxDay);
            Log.d(TAG, "generateCaption #1 caption=" + caption);
        } else {
            // The items are not from the same year - only show
            // month and year.
            int flags = DateUtils.FORMAT_NO_MONTH_DAY
                    | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE;
            caption = DateUtils.formatDateRange(context, minTimestamp,
                    maxTimestamp, flags);
            Log.d(TAG, "generateCaption #3 caption=" + caption);
        }

        return caption;
    }

    public String generateDate() {
        if (mItems.size() < 1) {
            return "";
        }
        return mItems.get(0).name;
    }
}

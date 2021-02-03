package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.sprd.frameworks.StandardFrameworks;

import java.util.ArrayList;

public class LocationAlbumSet extends MediaSet implements FutureListener<ArrayList<MediaSet>> {
    private static final String TAG = LocationAlbumSet.class.getSimpleName();

    public static final Path PATH_ALBUM_SET = Path.fromString("/discover/location/albumset");

    private static final int[] placeHolderId = new int[]{
            R.drawable.blank_fuji,
            R.drawable.blank_island,
            R.drawable.blank_london,
            R.drawable.blank_netherlands,
            R.drawable.blank_paris,
            R.drawable.blank_roma,
            R.drawable.blank_sfo,
            R.drawable.blank_more
    };

    private static final Uri[] mWatchUris = {
            DiscoverStore.LocationInfo.Media.CONTENT_URI,
            DiscoverStore.LocationGroup.Media.CONTENT_URI,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    };

    private final GalleryApp mApplication;
    private ArrayList<MediaSet> mAlbums = new ArrayList<>();
    private final ChangeNotifier mNotifier;
    private boolean mIsLoading;
    private String mOrderClause;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    public LocationAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
        mOrderClause = MediaStore.Images.ImageColumns.DATE_MODIFIED + " DESC, "
                + MediaStore.Images.ImageColumns._ID + " DESC";
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mApplication.getResources().getString(
                R.string.tf_discover_location);
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        public ArrayList<MediaSet> run(ThreadPool.JobContext jc) {
            DataManager dataManager = mApplication.getDataManager();
            ArrayList<MediaSet> albums = new ArrayList<>();

            ArrayList<Integer> groups = getLocationGroupIds();

            for (int i = 0; i < groups.size(); i++) {
                albums.add(dataManager.getMediaSet(LocationAlbum.PATH_ITEM.getChild(groups.get(i))));
            }
            int size = albums.size();
            for (int i = size; i < placeHolderId.length; i++) {
                albums.add(dataManager.getMediaSet(LocationAlbum.PATH_PLACE_HOLDER_ITEM.getChild(placeHolderId[i])));
            }
            return albums;
        }
    }

    private ArrayList<Integer> getLocationGroupIds() {
        ArrayList<Integer> locationGroups = new ArrayList<>();

        String where = DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " > 0 and "
                + DiscoverStore.LocationInfo.Columns.IMAGE_ID + " in (" + getImageIdsWithGps() + ")" +
                " group by " + DiscoverStore.LocationInfo.Columns.LOCATION_GROUP;

        Cursor cursor = mApplication.getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns.LOCATION_GROUP
                }, where, null, DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int groupId = cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.LOCATION_GROUP));
                locationGroups.add(groupId);
            }
        }

        Utils.closeSilently(cursor);

        return locationGroups;
    }

    private String getImageIdsWithGps() {
        String imageWhere = MediaStore.Images.ImageColumns.LATITUDE + " is not null and "
                + MediaStore.Images.ImageColumns.LONGITUDE + " is not null and "
                + MediaStore.Images.ImageColumns.LATITUDE + " > 0 and "
                + MediaStore.Images.ImageColumns.LONGITUDE + " > 0";
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            imageWhere += ") AND (" + "(file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null)";
        }

        Cursor cursor = mApplication.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                MediaStore.Images.ImageColumns._ID
        }, imageWhere, null, null);

        StringBuilder sb = new StringBuilder();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID)));
            }
        }

        Utils.closeSilently(cursor);

        return sb.toString();
    }

    @Override
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    @Override
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) {
                mLoadTask.cancel();
            }
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), this);
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public void onFutureDone(Future<ArrayList<MediaSet>> future) {
        synchronized (this) {
            if (mLoadTask != future) {
                return; // ignore, wait for the latest task
            }
            mLoadBuffer = future.get();
            mIsLoading = false;
            if (mLoadBuffer == null) {
                mLoadBuffer = new ArrayList<MediaSet>();
            }
        }
        Log.d(TAG, "onFutureDone notifyContentChanged");
        notifyContentChanged(Uri.parse("content://com.android.gallery3d.v2.discover.data.LocationAlbumSet/onFutureDone"));
    }
}

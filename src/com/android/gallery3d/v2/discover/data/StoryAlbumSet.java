package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

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
import java.util.HashMap;

public class StoryAlbumSet extends MediaSet implements FutureListener<ArrayList<MediaSet>> {
    private static final String TAG = StoryAlbumSet.class.getSimpleName();

    public static final Path PATH_ALBUM_SET = Path.fromString("/discover/story/albumset");

    private static final int AT_LEAST_COUNT = 4;

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

    public StoryAlbumSet(Path path, GalleryApp application) {
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
                R.string.tf_discover_story);
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        public ArrayList<MediaSet> run(ThreadPool.JobContext jc) {
            DataManager dataManager = mApplication.getDataManager();
            ArrayList<MediaSet> albums = new ArrayList<>();

            ArrayList<String> groups = getStoryGroups();

            for (int i = 0; i < groups.size(); i++) {
                albums.add(dataManager.getMediaSet(StoryAlbum.PATH_ITEM.getChild(groups.get(i))));
            }
            int size = albums.size();
            for (int i = size; i < placeHolderId.length; i++) {
                albums.add(dataManager.getMediaSet(StoryAlbum.PATH_PLACE_HOLDER_ITEM.getChild(placeHolderId[i])));
            }
            return albums;
        }
    }

    private ArrayList<String> getStoryGroups() {
        ArrayList<StoryGroupItem> groups = new ArrayList<>();
        HashMap<String, StoryGroupItem> keySet = new HashMap<>();

        String where = DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " > 0 and "
                + DiscoverStore.LocationInfo.Columns.IMAGE_ID + " in (" + getImageIdsWithGps() + ")";

        Cursor cursor = mApplication.getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns.DATE_STRING,
                        DiscoverStore.LocationInfo.Columns.LOCATION_GROUP
                }, where, null, DiscoverStore.LocationInfo.Columns.DATE_TAKEN + " DESC");

        if (cursor != null) {
            String date;
            int locationGroup;
            String key;
            while (cursor.moveToNext()) {
                date = cursor.getString(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.DATE_STRING));
                locationGroup = cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.LOCATION_GROUP));
                key = date + "-" + locationGroup;

                StoryGroupItem item = keySet.get(key);

                if (item == null) {
                    item = new StoryGroupItem(key);
                    groups.add(item);
                    keySet.put(key, item);
                }
                item.count++;
            }
        }

        Utils.closeSilently(cursor);

        ArrayList<String> out = new ArrayList<>();

        for (StoryGroupItem item : groups) {
            if (item.count >= AT_LEAST_COUNT) {
                out.add(item.key);
            }
        }

        return out;
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
        notifyContentChanged(Uri.parse("content://com.android.gallery3d.v2.discover.data.StoryAlbumSet/onFutureDone"));
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

    private static class StoryGroupItem {
        String key;
        int count;

        StoryGroupItem(String key) {
            this.key = key;
            this.count = 0;
        }
    }
}

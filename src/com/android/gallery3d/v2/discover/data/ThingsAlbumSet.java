package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ThingsAlbumSet extends MediaSet implements FutureListener<ArrayList<MediaSet>> {
    private static final String TAG = ThingsAlbumSet.class.getSimpleName();

    private static final int[] placeHolderId = new int[]{
            R.drawable.blank_bike,
            R.drawable.blank_muffin,
            R.drawable.blank_bus,
            R.drawable.blank_dog,
            R.drawable.blank_cake,
            R.drawable.blank_plant,
            R.drawable.blank_badminton,
            R.drawable.blank_more
    };

    public static final Path PATH_ALBUM_SET = Path.fromString("/discover/things/albumset");

    private static final Uri[] mWatchUris = {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            DiscoverStore.Things.Media.CONTENT_URI
    };

    private final GalleryApp mApplication;
    private ArrayList<MediaSet> mAlbums = new ArrayList<MediaSet>();
    private final ChangeNotifier mNotifier;
    private boolean mIsLoading;
    private String mOrderClause;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    public ThingsAlbumSet(Path path, GalleryApp application) {
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
                R.string.tf_discover_things);
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        public ArrayList<MediaSet> run(ThreadPool.JobContext jc) {
            DataManager dataManager = mApplication.getDataManager();
            ArrayList<MediaSet> albums = new ArrayList<>();
            SparseArray<Small> smallSparseArray = getClassifications();
            for (int i = 0; i < smallSparseArray.size(); i++) {
                albums.add(dataManager.getMediaSet(ThingsAlbum.PATH_ITEM.getChild(smallSparseArray.keyAt(i))));
            }
            int size = albums.size();
            for (int i = size; i < placeHolderId.length; i++) {
                albums.add(dataManager.getMediaSet(ThingsAlbum.PATH_PLACE_HOLDER_ITEM.getChild(placeHolderId[i])));
            }
            return albums;
        }
    }

    private class Small {
        int imageId;
        int classification;

        public Small(int imageId, int classification) {
            this.imageId = imageId;
            this.classification = classification;
        }
    }

    private SparseArray<Small> getClassifications() {
        List<Small> images = new ArrayList<>();
        try {
            Cursor cursor = mApplication.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Images.ImageColumns._ID
            }, hidedAlbumsClause(), null, mOrderClause);
            if (cursor != null) {
                SparseIntArray things = DiscoverStore.getClassifiedThings();
                int id;
                while (cursor.moveToNext()) {
                    id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                    if (things.get(id, -1) == -1) {
                        continue;
                    }
                    images.add(new Small(id, things.get(id)));
                }
            }
            Utils.closeSilently(cursor);
        } catch (Exception e) {
            Log.d(TAG, "getClassifications error", e);
        }
        Collections.sort(images, new Comparator<Small>() {
            @Override
            public int compare(Small o1, Small o2) {
                return Utils.compare(o1.classification, o2.classification);
            }
        });
        SparseArray<Small> cls = new SparseArray<>();
        for (Small small : images) {
            if (cls.get(small.classification) == null) {
                cls.put(small.classification, small);
            }
        }
        return cls;
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
        notifyContentChanged(Uri.parse("content://com.android.gallery3d.v2.discover.data.ThingsAlbumSet/onFutureDone"));
    }
}

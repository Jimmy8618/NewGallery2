package com.android.gallery3d.v2.discover.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

import java.util.ArrayList;
import java.util.List;

public class StoryAlbum extends MediaSet {
    private static final String TAG = StoryAlbum.class.getSimpleName();

    public static final Path PATH_ITEM = Path.fromString("/discover/story/album");
    public static final Path PATH_PLACE_HOLDER_ITEM = Path.fromString("/discover/story/album/placeholder");

    private static final int INVALID_COUNT = -1;
    private final Uri mBaseUri;
    private final String mOrderClause;
    private final String[] mProjection;
    private final Path mItemPath;

    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final String mStoryGroup;
    private final ChangeNotifier mNotifier;

    private int mCachedCount = INVALID_COUNT;
    private List<Integer> mImageIds = new ArrayList<>();

    private boolean mIsPlaceHolder;

    private String mStoryDate;
    private String mStoryName;
    private int mLocationGroupId;

    public StoryAlbum(Path path, GalleryApp application, String storyGroup, boolean isPlaceHolder) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mStoryGroup = storyGroup;
        mIsPlaceHolder = isPlaceHolder;
        mNotifier = new ChangeNotifier(this, new Uri[]{
                DiscoverStore.LocationInfo.Media.CONTENT_URI,
                DiscoverStore.LocationGroup.Media.CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }, application);

        mOrderClause = MediaStore.Images.ImageColumns.DATE_MODIFIED + " DESC, "
                + MediaStore.Images.ImageColumns._ID + " DESC";
        mBaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        mProjection = LocalImage.PROJECTION;
        mItemPath = LocalImage.ITEM_PATH;

        initName();
    }

    @Override
    public Uri getContentUri() {
        return DiscoverStore.getLocationGroupUriWithLocationGroupId(mStoryGroup);
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> list = new ArrayList<>();
        if (mCachedCount == INVALID_COUNT) {
            getMediaItemCount();
        }
        if (mIsPlaceHolder) {
            MediaItem mediaItem = loadPlaceHolder(PlaceHolder.PATH_ITEM.getChild(Integer.parseInt(mStoryGroup)),
                    mApplication.getDataManager(), Integer.parseInt(mStoryGroup));
            list.add(mediaItem);
            return list;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + count; i++) {
            if (i >= 0 && i < mImageIds.size()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(mImageIds.get(i));
            }
        }

        String where = MediaStore.Images.ImageColumns._ID + " in (" + sb.toString() + ")";

        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("nonotify", "1")
                .build();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    uri, mProjection, where, null, mOrderClause);
        } catch (Exception e) {
            Log.w(TAG, "Gallery permissions is error,can't get data.", e);
            if (cursor != null) {
                cursor.close();
            }
            return list;
        }
        if (cursor == null) {
            Log.w(TAG, "query fail: " + uri);
            return list;
        }

        try {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column
                Path childPath = mItemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager, mApplication);
                list.add(item);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor,
                                              DataManager dataManager, GalleryApp app) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new LocalImage(path, app, cursor);
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    private static MediaItem loadPlaceHolder(Path path, DataManager dataManager, int placeHolderId) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new PlaceHolder(path, placeHolderId);
            }
            return item;
        }
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            if (mIsPlaceHolder) {
                mCachedCount = 1;
            } else {
                mImageIds = getImageIds();
                mCachedCount = mImageIds.size();
            }
        }
        return mCachedCount;
    }

    @Override
    public String getName() {
        return mStoryName;
    }

    private List<Integer> getImageIds() {
        List<Integer> images = new ArrayList<>();

        StringBuilder sb = new StringBuilder();

        Cursor cursor = mApplication.getContentResolver().query(DiscoverStore.LocationInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.LocationInfo.Columns.IMAGE_ID
                }, DiscoverStore.LocationInfo.Columns.DATE_STRING + " = ? and "
                        + DiscoverStore.LocationInfo.Columns.LOCATION_GROUP + " = ?",
                new String[]{
                        mStoryDate,
                        String.valueOf(mLocationGroupId)
                }, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(cursor.getInt(cursor.getColumnIndex(DiscoverStore.LocationInfo.Columns.IMAGE_ID)));
            }
        }

        Utils.closeSilently(cursor);

        ////

        cursor = mResolver.query(mBaseUri, new String[]{
                MediaStore.Images.ImageColumns._ID
        }, MediaStore.Images.ImageColumns._ID
                + " in (" + sb.toString() + ")" +
                " ) AND (" + hidedAlbumsClause(), null, mOrderClause);
        if (cursor != null) {
            int id;
            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                images.add(id);
            }
        }
        Utils.closeSilently(cursor);

        return images;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
            initName();
        }
        return mDataVersion;
    }

    private void initName() {
        if (mIsPlaceHolder) {
            return;
        }
        //[2019, 03, 11, 1]
        String[] s = mStoryGroup.split("-");

        mStoryDate = s[0] + "-" + s[1] + "-" + s[2];
        mLocationGroupId = Integer.parseInt(s[3]);

        mStoryName = mStoryDate;
    }

    @Override
    public boolean isPlaceHolder() {
        return mIsPlaceHolder;
    }
}

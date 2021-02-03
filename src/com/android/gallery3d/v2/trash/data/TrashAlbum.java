package com.android.gallery3d.v2.trash.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ChangeNotifier;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.trash.db.TrashStore;

import java.util.ArrayList;

/**
 * @author baolin.li
 */
public class TrashAlbum extends MediaSet {
    private static final String TAG = TrashAlbum.class.getSimpleName();

    public static final String PATH = "/trash/all";

    private static final int INVALID_COUNT = -1;
    private final Uri mBaseUri;
    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final ChangeNotifier mNotifier;
    private int mCachedCount = INVALID_COUNT;

    private int mPhotoCount;
    private int mVideoCount;

    private final String mOrderClause = TrashStore.Local.Columns.DELETED_TIME + " DESC, "
            + TrashStore.Local.Columns._ID + " DESC";

    public TrashAlbum(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mBaseUri = TrashStore.Local.Media.CONTENT_URI;
        mApplication = application;
        mResolver = application.getContentResolver();
        mNotifier = new ChangeNotifier(this, new Uri[]{mBaseUri}, application);
    }

    @Override
    public Uri getContentUri() {
        return mBaseUri;
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> itemArrayList = new ArrayList<>();
        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("limit", start + "," + count)
                .appendQueryParameter("nonotify", "1")
                .build();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = mResolver.query(uri, null,
                "(" + TrashStore.Local.Columns.IS_PENDING + " = 0) AND (" +
                        TrashStore.Local.Columns.FILE_FLAG + " != " + LocalImage.IMG_TYPE_MODE_BURST + ")",
                null, mOrderClause);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID));
                    Path childPath = TrashItem.ITEM_PATH.getChild(id);
                    MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager, mApplication);
                    itemArrayList.add(item);
                }
            } catch (Exception e) {
                Log.e(TAG, "exception in getMediaItem : start " + start + ", count " + count);
            }
        }
        Utils.closeSilently(cursor);
        return itemArrayList;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor, DataManager dataManager, GalleryApp app) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new TrashItem(path, app, cursor);
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    public static Cursor getItemCursor(ContentResolver resolver, Uri uri,
                                       String[] projection, int id) {
        return resolver.query(uri, projection, TrashStore.Local.Columns._ID + "=?",
                new String[]{String.valueOf(id)}, null);
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            //Photo Count
            Cursor photo = null;
            photo = mResolver.query(mBaseUri, new String[]{
                            TrashStore.Local.Columns._ID
                    }, "(" + TrashStore.Local.Columns.IS_PENDING + " = 0) AND (" +
                            TrashStore.Local.Columns.IS_IMAGE + "=1 AND " +
                            TrashStore.Local.Columns.FILE_FLAG + " != " + LocalImage.IMG_TYPE_MODE_BURST + ")",
                    null, null);
            if (photo != null) {
                mPhotoCount = photo.getCount();
            }
            Utils.closeSilently(photo);
            //Video Count
            Cursor video = null;
            video = mResolver.query(mBaseUri, new String[]{
                            TrashStore.Local.Columns._ID
                    }, "(" + TrashStore.Local.Columns.IS_PENDING + " = 0) AND (" +
                            TrashStore.Local.Columns.IS_IMAGE + "=0" + ")",
                    null, null);
            if (video != null) {
                mVideoCount = video.getCount();
            }
            Utils.closeSilently(video);
            //Both Count
            mCachedCount = mPhotoCount + mVideoCount;
        }
        return mCachedCount;
    }

    public int getPhotoCount() {
        if (mCachedCount == INVALID_COUNT) {
            getMediaItemCount();
        }
        return mPhotoCount;
    }

    public int getVideoCount() {
        if (mCachedCount == INVALID_COUNT) {
            getMediaItemCount();
        }
        return mVideoCount;
    }

    @Override
    public int getSupportedOperations() {
        return 0;
    }

    @Override
    public void delete() {
    }

    @Override
    public String getName() {
        return mApplication.getResources().getString(R.string.recently_deleted);
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
        }
        return mDataVersion;
    }

    public static TrashItem load(Path path, Cursor cursor) {
        return (TrashItem) loadOrUpdateItem(path, cursor, GalleryAppImpl.getApplication().getDataManager(), GalleryAppImpl.getApplication());
    }
}

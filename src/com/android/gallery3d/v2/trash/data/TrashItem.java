package com.android.gallery3d.v2.trash.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.DateUtils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.UpdateHelper;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.trash.TrashStoreValue;
import com.android.gallery3d.v2.trash.db.TrashStore;

import java.io.File;

public class TrashItem extends LocalMediaItem {
    private static final String TAG = TrashItem.class.getSimpleName();

    public static final Path ITEM_PATH = Path.fromString("/trash/all/item");

    private static final long ONE_DAY = 1000 * 60 * 60 * 24L; // 1 day ms
    private static final long THIRTY_DAYS = ONE_DAY * 30L;//ms

    private GalleryApp mApplication;

    public String localPath;
    private long deletedTime;
    public boolean isImage;
    private TrashStoreValue storeValue;
    //
    private int rotation;
    private int durationInSec;

    private int leftDay;

    public TrashItem(Path path, GalleryApp app, Cursor cursor) {
        super(path, nextVersionNumber());
        this.mApplication = app;
        loadFromCursor(cursor);
    }

    public TrashItem(Path path, GalleryApp app, int id) {
        super(path, nextVersionNumber());
        this.mApplication = app;
        ContentResolver resolver = mApplication.getContentResolver();
        Uri uri = TrashStore.Local.Media.CONTENT_URI;
        Cursor cursor = null;
        try {
            cursor = TrashAlbum.getItemCursor(resolver, uri, null, id);
        } catch (SecurityException e) {
            Log.d(TAG, "SecurityException " + e.toString());
        }
        if (cursor == null) {
            path.recycleObject();
            throw new RuntimeException("cannot get cursor for: " + path);
        }
        try {
            if (cursor.moveToNext()) {
                loadFromCursor(cursor);
            } else {
                path.recycleObject();
                throw new RuntimeException("cannot find data for: " + path);
            }
        } finally {
            cursor.close();
        }
    }

    private void loadFromCursor(Cursor cursor) {
        this.id = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID));
        this.localPath = cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.LOCAL_PATH));
        this.filePath = cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.TRASH_FILE_PATH));
        this.deletedTime = cursor.getLong(cursor.getColumnIndex(TrashStore.Local.Columns.DELETED_TIME));
        this.isImage = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns.IS_IMAGE)) == 1;
        this.mFileFlag = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns.FILE_FLAG));
        this.storeValue = TrashStoreValue.fromByte(cursor.getBlob(cursor.getColumnIndex(TrashStore.Local.Columns.MEDIA_STORE_VALUES)));
        //
        this.mimeType = this.storeValue == null ? "" : this.storeValue.getMimeType();
        this.rotation = this.storeValue == null ? 0 : this.storeValue.getOrientation();
        this.dateModifiedInSec = this.storeValue == null ? 0 : this.storeValue.getDateModified();
        this.durationInMs = this.storeValue == null ? 0 : this.storeValue.getDuration();
        this.durationInSec = (int) (this.durationInMs / 1000);
        this.mIsDrmFile = this.storeValue != null && this.storeValue.isDrm();
        this.dateTakenInMs = this.storeValue == null ? 0 : this.storeValue.getDatetaken();

        this.leftDay = getLeftDay(this.deletedTime);
    }

    public static int getLeftDay(long deletedTime) {
        long goTime = System.currentTimeMillis() - deletedTime;
        if (goTime < 0) {
            goTime = 0;
        }
        long leftTime = THIRTY_DAYS - goTime;
        return leftTime < 0 ? 0 : (int) (leftTime / ONE_DAY);
    }

    @Override
    public Uri getContentUri() {
        return GalleryUtils.transFileToContentUri(mApplication.getAndroidContext(), new File(filePath));
    }

    @Override
    protected boolean updateFromCursor(Cursor cursor) {
        UpdateHelper uh = new UpdateHelper();
        this.id = uh.update(this.id, cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID)));
        this.localPath = uh.update(this.localPath, cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.LOCAL_PATH)));
        this.filePath = uh.update(this.filePath, cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.TRASH_FILE_PATH)));
        this.deletedTime = uh.update(this.deletedTime, cursor.getLong(cursor.getColumnIndex(TrashStore.Local.Columns.DELETED_TIME)));
        this.isImage = uh.update(this.isImage, cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns.IS_IMAGE)) == 1);
        this.mFileFlag = uh.update(this.mFileFlag, cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns.FILE_FLAG)));
        this.storeValue = TrashStoreValue.fromByte(cursor.getBlob(cursor.getColumnIndex(TrashStore.Local.Columns.MEDIA_STORE_VALUES)));
        //
        this.mimeType = this.storeValue == null ? "" : this.storeValue.getMimeType();
        this.rotation = this.storeValue == null ? 0 : this.storeValue.getOrientation();
        this.dateModifiedInSec = this.storeValue == null ? 0 : this.storeValue.getDateModified();
        this.durationInMs = this.storeValue == null ? 0 : this.storeValue.getDuration();
        this.durationInSec = (int) (this.durationInMs / 1000);
        this.mIsDrmFile = this.storeValue != null && this.storeValue.isDrm();
        this.dateTakenInMs = this.storeValue == null ? 0 : this.storeValue.getDatetaken();

        this.leftDay = getLeftDay(this.deletedTime);
        return uh.isUpdated();
    }

    @Override
    public ThreadPool.Job<Bitmap> requestImage(int type) {
        if (isImage) {
            return new LocalImage.LocalImageRequest(mApplication, getPath(), dateModifiedInSec,
                    type, filePath);
        } else {
            return new LocalVideo.LocalVideoRequest(mApplication, getPath(), dateModifiedInSec,
                    type, filePath);
        }
    }

    @Override
    public ThreadPool.Job<BitmapRegionDecoder> requestLargeImage() {
        if (isImage) {
            return new LocalImage.LocalLargeImageRequest(filePath);
        } else {
            throw new UnsupportedOperationException("Cannot regquest a large image"
                    + " to a local video!");
        }
    }

    @Override
    public String getFilePath() {
        return this.filePath;
    }

    @Override
    public int getWidth() {
        return this.storeValue == null ? 0 : this.storeValue.getWidth();
    }

    @Override
    public int getHeight() {
        return this.storeValue == null ? 0 : this.storeValue.getHeight();
    }

    @Override
    public String getDate() {
        return DateUtils.timeStringWithDateInMs(mApplication.getAndroidContext(), this.deletedTime);
    }

    @Override
    public int getRotation() {
        return rotation;
    }

    @Override
    public int getMediaType() {
        if (isImage) {
            return GalleryUtils.getMediaType(mimeType, mFileFlag);
        } else {
            return MEDIA_TYPE_VIDEO;
        }
    }

    @Override
    public String getDurationString() {
        return GalleryUtils.formatDuration(mApplication.getAndroidContext(), durationInSec);
    }

    @Override
    public int getSupportedOperations() {
        if (isImage) {
            return MediaObject.SUPPORT_TRASH_RESTORE | MediaObject.SUPPORT_TRASH_DELETE;
        } else {
            return MediaObject.SUPPORT_TRASH_RESTORE | MediaObject.SUPPORT_TRASH_DELETE | MediaObject.SUPPORT_PLAY;
        }
    }

    @Override
    public void delete() {
        Log.d(TAG, "delete");
        TrashManager.getDefault().delete(this);
    }

    @Override
    public void restore() {
        Log.d(TAG, "restore");
        TrashManager.getDefault().restore(this);
    }

    public TrashStoreValue getStoreValue() {
        return storeValue;
    }

    @Override
    public int getLeftDay() {
        return leftDay;
    }

    @Override
    public Uri getPlayUri() {
        return getContentUri();
    }

    @Override
    public String getName() {
        return "";
    }
}

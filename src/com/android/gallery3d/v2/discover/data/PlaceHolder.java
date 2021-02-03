package com.android.gallery3d.v2.discover.data;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;

import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.ThreadPool;

public class PlaceHolder extends LocalMediaItem {
    public static final Path PATH_ITEM = Path.fromString("/discover/all/album/placeholder/item");

    private final Integer mPlaceHolderId;

    public PlaceHolder(Path path, int placeHolderId) {
        super(path, nextVersionNumber());
        this.mPlaceHolderId = placeHolderId;
    }

    @Override
    public Integer getPlaceHolderId() {
        return mPlaceHolderId;
    }

    @Override
    protected boolean updateFromCursor(Cursor cursor) {
        return false;
    }

    @Override
    public ThreadPool.Job<Bitmap> requestImage(int type) {
        return null;
    }

    @Override
    public ThreadPool.Job<BitmapRegionDecoder> requestLargeImage() {
        return null;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public String getDate() {
        return "";
    }

    @Override
    public Uri getContentUri() {
        return null;
    }
}

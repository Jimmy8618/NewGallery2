package com.android.gallery3d.data;

/**
 * Created by baolin.li on 4/24/17.
 */

public class AlbumSetData {
    private int mCurrentIndex;
    private MediaSet mMediaSet;
    private int mAlbumSetSize;

    public AlbumSetData(int index, MediaSet mediaSet, int albumSetSize) {
        mCurrentIndex = index;
        mMediaSet = mediaSet;
        mAlbumSetSize = albumSetSize;
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    public int getAlbumSetSize() {
        return mAlbumSetSize;
    }
}

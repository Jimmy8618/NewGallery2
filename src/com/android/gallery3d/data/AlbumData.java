package com.android.gallery3d.data;

import java.util.ArrayList;

/**
 * Created by baolin.li on 4/24/17.
 */

public class AlbumData {
    private int mCurrentIndex;
    private MediaSet mMediaSet;
    private int mMediaItemCount;
    private ArrayList<MediaItem> mMediaItems;

    public AlbumData(int index, MediaSet mediaSet, int count, ArrayList<MediaItem> mediaItems) {
        mCurrentIndex = index;
        mMediaSet = mediaSet;
        mMediaItemCount = count;
        mMediaItems = mediaItems;
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    public int getMediaItemCount() {
        return mMediaItemCount;
    }

    public ArrayList<MediaItem> getMediaItems() {
        return mMediaItems;
    }
}

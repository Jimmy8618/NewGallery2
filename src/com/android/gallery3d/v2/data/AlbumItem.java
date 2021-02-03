package com.android.gallery3d.v2.data;

import com.android.gallery3d.data.MediaSet;

public abstract class AlbumItem {
    public static class Type {
        public static final int LABEL = 0;
        public static final int IMAGE = 1;
        public static final int TRASH_TIP = 2;
    }

    private final int mType;
    private final MediaSet mMediaSet;
    private final int mPosition;
    private final String mDate;

    private boolean mIsSelected;

    private boolean mThumbLoaded;

    public AlbumItem(int type, MediaSet mediaSet, String date, int position) {
        this.mType = type;
        this.mMediaSet = mediaSet;
        this.mDate = date;
        this.mPosition = position;
        this.mThumbLoaded = false;
    }

    public int getType() {
        return mType;
    }

    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    public String getMediaSetPath() {
        return mMediaSet == null ? "" : mMediaSet.getPath().toString();
    }

    public int getPosition() {
        return mPosition;
    }

    public String getDate() {
        return mDate;
    }

    public void setSelected(boolean selected) {
        mIsSelected = selected;
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    public abstract String getItemPath();

    public void setThumbLoaded(boolean thumbLoaded) {
        mThumbLoaded = thumbLoaded;
    }

    public boolean isThumbLoaded() {
        return mThumbLoaded;
    }
}

package com.android.gallery3d.data;

/**
 * Created by apuser on 12/28/16.
 */

public class MediaInfo {
    public enum ItemType {
        LABEL,
        IMAGE
    }

    private String mTime;
    private boolean mIsSelected;
    private int mPosition;
    protected String mPath;

    private ItemType mItemType;
    protected MediaSet mAlbum;

    protected MediaInfo(ItemType type) {
        mItemType = type;
        mIsSelected = false;
    }

    public ItemType getItemType() {
        return mItemType;
    }

    public MediaSet getMediaSet() {
        return mAlbum;
    }

    public void setSelected(boolean selected) {
        mIsSelected = selected;
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public int getPosition() {
        return mPosition;
    }

    public String getTime() {
        return mTime;
    }

    public void setTime(String time) {
        mTime = time;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String path) {
        this.mPath = path;
    }
}



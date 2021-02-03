package com.android.gallery3d.v2.data;

import android.net.Uri;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;

public class ImageItem extends AlbumItem {

    private final LabelItem mLabelItem;

    private final String mMediaItemPath;
    private final String mFilePath;
    private final String mMimeType;
    private final int mOrientation;
    private final long mDateModified;
    private final int mMediaType;
    private final String mDuration;
    private final boolean mIsDrm;
    private final int mIndexInMediaSet;
    private final Uri mUri;
    private final int mWidth;
    private final int mHeight;

    private final int leftDay;

    public ImageItem(MediaSet mediaSet, MediaItem mediaItem, LabelItem labelItem, int position, int indexInMediaSet) {
        super(Type.IMAGE, mediaSet, labelItem.getDate(), position);
        this.mLabelItem = labelItem;
        this.mLabelItem.addChild(this);
        Utils.checkNotNull(mediaItem);
        this.mMediaItemPath = mediaItem.getPath().toString();
        this.mFilePath = mediaItem.getFilePath();
        this.mMimeType = mediaItem.getMimeType();
        this.mOrientation = mediaItem.getRotation();
        this.mDateModified = mediaItem.getModifiedInSec();
        this.mMediaType = mediaItem.getMediaType();
        this.mDuration = mediaItem.getDurationString();
        this.mIsDrm = mediaItem.mIsDrmFile;
        this.mIndexInMediaSet = indexInMediaSet;
        this.mUri = mediaItem.getContentUri();
        this.mWidth = mediaItem.getWidth();
        this.mHeight = mediaItem.getHeight();
        this.leftDay = mediaItem.getLeftDay();
    }

    @Override
    public String getItemPath() {
        return this.mMediaItemPath;
    }

    public LabelItem getLabelItem() {
        return mLabelItem;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public long getDateModified() {
        return mDateModified;
    }

    public int getMediaType() {
        return mMediaType;
    }

    public String getDuration() {
        return mDuration;
    }

    public boolean isDrm() {
        return mIsDrm;
    }

    public int getIndexInMediaSet() {
        return mIndexInMediaSet;
    }

    public Uri getUri() {
        return mUri;
    }

    public int getLeftDay() {
        return leftDay;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
}

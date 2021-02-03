package com.android.gallery3d.v2.util;

import android.graphics.Bitmap;

public class MotionThumbItem {
    private int mFrameIndex;
    private long mPresentationTimeUs;
    private Bitmap mBitmap;

    private boolean mIsMainPhoto;
    private boolean mHasHighResolution;

    private int mPosition;

    MotionThumbItem(int frameIndex, long presentationTimeUs, Bitmap bitmap, boolean isMainPhoto, boolean hasHighResolution) {
        this.mFrameIndex = frameIndex;
        this.mPresentationTimeUs = presentationTimeUs;
        this.mBitmap = bitmap;
        this.mIsMainPhoto = isMainPhoto;
        this.mHasHighResolution = hasHighResolution;
    }

    public int getFrameIndex() {
        return mFrameIndex;
    }

    public long getPresentationTimeUs() {
        return mPresentationTimeUs;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public boolean isMainPhoto() {
        return mIsMainPhoto;
    }

    public boolean hasHighResolution() {
        return mHasHighResolution;
    }

    public void setPosition(int position) {
        this.mPosition = position;
    }

    public int getPosition() {
        return mPosition;
    }
}

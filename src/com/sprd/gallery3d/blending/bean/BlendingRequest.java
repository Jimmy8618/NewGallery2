package com.sprd.gallery3d.blending.bean;

import android.graphics.Point;

import com.android.gallery3d.filtershow.filters.ImageFilter;

/**
 * Created by cz on 17-11-28.
 */
public class BlendingRequest {

    private ImageFilter mImageFilter;
    private Point mTargetInSrcPosition;
    private Point mTargetInSrcCenterPosition;
    private String path;
    private boolean mIsOrigImage;
    private Point screenPointInSrcLoc;
    private float scaleFactor;
    private float angle;

    public float getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public BlendingRequest(ImageFilter imageFilter, Point targetInSrcPosition, Point targetInSrcCenterPosition, String path, boolean isOrigImage, Point screenPointInSrcLoc, float scaleFactor, float angle) {
        mImageFilter = imageFilter;
        mTargetInSrcPosition = targetInSrcPosition;
        mTargetInSrcCenterPosition = targetInSrcCenterPosition;
        this.path = path;
        mIsOrigImage = isOrigImage;
        this.screenPointInSrcLoc = screenPointInSrcLoc;
        this.scaleFactor = scaleFactor;
        this.angle = angle;
    }

    public ImageFilter getmImageFilter() {
        return mImageFilter;
    }

    public void setmImageFilter(ImageFilter mImageFilter) {
        this.mImageFilter = mImageFilter;
    }

    public Point getmTargetInSrcPosition() {
        return mTargetInSrcPosition;
    }

    public void setmTargetInSrcPosition(Point mTargetInSrcPosition) {
        this.mTargetInSrcPosition = mTargetInSrcPosition;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean ismIsOrigImage() {
        return mIsOrigImage;
    }

    public void setmIsOrigImage(boolean mIsOrigImage) {
        this.mIsOrigImage = mIsOrigImage;
    }

    public Point getScreenPointInSrcLoc() {
        return screenPointInSrcLoc;
    }

    public void setScreenPointInSrcLoc(Point screenPointInSrcLoc) {
        this.screenPointInSrcLoc = screenPointInSrcLoc;
    }

    @Override
    public String toString() {
        return "BlendingRequest{" +
                "mImageFilter=" + mImageFilter +
                ", mTargetInSrcPosition=" + mTargetInSrcPosition +
                ", path='" + path + '\'' +
                ", mIsOrigImage=" + mIsOrigImage +
                ", screenPointInSrcLoc=" + screenPointInSrcLoc +
                '}';
    }

    public Point getTargetInSrcCenterPosition() {
        return mTargetInSrcCenterPosition;
    }

    public void setTargetInSrcCenterPosition(Point targetInSrcCenterPosition) {
        mTargetInSrcCenterPosition = targetInSrcCenterPosition;
    }
}

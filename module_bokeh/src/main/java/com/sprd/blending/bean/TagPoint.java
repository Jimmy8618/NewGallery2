package com.sprd.blending.bean;

import android.graphics.Point;

public class TagPoint extends Point {
    private boolean mIsLast = false;
    private boolean mIsFirst = false;

    public TagPoint(int x, int y) {
        super(x, y);
    }

    public TagPoint(Point src) {
        super(src);
    }

    public TagPoint() {
    }

    public boolean isLast() {
        return mIsLast;
    }

    public void setIsLast(boolean isLast) {
        this.mIsLast = isLast;
    }

    public boolean ismIsFirst() {
        return mIsFirst;
    }

    public void setmIsFirst(boolean mIsFirst) {
        this.mIsFirst = mIsFirst;
    }
}
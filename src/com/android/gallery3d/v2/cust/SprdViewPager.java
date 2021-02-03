package com.android.gallery3d.v2.cust;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import android.util.AttributeSet;
import android.view.MotionEvent;

public class SprdViewPager extends ViewPager {
    private boolean mScrollable = true;

    public SprdViewPager(@NonNull Context context) {
        super(context);
    }

    public SprdViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setScrollable(boolean scrollable) {
        mScrollable = scrollable;
    }

    public boolean isScrollable() {
        return mScrollable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mScrollable) {
            return super.onInterceptTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mScrollable) {
            return super.onTouchEvent(ev);
        } else {
            return false;
        }
    }
}

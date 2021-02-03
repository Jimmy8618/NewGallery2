package com.sprd.gallery3d.burstphoto;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;

/**
 * Created by lx on 16/12/18.
 */

public class ThumbNailBurstRecycleView extends RecyclerView {
    private double flingXRatio = 1;

    public ThumbNailBurstRecycleView(Context context) {
        super(context);
    }

    public ThumbNailBurstRecycleView(Context context, @Nullable
            AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbNailBurstRecycleView(Context context, @Nullable
            AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        new LinearSnapHelper().attachToRecyclerView(this);
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        velocityX *= flingXRatio;
        return super.fling(velocityX, velocityY);
    }

    public void setFlingXRatio(double flingXRatio) {
        this.flingXRatio = flingXRatio;
    }
}

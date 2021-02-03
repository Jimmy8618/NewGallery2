package com.sprd.gallery3d.burstphoto;

import android.content.Context;
import android.graphics.Rect;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class FilmStripRecyclerView extends RecyclerView {
    public static final int ITEM_INTERVAL = 10;
    public static final int SHOW_LEFT_ITEM_WIDTH = 15;
    private static final int FLING_MAX_VELOCITY = 8000;
    private static final String TAG = "FilmStripRecyclerView";
    SizeChangedListener mSizeChangedListener;

    public FilmStripRecyclerView(Context context) {
        super(context);
    }


    public FilmStripRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FilmStripRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        new BurstLinearSnapHelper().attachToRecyclerView(this);
        addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = ITEM_INTERVAL;
                outRect.top = 0;
                outRect.right = ITEM_INTERVAL;
                outRect.bottom = 0;
            }
        });
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        velocityX = solveVelocity(velocityX);
        velocityY = solveVelocity(velocityY);
        return super.fling(velocityX, velocityY);
    }

    private int solveVelocity(int velocity) {
        if (velocity > 0) {
            return Math.min(velocity, FLING_MAX_VELOCITY);
        } else {
            return Math.max(velocity, -FLING_MAX_VELOCITY);
        }
    }

    public void setOnSizeChangedListener(SizeChangedListener listener) {
        mSizeChangedListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.d(TAG, "onSizeChanged w=" + w + ", h=" + h + ", mSizeChangedListener=" + mSizeChangedListener);
        if (mSizeChangedListener != null) {
            mSizeChangedListener.onSizeChanged(w, h, oldw, oldh);
        }
    }

    public interface SizeChangedListener {
        void onSizeChanged(int w, int h, int oldw, int oldh);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!isClickable()) {
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }
}

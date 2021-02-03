package com.android.gallery3d.v2.cust;

import android.content.Context;

import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class InterceptView extends View {
    public InterceptView(Context context) {
        super(context);
    }

    public InterceptView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public InterceptView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public InterceptView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }
}

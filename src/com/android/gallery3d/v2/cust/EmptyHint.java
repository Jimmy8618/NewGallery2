package com.android.gallery3d.v2.cust;

import android.content.Context;

import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.gallery3d.R;

public class EmptyHint extends LinearLayout {
    private TextView mTitle;

    public EmptyHint(Context context) {
        super(context);
    }

    public EmptyHint(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public EmptyHint(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EmptyHint(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitle = findViewById(R.id.empty_title);
    }

    public void setText(int resId) {
        mTitle.setText(resId);
    }

    public void setText(String text) {
        mTitle.setText(text);
    }

    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }

    public void setVisible(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

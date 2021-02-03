package com.android.gallery3d.v2.tab;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.gallery3d.R;

/**
 * @author baolin.li
 */
public class TabItemView extends LinearLayout {
    private ImageView mImageView;
    private TextView mTextView;
    private String mCurrentTab;

    public TabItemView(Context context) {
        super(context);
    }

    public TabItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TabItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TabItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mImageView = findViewById(R.id.image);
        this.mTextView = findViewById(R.id.text);
    }

    public void setImage(int resId) {
        this.mImageView.setImageResource(resId);
    }

    public void setText(String text) {
        this.mTextView.setText(text);
    }

    public void setText(int resId) {
        this.mTextView.setText(resId);
    }

    public void setCurrentTab(@NonNull Class currentTab) {
        mCurrentTab = currentTab.getSimpleName();
    }

    public String getCurrentTab() {
        return mCurrentTab;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        this.mImageView.setSelected(selected);
        this.mTextView.setSelected(selected);
    }
}

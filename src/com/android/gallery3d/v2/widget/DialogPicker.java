package com.android.gallery3d.v2.widget;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.gallery3d.util.GalleryUtils;

public class DialogPicker extends PickerActivity {

    @Override
    public int getContentViewLayoutId() {
        getIntent().setAction(Intent.ACTION_GET_CONTENT);
        return super.getContentViewLayoutId();
    }

    @Override
    public void initViews(@Nullable Bundle savedInstanceState) {
        super.initViews(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
    }
}

package com.android.gallery3d.v2.widget;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.util.Constants;

public class AlbumPicker extends PickerActivity {

    @Override
    public int getContentViewLayoutId() {
        getIntent().putExtra(Constants.KEY_BUNDLE_WIDGET_GET_ALBUM, true);
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

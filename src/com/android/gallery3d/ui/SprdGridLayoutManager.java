package com.android.gallery3d.ui;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;

/**
 * Created by apuser on 2/23/17.
 */

public class SprdGridLayoutManager extends GridLayoutManager {
    public SprdGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SprdGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public SprdGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            return super.scrollVerticallyBy(dy, recycler, state);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return 0;
    }
}

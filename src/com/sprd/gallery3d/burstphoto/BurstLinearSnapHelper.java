package com.sprd.gallery3d.burstphoto;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

public class BurstLinearSnapHelper extends LinearSnapHelper {
    public boolean mNoNeedToScroll = false;

    @Override
    public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager, @NonNull View targetView) {
        if (mNoNeedToScroll) {
            return new int[]{0, 0};
        } else {
            return super.calculateDistanceToFinalSnap(layoutManager, targetView);
        }
    }

}

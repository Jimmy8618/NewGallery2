package com.sprd.gallery3d.burstphoto;

import android.app.Activity;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class FilmStripAdapterHelper {
    private static final String TAG = "FilmStripAdapterHelper";
    private Activity mActivity;
    private int mParentWidth;
    private int mParentHeight;
    private int mItemWidth;
    private int mItemHeight;
    private int mMarginTop;
    private int mMarginBottom;

    public FilmStripAdapterHelper(Activity activity, int parentWidth, int parentHeight, int imageWidth, int
            imageHeight) {
        mActivity = activity;
        mParentWidth = parentWidth;
        mParentHeight = parentHeight;

        mItemWidth = mParentWidth - ScreenUtil.dip2px(activity, 2 * (FilmStripRecyclerView.ITEM_INTERVAL +
                FilmStripRecyclerView.SHOW_LEFT_ITEM_WIDTH));
        mItemHeight = (mItemWidth * imageHeight) / imageWidth;
        if (mItemHeight > mParentHeight) {
            mItemHeight = mParentHeight;
            mItemWidth = (mItemHeight * imageWidth) / imageHeight;
        }

        mMarginTop = mMarginBottom = Math.max(0, (mParentHeight - mItemHeight) / 2);
        Log.d(TAG, "constructor parentWidth=" + parentWidth + ", parentHeight=" + parentHeight + ", imageWidth=" +
                imageWidth + ", imageHeight=" + imageHeight + " ---> mItemWidth=" + mItemWidth + ", mItemHeight=" +
                mItemHeight + ", mMarginTop=" + mMarginTop);
    }

    public void onCreateViewHolder(ViewGroup parent, View itemView) {
        RecyclerView.LayoutParams itemParams = (RecyclerView.LayoutParams) itemView.getLayoutParams();
        itemParams.width = mItemWidth;
        itemParams.height = mItemHeight;
        itemView.setLayoutParams(itemParams);
    }

    public void onBindViewHolder(View itemView, final int position, int itemCount) {
        int leftMarin = position == 0 ? (mParentWidth - mItemWidth) / 2 : 0;
        int rightMarin = position == itemCount - 1 ? (mParentWidth - mItemWidth) / 2 : 0;
        Log.d(TAG, "onBindViewHolder position=" + position + ", leftMarin=" + leftMarin + ", rightMarin=" + rightMarin);
        setViewMargin(itemView, leftMarin, mMarginTop, rightMarin, mMarginBottom);
    }

    private void setViewMargin(View view, int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (lp.leftMargin != left || lp.topMargin != top || lp.rightMargin != right || lp.bottomMargin != bottom) {
            //lp.setMargins(left, top, right, bottom);
            lp.topMargin = top;
            lp.bottomMargin = bottom;
            lp.setMarginStart(left);
            lp.setMarginEnd(right);
            view.setLayoutParams(lp);
        }
    }

    public int getItemWidth() {
        return mItemWidth;
    }

    public int getItemHeight() {
        return mItemHeight;
    }
}

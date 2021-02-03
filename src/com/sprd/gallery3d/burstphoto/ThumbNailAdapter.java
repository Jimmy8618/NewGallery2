package com.sprd.gallery3d.burstphoto;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

/**
 * Created by apuser on 12/15/16.
 */

public class ThumbNailAdapter extends RecyclerView.Adapter<ThumbNailAdapter.ViewHolder> {
    private static final String TAG = ThumbNailAdapter.class.getSimpleName();
    private static final int NUM_OF_ITEMS_IN_ONE_SCREEN_PORT = 5;
    private static final int NUM_OF_ITEMS_IN_ONE_SCREEN_LAND = 9;
    public static int NUM_OF_EMPTY_ITEM_ON_HEAD;
    public static int VIEW_TYPE_EMPTY = 1;
    public static int VIEW_TYPE_IMAGE = 2;
    private Activity mActivity;
    private List<BurstImageItem> mItems;
    private int mHighLightPosition;

    private int mItemSize;

    public ThumbNailAdapter(Activity activity, List<BurstImageItem> items) {
        mActivity = activity;
        mItems = items;
        mHighLightPosition = getNumOfItemsInOneScreen() / 2;
        mItems.get(0).setHighLight(true);
        NUM_OF_EMPTY_ITEM_ON_HEAD = getNumOfItemsInOneScreen() / 2;
        mItemSize = itemSize(mActivity);
    }

    @Override
    public int getItemViewType(int position) {
        if (position < getNumOfItemsInOneScreen() / 2
                || position >= (mItems.size() + getNumOfItemsInOneScreen() / 2)) {
            return VIEW_TYPE_EMPTY;
        } else {
            return VIEW_TYPE_IMAGE;
        }
    }

    @Override
    public ThumbNailAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mActivity).inflate(R.layout.thumb_nail_item_layout, parent,
                false);

        return new ThumbNailAdapter.ViewHolder(view, mItemSize, viewType);
    }

    @Override
    public void onBindViewHolder(final ThumbNailAdapter.ViewHolder holder, final int position) {
        if (position < getNumOfItemsInOneScreen() / 2
                || position >= (mItems.size() + getNumOfItemsInOneScreen() / 2)) {
            holder.imageView.setImageBitmap(null);
            holder.setSelected(false);
        } else {
            holder.setSelected(mItems.get(position - getNumOfItemsInOneScreen() / 2).isSelected());
            RequestOptions requestOptions = new RequestOptions().override(400);
            Glide.with(mActivity)
                    .asBitmap()
                    .load(mItems.get(position - getNumOfItemsInOneScreen() / 2).getFile())
                    .apply(requestOptions)
                    //.override(400, 400)
                    .into(holder.imageView);
            holder.setHighLight(mItems.get(position - getNumOfItemsInOneScreen() / 2).isHighLight());
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size() + (getNumOfItemsInOneScreen() / 2) * 2;
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.imageView.setImageBitmap(null);
        holder.container.setSelected(false);
    }

    private int itemSize(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels / getNumOfItemsInOneScreen();
    }

    public void setHighLight(RecyclerView recyclerView, int position) {
        Log.d(TAG, "setHighLight position = " + position);
        clearCurrentHighLight(recyclerView);
        int itemPosition = position - NUM_OF_EMPTY_ITEM_ON_HEAD;
        if (itemPosition >= 0 && itemPosition < mItems.size()) {
            mItems.get(itemPosition).setHighLight(true);
            mHighLightPosition = position;
            setHighLightState(recyclerView, mHighLightPosition, true);
        }
    }

    public void clearCurrentHighLight(RecyclerView recyclerView) {
        Log.d(TAG, "clearHighLight mHighLightPosition=" + mHighLightPosition);
        int itemPosition = mHighLightPosition - NUM_OF_EMPTY_ITEM_ON_HEAD;
        if (itemPosition >= 0 && itemPosition < mItems.size()) {
            mItems.get(itemPosition).setHighLight(false);
            setHighLightState(recyclerView, mHighLightPosition, false);
        }
    }

    public void clearAllHighLight(RecyclerView recyclerView) {
        for (int i = 0; i < mItems.size(); i++) {
            mItems.get(i).setHighLight(false);
            setHighLightState(recyclerView, i + NUM_OF_EMPTY_ITEM_ON_HEAD, false);
        }
    }

    private void setHighLightState(RecyclerView recyclerView, int position, boolean isHighLight) {
        ThumbNailAdapter.ViewHolder holder = ((ThumbNailAdapter.ViewHolder) recyclerView
                .findViewHolderForAdapterPosition(position));
        if (holder != null) {
            holder.setHighLight(isHighLight);
        } else {
            Log.d(TAG, "setHighLightState Don't find ViewHolder on position " + position);
        }
    }

    public int getNumOfItemsInOneScreen() {
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return NUM_OF_ITEMS_IN_ONE_SCREEN_PORT;
        } else {
            return NUM_OF_ITEMS_IN_ONE_SCREEN_LAND;
        }
    }

    public int getItemSize() {
        return mItemSize;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View container;
        ImageView imageView;
        private ImageView select;
        private int viewType;

        public ViewHolder(View view, int size, int type) {
            super(view);
            view.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            container = view;
            select = view.findViewById(R.id.select);
            imageView = view.findViewById(R.id.image);
            viewType = type;
        }

        public int getViewType() {
            return viewType;
        }

        public void setSelected(boolean selected) {
            select.setVisibility(selected ? View.VISIBLE : View.GONE);
        }

        public void setHighLight(boolean isHighLight) {
            if (isHighLight) {
                container.setSelected(true);
            } else {
                container.setSelected(false);
            }
        }
    }
}

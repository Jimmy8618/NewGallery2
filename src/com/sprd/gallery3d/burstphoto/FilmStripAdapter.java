package com.sprd.gallery3d.burstphoto;

import android.app.Activity;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by rui.li on 4/30/17.
 */

public class FilmStripAdapter extends RecyclerView.Adapter<FilmStripAdapter.ViewHolder> {
    private static final String TAG = "FilmStripAdapter";
    private List<BurstImageItem> mList = new ArrayList<>();
    private FilmStripAdapterHelper mFilmStripAdapterHelper;
    private Activity mActivity;
    private OnItemClickListener mItemClickListener;
    private int overrideSize = 400;

    public FilmStripAdapter(Activity activity, List<BurstImageItem> list, FilmStripAdapterHelper helper) {
        this.mList = list;
        mActivity = activity;
        mFilmStripAdapterHelper = helper;
        overrideSize = Math.min(helper.getItemWidth(), helper.getItemHeight());
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.image_check_layout, parent, false);
        mFilmStripAdapterHelper.onCreateViewHolder(parent, itemView);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        mFilmStripAdapterHelper.onBindViewHolder(holder.itemView, position, getItemCount());
        holder.itemView.setSelected(mList.get(position).isSelected());
        ((ImageCheck) holder.itemView).setOnImageCheckedListener(new ImageCheck.OnImageCheckedListener() {

            @Override
            public void onImageChecked(boolean checked) {
                if (mItemClickListener != null) {
                    mItemClickListener.onItemClick(position);
                }
            }
        });
        Log.d(TAG, "onBindViewHolder holder.mImageView.getLeft()=" + holder.mImageView.getLeft() + ", holder" +
                ".mImageView.getTop()=" + holder.mImageView.getTop() + ", overrideSize=" + overrideSize);
        Glide.with(mActivity)
                .load(mList.get(position).getFile())
                .apply(RequestOptions.overrideOf(overrideSize).fitCenter())
                .into(holder.mImageView);
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        super.onViewRecycled(holder);
        ((ImageCheck) holder.itemView).setOnImageCheckedListener(null);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mItemClickListener = listener;
    }

    public int getItemWidth() {
        return mFilmStripAdapterHelper.getItemWidth();
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView mImageView;

        public ViewHolder(final View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.image);
        }

    }
}
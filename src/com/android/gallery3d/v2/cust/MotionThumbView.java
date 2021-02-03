package com.android.gallery3d.v2.cust;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.v2.util.MotionFrameDecoder;
import com.android.gallery3d.v2.util.MotionThumbItem;

import java.util.ArrayList;
import java.util.List;

public class MotionThumbView extends FrameLayout implements MotionFrameDecoder.OnFrameAvailableListener {
    private static final String TAG = MotionThumbView.class.getSimpleName();

    public interface OnThumbSelectListener {
        void onThumbSelected(MotionThumbItem item);
    }

    private final List<MotionThumbItem> mThumbItems = new ArrayList<>();
    private RecyclerView mRecyclerView;
    private ThumbAdapter mAdapter;
    private int mThumbWidth = 160;
    private int mThumbHeight = 160;
    private int mMarginHeight = GalleryUtils.dpToPixel(20);

    private int mCurrentSelectFrameIndex = -1;
    private OnThumbSelectListener mOnThumbSelectListener;

    private boolean mIsLoading;
    private Toast mToast;

    public MotionThumbView(@NonNull Context context) {
        super(context);
    }

    public MotionThumbView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MotionThumbView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MotionThumbView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRecyclerView = findViewById(R.id.recycler_view_motion);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL,
                false);
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new ThumbAdapter();
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mThumbHeight = bottom - top;
            mAdapter.notifyDataSetChanged();
        }
    }

    public void setOnThumbSelectListener(OnThumbSelectListener l) {
        mOnThumbSelectListener = l;
    }

    @Override
    public void onScreenNail(Bitmap bitmap) {
    }

    @Override
    public void onFrameDecodeStart(boolean forSave) {
        mIsLoading = true;
    }

    @Override
    public void onFrameAvailable(MotionThumbItem item) {
        if (item.getFrameIndex() == 0) {
            mThumbItems.clear();
        }
        item.setPosition(mThumbItems.size());
        mThumbItems.add(item);
        mAdapter.notifyItemChanged(item.getPosition());
    }

    @Override
    public void onFrameDecodeEnd(boolean forSave) {
        boolean findMainPhoto = false;
        for (MotionThumbItem item : mThumbItems) {
            if (item.isMainPhoto()) {
                selectItem(item);
                findMainPhoto = true;
                goToCenter(item.getPosition());
                break;
            }
        }
        if (!findMainPhoto && mThumbItems.size() > 0) {
            MotionThumbItem item = mThumbItems.get(0);
            selectItem(item);
            goToCenter(item.getPosition());
        }
        mIsLoading = false;
    }

    @Override
    public void onSaveFrame(String path, Bitmap bitmap, long saveTime) {
    }

    @Override
    public void onSaveFrame(Uri uri, Bitmap bitmap, long saveTime) {
    }

    private void goToCenter(int position) {
        Rect rect = new Rect();
        mRecyclerView.getGlobalVisibleRect(rect);
        int offset = (rect.right - rect.left - mThumbWidth) / 2;
        ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                .scrollToPositionWithOffset(position, offset);
        Log.d(TAG, "goToCenter position = " + position + ", offset = " + offset);
    }

    private void selectItem(MotionThumbItem item) {
        mCurrentSelectFrameIndex = item.getFrameIndex();
        mAdapter.notifyDataSetChanged();
        if (mOnThumbSelectListener != null) {
            mOnThumbSelectListener.onThumbSelected(item);
        }
    }

    private class ThumbHolder extends RecyclerView.ViewHolder {
        private View mImageContent;
        private ImageView mImageView;
        private View mSelectView;
        private View mIndicator;

        private MotionThumbItem mThumbItem;

        public ThumbHolder(View itemView) {
            super(itemView);
            mImageContent = itemView.findViewById(R.id.image_content);
            mImageView = itemView.findViewById(R.id.image);
            mSelectView = itemView.findViewById(R.id.select);
            mIndicator = itemView.findViewById(R.id.indicator);
            itemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mIsLoading) {
                        mToast = ToastUtil.showMessage(getContext(), mToast, R.string.loading, Toast.LENGTH_SHORT);
                        return;
                    }
                    selectItem(mThumbItem);
                }
            });
        }

        public void bind(MotionThumbItem item) {
            mThumbItem = item;

            int width = mThumbHeight - mMarginHeight;
            if (item.getBitmap() != null && item.getBitmap().getHeight() > 0) {
                width = item.getBitmap().getWidth() * (mThumbHeight - mMarginHeight)
                        / item.getBitmap().getHeight();
            }
            mThumbWidth = width;

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) itemView.getLayoutParams();
            params.width = mThumbWidth;
            params.height = mThumbHeight;

            FrameLayout.LayoutParams params1 = (FrameLayout.LayoutParams) mImageContent.getLayoutParams();
            params1.width = mThumbWidth;
            params1.height = mThumbHeight - mMarginHeight;

            mImageView.setImageBitmap(item.getBitmap());
            mSelectView.setSelected(mCurrentSelectFrameIndex == item.getFrameIndex());
            mIndicator.setVisibility((item.isMainPhoto() || item.hasHighResolution()) ? View.VISIBLE
                    : View.INVISIBLE);
        }

        public void onViewRecycled() {
            mImageView.setImageBitmap(null);
            mSelectView.setSelected(false);
            mIndicator.setVisibility(View.INVISIBLE);
        }
    }

    private class ThumbAdapter extends RecyclerView.Adapter<ThumbHolder> {

        @NonNull
        @Override
        public ThumbHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ThumbHolder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.motion_thumb_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ThumbHolder holder, int position) {
            holder.bind(mThumbItems.get(position));
        }

        @Override
        public int getItemCount() {
            return mThumbItems.size();
        }

        @Override
        public void onViewRecycled(@NonNull ThumbHolder holder) {
            holder.onViewRecycled();
        }
    }
}

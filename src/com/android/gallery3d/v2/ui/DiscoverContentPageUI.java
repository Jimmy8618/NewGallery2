package com.android.gallery3d.v2.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.BitmapLoader;
import com.android.gallery3d.ui.DrmThumbImageLoader;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.AlbumSetLoadingListener;
import com.android.gallery3d.v2.page.DiscoverContentPageFragment;
import com.android.gallery3d.v2.util.AlbumSetItemDiff;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.MediaStoreSignature;
import com.sprd.refocus.RefocusUtils;

import java.util.ArrayList;
import java.util.List;

public class DiscoverContentPageUI extends FrameLayout implements AlbumSetLoadingListener {
    private static final String TAG = DiscoverContentPageUI.class.getSimpleName();

    private DiscoverContentPageFragment mDiscoverContentPageFragment;

    private static final int THUMB_SIZE = 400;
    private static final int M_SLOT_GAP = 3;

    private int mColumnPort;
    private int mColumnLand;
    private int mColumns;

    private int mSlotSize;

    private RecyclerView mRecyclerView;
    private Adapter mAdapter;

    private boolean mSupportBlur;
    private boolean mSupportBokeh;

    private boolean mFirstLoad;

    private OnAlbumSetItemClickListener mOnAlbumSetItemClickListener;

    private View mLoadingView;

    public interface OnAlbumSetItemClickListener {
        void onAlbumSetItemClick(View v, MediaSet mediaSet);
    }

    public DiscoverContentPageUI(@NonNull Context context) {
        super(context);
    }

    public DiscoverContentPageUI(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DiscoverContentPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DiscoverContentPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOnAlbumSetItemClickListener(OnAlbumSetItemClickListener onAlbumSetItemClickListener) {
        mOnAlbumSetItemClickListener = onAlbumSetItemClickListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSupportBlur = RefocusUtils.isSupportBlur();
        mSupportBokeh = RefocusUtils.isSupportBokeh();

        mColumnPort = getResources().getInteger(R.integer.albumset_image_rows_port);
        mColumnLand = getResources().getInteger(R.integer.albumset_image_rows_land);
        mColumns = Utils.getMinMultiple(mColumnPort, mColumnLand);

        mRecyclerView = findViewById(R.id.recycler_view_discover_content_page);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), mColumns);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                        ? (mColumns / mColumnPort)
                        : (mColumns / mColumnLand);
            }
        });
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = M_SLOT_GAP;
                outRect.top = M_SLOT_GAP;
                outRect.right = M_SLOT_GAP;
                outRect.bottom = M_SLOT_GAP;
            }
        });
        mAdapter = new Adapter();
        mRecyclerView.setAdapter(mAdapter);
        mFirstLoad = true;

        mLoadingView = findViewById(R.id.loading_view);
    }

    public void bind(DiscoverContentPageFragment pageFragment) {
        this.mDiscoverContentPageFragment = pageFragment;
        mAdapter.addItems(mDiscoverContentPageFragment.getData());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            int w = right - left;
            int h = bottom - top;
            int column = w < h ? mColumnPort : mColumnLand;
            mSlotSize = (w - (column * 2) * M_SLOT_GAP) / column;
            //更新界面
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void loadStart() {
        Log.d(TAG, "loadStart");
        mLoadingView.setVisibility(View.VISIBLE);
    }

    @Override
    public void loading(int index, int size, AlbumSetItem[] item) {
        if (this.mDiscoverContentPageFragment == null) {
            throw new RuntimeException("Page not bind.");
        }
        Log.d(TAG, "loading B (" + index + ", " + size + ", " + item[item.length - 1].getName() + ").");
        if (index == 0) {
            if (mFirstLoad) {
                if (this.mDiscoverContentPageFragment.getData().size() > 0) {
                    mFirstLoad = false;
                }
            }
            this.mDiscoverContentPageFragment.getData().clear();
        }
        for (AlbumSetItem albumSetItem : item) {
            this.mDiscoverContentPageFragment.getData().add(albumSetItem);
        }

        if (mFirstLoad) {
            for (AlbumSetItem albumSetItem : item) {
                mAdapter.addItem(albumSetItem);
            }
        } else if (index + 1 >= size) {
            mAdapter.addItems(this.mDiscoverContentPageFragment.getData());
        }
        Log.d(TAG, "loading E (" + index + ", " + size + ", " + item[item.length - 1].getName() + ").");
    }

    @Override
    public void loadEnd() {
        Log.d(TAG, "loadEnd");
        mLoadingView.setVisibility(View.GONE);
    }

    @Override
    public void loadEmpty() {
        Log.d(TAG, "loadEmpty");
        mAdapter.clearItems();
    }

    private abstract class Holder extends RecyclerView.ViewHolder {
        MediaSet mMediaSet;

        Holder(View itemView) {
            super(itemView);
        }

        public abstract void bind(AlbumSetItem item);

        public abstract void onRecycled();
    }

    private class ImageHolder extends Holder {
        private View mContainer;
        private ImageView mImageView;
        private View mVideoCover;
        private TextView mVideoDuration;
        private ImageView mImageType1;
        private ImageView mImageType2;
        private ImageView mImageType3;
        private View mCheckBox;
        private ImageView mDrmType;
        private TextView mTitle;

        private BitmapLoader mDrmLoader;

        public ImageHolder(View itemView) {
            super(itemView);
            mContainer = itemView.findViewById(R.id.album_item_container);
            mImageView = itemView.findViewById(R.id.album_item_image);
            mVideoCover = itemView.findViewById(R.id.video_cover);
            mVideoDuration = itemView.findViewById(R.id.video_duration);
            mImageType1 = itemView.findViewById(R.id.image_type_1);
            mImageType2 = itemView.findViewById(R.id.image_type_2);
            mImageType3 = itemView.findViewById(R.id.image_type_3);
            mCheckBox = itemView.findViewById(R.id.item_checkbox);
            mDrmType = itemView.findViewById(R.id.drm_type);
            mTitle = itemView.findViewById(R.id.title);

            mContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnAlbumSetItemClickListener != null && !mMediaSet.isPlaceHolder()) {
                        mOnAlbumSetItemClickListener.onAlbumSetItemClick(v, mMediaSet);
                    }
                }
            });
        }

        @Override
        public void bind(AlbumSetItem item) {
            this.mMediaSet = item.getMediaSet();
            //
            GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) itemView.getLayoutParams();
            params.width = mSlotSize;
            params.height = mSlotSize;
            itemView.setLayoutParams(params);

            setImageTypeIndicator(item);
            setImage(item);
            setTitle(item);
        }

        private void setTitle(AlbumSetItem item) {
            mTitle.setText(item.getMediaSet().getName());
            if (TextUtils.isEmpty(item.getCoverPath())) {
                mTitle.setVisibility(View.GONE);
            } else {
                mTitle.setVisibility(View.VISIBLE);
            }
        }

        private void setImage(AlbumSetItem item) {
            if (item.isCoverItemDrm()) {
                mDrmLoader = DrmThumbImageLoader.submit(GalleryAppImpl.getApplication().getThreadPool(),
                        mImageView, item.getCoverPath(), item.getCoverDateModified(),
                        Path.fromString(item.getCoverItemPath()), mDrmType, mRecyclerView);
                return;
            }
            mDrmType.setVisibility(View.GONE);
            loadImage(item);
        }

        private void setImageTypeIndicator(AlbumSetItem item) {
            switch (item.getCoverMediaType()) {
                case MediaObject.MEDIA_TYPE_IMAGE_RAW:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_raw);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_GIF:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_gif);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_burst);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BLUR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mImageType1.setVisibility(mSupportBlur ? View.VISIBLE : View.GONE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH:
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mImageType1.setVisibility(mSupportBokeh ? View.VISIBLE : View.GONE);
                    break;
                case MediaObject.MEDIA_TYPE_VIDEO:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_video);
                    mImageType1.setVisibility(View.VISIBLE);
                    mVideoDuration.setText(item.getCoverDuration());
                    mVideoDuration.setVisibility(View.VISIBLE);
                    mVideoCover.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_voice);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    mImageType3.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType3.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_HDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_VHDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_voice);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR:
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mImageType1.setVisibility(mSupportBokeh ? View.VISIBLE : View.GONE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                /*support FDR pic */
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_fdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO:
                    mImageType1.setImageResource(R.drawable.motion_photo);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_fdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    mImageType3.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType3.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_FDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_fdr);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_VFDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_voice);
                    mImageType1.setVisibility(View.VISIBLE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_fdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR:
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mImageType1.setVisibility(mSupportBokeh ? View.VISIBLE : View.GONE);
                    mImageType2.setImageResource(R.drawable.ic_newui_indicator_fdr);
                    mImageType2.setVisibility(View.VISIBLE);
                    break;
                default:
                    mImageType1.setVisibility(View.GONE);
                    mImageType2.setVisibility(View.GONE);
                    mImageType3.setVisibility(View.GONE);
                    mVideoDuration.setVisibility(View.GONE);
                    mVideoCover.setVisibility(View.GONE);
                    break;
            }
        }

        private void loadImage(AlbumSetItem item) {
            RequestOptions options = RequestOptions.overrideOf(THUMB_SIZE)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .signature(new MediaStoreSignature(item.getCoverMimeType(),
                            item.getCoverDateModified(),
                            item.getCoverOrientation()));

            if (!TextUtils.isEmpty(item.getCoverPath())) {
                options = options.placeholder(R.drawable.ic_no_texture)
                        .error(R.drawable.ic_newui_damaged_image);
            }

            RequestBuilder builder = Glide.with(itemView.getContext())
                    .asBitmap()
                    .apply(options);

            if (item.getCoverItemUri() != null) {
                mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                builder.transition(BitmapTransitionOptions.withCrossFade()).load(item.getCoverItemUri()).into(mImageView);
            } else if (item.getCoverPlaceHolderId() != null) {
                mImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                builder.load(item.getCoverPlaceHolderId()).into(mImageView);
            }
        }

        @Override
        public void onRecycled() {
            if (!((Activity) itemView.getContext()).isDestroyed()) {
                Glide.with(itemView.getContext()).clear(mImageView);
            }
            mImageView.setImageBitmap(null);
            mVideoCover.setVisibility(View.GONE);
            mVideoDuration.setVisibility(View.GONE);
            mImageType1.setVisibility(View.GONE);
            mImageType2.setVisibility(View.GONE);
            mImageType3.setVisibility(View.GONE);
            mDrmType.setVisibility(View.GONE);
            mTitle.setVisibility(View.GONE);
            if (mDrmLoader != null) {
                mDrmLoader.cancelLoad();
                mDrmLoader = null;
            }
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<AlbumSetItem> mData = new ArrayList<>();

        public void addItem(AlbumSetItem item) {
            mData.add(item);
            notifyItemInserted(mData.size() - 1 < 0 ? 0 : mData.size() - 1);
        }

        public void addItems(List<AlbumSetItem> items) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new AlbumSetItemDiff(mData, items));
            mData.clear();
            mData.addAll(items);
            result.dispatchUpdatesTo(this);
        }

        public void clearItems() {
            mData.clear();
            notifyDataSetChanged();
        }

        public List<AlbumSetItem> getItems() {
            return mData;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ImageHolder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_album_page_image_v2, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(mData.get(position));
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public void onViewRecycled(@NonNull Holder holder) {
            holder.onRecycled();
            super.onViewRecycled(holder);
        }
    }
}

package com.android.gallery3d.v2.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.BitmapLoader;
import com.android.gallery3d.ui.DrmThumbImageLoader;
import com.android.gallery3d.util.ImageCache;
import com.android.gallery3d.v2.cust.EmptyHint;
import com.android.gallery3d.v2.data.AlbumItem;
import com.android.gallery3d.v2.data.AlbumLoadingListener;
import com.android.gallery3d.v2.data.ImageItem;
import com.android.gallery3d.v2.data.LabelItem;
import com.android.gallery3d.v2.data.TrashTip;
import com.android.gallery3d.v2.option.ActionModeHandler;
import com.android.gallery3d.v2.page.AlbumPageFragment;
import com.android.gallery3d.v2.util.AlbumItemDiff;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.MediaStoreSignature;
import com.sprd.refocus.RefocusUtils;

import java.util.ArrayList;
import java.util.List;

public class AlbumPageUI extends FrameLayout implements AlbumLoadingListener, ActionModeHandler.OnSelectionModeChangeListener {
    private static final String TAG = AlbumPageUI.class.getSimpleName();

    public interface OnAlbumItemClickListener {
        void onImageClick(ImageItem item);

        boolean onImageLongClick(ImageItem item);

        void onImageSelected(ImageItem item);

        void onLabelSelected(LabelItem item);
    }

    private static final int THUMB_SIZE = 400;
    private static final int M_SLOT_GAP = 3;

    private int mColumnPort;
    private int mColumnLand;
    private int mColumns;

    private int mSlotSize;

    private EmptyHint mEmptyHint;

    private RecyclerView mRecyclerView;
    private Adapter mAdapter;

    private boolean mFirstLoad;

    private AlbumPageFragment mAlbumPageFragment;

    private OnAlbumItemClickListener mOnAlbumItemClickListener;

    private boolean mSupportBlur;
    private boolean mSupportBokeh;

    private int mSelectionMode = ActionModeHandler.LEAVE_SELECTION_MODE;

    private View mLoadingView;

    public AlbumPageUI(@NonNull Context context) {
        super(context);
    }

    public AlbumPageUI(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AlbumPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlbumPageUI(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSupportBlur = RefocusUtils.isSupportBlur();
        mSupportBokeh = RefocusUtils.isSupportBokeh();

        mColumnPort = getResources().getInteger(R.integer.albumset_image_rows_port);
        mColumnLand = getResources().getInteger(R.integer.albumset_image_rows_land);
        mColumns = Utils.getMinMultiple(mColumnPort, mColumnLand);

        mEmptyHint = findViewById(R.id.empty_hint);
        mEmptyHint.setText(R.string.no_photos_or_videos);
        mRecyclerView = findViewById(R.id.recycler_view_album_page_v2);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), mColumns);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (mAdapter.getItemViewType(position) == AlbumItem.Type.LABEL
                        || mAdapter.getItemViewType(position) == AlbumItem.Type.TRASH_TIP) ? mColumns
                        : (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? (mColumns / mColumnPort)
                        : (mColumns / mColumnLand));
            }
        });
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                if (parent.getLayoutManager().getItemViewType(view) == AlbumItem.Type.LABEL) {
                    return;
                }
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

    public void setOnAlbumItemClickListener(OnAlbumItemClickListener onAlbumItemClickListener) {
        mOnAlbumItemClickListener = onAlbumItemClickListener;
    }

    public void bind(AlbumPageFragment page) {
        this.mAlbumPageFragment = page;
        mAdapter.addItems(page.getData());
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
    public void loading(int index, int size, List<AlbumItem> items, int loadedSize) {
        if (this.mAlbumPageFragment == null) {
            throw new RuntimeException("Page not bind.");
        }
        Log.d(TAG, "loading B (" + index + ", " + size + ", " + loadedSize + ")");

        if (mEmptyHint.isVisible()) {
            mEmptyHint.setVisible(false);
        }

        if (index == 0) {
            if (mFirstLoad) {
                if (this.mAlbumPageFragment.getData().size() > 0) {
                    mFirstLoad = false;
                }
            }
            this.mAlbumPageFragment.getData().clear();
        }

        this.mAlbumPageFragment.getData().addAll(items);
        if (this.mAlbumPageFragment.isTrashAlbum() && index + loadedSize == size) {
            this.mAlbumPageFragment.getData().add(new TrashTip());
        }

        if (mFirstLoad) {
            mAdapter.addItem(items);
            if (this.mAlbumPageFragment.isTrashAlbum() && index + loadedSize == size) {
                mAdapter.addItem(new TrashTip());
            }
        } else if (index + loadedSize == size) {
            mAdapter.addItems(this.mAlbumPageFragment.getData());
        }

        Log.d(TAG, "loading E (" + index + ", " + size + ", " + loadedSize + ")");
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
        if (!this.mAlbumPageFragment.isTrashAlbum()) {
            if (!mEmptyHint.isVisible()) {
                mEmptyHint.setVisible(true);
            }
        } else {
            mAdapter.addItem(new TrashTip());
        }
    }

    private abstract class Holder extends RecyclerView.ViewHolder {
        protected String mAlbumItemPath;

        public Holder(View itemView) {
            super(itemView);
        }

        protected final AlbumItem getAlbumItem() {
            AlbumItem albumItem = null;
            for (AlbumItem item : mAdapter.getItems()) {
                if (item.getItemPath().equals(mAlbumItemPath)) {
                    albumItem = item;
                    break;
                }
            }
            return albumItem;
        }

        public abstract void bind(AlbumItem item);

        public abstract void onRecycled();
    }

    private class EmptyHolder extends Holder {

        public EmptyHolder(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(AlbumItem item) {
        }

        @Override
        public void onRecycled() {
        }
    }

    private class LabelHolder extends Holder {
        private TextView mLabelTitle;
        private View mContainer;
        private View mCheckBox;

        public LabelHolder(View itemView) {
            super(itemView);
            mLabelTitle = itemView.findViewById(R.id.label_title);
            mContainer = itemView.findViewById(R.id.selection_container);
            mCheckBox = itemView.findViewById(R.id.label_checkbox);
            mContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    LabelItem labelItem = (LabelItem) getAlbumItem();
                    if (labelItem == null) {
                        return;
                    }
                    labelItem.setSelected(!labelItem.isSelected());
                    labelItem.selectChild(labelItem.isSelected());
                    mAdapter.notifyItemRangeChanged(labelItem.getPosition(), labelItem.getChildCount() + 1);
                    if (mOnAlbumItemClickListener != null) {
                        mOnAlbumItemClickListener.onLabelSelected(labelItem);
                    }
                }
            });
        }

        @Override
        public void bind(AlbumItem item) {
            this.mAlbumItemPath = item.getItemPath();
            //
            mLabelTitle.setText(item.getDate());
            setSelection((LabelItem) item);
        }

        private void setSelection(LabelItem item) {
            mCheckBox.setSelected(item.isSelected());
            if (mSelectionMode == ActionModeHandler.ENTER_SELECTION_MODE
                    || mSelectionMode == ActionModeHandler.SELECT_IMAGE_OR_VIDEO_MODE) {
                mContainer.setVisibility(View.VISIBLE);
            } else if (mSelectionMode == ActionModeHandler.LEAVE_SELECTION_MODE) {
                mContainer.setVisibility(View.GONE);
            }
        }

        @Override
        public void onRecycled() {
        }
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
                    ImageItem imageItem = (ImageItem) getAlbumItem();
                    if (imageItem == null) {
                        return;
                    }
                    if (mSelectionMode == ActionModeHandler.ENTER_SELECTION_MODE
                            || mSelectionMode == ActionModeHandler.SELECT_IMAGE_OR_VIDEO_MODE) {
                        imageItem.setSelected(!imageItem.isSelected());
                        mAdapter.notifyItemChanged(imageItem.getPosition());
                        imageItem.getLabelItem().setSelected(imageItem.getLabelItem().isChildSelected());
                        mAdapter.notifyItemChanged(imageItem.getLabelItem().getPosition());
                        if (mOnAlbumItemClickListener != null) {
                            mOnAlbumItemClickListener.onImageSelected(imageItem);
                        }
                    } else if (mSelectionMode == ActionModeHandler.LEAVE_SELECTION_MODE) {
                        if (mOnAlbumItemClickListener != null) {
                            try {
                                if (imageItem.isThumbLoaded()) {
                                    mImageView.setDrawingCacheEnabled(true);
                                    Bitmap bitmap = Bitmap.createBitmap(mImageView.getDrawingCache());
                                    mImageView.setDrawingCacheEnabled(false);
                                    if (bitmap != null
                                            && Math.min(imageItem.getWidth(), imageItem.getHeight()) > THUMB_SIZE) {
                                        ImageCache.getDefault(getContext()).saveGlideBitmap(imageItem.getFilePath(), bitmap);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            mOnAlbumItemClickListener.onImageClick(imageItem);
                        }
                    }
                }
            });

            mContainer.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    ImageItem imageItem = (ImageItem) getAlbumItem();
                    if (imageItem == null) {
                        return true;
                    }
                    if (mSelectionMode == ActionModeHandler.LEAVE_SELECTION_MODE
                            && mOnAlbumItemClickListener != null) {
                        if (mOnAlbumItemClickListener.onImageLongClick(imageItem)) {
                            imageItem.setSelected(!imageItem.isSelected());
                            mAdapter.notifyItemChanged(imageItem.getPosition());
                            imageItem.getLabelItem().setSelected(imageItem.getLabelItem().isChildSelected());
                            mAdapter.notifyItemChanged(imageItem.getLabelItem().getPosition());
                            if (mOnAlbumItemClickListener != null) {
                                mOnAlbumItemClickListener.onImageSelected(imageItem);
                            }
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public void bind(AlbumItem item) {
            this.mAlbumItemPath = item.getItemPath();
            //
            GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) itemView.getLayoutParams();
            params.width = mSlotSize;
            params.height = mSlotSize;
            itemView.setLayoutParams(params);

            setImageTypeIndicator((ImageItem) item);
            setImage((ImageItem) item);
            setSelection((ImageItem) item);
            setScale((ImageItem) item);
            setTitle((ImageItem) item);
        }

        private void setSelection(ImageItem item) {
            mCheckBox.setSelected(item.isSelected());
            if (mSelectionMode == ActionModeHandler.ENTER_SELECTION_MODE
                    || mSelectionMode == ActionModeHandler.SELECT_IMAGE_OR_VIDEO_MODE) {
                mCheckBox.setVisibility(View.VISIBLE);
            } else if (mSelectionMode == ActionModeHandler.LEAVE_SELECTION_MODE) {
                mCheckBox.setVisibility(View.GONE);
            }
        }

        private void setScale(ImageItem item) {

        }

        private void setTitle(ImageItem item) {
            if (mAlbumPageFragment.isTrashAlbum()) {
                mTitle.setVisibility(View.VISIBLE);
                mTitle.setText(getContext().getResources().getQuantityString(
                        R.plurals.left_day, item.getLeftDay(), item.getLeftDay()));
            } else {
                mTitle.setVisibility(View.GONE);
            }
        }

        private void setImage(ImageItem item) {
            if (item.isDrm()) {
                mDrmLoader = DrmThumbImageLoader.submit(GalleryAppImpl.getApplication().getThreadPool(),
                        mImageView, item.getFilePath(), item.getDateModified(),
                        Path.fromString(item.getItemPath()), mDrmType, mRecyclerView);
                return;
            }
            mDrmType.setVisibility(View.GONE);
            loadImage(item);
        }

        private void setImageTypeIndicator(ImageItem item) {
            switch (item.getMediaType()) {
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
                    mVideoDuration.setText(item.getDuration());
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
                case MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType1.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType1.setVisibility(View.VISIBLE);
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
                case MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_FDR:
                    mImageType1.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mImageType1.setVisibility(View.VISIBLE);
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

        private void loadImage(final ImageItem item) {
            RequestOptions options = new RequestOptions()
                    .override(THUMB_SIZE)
                    .centerCrop()
                    .placeholder(R.drawable.ic_no_texture)
                    .error(R.drawable.ic_newui_damaged_image)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .signature(new MediaStoreSignature(item.getMimeType(),
                            item.getDateModified(),
                            item.getOrientation()));
            Glide.with(itemView.getContext())
                    .asBitmap()
                    .load(item.getUri())
                    .transition(BitmapTransitionOptions.withCrossFade())
                    .apply(options)
                    .listener(new RequestListener<Bitmap>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object o, Target<Bitmap> target, boolean b) {
                            item.setThumbLoaded(false);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap bitmap, Object o, Target<Bitmap> target, DataSource dataSource, boolean b) {
                            if (bitmap != null && bitmap.getWidth() >= THUMB_SIZE && bitmap.getHeight() >= THUMB_SIZE) {
                                item.setThumbLoaded(true);
                            }
                            return false;
                        }
                    })
                    .into(mImageView);
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
            if (mDrmLoader != null) {
                mDrmLoader.cancelLoad();
                mDrmLoader = null;
            }
        }
    }

    private class TrashTipHolder extends Holder {
        public TrashTipHolder(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(AlbumItem item) {
        }

        @Override
        public void onRecycled() {
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<AlbumItem> mData = new ArrayList<>();

        public void addItem(AlbumItem item) {
            mData.add(item);
            notifyItemInserted(mData.size() - 1 < 0 ? 0 : mData.size() - 1);
        }

        public void addItem(List<AlbumItem> items) {
            int from = mData.size();
            mData.addAll(items);
            int count = mData.size() - from;
            notifyItemRangeInserted(from, count);
        }

        public void addItems(List<AlbumItem> items) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new AlbumItemDiff(mData, items));
            mData.clear();
            mData.addAll(items);
            result.dispatchUpdatesTo(this);
        }

        public void clearItems() {
            mData.clear();
            notifyDataSetChanged();
        }

        public void unSelect() {
            for (AlbumItem item : mData) {
                item.setSelected(false);
            }
        }

        public List<AlbumItem> getItems() {
            return mData;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AlbumItem.Type.LABEL) {
                return new LabelHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_album_page_label_v2, parent, false));
            } else if (viewType == AlbumItem.Type.IMAGE) {
                return new ImageHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_album_page_image_v2, parent, false));
            } else if (viewType == AlbumItem.Type.TRASH_TIP) {
                return new TrashTipHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_album_page_trash_tip_v2, parent, false));
            }
            return new EmptyHolder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_album_page_empty_v2, parent, false));
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
        public int getItemViewType(int position) {
            return mData.get(position).getType();
        }

        @Override
        public void onViewRecycled(@NonNull Holder holder) {
            holder.onRecycled();
            super.onViewRecycled(holder);
        }
    }

    @Override
    public void onSelectionModeChanged(int mode) {
        mSelectionMode = mode;
        if (mode == ActionModeHandler.LEAVE_SELECTION_MODE) {
            mAdapter.unSelect();
        }
        mAdapter.notifyDataSetChanged();
    }

    public void makeSlotVisible(int index) {
        int position = -1;
        GridLayoutManager manager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        List<AlbumItem> data = mAdapter.getItems();
        for (AlbumItem item : data) {
            if ((item instanceof ImageItem) && (((ImageItem) item).getIndexInMediaSet() == index)) {
                position = item.getPosition();
                break;
            }
        }
        if (position >= 0) {
            manager.scrollToPosition(position);
        }
    }

    public void updateUI() {
        if (mAdapter == null) {
            return;
        }
        mAdapter.notifyDataSetChanged();
    }
}

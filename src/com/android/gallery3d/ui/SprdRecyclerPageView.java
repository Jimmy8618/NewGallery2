package com.android.gallery3d.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ItemInfo;
import com.android.gallery3d.data.LabelInfo;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaInfo;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ImageCache;
import com.android.gallery3d.util.MediaInfoDiffUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.MediaStoreSignature;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.RefocusUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by rui.li on 2/22/17.
 */

public abstract class SprdRecyclerPageView extends FrameLayout {

    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam(); //include 1g/512m
    private static boolean is512Ram = StandardFrameworks.getInstances().getRamConfig() == 2;
    protected static boolean isMonkey = GalleryUtils.isMonkey();
    private static int THUMB_NAIL_WIDTH = 400;
    private static final int M_SLOT_GAP = 3;
    private static final String TAG = SprdRecyclerPageView.class.getSimpleName();
    protected SprdRecyclerView mRecyclerView;
    private GridLayoutManager mGridLayoutManagerV;
    private GridLayoutManager mGridLayoutManagerH;
    protected List<MediaInfo> mMediaInfosList;
    protected SprdRecyclerViewAdapter mSprdRecyclerViewAdapter;
    protected int mMaxThumbImageCount;
    private int mRowsLand;
    private int mRowsPort;
    private int mRowsCount;
    private int mImageSlotWidth;
    private int mImageSlotHeight;
    private int mSelectedViewWidth;
    private float mSelectedScale;
    protected SelectionManager mSelectionManager;
    protected ActionModeHandler mActionModeHandler;

    protected static final int STATE_NORMAL = 0;
    protected static final int STATE_SELECTION = 1;
    protected static final int STATE_ALBUM_SELECTION = 2;
    protected int mState;

    protected AbstractGalleryActivity mActivity;

    private boolean mIsGetAlbumIntent;

    protected boolean mPaused;
    protected boolean mIsLoading;

    protected boolean mIsFirstLoad;
    protected boolean mIsFirstScrolled = false;

    private boolean mIsScrollUp;
    private boolean mSupportBlur;
    private boolean mSupportBokeh;
    private AtomicBoolean mIsFrozen = new AtomicBoolean(false);

    protected static final int MSG_UPDATE_DATA = 1;
    protected static Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_DATA:
                    ((Runnable) msg.obj).run();
                    break;
            }
        }
    };

    public SprdRecyclerPageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SprdRecyclerPageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SprdRecyclerPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SprdRecyclerPageView(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Log.d(TAG, "onFinishInflate " + this);

        Resources r = getResources();
        mSelectedViewWidth = GalleryUtils.getImageSize(getContext(), R.drawable.ic_newui_selected).getWidth();
        mSupportBlur = RefocusUtils.isSupportBlur();
        mSupportBokeh = RefocusUtils.isSupportBokeh();
        mMaxThumbImageCount = r.getInteger(R.integer.albumset_image_max_count);
        mRowsLand = r.getInteger(R.integer.albumset_image_rows_land);
        mRowsPort = r.getInteger(R.integer.albumset_image_rows_port);
        if (isLowRam) {
            DisplayMetrics metrics = r.getDisplayMetrics();
            Log.d(TAG, metrics.toString() + ", " + metrics.density + ", " + metrics.densityDpi);
            int screenWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
            if (screenWidth <= 480) {
                THUMB_NAIL_WIDTH = 150;
            } else {
                THUMB_NAIL_WIDTH = 300;
            }
        }

        mGridLayoutManagerV = new SprdGridLayoutManager(getContext(), mRowsPort);
        mGridLayoutManagerH = new SprdGridLayoutManager(getContext(), mRowsLand);
        mGridLayoutManagerV.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                List<MediaInfo> infoList = ((SprdRecyclerViewAdapter) mRecyclerView.getAdapter()).getDataList();
                if (infoList.size() == 0 || position >= infoList.size() || null == infoList.get(position)) {
                    return 1;
                }
                return infoList.get(position).getItemType() == MediaInfo.ItemType.LABEL ? mRowsPort : 1;
            }
        });
        mGridLayoutManagerH.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                List<MediaInfo> infoList = ((SprdRecyclerViewAdapter) mRecyclerView.getAdapter()).getDataList();
                if (infoList.size() == 0 || position >= infoList.size() || null == infoList.get(position)) {
                    return 1;
                }
                return infoList.get(position).getItemType() == MediaInfo.ItemType.LABEL ? mRowsLand : 1;
            }
        });
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mRecyclerView.setLayoutManager(mGridLayoutManagerV);
        } else {
            mRecyclerView.setLayoutManager(mGridLayoutManagerH);
        }

        mMediaInfosList = new ArrayList<MediaInfo>();
        mSprdRecyclerViewAdapter = new SprdRecyclerViewAdapter(getContext(), mMediaInfosList);
        mRecyclerView.setAdapter(mSprdRecyclerViewAdapter);
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                if (parent.getLayoutManager().getItemViewType(view) == SprdRecyclerViewAdapter.VIEW_TYPE_LABEL) {
                    return;
                }

                outRect.left = M_SLOT_GAP;
                outRect.top = M_SLOT_GAP;
                outRect.right = M_SLOT_GAP;
                outRect.bottom = M_SLOT_GAP;
            }
        });

        mState = STATE_NORMAL;
        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                scrollImage(dy > 0);
                SprdRecyclerPageView.this.onScrolled(dx, dy);
            }
        });

        if (isMonkey) {
            mRecyclerView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d(TAG, "mRecyclerView.onTouch: mIsFrozen = " + mIsFrozen.get());
                    return mIsFrozen.get();
                }
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            int w = right - left;
            int h = bottom - top;
            mRowsCount = w < h ? mRowsPort : mRowsLand;
            mImageSlotWidth = mImageSlotHeight = (w - (mRowsCount * 2) * M_SLOT_GAP) / mRowsCount;
            mSelectedScale = 1f - (float) mSelectedViewWidth / (float) mImageSlotWidth;
            Log.d(TAG, "onLayout image slot size : " + mImageSlotWidth + ", mSelectedViewWidth=" + mSelectedViewWidth
                    + ", mSelectedScale=" + mSelectedScale);
        }
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged orientation : " + newConfig.orientation);
        int position = 0;
        try {
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                position = mGridLayoutManagerH.findFirstVisibleItemPosition();
                mRecyclerView.setLayoutManager(mGridLayoutManagerV);
            } else {
                position = mGridLayoutManagerV.findFirstVisibleItemPosition();
                mRecyclerView.setLayoutManager(mGridLayoutManagerH);
            }
        } catch (Exception e) {
        }
        mRecyclerView.getLayoutManager().scrollToPosition(position);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, "onDetachedFromWindow " + this.getClass().getSimpleName());
    }

    public void pause() {
        Log.d(TAG, "pause " + this.getClass().getSimpleName());
        mPaused = true;
    }

    public void stop() {
        Log.d(TAG, "stop " + this.getClass().getSimpleName());
        if (isLowRam) {
            mSprdRecyclerViewAdapter.getDataList().clear();
            mSprdRecyclerViewAdapter.notifyDataSetChanged();
            System.gc();
        }
    }

    public void start() {
        Log.d(TAG, "start " + this.getClass().getSimpleName());
        if (isLowRam) {
            mSprdRecyclerViewAdapter.setDataList(mMediaInfosList);
            mSprdRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    public void resume() {
        Log.d(TAG, "resume " + this.getClass().getSimpleName());
        mPaused = false;
        mIsFirstScrolled = false;
    }

    class SprdRecyclerViewAdapter extends RecyclerView.Adapter<MediaHolder> {
        public static final int VIEW_TYPE_LABEL = 0;
        public static final int VIEW_TYPE_IMAGE = 1;
        private List<MediaInfo> mDataList = new ArrayList<MediaInfo>();
        private LayoutInflater mInflater;
        private Object mMutex = new Object();

        public SprdRecyclerViewAdapter(Context context, List<MediaInfo> data) {
            mDataList.clear();
            mDataList.addAll(data);
            mInflater = LayoutInflater.from(context);
        }

        public List<MediaInfo> getDataList() {
            synchronized (mMutex) {
                return mDataList;
            }
        }

        public void setDataList(List<MediaInfo> dataList) {
            synchronized (mMutex) {
                mDataList.clear();
                mDataList.addAll(dataList);
            }
        }

        @Override
        public MediaHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            MediaHolder holder;
            if (VIEW_TYPE_LABEL == viewType) {
                holder = new LabelHolder(mInflater.inflate(R.layout.albumset_item_label, viewGroup, false));
            } else {
                holder = new ImageHolder(mInflater.inflate(R.layout.albumset_item_content, viewGroup, false));
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(MediaHolder mediaHolder, int i) {
            synchronized (mMutex) {
                mDataList.get(i).setPosition(i);
                if (mediaHolder instanceof LabelHolder) {
                    mediaHolder.setMediaInfo(mDataList.get(i));
                } else if (mediaHolder instanceof ImageHolder) {
                    mediaHolder.setMediaInfo(mDataList.get(i));
                }
            }
        }

        @Override
        public void onBindViewHolder(MediaHolder holder, int position, List<Object> payloads) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position);
            } else {
                Bundle bundle = (Bundle) payloads.get(0);
                if (bundle.size() > 0) {
                    holder.refresh(findMediaInfoWithPath(holder.getPath()));
                }
            }
        }

        @Override
        public void onViewRecycled(MediaHolder holder) {
            holder.onRecycled();
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            synchronized (mMutex) {
                return mDataList.size();
            }
        }

        @Override
        public int getItemViewType(int position) {
            synchronized (mMutex) {
                if (MediaInfo.ItemType.LABEL.equals(mDataList.get(position).getItemType())) {
                    return VIEW_TYPE_LABEL;
                } else {
                    return VIEW_TYPE_IMAGE;
                }
            }
        }
    }

    public abstract class MediaHolder extends RecyclerView.ViewHolder {
        protected String mPath;

        public MediaHolder(View itemView) {
            super(itemView);
        }

        protected String getPath() {
            return mPath;
        }

        protected void setMediaInfo(MediaInfo info) {
            mPath = info.getPath();
        }

        protected void refresh(MediaInfo info) {
            setTitle(info);
            setMoreVisible(info);
            setImageTypeIndicator(info);
            setScale(info);
            setSelected(info);
        }

        protected void setTitle(MediaInfo info) {
        }

        protected void setMoreVisible(MediaInfo info) {
        }

        protected void setImageTypeIndicator(MediaInfo info) {
        }

        protected void setScale(MediaInfo info) {
        }

        protected void setSelected(MediaInfo info) {
        }

        protected void setImage(MediaInfo info) {
        }

        public abstract void onRecycled();
    }

    class LabelHolder extends MediaHolder {
        private TextView mTextView;
        private ImageView mSelectedView;
        private View mLabelSelectionContainer;

        public LabelHolder(View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.albumset_label);
            mSelectedView = itemView.findViewById(R.id.label_checkbox);
            mLabelSelectionContainer = itemView.findViewById(R.id.label_selection_container);
            mLabelSelectionContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mActivity.isGetContent() || mActivity.isPick() || mPaused) {
                        return;
                    }
                    onLabelClicked((LabelInfo) findMediaInfoWithPath(mPath), getPosition());
                }
            });
        }

        @Override
        protected void setMediaInfo(MediaInfo info) {
            super.setMediaInfo(info);

            setTitle(info);
            setSelected(info);
        }

        @Override
        protected void setTitle(MediaInfo info) {
            if (!(info instanceof LabelInfo)) {
                return;
            }
            LabelInfo labelInfo = (LabelInfo) info;
            mTextView.setText(labelInfo.getTitle());
        }

        @Override
        protected void setSelected(MediaInfo info) {
            if (mState == STATE_SELECTION) {
                mSelectedView.setSelected(info.isSelected());
                mLabelSelectionContainer.setVisibility(View.VISIBLE);
            } else if (mState == STATE_ALBUM_SELECTION && mSelectionManager != null && !mSelectionManager.isLocalAlbum(info.getMediaSet().getPath())) {
                boolean isSelected = false;
                try {
                    isSelected = mSelectionManager.isHideAlbumSelected(info.getMediaSet().getPath());
                } catch (Exception e) {
                    Log.d(TAG, "setSelected Exception :" + e.toString());
                }
                info.setSelected(isSelected);
                mSelectedView.setSelected(isSelected);
                mLabelSelectionContainer.setVisibility(View.VISIBLE);
            } else if (mIsGetAlbumIntent && mState == STATE_ALBUM_SELECTION) {
                mLabelSelectionContainer.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onRecycled() {
            mLabelSelectionContainer.setVisibility(View.GONE);
        }
    }

    class ImageHolder extends MediaHolder {
        private final float SCALE = 0.8f;
        private final long SCALE_DURATION = 200;
        private ImageView mImageView;
        private View mImageContainer;
        private ImageView mTypeView;
        private ImageView mTypeView2;
        private ImageView mHdrTypeView;
        private TextView mVideoDurationView;
        private View mVideoCover;
        private View mMoreImage;
        private ImageView mSelectedView;
        private View mContainer;
        private ImageView mDrmIcon;
        private BitmapLoader mDrmLoader;

        public ImageHolder(View itemView) {
            super(itemView);
            mImageContainer = itemView.findViewById(R.id.albumset_item_container);
            mImageView = itemView.findViewById(R.id.albumset_item_content);
            mImageContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPaused) {
                        return;
                    }
                    ItemInfo itemInfo = (ItemInfo) findMediaInfoWithPath(mPath);
                    if (itemInfo != null) {
                        try {
                            Bitmap bitmap = ((BitmapDrawable) ((ImageView) v.findViewById(R.id.albumset_item_content))
                                    .getDrawable()).getBitmap();
                            if (itemInfo.getDecodeStatus() && Math.min(itemInfo.getMediaItem().getWidth(), itemInfo
                                    .getMediaItem().getHeight()) >= THUMB_NAIL_WIDTH) {
                                ImageCache.getDefault(mActivity).saveGlideBitmap(itemInfo.getMediaItem().getFilePath(), bitmap);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    onImageClicked(itemInfo, getPosition());
                }
            });
            mImageContainer.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Log.d(TAG, "ImageHolder onLongClick isGetContent ? " + mActivity.isGetContent()
                            + ", isPick ? " + mActivity.isPick() + ", mIsLoading=" + mIsLoading
                            + ", mPath=" + mPath);
                    if (mActivity.isGetContent() || mActivity.isPick() || mPaused) {
                        return true;
                    }
                    if (mIsLoading) {
                        Toast.makeText(getContext(), R.string.album_set_loading_tip, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return onImageLongClicked((ItemInfo) findMediaInfoWithPath(mPath), getPosition());
                }
            });
            mSelectedView = itemView.findViewById(R.id.item_checkbox);

            mTypeView = itemView.findViewById(R.id.image_type);
            mTypeView2 = itemView.findViewById(R.id.image_type2);
            mHdrTypeView = itemView.findViewById(R.id.image_type_hdr);

            mVideoDurationView = itemView.findViewById(R.id.video_duration);
            mVideoCover = itemView.findViewById(R.id.video_cover);
            mMoreImage = itemView.findViewById(R.id.more_image);
            mContainer = itemView.findViewById(R.id.image_content);
            mDrmIcon = itemView.findViewById(R.id.drm_type);
        }

        @Override
        protected void setMediaInfo(MediaInfo info) {
            super.setMediaInfo(info);

            GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) mContainer.getLayoutParams();
            params.width = mImageSlotWidth;
            params.height = mImageSlotHeight;
            mContainer.setLayoutParams(params);

            setImageTypeIndicator(info);
            setMoreVisible(info);
            setSelected(info);
            setScale(info);
            setImage(info);
        }

        @Override
        protected void setImage(MediaInfo info) {
            if (!(info instanceof ItemInfo)) {
                return;
            }
            ItemInfo itemInfo = (ItemInfo) info;

            if (itemInfo.getMediaItem().mIsDrmFile) {
                Log.d(TAG, "setImage " + itemInfo.getMediaItem().getFilePath() + " is DRM file!");
                mDrmLoader = DrmThumbImageLoader.submit(mActivity.getThreadPool(),
                        mImageView, itemInfo.getMediaItem().getFilePath(), itemInfo.getMediaItem().getModifiedInSec(),
                        itemInfo.getMediaItem().getPath(), mDrmIcon, mRecyclerView);
                return;
            }
            mDrmIcon.setVisibility(View.GONE);
            loadImage(itemInfo, mImageView);
        }

        @Override
        protected void setScale(MediaInfo info) {
            if (!(info instanceof ItemInfo)) {
                return;
            }
            ItemInfo itemInfo = (ItemInfo) info;
            if (itemInfo.isSelected() && !itemInfo.isMore()) {
                setScale(mImageContainer, mSelectedScale);
            } else {
                setScale(mImageContainer, 1.0f);
            }
        }

        @Override
        protected void setSelected(MediaInfo info) {
            if (!(info instanceof ItemInfo)) {
                return;
            }
            ItemInfo itemInfo = (ItemInfo) info;
            if (mState == STATE_SELECTION && !itemInfo.isMore()) {
                mSelectedView.setSelected(info.isSelected());
                mSelectedView.setVisibility(View.VISIBLE);
            } else if (itemInfo.isMore()) {
                mSelectedView.setVisibility(View.GONE);
            }
        }

        @Override
        protected void setMoreVisible(MediaInfo info) {
            if (!(info instanceof ItemInfo)) {
                return;
            }
            ItemInfo itemInfo = (ItemInfo) info;
            mMoreImage.setVisibility(itemInfo.isMore() ? View.VISIBLE : View.GONE);
        }

        @Override
        protected void setImageTypeIndicator(MediaInfo info) {
            if (!(info instanceof ItemInfo)) {
                return;
            }
            ItemInfo itemInfo = (ItemInfo) info;
            int mediaType = itemInfo.getMediaItem().getMediaType();
            Log.i(TAG, " setImageTypeIndicator mediaType = " + mediaType);
            switch (mediaType) {
                case MediaObject.MEDIA_TYPE_IMAGE_RAW:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_raw);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_GIF:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_gif);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_burst);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BLUR:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mTypeView.setVisibility(mSupportBlur ? View.VISIBLE : View.GONE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH:
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mTypeView.setVisibility(mSupportBokeh ? View.VISIBLE : View.GONE);
                    break;
                case MediaObject.MEDIA_TYPE_VIDEO:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_video);
                    mTypeView.setVisibility(View.VISIBLE);
                    mVideoDurationView.setText(itemInfo.getVideoDuration());
                    mVideoDurationView.setVisibility(View.VISIBLE);
                    mVideoCover.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_voice);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_HDR:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_VHDR:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_voice);
                    mTypeView.setVisibility(View.VISIBLE);
                    mHdrTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mHdrTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO:
                    mTypeView.setImageResource(R.drawable.motion_photo);
                    mTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO:
                    mTypeView.setImageResource(R.drawable.motion_photo);
                    mTypeView.setVisibility(View.VISIBLE);
                    mHdrTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mHdrTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO:
                    mTypeView.setImageResource(R.drawable.motion_photo);
                    mTypeView.setVisibility(View.VISIBLE);
                    mTypeView2.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mTypeView2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO:
                    mTypeView.setImageResource(R.drawable.motion_photo);
                    mTypeView.setVisibility(View.VISIBLE);
                    mHdrTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mHdrTypeView.setVisibility(View.VISIBLE);
                    mTypeView2.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mTypeView2.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_ai_scene);
                    mTypeView.setVisibility(View.VISIBLE);
                    mHdrTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mHdrTypeView.setVisibility(View.VISIBLE);
                    break;
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY:
                case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR:
                    mTypeView.setImageResource(R.drawable.ic_newui_indicator_refocus);
                    mTypeView.setVisibility(mSupportBokeh ? View.VISIBLE : View.GONE);
                    mHdrTypeView.setImageResource(R.drawable.ic_newui_indicator_hdr);
                    mHdrTypeView.setVisibility(View.VISIBLE);
                    break;
                default:
                    mTypeView.setVisibility(View.GONE);
                    mTypeView2.setVisibility(View.GONE);
                    mHdrTypeView.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public void onRecycled() {
            mImageView.setImageBitmap(null);
            mMoreImage.setVisibility(View.GONE);
            mSelectedView.setVisibility(View.GONE);
            mTypeView.setVisibility(View.GONE);
            mTypeView2.setVisibility(View.GONE);
            mHdrTypeView.setVisibility(View.GONE);
            mVideoDurationView.setVisibility(View.GONE);
            mVideoCover.setVisibility(View.GONE);
            setScale(mImageContainer, 1.0f);
            mDrmIcon.setVisibility(View.GONE);
            if (mDrmLoader != null) {
                mDrmLoader.cancelLoad();
            }
            MediaInfo info = findMediaInfoWithPath(getPath());
        }

        private void animatorScale(View view, float from, float to, long duration) {
            ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat("scaleX", from, to), PropertyValuesHolder.ofFloat("scaleY", from, to)).setDuration(duration).start();
        }

        private void setScale(View view, float scale) {
            view.setScaleX(scale);
            view.setScaleY(scale);
        }
    }

    private MediaInfo findMediaInfoWithPath(String path) {
        for (MediaInfo info : mSprdRecyclerViewAdapter.getDataList()) {
            if (path.equals(info.getPath())) {
                return info;
            }
        }
        return null;
    }

    public interface OnItemClickListener {
        void onItemClick(ItemInfo itemInfo);

        void onItemLongClick(ItemInfo itemInfo);
    }

    protected OnItemClickListener mOnItemClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public interface OnLabelClickListener {
        void onLabelClick(LabelInfo labelInfo, OnLabelClickEventHandledListener l);
    }

    public interface OnLabelClickEventHandledListener {
        void onEventHandled();
    }

    protected OnLabelClickListener mOnLabelClickListener;

    public void setOnLabelClickListener(OnLabelClickListener listener) {
        mOnLabelClickListener = listener;
    }

    public void leaveSelectionMode() {
        mState = STATE_NORMAL;
        for (MediaInfo mediaInfo : mSprdRecyclerViewAdapter.getDataList()) {
            mediaInfo.setSelected(false);
        }
        mSprdRecyclerViewAdapter.notifyDataSetChanged();
    }

    public boolean inSelectionMode() {
        return mState == STATE_SELECTION;
    }

    public void setSelectionManager(SelectionManager selectionManager) {
        mSelectionManager = selectionManager;
    }

    public void setActionModeHandler(ActionModeHandler acitonModeHandler) {
        mActionModeHandler = acitonModeHandler;
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mRecyclerView == null || mGridLayoutManagerV == null || mGridLayoutManagerH == null) {
            return;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mRecyclerView.setLayoutManager(mGridLayoutManagerV);
        } else {
            mRecyclerView.setLayoutManager(mGridLayoutManagerH);
        }
    }

    protected abstract void onLabelClicked(LabelInfo labelInfo, int position);

    protected abstract void onImageClicked(ItemInfo itemInfo, int position);

    protected abstract boolean onImageLongClicked(ItemInfo itemInfo, int position);

    protected void notifyDataSetChanged(final List<MediaInfo> newData, boolean detectMoves) {
        /**TODO maybe take longtime in main thread*/
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new MediaInfoDiffUtil(mSprdRecyclerViewAdapter.getDataList(), newData), detectMoves);
        updateData(new Runnable() {
            @Override
            public void run() {
                mSprdRecyclerViewAdapter.setDataList(newData);
                diffResult.dispatchUpdatesTo(mSprdRecyclerViewAdapter);
            }
        });
    }

    public void setActivity(AbstractGalleryActivity activity) {
        mActivity = activity;
    }

    public void forceUpdate() {
        if (mSprdRecyclerViewAdapter != null) {
            mSprdRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    public void setIsGetAlbumIntent(boolean isGetAlbumIntent) {
        mIsGetAlbumIntent = isGetAlbumIntent;
    }

    protected void loadImage(final ItemInfo info, ImageView imageView) {
        if (mImageSlotWidth <= 0 || mImageSlotHeight <= 0 || imageView == null) {
            return;
        }
        Log.d(TAG, "loadImage " + info.getMediaItem().getFilePath());
        String mimeType = info.getMediaItem().getMimeType();
        long dateModified = info.getMediaItem().getModifiedInSec();
        int orientation = info.getMediaItem().getRotation();
        RequestOptions requestOptions = DecodeUtils.withOptions(THUMB_NAIL_WIDTH,
                info.getMediaItem().getWidth(),
                info.getMediaItem().getHeight())
                .fitCenter()
                .placeholder(placeholder(info))
                .error(getResources().getDrawable(R.drawable.ic_newui_damaged_image, null))
                .signature(new MediaStoreSignature(mimeType, dateModified, orientation));

        RequestBuilder builder = Glide.with(getContext())
                .asBitmap()
                .load(info.getMediaItem().getFilePath())
                .apply(requestOptions)
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target,
                                                boolean isFirstResource) {
                        info.setDecodeStatus(false);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap bitmap, Object model, Target<Bitmap> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        Log.i(TAG, " onResourceReady mRamConfig = " + is512Ram);
                        if (bitmap != null) {
                            if (info.getMediaItem() instanceof LocalImage &&
                                    (bitmap.getWidth() >= THUMB_NAIL_WIDTH || bitmap.getHeight() >= THUMB_NAIL_WIDTH)) {
                                //onImageCreated(s, bitmap);
                                if (!isMonkey) {
                                    ImageCache.getDefault(getContext()).add(info.getKey(), bitmap);
                                }
                                info.setDecodeStatus(true);
                                Log.d(TAG, "onResourceReady " + info.getMediaItem().getFilePath());
                            }
                        }
                        return false;
                    }
                });

        if (!isLowRam && !hasCache(info)) {
            builder.thumbnail(0.05f);
        }
        builder.into(imageView);
    }

    private boolean hasCache(ItemInfo info) {
        return null != ImageCache.getDefault(getContext()).get(info.getKey());
    }

    private Drawable placeholder(ItemInfo info) {
        Drawable drawable = null;
        drawable = ImageCache.getDefault(getContext()).get(info.getKey());
        if (drawable != null) {
            return drawable;
        }
        return getContext().getDrawable(R.drawable.ic_newui_wait_loading);
    }

    private int itemId(ItemInfo info) {
        String uri = info.getMediaItem().getContentUri().toString();
        int a = uri.lastIndexOf("/");
        String id = uri.substring(a + 1);
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            return 0;
        }
    }

    protected void onScrolled(int dx, int dy) {
    }

    private void scrollImage(boolean isUp) {
        if (mIsScrollUp != isUp) {
            mIsScrollUp = isUp;
            ImageCache.getDefault(getContext()).setScrollUp(mIsScrollUp);
        }
    }

    private boolean isActivityFinishing() {
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return activity.isDestroyed() || activity.isFinishing();
        }
        return false;
    }

    protected void setRecyclerViewFrozen(final boolean frozen) {
        Log.d(TAG, "setRecyclerViewFrozen frozen=" + frozen);
        mIsFrozen.set(frozen);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "RecyclerView.setLayoutFrozen frozen=" + frozen);
                mRecyclerView.setLayoutFrozen(frozen);
            }
        });
    }

    protected void updateData(Runnable runnable) {
        if (isMonkey) {
            if (mMainHandler.hasMessages(MSG_UPDATE_DATA)) {
                mMainHandler.removeMessages(MSG_UPDATE_DATA);
            }
        }
        Message msg = mMainHandler.obtainMessage(MSG_UPDATE_DATA, runnable);
        mMainHandler.sendMessage(msg);
    }
}

package com.android.gallery3d.v2.ui;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.BitmapLoader;
import com.android.gallery3d.ui.DrmThumbImageLoader;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.v2.cust.EmptyHint;
import com.android.gallery3d.v2.cust.NewAlbumDialog;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.AlbumSetLoadingListener;
import com.android.gallery3d.v2.data.CameraMergeAlbum;
import com.android.gallery3d.v2.data.MyAlbumLabelItem;
import com.android.gallery3d.v2.data.NewAlbumItem;
import com.android.gallery3d.v2.page.AlbumSetPageFragment;
import com.android.gallery3d.v2.util.AlbumSetItemDiff;
import com.android.gallery3d.v2.util.ClickInterval;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.MediaStoreSignature;

import java.util.ArrayList;
import java.util.List;

public class AlbumSetPageUI extends FrameLayout implements AlbumSetLoadingListener {
    private static final String TAG = AlbumSetPageUI.class.getSimpleName();
    private static final int THUMB_SIZE = 400;

    public interface OnAlbumSetItemClickListener {
        void onAlbumSetItemClick(View v, AlbumSetItem item);

        void onAddToAlbum(String dir);

        void onHideAlbum(String path, boolean hide);
    }

    private EmptyHint mEmptyHint;

    private RecyclerView mRecyclerView;
    private Adapter mAdapter;

    private FloatingActionButton mAddAlbumButton;

    private boolean mFirstLoad;

    private AlbumSetPageFragment mAlbumSetPageFragment;

    private OnAlbumSetItemClickListener mOnAlbumSetItemClickListener;

    private View mLoadingView;

    private List<Integer> mLocalAlbumIds;

    public AlbumSetPageUI(Context context) {
        super(context);
    }

    public AlbumSetPageUI(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AlbumSetPageUI(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEmptyHint = findViewById(R.id.empty_hint);
        mEmptyHint.setText(R.string.no_albums);

        mRecyclerView = findViewById(R.id.recycler_view_albumset_page);
        mAddAlbumButton = findViewById(R.id.fab_add_new_album);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        mAdapter = new Adapter();
        mRecyclerView.setAdapter(mAdapter);
        mFirstLoad = true;
        mAddAlbumButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddAlbumClick();
            }
        });

        mLoadingView = findViewById(R.id.loading_view);
        mLocalAlbumIds = MediaSetUtils.getLocalAlbumBuckedIds();
    }

    public void setOnAlbumSetItemClickListener(OnAlbumSetItemClickListener onAlbumSetItemClickListener) {
        mOnAlbumSetItemClickListener = onAlbumSetItemClickListener;
    }

    public void bind(AlbumSetPageFragment page) {
        this.mAlbumSetPageFragment = page;
        mAdapter.addItems(page.getData());
    }

    @Override
    public void loadStart() {
        Log.d(TAG, "loadStart");
        mLoadingView.setVisibility(View.VISIBLE);
    }

    @Override
    public void loading(int index, int size, AlbumSetItem[] item) {
        if (this.mAlbumSetPageFragment == null) {
            throw new RuntimeException("Page not bind.");
        }
        Log.d(TAG, "loading B (" + index + ", " + size + ", " + item[item.length - 1].getName() + ").");
        if (mEmptyHint.isVisible()) {
            mEmptyHint.setVisible(false);
        }

        if ((mAddAlbumButton.getVisibility() == View.GONE)
                && this.mAlbumSetPageFragment.isMainIntent()
                && !this.mAlbumSetPageFragment.isAddToAlbum()
                && !this.mAlbumSetPageFragment.isAddAlbumSelectItems()
                && !this.mAlbumSetPageFragment.isHideAlbums()) {
            mAddAlbumButton.setVisibility(View.VISIBLE);
        }

        if (index == 0) {
            if (mFirstLoad) {
                if (this.mAlbumSetPageFragment.getData().size() > 0) {
                    mFirstLoad = false;
                }
            }
            this.mAlbumSetPageFragment.getData().clear();

            if (this.mAlbumSetPageFragment.isAddToAlbum()) {
                NewAlbumItem newAlbumItem = new NewAlbumItem();
                this.mAlbumSetPageFragment.getData().add(newAlbumItem);
                if (mFirstLoad) {
                    mAdapter.addItem(newAlbumItem);
                }
            }
        }
        for (AlbumSetItem albumSetItem : item) {
            if (!albumSetItem.isTrash()
                    && !albumSetItem.isAllAlbum()
                    && !(albumSetItem instanceof MyAlbumLabelItem)
                    && (albumSetItem.getPhotoCount() + albumSetItem.getVideoCount() <= 0)) {
                continue;
            }
            if (albumSetItem.isTrash() && (!this.mAlbumSetPageFragment.isMainIntent()
                    || this.mAlbumSetPageFragment.isAddToAlbum()
                    || this.mAlbumSetPageFragment.isAddAlbumSelectItems()
                    || this.mAlbumSetPageFragment.isHideAlbums())) {
                continue;
            }
            if (this.mAlbumSetPageFragment.isAddToAlbum()) {
                String dir = albumSetItem.getDir();
                if (!NewAlbumDialog.isMyAlbum(dir)) {
                    continue;
                }
            }
            if (albumSetItem.getMediaSet() != null
                    && this.mAlbumSetPageFragment.isHideAlbums() && (albumSetItem.isAllAlbum()
                    || albumSetItem.getMediaSet() instanceof CameraMergeAlbum
                    || mLocalAlbumIds.contains(albumSetItem.getMediaSet().getBucketId()))) {
                continue;
            }
            this.mAlbumSetPageFragment.getData().add(albumSetItem);
        }

        if (mFirstLoad) {
            for (AlbumSetItem albumSetItem : item) {
                if (!albumSetItem.isTrash()
                        && !albumSetItem.isAllAlbum()
                        && !(albumSetItem instanceof MyAlbumLabelItem)
                        && (albumSetItem.getPhotoCount() + albumSetItem.getVideoCount() <= 0)) {
                    continue;
                }
                if (albumSetItem.isTrash() && (!this.mAlbumSetPageFragment.isMainIntent()
                        || this.mAlbumSetPageFragment.isAddToAlbum()
                        || this.mAlbumSetPageFragment.isAddAlbumSelectItems()
                        || this.mAlbumSetPageFragment.isHideAlbums())) {
                    continue;
                }
                if (this.mAlbumSetPageFragment.isAddToAlbum()) {
                    String dir = albumSetItem.getDir();
                    if (!NewAlbumDialog.isMyAlbum(dir)) {
                        continue;
                    }
                }
                if (albumSetItem.getMediaSet() != null
                        && this.mAlbumSetPageFragment.isHideAlbums() && (albumSetItem.isAllAlbum()
                        || albumSetItem.getMediaSet() instanceof CameraMergeAlbum
                        || mLocalAlbumIds.contains(albumSetItem.getMediaSet().getBucketId()))) {
                    continue;
                }
                mAdapter.addItem(albumSetItem);
            }
        } else if (index + 1 >= size) {
            mAdapter.addItems(this.mAlbumSetPageFragment.getData());
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
        if (!mEmptyHint.isVisible()) {
            mEmptyHint.setVisible(true);
        }

        mAddAlbumButton.setVisibility(View.GONE);
    }

    private abstract class BaseHolder extends RecyclerView.ViewHolder {

        public BaseHolder(View itemView) {
            super(itemView);
        }

        public abstract void bind(AlbumSetItem item);

        public void onRecycled() {
        }
    }

    private class EmptyHolder extends BaseHolder {

        public EmptyHolder(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(AlbumSetItem item) {

        }
    }

    private class NewAlbumHolder extends BaseHolder {
        private ImageView mCover;
        private TextView mAlbumName;
        private View mArrow;

        public NewAlbumHolder(View itemView) {
            super(itemView);
            mCover = itemView.findViewById(R.id.cover);
            mCover.setBackgroundResource(R.color.colorSetBack);
            mCover.setImageResource(R.drawable.ic_list_new_album_36dp);
            mCover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mAlbumName = itemView.findViewById(R.id.album_name);
            mAlbumName.setVisibility(View.VISIBLE);
            mAlbumName.setText(R.string.new_album);
            mArrow = itemView.findViewById(R.id.arrow);
            mArrow.setVisibility(View.GONE);
            itemView.findViewById(R.id.album_info).setVisibility(View.GONE);
            itemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onAddAlbumClick();
                }
            });
        }

        @Override
        public void bind(AlbumSetItem item) {

        }
    }

    private class LabelHolder extends BaseHolder {
        private TextView mLabel;

        public LabelHolder(View itemView) {
            super(itemView);
            mLabel = itemView.findViewById(R.id.label);
        }

        @Override
        public void bind(AlbumSetItem item) {
            mLabel.setText(R.string.my_album);
        }
    }

    private class ImageHolder extends BaseHolder {
        private AlbumSetItem mAlbumSetItem;

        private ImageView mCoverImageView;
        private TextView mAlbumName;
        private TextView mAlbumInfo;

        private View mVideoCover;
        private TextView mVideoDuration;
        private ImageView mType1;
        private ImageView mDrmType;

        private View mArrowView;
        private Switch mSwitch;

        private BitmapLoader mDrmLoader;

        public ImageHolder(View itemView) {
            super(itemView);
            mCoverImageView = itemView.findViewById(R.id.cover);
            mAlbumName = itemView.findViewById(R.id.album_name);
            mAlbumInfo = itemView.findViewById(R.id.album_info);
            mVideoCover = itemView.findViewById(R.id.video_cover);
            mVideoDuration = itemView.findViewById(R.id.video_duration);
            mType1 = itemView.findViewById(R.id.image_type_1);
            mDrmType = itemView.findViewById(R.id.drm);
            mArrowView = itemView.findViewById(R.id.arrow);
            mSwitch = itemView.findViewById(R.id.hide_album_switch);
            if (mAlbumSetPageFragment.isHideAlbums()) {
                mSwitch.setVisibility(View.VISIBLE);
                mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (mOnAlbumSetItemClickListener != null) {
                            mOnAlbumSetItemClickListener.onHideAlbum(mAlbumSetItem.getMediaSetPath(), isChecked);
                        }
                    }
                });
            } else {
                itemView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mOnAlbumSetItemClickListener != null) {
                            if (mAlbumSetPageFragment.isAddToAlbum()) {
                                mOnAlbumSetItemClickListener.onAddToAlbum(mAlbumSetItem.getDir());
                            } else {
                                mOnAlbumSetItemClickListener.onAlbumSetItemClick(v, mAlbumSetItem);
                            }
                        }
                    }
                });
            }
        }

        @Override
        public void bind(AlbumSetItem item) {
            mAlbumSetItem = item;

            setImage(item);
            mAlbumName.setText(item.getName());
            mAlbumInfo.setText(getInfo(item));
            setImageTypeIndicator(item);

            if (mAlbumSetPageFragment.isWidgetGetAlbumIntent()
                    || mAlbumSetPageFragment.isHideAlbums()) {
                mArrowView.setVisibility(View.GONE);
                if (item.getMediaSet() != null) {
                    if (mAlbumSetPageFragment.getHidedAlbums().contains(item.getMediaSet().getPath().toString())) {
                        mSwitch.setChecked(true);
                    } else {
                        mSwitch.setChecked(false);
                    }
                }
            } else {
                mArrowView.setVisibility(View.VISIBLE);
            }
        }

        private void setImage(AlbumSetItem item) {
            if (item.isCoverItemDrm()) {
                mDrmLoader = DrmThumbImageLoader.submit(GalleryAppImpl.getApplication().getThreadPool(),
                        mCoverImageView, item.getCoverPath(), item.getCoverDateModified(),
                        Path.fromString(item.getCoverItemPath()), mDrmType, mRecyclerView);
                return;
            }
            mDrmType.setVisibility(View.GONE);
            loadImage(item);
        }

        private void loadImage(AlbumSetItem item) {
            if (item.isTrash() && (item.getPhotoCount() + item.getVideoCount() <= 0)) {
                mCoverImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mCoverImageView.setBackgroundResource(R.color.colorSetBack);
                mCoverImageView.setImageResource(R.drawable.ic_list_recent_delete_36dp);
                return;
            } else if (item.isAllAlbum() && (item.getPhotoCount() + item.getVideoCount() <= 0)) {
                mCoverImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mCoverImageView.setBackgroundResource(R.color.colorSetBack);
                mCoverImageView.setImageResource(R.drawable.ic_list_default_album_36dp);
                return;
            }
            RequestOptions options = new RequestOptions()
                    .override(THUMB_SIZE)
                    .centerCrop()
                    .placeholder(R.drawable.ic_no_texture)
                    .error(R.drawable.ic_newui_damaged_image)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .signature(new MediaStoreSignature(item.getCoverMimeType(),
                            item.getCoverDateModified(),
                            item.getCoverOrientation()));
            Glide.with(itemView.getContext())
                    .asBitmap()
                    .load(item.getCoverItemUri())
                    .transition(BitmapTransitionOptions.withCrossFade())
                    .apply(options)
                    .into(mCoverImageView);
        }

        private String getInfo(AlbumSetItem item) {
            String info;
            if (item.isTrash()
                    || item.isAllAlbum()
                    || (item.getPhotoCount() != 0 && item.getVideoCount() != 0)) {
                info = itemView.getContext().getResources().getQuantityString(
                        R.plurals.album_info_images, item.getPhotoCount(), item.getPhotoCount())
                        + ", "
                        + itemView.getContext().getResources().getQuantityString(
                        R.plurals.album_info_videos, item.getVideoCount(), item.getVideoCount());
            } else if (item.getPhotoCount() != 0) {
                info = itemView.getContext().getResources().getQuantityString(
                        R.plurals.album_info_images, item.getPhotoCount(), item.getPhotoCount());
            } else if (item.getVideoCount() != 0) {
                info = itemView.getContext().getResources().getQuantityString(
                        R.plurals.album_info_videos, item.getVideoCount(), item.getVideoCount());
            } else {
                info = null;
            }
            return info;
        }

        private void setImageTypeIndicator(AlbumSetItem item) {
            switch (item.getCoverMediaType()) {
                case MediaObject.MEDIA_TYPE_VIDEO: {
                    mVideoCover.setVisibility(View.VISIBLE);
                    mVideoDuration.setVisibility(View.VISIBLE);
                    mType1.setVisibility(View.VISIBLE);
                    mVideoDuration.setText(item.getCoverDuration());
                    mType1.setImageResource(R.drawable.ic_newui_indicator_video);
                    break;
                }
                default: {
                    mVideoCover.setVisibility(View.GONE);
                    mVideoDuration.setVisibility(View.GONE);
                    mType1.setVisibility(View.GONE);
                    break;
                }
            }
        }

        @Override
        public void onRecycled() {
            if (!((Activity) itemView.getContext()).isDestroyed()) {
                Glide.with(itemView.getContext()).clear(mCoverImageView);
            }
            mCoverImageView.setImageBitmap(null);
            mVideoCover.setVisibility(View.GONE);
            mVideoDuration.setVisibility(View.GONE);
            mType1.setVisibility(View.GONE);
            mDrmType.setVisibility(View.GONE);
            if (mDrmLoader != null) {
                mDrmLoader.cancelLoad();
                mDrmLoader = null;
            }
        }
    }

    private class Adapter extends RecyclerView.Adapter<BaseHolder> {
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
        public BaseHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AlbumSetItem.Type_Label) {
                return new LabelHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_albumset_page_label_v2, parent, false));
            } else if (viewType == AlbumSetItem.Type_Image) {
                return new ImageHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_albumset_page_image_v2, parent, false));
            } else if (viewType == AlbumSetItem.Type_NewAlbum) {
                return new NewAlbumHolder(LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_albumset_page_image_v2, parent, false));
            }
            return new EmptyHolder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_albumset_page_empty_v2, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull BaseHolder holder, int position) {
            holder.bind(mData.get(position));
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mData.get(position).getItemViewType();
        }

        @Override
        public void onViewRecycled(@NonNull BaseHolder holder) {
            holder.onRecycled();
        }
    }

    private void onAddAlbumClick() {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onAddAlbumClick ignore");
            return;
        }
        NewAlbumDialog dialog = new NewAlbumDialog();
        dialog.setOnNewAlbumCreatedListener(this.mAlbumSetPageFragment);
        FragmentManager fm = mAlbumSetPageFragment.getActivity().getSupportFragmentManager();
        dialog.show(fm.beginTransaction().setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE),
                NewAlbumDialog.class.getSimpleName());
    }

    public void updateUI() {
        if (mAdapter == null) {
            return;
        }
        mAdapter.notifyDataSetChanged();
    }
}

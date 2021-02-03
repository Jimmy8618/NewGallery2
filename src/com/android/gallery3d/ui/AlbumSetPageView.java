package com.android.gallery3d.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.android.gallery3d.R;
import com.android.gallery3d.data.AlbumSetData;
import com.android.gallery3d.data.ItemInfo;
import com.android.gallery3d.data.LabelInfo;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaInfo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.sidebar.SideBarItem;
import com.android.gallery3d.util.GalleryUtils;

import java.util.ArrayList;

/**
 * Created by apuser on 12/28/16.
 */

public class AlbumSetPageView extends SprdRecyclerPageView {
    private static final String TAG = "AlbumSetPageView";

    private boolean mDoCluster;
    private String mCurrentCluster = SideBarItem.ALBUM;

    private ProgressBar mProgressBar;

    private OnPickAlbumListener mOnPickAlbumListener;

    public interface OnPickAlbumListener {
        void onPickAlbum(LabelInfo labelInfo);
    }

    private OnFirstLoadListener mOnFirstLoadListener;

    public interface OnFirstLoadListener {
        void onFirstLoad();
    }

    public void setOnFirstLoadListener(OnFirstLoadListener l) {
        mOnFirstLoadListener = l;
    }

    public void setOnPickAlbumListener(OnPickAlbumListener l) {
        mOnPickAlbumListener = l;
    }

    public AlbumSetPageView(Context context) {
        super(context);
    }

    public AlbumSetPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlbumSetPageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlbumSetPageView(Context context, AttributeSet attrs, int defStyleAttr, int
            defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        mRecyclerView = findViewById(R.id.recycler_view_album_set);
        mProgressBar = findViewById(R.id.loading_progress_set);
        super.onFinishInflate();
        mIsFirstLoad = true;
    }

    @Override
    protected void onScrolled(int dx, int dy) {
        if (!mIsFirstScrolled && mIsLoading && dy > 0) {
            mIsFirstScrolled = true;
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public void onMediaSetDataChangeStarted() {
        Log.d(TAG, "onMediaSetDataChangeStarted");
        mIsLoading = true;
        if (mIsFirstScrolled || !mIsFirstLoad) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public void clearData() {
        Log.d(TAG, "clearData");
        mSprdRecyclerViewAdapter.setDataList(new ArrayList<MediaInfo>());
        mSprdRecyclerViewAdapter.notifyDataSetChanged();
    }

    public void onMediaSetDataChangeFinished() {
        Log.d(TAG, "onMediaSetDataChangeFinished");
        mIsLoading = false;
        mProgressBar.setVisibility(View.GONE);
        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            mSelectionManager.clearData();
            if (mActionModeHandler != null) {
                mActionModeHandler.updateSelectionMenu();
            }
        }
    }

    public synchronized void onMediaSetDataChanged(AlbumSetData data) {
        MediaSet mediaSet = data.getMediaSet();
        int loaderIndex = data.getCurrentIndex();
        int size = data.getAlbumSetSize();

        Log.d(TAG, "onMediaSetDataChanged B index = " + loaderIndex + ", size = " + size + " mediaSet = " + (mediaSet == null ? "null" : mediaSet.getName()));

        int itemInOneGroupSize;
        int updateFrom = 0;
        int updateCount = 0;
        LabelInfo labelInfo = null;

        if (loaderIndex == 0) {
            if (isMonkey) {
                setRecyclerViewFrozen(true);
            }
            mMediaInfosList.clear();
            if (mMainHandler.hasMessages(MSG_UPDATE_DATA)) {
                mMainHandler.removeMessages(MSG_UPDATE_DATA);
            }
        }
        if (mediaSet == null) {
            Log.d(TAG, "onMediaSetDataChanged index = " + loaderIndex + " mediaSet is null");
        } else if (mediaSet.getMediaItemCount() < 1) {
            Log.d(TAG, "onMediaSetDataChanged mediaSet -> " + mediaSet.getName() + " MediaItemCount < 1 --> " + mediaSet.getMediaItemCount());
            //如果仅有一个专辑才更新界面
            if (size == 1) {
                mMediaInfosList.clear();
                updateData(new Runnable() {
                    @Override
                    public void run() {
                        mSprdRecyclerViewAdapter.setDataList(mMediaInfosList);
                        mSprdRecyclerViewAdapter.notifyDataSetChanged();
                    }
                });
            }
        } else {
            updateFrom = mMediaInfosList.size();
            labelInfo = new LabelInfo(mediaSet.getName() + " | " + mediaSet.getMediaItemCount(), mediaSet);
            mMediaInfosList.add(labelInfo); // add label item

            itemInOneGroupSize = Math.min(mMaxThumbImageCount, mediaSet.getMediaItemCount());
            ArrayList<MediaItem> mediaItems = mediaSet.getMediaItem(0, itemInOneGroupSize);
            MediaItem mediaItem = null;
            for (int j = 0; j < mediaItems.size(); j++) {
                mediaItem = mediaItems.get(j);
                if (mediaItem == null) {
                    continue;
                }
                ItemInfo imageInfo = new ItemInfo(mediaItem, mediaSet);
                imageInfo.setParentLabelInfo(labelInfo);
                if (j == (mMaxThumbImageCount - 1) && mediaSet.getMediaItemCount() > mMaxThumbImageCount) {
                    imageInfo.setMore(true);
                }
                imageInfo.setIndexInOneGroup(j);
                if (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO && (mediaItem instanceof LocalVideo)) {
                    String duration = GalleryUtils.formatDuration(getContext(), ((LocalVideo) mediaItem).getDuration());
                    imageInfo.setVideoDuration(duration);
                }
                labelInfo.addChildItem(imageInfo);
                mMediaInfosList.add(imageInfo);// add image item
            }
            updateCount = mMediaInfosList.size() - updateFrom;

            if (mDoCluster || mIsFirstLoad) {
                if (loaderIndex == 0) {
                    updateData(new Runnable() {
                        @Override
                        public void run() {
                            if (mOnFirstLoadListener != null && mIsFirstLoad) {
                                mOnFirstLoadListener.onFirstLoad();
                            }
                            mSprdRecyclerViewAdapter.setDataList(mMediaInfosList);
                            mSprdRecyclerViewAdapter.notifyDataSetChanged();
                        }
                    });
                } else {
                    final int finalUpdateCount = updateCount;
                    final int finalUpdateFrom = updateFrom;
                    updateData(new Runnable() {
                        @Override
                        public void run() {
                            mSprdRecyclerViewAdapter.setDataList(mMediaInfosList);
                            mSprdRecyclerViewAdapter.notifyItemRangeChanged(finalUpdateFrom, finalUpdateCount);
                        }
                    });
                }
            }

            if (loaderIndex + 1 == size) {
                if (mIsFirstLoad) {
                    mIsFirstLoad = false;
                    mIsFirstScrolled = true;
                } else if (!mDoCluster) {
                    notifyDataSetChanged(mMediaInfosList, false);
                }
                if (mDoCluster) {
                    mDoCluster = false;
                }
                if (isMonkey) {
                    setRecyclerViewFrozen(false);
                }
            }
        }

        Log.d(TAG, "onMediaSetDataChanged E index = " + loaderIndex + ", size = " + size);
    }

    public void enterAlbumSelectionMode() {
        mState = STATE_ALBUM_SELECTION;
        mSprdRecyclerViewAdapter.notifyDataSetChanged();
    }

    public void leaveAlbumSelectionMode() {
        mState = STATE_NORMAL;
        mSprdRecyclerViewAdapter.notifyDataSetChanged();
    }

    public boolean inAlbumSelectionMode() {
        return mState == STATE_ALBUM_SELECTION;
    }

    public boolean inItemSelectionMode() {
        return mState == STATE_SELECTION;
    }

    @Override
    protected void onLabelClicked(LabelInfo labelInfo, final int position) {
        if (mOnItemClickListener != null && mOnLabelClickListener != null && labelInfo != null) {
            if (mState == STATE_SELECTION) {
                labelInfo.setSelected(!labelInfo.isSelected());
                int childItemSize = labelInfo.getChildItemSize();
                for (int i = 0; i < childItemSize; i++) {
                    ItemInfo item = labelInfo.getChildItem(i);
                    item.setSelected(labelInfo.isSelected());
                }
                mOnLabelClickListener.onLabelClick(labelInfo, null);
                mSprdRecyclerViewAdapter.notifyItemRangeChanged(position, childItemSize + 1);
            } else if (mState == STATE_ALBUM_SELECTION) {
                mOnLabelClickListener.onLabelClick(labelInfo, new OnLabelClickEventHandledListener() {
                    @Override
                    public void onEventHandled() {
                        mSprdRecyclerViewAdapter.notifyItemChanged(position);
                    }
                });
                mSprdRecyclerViewAdapter.notifyItemChanged(position);
            }
        } else if (mOnPickAlbumListener != null && labelInfo != null) {
            mOnPickAlbumListener.onPickAlbum(labelInfo);
        }
    }

    @Override
    protected void onImageClicked(ItemInfo itemInfo, int position) {
        if (mOnItemClickListener != null && mOnLabelClickListener != null && itemInfo != null) {
            if (mState == STATE_ALBUM_SELECTION) {
                return;
            }
            if (mState == STATE_SELECTION && !itemInfo.isMore()) {
                itemInfo.setSelected(!itemInfo.isSelected());
                mOnItemClickListener.onItemLongClick(itemInfo);
                boolean isUpdateLabel = false;
                LabelInfo labelInfo = itemInfo.getParentLabelInfo();
                if (labelInfo != null && labelInfo.isSelected() && !itemInfo.isSelected()) {
                    labelInfo.setSelected(false);
                    isUpdateLabel = true;
                } else if (labelInfo != null && labelInfo.isAllChildSelected()) {
                    labelInfo.setSelected(true);
                    isUpdateLabel = true;
                    mOnLabelClickListener.onLabelClick(labelInfo, null);
                }
                if (isUpdateLabel) {
                    mSprdRecyclerViewAdapter.notifyItemChanged(labelInfo.getPosition());
                }
                mSprdRecyclerViewAdapter.notifyItemChanged(position);
            } else {
                mOnItemClickListener.onItemClick(itemInfo);
            }
        }
    }

    @Override
    protected boolean onImageLongClicked(ItemInfo itemInfo, int position) {
        if (null != itemInfo) {
            Log.d(TAG, "onImageLongClicked " + itemInfo.getMediaItem().getFilePath() + ", mState=" + mState);
        } else {
            Log.d(TAG, "onImageLongClicked itemInfo is null !");
            return false;
        }
        if (mState == STATE_ALBUM_SELECTION) {
            return true;
        }

        if (mState == STATE_SELECTION) {
            Log.d(TAG, "onImageLongClicked in selection state, item state: isSelected=" + itemInfo.isSelected() + ", isMore=" + itemInfo.isMore());
            if (!itemInfo.isSelected() && !itemInfo.isMore()) {
                onImageClicked(itemInfo, position);
            }
            return true;
        }

        if (mOnItemClickListener != null && mOnLabelClickListener != null && itemInfo != null && !itemInfo.isMore()) {
            mState = STATE_SELECTION;
            itemInfo.setSelected(true);
            mOnItemClickListener.onItemLongClick(itemInfo);
            LabelInfo labelInfo = itemInfo.getParentLabelInfo();
            if (labelInfo != null && labelInfo.isAllChildSelected()) {
                labelInfo.setSelected(true);
                mOnLabelClickListener.onLabelClick(labelInfo, null);
            }
            mSprdRecyclerViewAdapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    public void setCluster(String key) {
        this.mDoCluster = !mCurrentCluster.equals(key);
        mCurrentCluster = key;
    }

    public void enterPickAlbumMode() {
        setIsGetAlbumIntent(true);
        mState = STATE_ALBUM_SELECTION;
    }
}

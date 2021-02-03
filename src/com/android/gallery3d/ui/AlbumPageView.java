package com.android.gallery3d.ui;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;

import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.android.gallery3d.R;
import com.android.gallery3d.data.AlbumData;
import com.android.gallery3d.data.ItemInfo;
import com.android.gallery3d.data.LabelInfo;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.DateUtils;
import com.android.gallery3d.util.GalleryUtils;

import java.util.ArrayList;

/**
 * Created by apuser on 1/5/17.
 */

public class AlbumPageView extends SprdRecyclerPageView {
    private static final String TAG = "AlbumPageView";

    private ProgressBar mProgressBar;
    private LabelInfo mLabelInfo;

    public AlbumPageView(Context context) {
        super(context);
    }

    public AlbumPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlbumPageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlbumPageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void resume() {
        super.resume();
    }

    @Override
    protected void onFinishInflate() {
        mRecyclerView = findViewById(R.id.recycler_view_album_page);
        mProgressBar = findViewById(R.id.loading_progress);
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

    public synchronized void onMediaSetDataChanged(AlbumData data) {
        int index = data.getCurrentIndex();
        int size = data.getMediaItemCount();
        MediaSet mediaSet = data.getMediaSet();
        ArrayList<MediaItem> mediaItems = data.getMediaItems();
        Log.d(TAG, "onMediaSetDataChanged B index = " + index + ", size = " + size + ", load size: " + mediaItems.size() + " mediaSet = " + (mediaSet == null ? "null" : mediaSet.getName()));

        int updateFrom = 0;
        int updateCount = 0;

        if (index == 0) {
            if (isMonkey) {
                setRecyclerViewFrozen(true);
            }
            mLabelInfo = null;
            mMediaInfosList.clear();
        }

        updateFrom = mMediaInfosList.size();

        MediaItem mediaItem = null;
        for (int i = 0; i < mediaItems.size(); i++) {
            mediaItem = mediaItems.get(i);
            if (mediaItem == null) {
                continue;
            }
            long date = mediaItem.getDateInMs();
            if (date == 0) {
                date = mediaItem.getModifiedInSec() * 1000;
            }
            String time = DateUtils.timeStringWithDateInMs(getContext(), date);
            if (mLabelInfo == null) {
                addLabelInfo(time, mediaSet);
                addImageInfo(mediaItem, mediaSet, time, index + i);
            } else {
                if (mLabelInfo.getTime().equals(time)) {
                    addImageInfo(mediaItem, mediaSet, time, index + i);
                } else {
                    addLabelInfo(time, mediaSet);
                    addImageInfo(mediaItem, mediaSet, time, index + i);
                }
            }
        }

        updateCount = mMediaInfosList.size() - updateFrom;

        if (mIsFirstLoad) {
            final int finalUpdateCount = updateCount;
            final int finalUpdateFrom = updateFrom;
            updateData(new Runnable() {
                @Override
                public void run() {
                    mSprdRecyclerViewAdapter.setDataList(mMediaInfosList);
                    mSprdRecyclerViewAdapter.notifyItemRangeChanged(finalUpdateFrom, finalUpdateCount);
                }
            });
        } else if (index + mediaItems.size() == size) {
            notifyDataSetChanged(mMediaInfosList, false);
        }

        if (index + mediaItems.size() == size) {
            mIsFirstLoad = false;
            mIsFirstScrolled = true;
            if (isMonkey) {
                setRecyclerViewFrozen(false);
            }
        }

        Log.d(TAG, "onMediaSetDataChanged E index = " + index + ", size = " + size + ", load size: " + mediaItems.size());
    }

    private void addLabelInfo(String time, MediaSet mediaSet) {
        mLabelInfo = new LabelInfo(time, mediaSet);
        mLabelInfo.setTime(time);
        mLabelInfo.setPath(mLabelInfo.getPath() + "/" + mLabelInfo.getTitle());
        mMediaInfosList.add(mLabelInfo);
    }

    private void addImageInfo(MediaItem mediaItem, MediaSet mediaSet, String time, int indexInAlbumPage) {
        ItemInfo imageInfo = new ItemInfo(mediaItem, mediaSet);
        imageInfo.setTime(time);
        mLabelInfo.addChildItem(imageInfo);
        imageInfo.setParentLabelInfo(mLabelInfo);
        imageInfo.setIndexInAlbumPage(indexInAlbumPage);
        if (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
            String duration = GalleryUtils.formatDuration(getContext(), ((LocalVideo) mediaItem).getDuration());
            imageInfo.setVideoDuration(duration);
        }
        mMediaInfosList.add(imageInfo);
    }

    public void clearData() {
        mIsFirstLoad = true;
        mSprdRecyclerViewAdapter.getDataList().clear();
        mSprdRecyclerViewAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onLabelClicked(LabelInfo labelInfo, int position) {
        if (mOnItemClickListener != null && labelInfo != null) {
            if (mState == STATE_SELECTION) {
                labelInfo.setSelected(!labelInfo.isSelected());
                int childItemSize = labelInfo.getChildItemSize();
                for (int i = 0; i < childItemSize; i++) {
                    ItemInfo item = labelInfo.getChildItem(i);
                    item.setSelected(labelInfo.isSelected());
                }
                mOnLabelClickListener.onLabelClick(labelInfo, null);
                mSprdRecyclerViewAdapter.notifyItemRangeChanged(position,
                        childItemSize + 1);
            }
        }
    }

    @Override
    protected void onImageClicked(ItemInfo itemInfo, int position) {
        if (mOnItemClickListener != null && itemInfo != null) {
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
        Log.d(TAG, "onImageLongClicked " + itemInfo.getMediaItem().getFilePath() + ", mState=" + mState);
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

        if (mOnItemClickListener != null && itemInfo != null && !itemInfo.isMore()) {
            mState = STATE_SELECTION;
            itemInfo.setSelected(true);
            mOnItemClickListener.onItemLongClick(itemInfo);
            LabelInfo labelInfo = itemInfo.getParentLabelInfo();
            if (labelInfo != null && labelInfo.isAllChildSelected()) {
                labelInfo.setSelected(true);
            }
            mSprdRecyclerViewAdapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    public void scrollToPosition(int position) {
        int index = 0;
        int visibleCount = 0;
        GridLayoutManager layoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        for (int i = 0; i < mSprdRecyclerViewAdapter.getDataList().size(); i++) {
            if (mSprdRecyclerViewAdapter.getDataList().get(i) instanceof ItemInfo && ((ItemInfo) mSprdRecyclerViewAdapter.getDataList().get(i)).getIndexInAlbumPage() == position) {
                index = i;
                break;
            }
        }
        try {
            visibleCount = (layoutManager.findLastCompletelyVisibleItemPosition() - layoutManager.findFirstCompletelyVisibleItemPosition());
        } catch (Exception e) {
            Log.d(TAG, "scrollToPosition Exception e : " + e.toString());
        }
        position = index - visibleCount / 2;
        layoutManager.scrollToPositionWithOffset(position > 0 ? position : 0, 0);
    }

}

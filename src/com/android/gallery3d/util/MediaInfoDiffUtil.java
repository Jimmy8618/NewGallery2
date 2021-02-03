package com.android.gallery3d.util;

import android.os.Bundle;

import androidx.recyclerview.widget.DiffUtil;

import android.util.Log;

import com.android.gallery3d.data.ItemInfo;
import com.android.gallery3d.data.LabelInfo;
import com.android.gallery3d.data.MediaInfo;

import java.util.List;

/**
 * Created by apuser on 1/24/17.
 */

public class MediaInfoDiffUtil extends DiffUtil.Callback {
    private static final String TAG = "MediaInfoDiffUtil";
    private List<MediaInfo> mOldDatas;
    private List<MediaInfo> mNewDatas;

    public MediaInfoDiffUtil(List<MediaInfo> oldDatas, List<MediaInfo> newDatas) {
        mOldDatas = oldDatas;
        mNewDatas = newDatas;
    }

    @Override
    public int getOldListSize() {
        return mOldDatas != null ? mOldDatas.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return mNewDatas != null ? mNewDatas.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        MediaInfo oldInfo = null;
        MediaInfo newInfo = null;
        try {
            oldInfo = mOldDatas.get(oldItemPosition);
            newInfo = mNewDatas.get(newItemPosition);
        } catch (Exception e) {
            Log.d(TAG, "areItemsTheSame Exception:" + e.toString());
            return false;
        }
        if (oldInfo == null || newInfo == null) {
            return false;
        }
        String oldPath = oldInfo.getPath();
        String newPath = newInfo.getPath();

        if (oldInfo instanceof LabelInfo && newInfo instanceof LabelInfo && oldPath.equals(newPath)) {
            return true;
        }

        return oldInfo instanceof ItemInfo && newInfo instanceof ItemInfo && oldPath.equals(newPath);

    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        MediaInfo oldInfo = null;
        MediaInfo newInfo = null;
        try {
            oldInfo = mOldDatas.get(oldItemPosition);
            newInfo = mNewDatas.get(newItemPosition);
        } catch (Exception e) {
            Log.d(TAG, "areContentsTheSame Exception:" + e.toString());
            return false;
        }
        if (oldInfo == null || newInfo == null) {
            return false;
        }

        if (oldInfo.isSelected() != newInfo.isSelected()) {
            return false;
        }

        if (oldInfo instanceof LabelInfo && newInfo instanceof LabelInfo) {
            LabelInfo oldLabelInfo = (LabelInfo) oldInfo;
            LabelInfo newLabelInfo = (LabelInfo) newInfo;

            return oldLabelInfo.getTitle().equals(newLabelInfo.getTitle());

        } else if (oldInfo instanceof ItemInfo && newInfo instanceof ItemInfo) {
            ItemInfo oldItemInfo = (ItemInfo) oldInfo;
            ItemInfo newItemInfo = (ItemInfo) newInfo;

            if (!oldItemInfo.getVideoDuration().equals(newItemInfo.getVideoDuration())) {
                return false;
            }

            if (oldItemInfo.isMore() != newItemInfo.isMore()) {
                return false;
            }

            if (oldItemInfo.getRotation() != newItemInfo.getRotation()) {
                return false;
            }

            if (!oldItemInfo.isMimeTypeEquals(newItemInfo)) {
                return false;
            }

            return oldItemInfo.isMediaTypeEquals(newItemInfo);
        }
        return true;
    }

    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        MediaInfo oldInfo = null;
        MediaInfo newInfo = null;
        try {
            oldInfo = mOldDatas.get(oldItemPosition);
            newInfo = mNewDatas.get(newItemPosition);
        } catch (Exception e) {
            Log.d(TAG, "getChangePayload Exception:" + e.toString());
            return null;
        }
        if (oldInfo == null || newInfo == null) {
            return null;
        }

        Bundle bundle = new Bundle();

        if (oldInfo.isSelected() != newInfo.isSelected()) {
            bundle.putBoolean("isSelected", newInfo.isSelected());
        }

        if (oldInfo instanceof LabelInfo && newInfo instanceof LabelInfo) {
            LabelInfo oldLabelInfo = (LabelInfo) oldInfo;
            LabelInfo newLabelInfo = (LabelInfo) newInfo;

            if (!oldLabelInfo.getTitle().equals(newLabelInfo.getTitle())) {
                bundle.putString("label-title", newLabelInfo.getTitle());
            }

        } else if (oldInfo instanceof ItemInfo && newInfo instanceof ItemInfo) {
            ItemInfo oldItemInfo = (ItemInfo) oldInfo;
            ItemInfo newItemInfo = (ItemInfo) newInfo;

            if (!oldItemInfo.getVideoDuration().equals(newItemInfo.getVideoDuration())) {
                bundle.putString("item-VideoDuration", newItemInfo.getVideoDuration());
            }

            if (oldItemInfo.isMore() != newItemInfo.isMore()) {
                bundle.putBoolean("item-isMore", newItemInfo.isMore());
            }
        }

        if (bundle.size() == 0) {
            return null;
        }
        return bundle;
    }

}

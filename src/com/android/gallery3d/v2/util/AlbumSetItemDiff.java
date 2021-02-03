package com.android.gallery3d.v2.util;

import androidx.recyclerview.widget.DiffUtil;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.data.AlbumSetItem;

import java.util.List;

public class AlbumSetItemDiff extends DiffUtil.Callback {
    private List<AlbumSetItem> oldList;
    private List<AlbumSetItem> newList;

    public AlbumSetItemDiff(List<AlbumSetItem> oldList, List<AlbumSetItem> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return this.oldList.size();
    }

    @Override
    public int getNewListSize() {
        return this.newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        AlbumSetItem oldItem;
        AlbumSetItem newItem;

        try {
            oldItem = this.oldList.get(oldItemPosition);
            newItem = this.newList.get(newItemPosition);
        } catch (Exception e) {
            return false;
        }

        return Utils.equals(oldItem.getMediaSetPath(), newItem.getMediaSetPath());

    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        AlbumSetItem oldItem;
        AlbumSetItem newItem;

        try {
            oldItem = this.oldList.get(oldItemPosition);
            newItem = this.newList.get(newItemPosition);
        } catch (Exception e) {
            return false;
        }

        if (!Utils.equals(oldItem.getName(), newItem.getName())) {
            return false;
        }

        if (oldItem.getPhotoCount() != newItem.getPhotoCount()) {
            return false;
        }

        if (oldItem.getVideoCount() != newItem.getVideoCount()) {
            return false;
        }

        if (!Utils.equals(oldItem.getCoverPath(), newItem.getCoverPath())) {
            return false;
        }

        if (!Utils.equals(oldItem.getCoverMimeType(), newItem.getCoverMimeType())) {
            return false;
        }

        if (oldItem.getCoverOrientation() != newItem.getCoverOrientation()) {
            return false;
        }

        if (oldItem.getCoverDateModified() != newItem.getCoverDateModified()) {
            return false;
        }

        if (oldItem.getCoverMediaType() != newItem.getCoverMediaType()) {
            return false;
        }

        return Utils.equals(oldItem.getCoverDuration(), newItem.getCoverDuration());
    }
}

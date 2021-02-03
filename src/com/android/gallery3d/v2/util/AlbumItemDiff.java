package com.android.gallery3d.v2.util;

import androidx.recyclerview.widget.DiffUtil;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.data.AlbumItem;
import com.android.gallery3d.v2.data.ImageItem;
import com.android.gallery3d.v2.data.LabelItem;

import java.util.List;

public class AlbumItemDiff extends DiffUtil.Callback {
    private List<AlbumItem> oldList;
    private List<AlbumItem> newList;

    public AlbumItemDiff(List<AlbumItem> oldList, List<AlbumItem> newList) {
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
        AlbumItem oldItem;
        AlbumItem newItem;

        try {
            oldItem = this.oldList.get(oldItemPosition);
            newItem = this.newList.get(newItemPosition);
        } catch (Exception e) {
            return false;
        }

        return Utils.equals(oldItem.getItemPath(), newItem.getItemPath());

    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        AlbumItem oldItem;
        AlbumItem newItem;

        try {
            oldItem = this.oldList.get(oldItemPosition);
            newItem = this.newList.get(newItemPosition);
        } catch (Exception e) {
            return false;
        }
        //Common
        if (!Utils.equals(oldItem.getDate(), newItem.getDate())) {
            return false;
        }

        if (oldItem.isSelected() != newItem.isSelected()) {
            return false;
        }
        //

        if (oldItem instanceof LabelItem && newItem instanceof LabelItem) {
            return isContentSame((LabelItem) oldItem, (LabelItem) newItem);
        } else if (oldItem instanceof ImageItem && newItem instanceof ImageItem) {
            return isContentSame((ImageItem) oldItem, (ImageItem) newItem);
        } else {
            return false;
        }
    }

    private boolean isContentSame(LabelItem oldItem, LabelItem newItem) {
        return true;
    }

    private boolean isContentSame(ImageItem oldItem, ImageItem newItem) {
        if (!Utils.equals(oldItem.getMimeType(), newItem.getMimeType())) {
            return false;
        }

        if (oldItem.getOrientation() != newItem.getOrientation()) {
            return false;
        }

        if (oldItem.getMediaType() != newItem.getMediaType()) {
            return false;
        }

        return Utils.equals(oldItem.getDuration(), newItem.getDuration());
    }
}

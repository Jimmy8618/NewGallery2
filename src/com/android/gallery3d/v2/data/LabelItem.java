package com.android.gallery3d.v2.data;

import com.android.gallery3d.data.MediaSet;

import java.util.ArrayList;
import java.util.List;

public class LabelItem extends AlbumItem {

    private final List<ImageItem> mChildren;

    public LabelItem(MediaSet mediaSet, String date, int position) {
        super(Type.LABEL, mediaSet, date, position);
        mChildren = new ArrayList<>();
    }

    @Override
    public String getItemPath() {
        return getMediaSet().getPath().toString() + "/" + getDate();
    }

    public void addChild(ImageItem item) {
        this.mChildren.add(item);
    }

    public void selectChild(boolean selected) {
        for (ImageItem item : mChildren) {
            item.setSelected(selected);
        }
    }

    public int getChildCount() {
        return mChildren.size();
    }

    public boolean isChildSelected() {
        boolean selected = true;
        for (ImageItem item : mChildren) {
            if (!item.isSelected()) {
                selected = false;
                break;
            }
        }
        return selected;
    }

    public List<ImageItem> getChildren() {
        return mChildren;
    }
}

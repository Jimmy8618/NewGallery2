package com.android.gallery3d.data;

import java.util.ArrayList;

/**
 * Created by rui.li on 2017-1-1.
 */

public class LabelInfo extends MediaInfo {
    private String mTitle; //label title
    private ArrayList<ItemInfo> items;

    public LabelInfo(String title, MediaSet album) {
        super(ItemType.LABEL);
        mTitle = title;
        mAlbum = album;
        items = new ArrayList<ItemInfo>();
        mPath = album.getPath().toString();
    }

    public void addChildItem(ItemInfo itemInfo) {
        items.add(itemInfo);
    }

    public int getChildItemSize() {
        return items.size();
    }

    public ItemInfo getChildItem(int i) {
        if (i < 0 || i >= items.size()) {
            return null;
        }
        return items.get(i);
    }

    public boolean isAllChildSelected() {
        for (ItemInfo itemInfo : items) {
            if (!itemInfo.isSelected()) {
                return false;
            }
        }
        return true;
    }

    public String getTitle() {
        return mTitle;
    }
}

package com.android.gallery3d.v2.data;

public class TrashTip extends AlbumItem {

    public TrashTip() {
        super(Type.TRASH_TIP, null, "", 0);
    }

    @Override
    public String getItemPath() {
        return "/trash/all/item/tip";
    }
}

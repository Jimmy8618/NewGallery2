package com.android.gallery3d.v2.data;

public class NewAlbumItem extends AlbumSetItem {

    public NewAlbumItem() {
        super(null);
    }

    @Override
    public int getItemViewType() {
        return Type_NewAlbum;
    }
}

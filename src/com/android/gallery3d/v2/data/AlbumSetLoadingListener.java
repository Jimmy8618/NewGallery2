package com.android.gallery3d.v2.data;

public interface AlbumSetLoadingListener {
    void loadStart();

    void loading(int index, int size, AlbumSetItem[] item);

    void loadEnd();

    void loadEmpty();
}

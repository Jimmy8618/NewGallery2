package com.android.gallery3d.v2.data;

import java.util.List;

public interface AlbumLoadingListener {
    void loadStart();

    void loading(int index, int size, List<AlbumItem> items, int loadedSize);

    void loadEnd();

    void loadEmpty();
}

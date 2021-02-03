package com.android.gallery3d.v2.data;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.LocalAlbumSet;
import com.android.gallery3d.data.Path;

public class HideAlbumSet extends LocalAlbumSet {

    public static final String PATH = "/hide/all";

    HideAlbumSet(Path path, GalleryApp application) {
        super(path, application);
        TAG = HideAlbumSet.class.getSimpleName();
    }
}

package com.android.gallery3d.v2.data;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;

public class HideSource extends MediaSource {
    private static PathMatcher mPathMatcher;

    private static final int HIDE_ALBUM_SET = 0;

    private GalleryApp mApplication;

    public HideSource(GalleryApp app) {
        super("hide");
        mApplication = app;
    }

    static {
        mPathMatcher = new PathMatcher();
        mPathMatcher.add("/hide/all", HIDE_ALBUM_SET);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        switch (mPathMatcher.match(path)) {
            case HIDE_ALBUM_SET:
                return new HideAlbumSet(path, mApplication);
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}

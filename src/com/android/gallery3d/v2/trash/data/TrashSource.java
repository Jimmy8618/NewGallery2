package com.android.gallery3d.v2.trash.data;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;

public class TrashSource extends MediaSource {
    private GalleryApp mApplication;
    private PathMatcher mPathMatcher;

    private static final int TRASH_ALBUM = 0;
    private static final int TRASH_ITEM = 1;

    public TrashSource(GalleryApp app) {
        super("trash");
        this.mApplication = app;
        mPathMatcher = new PathMatcher();
        mPathMatcher.add("/trash/all", TRASH_ALBUM);
        mPathMatcher.add("/trash/all/item/*", TRASH_ITEM);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        switch (mPathMatcher.match(path)) {
            case TRASH_ALBUM:
                return new TrashAlbum(path, mApplication);
            case TRASH_ITEM:
                return new TrashItem(path, mApplication, mPathMatcher.getIntVar(0));
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}

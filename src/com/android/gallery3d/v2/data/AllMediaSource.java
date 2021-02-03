package com.android.gallery3d.v2.data;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MediaSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.PathMatcher;

public class AllMediaSource extends MediaSource {
    private GalleryApp mApplication;
    private PathMatcher mPathMatcher;

    private static final int IMAGE_ALBUM = 0;
    private static final int VIDEO_ALBUM = 1;
    private static final int ALL = 2;

    public AllMediaSource(GalleryApp app) {
        super("all");
        mApplication = app;
        mPathMatcher = new PathMatcher();
        mPathMatcher.add("/all/media/image", IMAGE_ALBUM);
        mPathMatcher.add("/all/media/video", VIDEO_ALBUM);
        mPathMatcher.add("/all/media", ALL);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        switch (mPathMatcher.match(path)) {
            case IMAGE_ALBUM:
                return new AllMediaAlbum(path, new MediaSet[]{
                        new LocalAlbum(path.getChild(0), mApplication, null, true,
                                mApplication.getResources().getString(R.string.image))
                });
            case VIDEO_ALBUM:
                return new AllMediaAlbum(path, new MediaSet[]{
                        new LocalAlbum(path.getChild(1), mApplication, null, false,
                                mApplication.getResources().getString(R.string.video))
                });
            case ALL:
                return new AllMediaAlbum(path, new MediaSet[]{
                        new LocalAlbum(path.getChild(0), mApplication, null, true,
                                mApplication.getResources().getString(R.string.image)),
                        new LocalAlbum(path.getChild(1), mApplication, null, false,
                                mApplication.getResources().getString(R.string.video))
                });
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}

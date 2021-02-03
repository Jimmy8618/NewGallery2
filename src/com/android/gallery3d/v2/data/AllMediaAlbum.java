package com.android.gallery3d.v2.data;

import android.net.Uri;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

public class AllMediaAlbum extends LocalMergeAlbum {
    public static final Path PATH = Path.fromString("/all/media");
    public static final Path IMAGE_PATH = Path.fromString("/all/media/image");
    public static final Path VIDEO_PATH = Path.fromString("/all/media/video");

    public AllMediaAlbum(Path path, MediaSet[] sources) {
        super(path, sources, 0);
    }

    @Override
    public Uri getContentUri() {
        throw new UnsupportedOperationException("this method should never called in AllMediaAlbum");
    }

    @Override
    public String getName() {
        return GalleryAppImpl.getApplication().getString(R.string.all_medias);
    }
}

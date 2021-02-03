package com.android.gallery3d.v2.discover.data;

import android.net.Uri;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import java.util.ArrayList;

public class PeopleMergeAlbum extends LocalMergeAlbum {
    public static final Path PATH_ITEM = Path.fromString("/discover/people/merge/album");
    public static final Path PATH_PLACE_HOLDER_ITEM = Path.fromString("/discover/people/merge/album/placeholder");

    private boolean mIsPlaceHolder;
    private int mPlaceHolderRes;

    public PeopleMergeAlbum(Path path, MediaSet[] sources,
                            int placeHolderRes, boolean isPlaceHolder) {
        super(path, sources, 0);
        this.mIsPlaceHolder = isPlaceHolder;
        this.mPlaceHolderRes = placeHolderRes;
    }

    @Override
    public Uri getContentUri() {
        throw new UnsupportedOperationException("this method should never called in PeopleMergeAlbum");
    }

    @Override
    public synchronized ArrayList<MediaItem> getMediaItem(int start, int count) {
        if (mIsPlaceHolder) {
            ArrayList<MediaItem> list = new ArrayList<>();
            MediaItem mediaItem = loadPlaceHolder(PlaceHolder.PATH_ITEM.getChild(mPlaceHolderRes),
                    GalleryAppImpl.getApplication().getDataManager(), mPlaceHolderRes);
            list.add(mediaItem);
            return list;
        } else {
            return super.getMediaItem(start, count);
        }
    }

    private static MediaItem loadPlaceHolder(Path path, DataManager dataManager, int placeHolderId) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new PlaceHolder(path, placeHolderId);
            }
            return item;
        }
    }

    @Override
    public int getMediaItemCount() {
        if (mIsPlaceHolder) {
            return 1;
        } else {
            return super.getMediaItemCount();
        }
    }

    @Override
    public String getName() {
        String name = super.getName();
        if (TextUtils.isEmpty(name)) {
            name = GalleryAppImpl.getApplication().getString(R.string.un_named);
        }
        return name;
    }

    @Override
    public boolean isPlaceHolder() {
        return mIsPlaceHolder;
    }

    public int[] getFaceIds() {
        MediaSet[] mediaSets = getSources();
        int[] faceIds = new int[mediaSets.length];
        for (int i = 0; i < mediaSets.length; i++) {
            faceIds[i] = ((PeopleAlbum) mediaSets[i]).getFaceId();
        }
        return faceIds;
    }
}

package com.android.gallery3d.v2.data;

import android.net.Uri;
import android.text.TextUtils;

import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.v2.discover.data.PeopleMergeAlbum;
import com.android.gallery3d.v2.trash.data.TrashAlbum;

import java.io.File;
import java.util.ArrayList;

public class AlbumSetItem {
    public static final int Type_Label = 0;
    public static final int Type_Image = 1;
    public static final int Type_NewAlbum = 2;
    private MediaSet mMediaSet;
    private String mMediaSetPath;
    private Cover mCover;
    private String mName;
    private int mPhotoCount;
    private int mVideoCount;
    private boolean mIsTrash;
    private boolean mIsAllAlbum;

    public AlbumSetItem(MediaSet mediaSet) {
        if (mediaSet == null) {
            return;
        }
        this.mMediaSet = mediaSet;
        this.mIsTrash = mediaSet instanceof TrashAlbum;
        this.mIsAllAlbum = mediaSet instanceof AllMediaAlbum;
        this.mMediaSetPath = mediaSet.getPath().toString();
        MediaItem coverItem = mediaSet.getCoverMediaItem();
        if (coverItem != null) {
            this.mCover = new Cover();
            this.mCover.itemPath = coverItem.getPath().toString();
            this.mCover.filePath = isPeopleAlbum(mediaSet) ? mediaSet.getHead() : coverItem.filePath;
            this.mCover.mimeType = coverItem.getMimeType();
            this.mCover.orientation = isPeopleAlbum(mediaSet) ? 0 : coverItem.getRotation();
            this.mCover.dateModified = coverItem.getModifiedInSec();
            this.mCover.mediaType = coverItem.getMediaType();
            this.mCover.duration = coverItem.getDurationString();
            this.mCover.placeHolderId = coverItem.getPlaceHolderId();
            this.mCover.isDrm = coverItem.mIsDrmFile;
            this.mCover.width = coverItem.getWidth();
            this.mCover.height = coverItem.getHeight();
            this.mCover.uri = (isPeopleAlbum(mediaSet) && coverItem.getContentUri() != null) ?
                    Uri.fromFile(new File(mediaSet.getHead())) : coverItem.getContentUri();
        }
        this.mName = mediaSet.getName();
        this.mPhotoCount = 0;
        this.mVideoCount = 0;
        if (mediaSet instanceof LocalAlbum) {
            if (((LocalAlbum) mediaSet).isImage()) {
                this.mPhotoCount = mediaSet.getMediaItemCount();
            } else {
                this.mVideoCount = mediaSet.getMediaItemCount();
            }
        } else if (mediaSet instanceof LocalMergeAlbum) {
            MediaSet[] sources = ((LocalMergeAlbum) mediaSet).getSources();
            for (int i = 0; i < sources.length; i++) {
                if (sources[i] instanceof LocalAlbum) {
                    if (((LocalAlbum) (sources[i])).isImage()) {
                        this.mPhotoCount += sources[i].getMediaItemCount();
                    } else {
                        this.mVideoCount += sources[i].getMediaItemCount();
                    }
                } else if (sources[i] instanceof LocalMergeAlbum) {
                    MediaSet[] subSets = ((LocalMergeAlbum) sources[i]).getSources();
                    for (int j = 0; j < subSets.length; j++) {
                        if (subSets[j] instanceof LocalAlbum) {
                            if (((LocalAlbum) (subSets[j])).isImage()) {
                                this.mPhotoCount += subSets[j].getMediaItemCount();
                            } else {
                                this.mVideoCount += subSets[j].getMediaItemCount();
                            }
                        }
                    }
                }
            }
        } else if (mediaSet instanceof TrashAlbum) {
            this.mPhotoCount = ((TrashAlbum) mediaSet).getPhotoCount();
            this.mVideoCount = ((TrashAlbum) mediaSet).getVideoCount();
        }
    }

    private boolean isPeopleAlbum(MediaSet mediaSet) {
        return mediaSet instanceof PeopleMergeAlbum;
    }

    public String getDir() {
        if (this.mCover == null || TextUtils.isEmpty(this.mCover.filePath)) {
            return null;
        }
        String dir = new File(this.mCover.filePath).getParent();
        ArrayList<Integer> cameraSource = CameraSource.getCameraSources();
        if (cameraSource.contains(GalleryUtils.getBucketId(dir))) {
            dir = MediaSetUtils.INTERNAL_CAMERA;
        }
        return dir;
    }

    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    public boolean isTrash() {
        return mIsTrash;
    }

    public boolean isAllAlbum() {
        return mIsAllAlbum;
    }

    public String getMediaSetPath() {
        return mMediaSetPath;
    }

    public String getName() {
        return mName;
    }

    public int getPhotoCount() {
        return mPhotoCount;
    }

    public int getVideoCount() {
        return mVideoCount;
    }

    public String getCoverPath() {
        if (mCover == null) {
            return null;
        }
        return mCover.filePath;
    }

    public String getCoverMimeType() {
        if (mCover == null) {
            return null;
        }
        return mCover.mimeType;
    }

    public int getCoverOrientation() {
        if (mCover == null) {
            return 0;
        }
        return mCover.orientation;
    }

    public long getCoverDateModified() {
        if (mCover == null) {
            return 0;
        }
        return mCover.dateModified;
    }

    public int getCoverMediaType() {
        if (mCover == null) {
            return 0;
        }
        return mCover.mediaType;
    }

    public String getCoverDuration() {
        if (mCover == null) {
            return "";
        }
        return mCover.duration;
    }

    public Integer getCoverPlaceHolderId() {
        if (mCover == null) {
            return null;
        }
        return mCover.placeHolderId;
    }

    public String getCoverItemPath() {
        if (mCover == null) {
            return "";
        }
        return mCover.itemPath;
    }

    public boolean isCoverItemDrm() {
        return mCover != null && mCover.isDrm;
    }

    public int getCoverItemWidth() {
        if (mCover == null) {
            return 0;
        }
        return mCover.width;
    }

    public int getCoverItemHeight() {
        if (mCover == null) {
            return 0;
        }
        return mCover.height;
    }

    public Uri getCoverItemUri() {
        if (mCover == null) {
            return null;
        }
        return mCover.uri;
    }

    public int getItemViewType() {
        return Type_Image;
    }

    private static class Cover {
        private String itemPath;
        private String filePath;
        private String mimeType;
        private int orientation;
        private long dateModified;
        private int mediaType;
        private String duration;
        private Integer placeHolderId;
        private boolean isDrm;
        private int width;
        private int height;
        private Uri uri;
    }
}

package com.android.gallery3d.data;

/**
 * Created by rui.li on 2017-1-1.
 */

public class ItemInfo extends MediaInfo {
    private boolean hasMore;
    private int mIndexInOneGroup;
    private int mIndexInAlbumPage;
    private String mVideoDuration;

    private MediaItem mMediaItem;
    private LabelInfo mParentLabelInfo;
    private int mOrientation;
    private String mimeType;
    private int mKey;
    private int mediaType;

    private boolean mDecodeStatus = false;

    public ItemInfo(MediaItem mediaItem, MediaSet album) {
        super(ItemType.IMAGE);
        mMediaItem = mediaItem;
        mAlbum = album;
        hasMore = false;
        mIndexInOneGroup = -1;
        mIndexInAlbumPage = 0;
        mPath = mediaItem.getPath().toString();
        mVideoDuration = "";
        mOrientation = mediaItem.getRotation();
        mimeType = mMediaItem.getMimeType();
        mKey = generateKey(getId(), mMediaItem.getModifiedInSec(), mOrientation);
        mediaType = mMediaItem.getMediaType();
    }

    public MediaItem getMediaItem() {
        return mMediaItem;
    }

    public int getRotation() {
        return mOrientation;
    }

    public void setMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public boolean isMore() {
        return hasMore;
    }

    public int getIndexInOneGroup() {
        return mIndexInOneGroup;
    }

    public void setIndexInOneGroup(int indexInOneGroup) {
        mIndexInOneGroup = indexInOneGroup;
    }

    public void setParentLabelInfo(LabelInfo labelInfo) {
        mParentLabelInfo = labelInfo;
    }

    public LabelInfo getParentLabelInfo() {
        return mParentLabelInfo;
    }

    public int getIndexInAlbumPage() {
        return mIndexInAlbumPage;
    }

    public void setIndexInAlbumPage(int indexInAlbumPage) {
        mIndexInAlbumPage = indexInAlbumPage;
    }

    public String getVideoDuration() {
        return mVideoDuration;
    }

    public void setVideoDuration(String mVideoDuration) {
        this.mVideoDuration = mVideoDuration;
    }

    public boolean isMimeTypeEquals(ItemInfo info) {
        boolean equal = true;
        try {
            equal = this.mimeType.equals(info.getMediaItem().getMimeType());
        } catch (Exception e) {
        }
        return equal;
    }

    public boolean isMediaTypeEquals(ItemInfo info) {
        boolean equal = false;
        if (mediaType == info.getMediaItem().getMediaType()) {
            equal = true;
        }
        return equal;
    }

    public int getKey() {
        return mKey;
    }

    private int getId() {
        String uri = mMediaItem.getContentUri().toString();
        int a = uri.lastIndexOf("/");
        String id = uri.substring(a + 1);
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            return 0;
        }
    }

    private int generateKey(int id, long dateModified, int orientation) {
        int result = 0;
        result = 31 * result + (int) (dateModified ^ (dateModified >>> 32));
        result = 31 * result + id;
        result = 31 * result + orientation;
        return result;
    }

    public void setDecodeStatus(boolean decodeStatus) {
        mDecodeStatus = decodeStatus;
    }

    public boolean getDecodeStatus() {
        return mDecodeStatus;
    }
}

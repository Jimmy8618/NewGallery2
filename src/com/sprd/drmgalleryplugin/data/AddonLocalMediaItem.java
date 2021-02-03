package com.sprd.drmgalleryplugin.data;

import android.content.ContentValues;
import android.content.Context;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.sprd.drmgalleryplugin.app.AddonGalleryAppImpl;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.LocalMediaItemUtils;

public class AddonLocalMediaItem extends LocalMediaItemUtils {
    private static final String TAG = "LocalMediaItemAddon";

    @Override
    public void loadDrmInfor(LocalMediaItem item) {
        //Log.d(TAG, "Addon-LocalMediaItem, loadDrmInfor");
        String filePathString = item.getFilePath();
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        item.mIsDrmFile = DrmUtil.isDrmFile(filePathString, null);
        //Log.d(TAG, "Addon-LocalMediaItem, loadDrmInfor, item.mIsDrmFile = " + item.mIsDrmFile);
        if (item.mIsDrmFile) {
            item.mIsDrmSupportTransfer = DrmUtil.isDrmSupportTransfer(filePathString);
        }
        try {
            ContentValues values = drmManagerClient.getMetadata(filePathString);
            if (values != null) {
                item.mDrmFileType = values.getAsString(DrmUtil.DRM_FILE_TYPE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Get extended_data error");
        }
    }

    @Override
    public int getImageSupportedOperations(LocalImage item, int operation) {
        //Log.d(TAG, "Addon-LocalMediaItem, getImageSupportedOperations " + item.getFilePath());
        if (item.mIsDrmFile) {
            operation = MediaObject.SUPPORT_DRM_RIGHTS_INFO | MediaObject.SUPPORT_DELETE | MediaObject.SUPPORT_INFO;
            if (item.mIsDrmSupportTransfer) {
                operation |= MediaObject.SUPPORT_SHARE;
            }
        }
        return operation;
    }

    @Override
    public int getVideoSupportedOperations(LocalVideo item, int operation) {
        if (item.mIsDrmFile) {
            operation &= (~MediaObject.SUPPORT_TRIM & ~MediaObject.SUPPORT_MUTE);
            operation |= MediaObject.SUPPORT_DRM_RIGHTS_INFO;
            if (!item.mIsDrmSupportTransfer) {
                operation &= ~MediaObject.SUPPORT_SHARE;
            }
        }
        return operation;

    }

    @Override
    public Bitmap decodeThumbnailWithDrm(JobContext jc, final int type, String path, BitmapFactory.Options options, int targetSize) {
        if (DrmUtil.isDrmFile(path, null) && DrmUtil.isDrmValid(path)) {
            //Log.d(TAG, "decodeDrmThumbnail");
            return DrmUtil.decodeDrmThumbnail(jc, path, options, targetSize, type);
        }
        //Log.d(TAG, "decodeThumbnail");
        return DecodeUtils.decodeThumbnail(jc, path, options, targetSize, type);
    }

    @Override
    public Bitmap decodeThumbnailWithDrm(JobContext jc, int type, Uri uri, BitmapFactory.Options options, int targetSize) {
        if (DrmUtil.isDrmFile(uri, null) && DrmUtil.isDrmValid(uri)) {
            //Log.d(TAG, "decodeDrmThumbnail");
            return DrmUtil.decodeDrmThumbnail(jc, uri, options, targetSize, type);
        }
        //Log.d(TAG, "decodeThumbnail");
        return DecodeUtils.decodeThumbnail(jc, uri, options, targetSize, type);
    }

    @Override
    public MediaDetails getDetailsByAction(LocalMediaItem item, MediaDetails details, int action, Context context) {
        if (!item.mIsDrmFile) {
            return details;
        }
        details.addDetail(MediaDetails.INDEX_FILENAME, item.getName());
        DrmManagerClient client = AddonGalleryAppImpl.getDrmManagerClient();
        boolean rightsValidity = DrmUtil.isDrmValid(item.filePath);
        details.addDetail(MediaDetails.INDEX_RIGHTS_VALIDITY,
                rightsValidity ? context.getString(R.string.rights_validity_valid) :
                        context.getString(R.string.rights_validity_invalid));
        details.addDetail(MediaDetails.INDEX_RIGHTS_STATUS,
                item.mIsDrmSupportTransfer ? context.getString(R.string.rights_status_share) :
                        context.getString(R.string.rights_status_not_share));
        ContentValues value = client.getConstraints(item.filePath, action);
        if (value != null) {
            Long startTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_START_TIME);
            Long endTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_EXPIRY_TIME);
            byte[] clickTime = value.getAsByteArray(DrmStore.ConstraintsColumns.EXTENDED_METADATA);
            details.addDetail(MediaDetails.INDEX_RIGHTS_STARTTIME, DrmUtil.transferDate(startTime, context));
            details.addDetail(MediaDetails.INDEX_RIGHTS_ENDTIME, DrmUtil.transferDate(endTime, context));
            details.addDetail(MediaDetails.INDEX_EXPIRATION_TIME,
                    DrmUtil.compareDrmExpirationTime(value.get(DrmStore.ConstraintsColumns.LICENSE_AVAILABLE_TIME), clickTime, context));
            details.addDetail(MediaDetails.INDEX_REMAIN_TIMES,
                    DrmUtil.compareDrmRemainRight(item.getFilePath(), value.get(DrmStore.ConstraintsColumns.REMAINING_REPEAT_COUNT), context));
        }
        if (DrmStore.Action.PLAY == action) {
            if (item.fileSize != 0 && (item.height == -1 || item.width == -1 || item.height == 0 || item.width == 0)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                StandardFrameworks.getInstances().decodeDRMBitmapWithBitmapOptions(client, item.filePath, options);
                item.width = options.outWidth;
                item.height = options.outHeight;
            }
            details.addDetail(MediaDetails.INDEX_WIDTH, item.width);
            details.addDetail(MediaDetails.INDEX_HEIGHT, item.width);
        }
        return details;
    }

    @Override
    public boolean isCanFound(boolean found, String filePath, int mediaType) {
        switch (mediaType) {
            case MediaObject.MEDIA_TYPE_IMAGE:
                if (DrmUtil.isDrmFile(filePath, null)) {
                    if (!DrmUtil.isDrmValid(filePath)) {
                        found = false;
                    }
                }
                break;
            case MediaObject.MEDIA_TYPE_VIDEO:
                break;
            default:
                break;
        }
        return found;
    }

    @Override
    public boolean isCanFound(boolean found, Uri uri, int mediaType) {
        switch (mediaType) {
            case MediaObject.MEDIA_TYPE_IMAGE:
                if (DrmUtil.isDrmFile(uri, null)) {
                    if (!DrmUtil.isDrmValid(uri)) {
                        found = false;
                    }
                }
                break;
            case MediaObject.MEDIA_TYPE_VIDEO:
                break;
            default:
                break;
        }
        return found;
    }
}

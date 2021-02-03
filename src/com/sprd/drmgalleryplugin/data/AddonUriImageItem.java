package com.sprd.drmgalleryplugin.data;
/* Add by Spreadst*/

//import android.app.AddonManager;

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
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.UriImage;
import com.android.gallery3d.util.ThreadPool;
import com.sprd.drmgalleryplugin.app.AddonGalleryAppImpl;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.gallery3d.drm.UriImageDrmUtils;

import java.io.FileDescriptor;

public class AddonUriImageItem extends UriImageDrmUtils {
    private Context mAddonContext;
    private static final String TAG = "AddonUriImageItem";

    public AddonUriImageItem(Context context) {
        mAddonContext = context;
    }

    @Override
    public void loadUriDrmInfo(UriImage item) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        item.mIsDrmFile = DrmUtil.isDrmFile(item.getContentUri(), null);
        Log.d(TAG, "Addon-UriImageItem, loadDrmInfo, item.mIsDrmFile = " + item.mIsDrmFile);
        if (item.mIsDrmFile) {
            item.mIsDrmSupportTransfer = DrmUtil.isDrmSupportTransfer(item.getContentUri());
        }
        try {
            ContentValues values = drmManagerClient.getMetadata(item.getContentUri());
            if (values != null) {
                item.mDrmFileType = values.getAsString(DrmUtil.DRM_FILE_TYPE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Get extended_data error");
        }
    }

    @Override
    public Bitmap decodeUriDrmImage(ThreadPool.JobContext jc, int type, UriImage uriImage, BitmapFactory.Options options, int targetSize, FileDescriptor fd) {
        if (DrmUtil.isDrmFile(uriImage.getContentUri(), null) && DrmUtil.isDrmValid(uriImage.getContentUri())) {
            Log.d(TAG, "decode uri DrmThumbnail path = " + uriImage.getFilePath());
            return DrmUtil.decodeDrmThumbnail(jc, uriImage.getFilePath(), options, targetSize, type);
        }
        Log.d(TAG, "decode uri Thumbnail");
        return DecodeUtils.decodeThumbnail(jc, fd, options, targetSize, type);
    }

    @Override
    public boolean isDrmFile(String filePath, String mimeType) {
        return DrmUtil.isDrmFile(filePath, mimeType);
    }

    @Override
    public boolean isDrmFile(Uri uri, String mimeType) {
        return DrmUtil.isDrmFile(uri, mimeType);
    }

    @Override
    public MediaDetails getUriDetailsByAction(UriImage item, MediaDetails details, int action) {
        if (!item.mIsDrmFile) {
            return details;
        }
        details.addDetail(MediaDetails.INDEX_FILENAME, item.getName());
        DrmManagerClient client = AddonGalleryAppImpl.getDrmManagerClient();
        boolean rightsValidity = DrmUtil.isDrmValid(item.getContentUri());
        details.addDetail(MediaDetails.INDEX_RIGHTS_VALIDITY,
                rightsValidity ? mAddonContext.getString(R.string.rights_validity_valid) :
                        mAddonContext.getString(R.string.rights_validity_invalid));
        details.addDetail(MediaDetails.INDEX_RIGHTS_STATUS,
                item.mIsDrmSupportTransfer ? mAddonContext.getString(R.string.rights_status_share) :
                        mAddonContext.getString(R.string.rights_status_not_share));
        ContentValues value = client.getConstraints(item.getContentUri(), action);
        if (value != null) {
            Long startTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_START_TIME);
            Long endTime = value.getAsLong(DrmStore.ConstraintsColumns.LICENSE_EXPIRY_TIME);
            byte[] clickTime = value.getAsByteArray(DrmStore.ConstraintsColumns.EXTENDED_METADATA);
            details.addDetail(MediaDetails.INDEX_RIGHTS_STARTTIME, DrmUtil.transferDate(startTime, mAddonContext));
            details.addDetail(MediaDetails.INDEX_RIGHTS_ENDTIME, DrmUtil.transferDate(endTime, mAddonContext));
            details.addDetail(MediaDetails.INDEX_EXPIRATION_TIME,
                    DrmUtil.compareDrmExpirationTime(value.get(DrmStore.ConstraintsColumns.LICENSE_AVAILABLE_TIME), clickTime, mAddonContext));
            details.addDetail(MediaDetails.INDEX_REMAIN_TIMES,
                    DrmUtil.compareDrmRemainRight(item.getContentUri(), value.get(DrmStore.ConstraintsColumns.REMAINING_REPEAT_COUNT), mAddonContext));
        }
        return details;
    }

}

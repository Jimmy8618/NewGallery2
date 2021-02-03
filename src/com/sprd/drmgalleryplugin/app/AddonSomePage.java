package com.sprd.drmgalleryplugin.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.drm.DrmManagerClient;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.ToastUtil;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.gallery3d.app.NewVideoActivity;
import com.sprd.gallery3d.drm.SomePageUtils;

public class AddonSomePage extends SomePageUtils {
    private Toast toast = null;
    private Dialog mDialog = null;

    @Override
    // SPRD: bug 624616 ,Slide to DRM image, should Consume authority
    public boolean checkPressedIsDrm(
            Activity activity, MediaItem item,
            AlertDialog.OnClickListener confirmListener,
            AlertDialog.OnClickListener cancelListener, DialogInterface.OnKeyListener onKeyListener, boolean getContent) {
        if (item != null && !getContent && item.mIsDrmFile
                && !item.mDrmFileType.equals(DrmUtil.FL_DRM_FILE)) {
            if (DrmUtil.isDrmValid(item.getFilePath())) {
                if (mDialog != null && mDialog.isShowing()) {
                    return true;
                }
                mDialog = new AlertDialog.Builder(activity).
                        setTitle(activity.getString(R.string.drm_consume_title)).
                        setMessage(activity.getString(R.string.drm_consume_hint)).
                        setPositiveButton(android.R.string.ok, confirmListener).
                        setNegativeButton(android.R.string.cancel, cancelListener).create();

                //setCancelable(false).
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.setOnKeyListener(onKeyListener);
                mDialog.show();
            } else {
                Intent intent = new Intent(DrmUtil.ACTION_DRM);
                LocalMediaItem mediaItem = (LocalMediaItem) item;
                intent.putExtra(DrmUtil.FILE_NAME, mediaItem.filePath);
                intent.putExtra(DrmUtil.KEY_DRM_MIMETYPE, DrmUtil.DCF_FILE_MIMETYPE);
                intent.putExtra(DrmUtil.IS_RENEW, true);
                activity.startActivity(intent);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean checkIsDrmFile(MediaSet targetSet) {
        return targetSet.getCoverMediaItem().mIsDrmFile;
    }

    @Override
    public boolean canGetFromDrm(Context context, boolean getContentForSetAs, MediaItem item) {
        if (item.mIsDrmFile && (getContentForSetAs || !item.mDrmFileType.equals(DrmUtil.SD_DRM_FILE))) {
            toast = ToastUtil.showMessage(context, toast, R.string.choose_drm_alert, Toast.LENGTH_SHORT);
            return true;
        }
        return false;
    }

    /* SPRD: Add for new feature DRM @{ */
    @Override
    public boolean newCheckPressedIsDrm(Context context,
                                        String url, OnClickListener listener, boolean getContent) {
        // TODO Auto-generated method stub
        if (DrmUtil.isDrmFile(url, null)) {
            if (DrmUtil.isDrmValid(url)) {
                /* SPRD:Add for bug597820 There is consume notification when playing non-count-limit drm videos @{ */
                if (!DrmUtil.getDrmFileType(url).equals(DrmUtil.FL_DRM_FILE)) {
                    new AlertDialog.Builder(context).
                            setTitle(context.getString(R.string.drm_consume_title)).
                            setMessage(context.getString(R.string.drm_consume_hint)).
                            setPositiveButton(android.R.string.ok, listener).
                            setNegativeButton(android.R.string.cancel, null).
                            show();
                } else {
                    return false;
                }
                /* Bug597820 end @} */
            } else {
                Intent intent = new Intent(DrmUtil.ACTION_DRM);
                intent.putExtra(DrmUtil.FILE_NAME, url);
                intent.putExtra(DrmUtil.KEY_DRM_MIMETYPE, DrmUtil.DCF_FILE_MIMETYPE);
                intent.putExtra(DrmUtil.IS_RENEW, true);
                context.startActivity(intent);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean checkIsDrmFile(String filePath) {
        // TODO Auto-generated method stub
        return DrmUtil.isDrmFile(filePath, null);
    }

    @Override
    public boolean checkIsDrmFileValid(String filePath) {
        // TODO Auto-generated method stub
        return DrmUtil.isDrmValid(filePath);
    }

    @Override
    public boolean isDrmSupportTransfer(String filePath) {
        // TODO Auto-generated method stub
        return DrmUtil.isDrmSupportTransfer(filePath);
    }

    @Override
    public Object newTransferDate(Long time, NewVideoActivity activity) {
        // TODO Auto-generated method stub
        return DrmUtil.newTransferDate(time, activity);
    }

    @Override
    public Object newCompareDrmExpirationTime(Object object, byte[] clickTime, NewVideoActivity activity) {
        // TODO Auto-generated method stub
        return DrmUtil.newCompareDrmExpirationTime(object, clickTime, activity);
    }

    @Override
    public Object newCompareDrmRemainRight(String filePath, Object object, NewVideoActivity activity) {
        // TODO Auto-generated method stub
        return DrmUtil.newCompareDrmRemainRight(filePath, object, activity);
    }

    @Override
    public DrmManagerClient getDrmManagerClient() {
        // TODO Auto-generated method stub
        return AddonGalleryAppImpl.getDrmManagerClient();
    }

    /* SPRD: Add for bug599941 non-sd drm videos are not supported to share @{ */
    @Override
    public boolean newIsSupportShare(String filePath) {
        return DrmUtil.newIsSupportShare(filePath);
    }
    /* Bug599941 end @} */
    /* DRM feature end @} */
}

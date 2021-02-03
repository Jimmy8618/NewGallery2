
package com.sprd.drmgalleryplugin.ui;

import android.content.Context;
import android.graphics.Rect;

import com.android.gallery3d.R;
import com.android.gallery3d.app.PhotoDataAdapter;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PhotoView.Model;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.gallery3d.drm.PhotoViewUtils;

public class AddonPhotoView extends PhotoViewUtils {
    private boolean mIsDrmLocked;
    private boolean mIsDrmUnLocked;
    private ResourceTexture mDRMLockedIcon;
    private ResourceTexture mDRMunLockedIcon;
    private static final String TAG = "AddonGalleryDrm";

    @Override
    public void initPictureDrmIcon(Context context) {
        mDRMLockedIcon = new ResourceTexture(context, R.drawable.ic_drm_lock);
        mDRMunLockedIcon = new ResourceTexture(context, R.drawable.ic_drm_unlock);
    }

    @Override
    public boolean isDrmUnLocked(Model mModel, int offset) {
        if (mModel instanceof PhotoDataAdapter) {
            MediaItem item = mModel.getMediaItem(offset);
            if (item != null) {
                String filePath = item.getFilePath();
                return (DrmUtil.isDrmFile(filePath, null) && DrmUtil.isDrmValid(filePath)
                        && !DrmUtil.getDrmFileType(filePath).equals(DrmUtil.FL_DRM_FILE));
            }
        }
        return false;
    }

    @Override
    public boolean isDrmLocked(Model mModel, int offset) {
        if (mModel instanceof PhotoDataAdapter) {
            MediaItem item = mModel.getMediaItem(offset);
            if (item != null) {
                String filePath = item.getFilePath();
                return (DrmUtil.isDrmFile(filePath, null) && !DrmUtil.isDrmValid(filePath)
                        && !DrmUtil.getDrmFileType(filePath).equals(DrmUtil.FL_DRM_FILE));
            }
        }
        return false;
    }

    @Override
    public void setDrmIcon(GLCanvas canvas, Rect r, PhotoView mPhotoView, Boolean mIsVideo, Boolean mIsDrmLocked, Boolean mIsDrmUnLocked) {
        int s = Math.min(mPhotoView.getWidth(), mPhotoView.getHeight()) / 10;
        if (mIsDrmLocked) {
            mDRMLockedIcon.draw(canvas, 3 * s, 3 * s);
        } else if (mIsVideo && mIsDrmUnLocked) {
            mDRMunLockedIcon.draw(canvas, 3 * s, 3 * s);
        }
    }
}

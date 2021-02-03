/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.print.PrintHelper;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ClusterAlbumSet;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.encoder.AudioImageEncoder;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.trash.data.TrashItem;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.MenuExecutorUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MenuExecutor implements SelectionManager.SelectionClearListener {
    private static final String TAG = "MenuExecutor";

    private static final int MSG_TASK_COMPLETE = 1;
    private static final int MSG_TASK_UPDATE = 2;
    private static final int MSG_TASK_START = 3;
    private static final int MSG_DO_SHARE = 4;

    public static final int EXECUTION_RESULT_SUCCESS = 1;
    public static final int EXECUTION_RESULT_FAIL = 2;
    public static final int EXECUTION_RESULT_CANCEL = 3;

    private ProgressDialog mDialog;
    private Future<?> mTask;
    // wait the operation to finish when we want to stop it.
    private boolean mWaitOnStop;
    private boolean mPaused;

    private final Activity mActivity;
    private final SelectionManager mSelectionManager;
    private final Handler mHandler;
    // SPRD: fix bug 378183, dialog doesn't dismiss
    private Dialog mAlertDialog;
    private Toast mToast;

    private static ProgressDialog createProgressDialog(
            Context context, int titleId, int messageId, int progressMax, boolean isIndeterminate) {
        ProgressDialog dialog = new ProgressDialog(context);
        String title = null;
        try {
            if (titleId != -1) {
                title = context.getResources().getString(titleId);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        dialog.setTitle(title);
        dialog.setTitle(titleId);
        //dialog.setMax(progressMax);
        dialog.setCancelable(false);
        //dialog.setIndeterminate(false);
        /* SPRD: bug 473969 AndroidL :Performance issues porting @{ */
        if (isIndeterminate || progressMax <= 1) {
            dialog.setIndeterminate(true);
            String message = null;
            try {
                if (messageId != -1) {
                    message = context.getResources().getString(messageId);
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
            dialog.setMessage(message);
        } else {
            dialog.setMax(progressMax);
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        }
        /* @} */
        return dialog;
    }

    @Override
    public void onSelectionCleared() {
        Log.d(TAG, "onSelectionCleared");
        dissmissDialog();
    }

    public interface ProgressListener {
        void onConfirmDialogShown();

        void onConfirmDialogDismissed(boolean confirmed);

        void onProgressStart();

        void onProgressUpdate(int index);

        void onProgressComplete(int result);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<MenuExecutor> mMenuExecutor;

        public MySynchronizedHandler(GLRoot root, MenuExecutor menuExecutor) {
            super(root);
            mMenuExecutor = new WeakReference<>(menuExecutor);
        }

        @Override
        public void handleMessage(Message message) {
            MenuExecutor menuExecutor = mMenuExecutor.get();
            if (menuExecutor != null) {
                menuExecutor.handleMySynchronizedHandlerMessage(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMessage(Message message) {
        switch (message.what) {
            case MSG_TASK_START: {
                if (message.obj != null) {
                    ProgressListener listener = (ProgressListener) message.obj;
                    listener.onProgressStart();
                }
                break;
            }
            case MSG_TASK_COMPLETE: {
                stopTaskAndDismissDialog();
                if (message.obj != null) {
                    ProgressListener listener = (ProgressListener) message.obj;
                    listener.onProgressComplete(message.arg1);
                }
                mSelectionManager.leaveSelectionMode();
                break;
            }
            case MSG_TASK_UPDATE: {
                if (mDialog != null && !mPaused) {
                    mDialog.setProgress(message.arg1);
                }
                if (message.obj != null) {
                    ProgressListener listener = (ProgressListener) message.obj;
                    listener.onProgressUpdate(message.arg1);
                }
                break;
            }
            case MSG_DO_SHARE: {
                (mActivity).startActivity((Intent) message.obj);
                break;
            }
            default:
                break;
        }
    }

    public MenuExecutor(
            Activity activity, GLRoot glRoot, SelectionManager selectionManager) {
        mActivity = Utils.checkNotNull(activity);
        mSelectionManager = Utils.checkNotNull(selectionManager);
        mSelectionManager.setSelectionClearListener(this);
        mHandler = new MySynchronizedHandler(glRoot, this);
    }

    private void stopTaskAndDismissDialog() {
        Log.d(TAG, "stopTaskAndDismissDialog mTask=" + mTask + ", mWaitOnStop=" + mWaitOnStop);
        if (mTask != null) {
            if (!mWaitOnStop) {
                mTask.cancel();
            }
            /* SPRD: Modify for bug574443, IllegalArgumentException will be thrown if mAcitivity has been destroyed @{ */
            Log.d(TAG, "stopTaskAndDismissDialog mActivity.isFinishing() =" + mActivity.isFinishing() + ", " +
                    (mDialog != null ? "mDialog is showing ? " + mDialog.isShowing()
                            : "mDialog is null"));
            if (!mActivity.isFinishing() && mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
            }
            /* @} */
            mDialog = null;
            mTask = null;
        }
    }

    public void resume() {
        mPaused = false;
        if (mDialog != null) {
            mDialog.show();
        }
    }

    public void pause() {
        mPaused = true;
        Log.d(TAG, "pause " + (mDialog != null ? "mDialog is showing ? " + mDialog.isShowing()
                : "mDialog is null"));
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.hide();
        }
    }

    public void destroy() {
        stopTaskAndDismissDialog();
    }

    private void onProgressUpdate(int index, ProgressListener listener) {
        mHandler.sendMessage(
                mHandler.obtainMessage(MSG_TASK_UPDATE, index, 0, listener));
    }

    private void onProgressStart(ProgressListener listener) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_TASK_START, listener));
    }

    private void onProgressComplete(int result, ProgressListener listener) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_TASK_COMPLETE, result, 0, listener));
    }

    public static void updateMenuOperation(Menu menu, int supported, boolean selectedItems) {
        boolean supportDelete = (supported & MediaObject.SUPPORT_DELETE) != 0;
        boolean supportRotate = (supported & MediaObject.SUPPORT_ROTATE) != 0;
        boolean supportCrop = (supported & MediaObject.SUPPORT_CROP) != 0;
        boolean supportTrim = (supported & MediaObject.SUPPORT_TRIM) != 0;
        boolean supportMute = (supported & MediaObject.SUPPORT_MUTE) != 0;
        boolean supportShare = (supported & MediaObject.SUPPORT_SHARE) != 0;
        boolean supportSetAs = (supported & MediaObject.SUPPORT_SETAS) != 0;
        boolean supportShowOnMap = (supported & MediaObject.SUPPORT_SHOW_ON_MAP) != 0;
        boolean supportCache = (supported & MediaObject.SUPPORT_CACHE) != 0;
        boolean supportEdit = (supported & MediaObject.SUPPORT_EDIT) != 0;
        boolean supportInfo = (supported & MediaObject.SUPPORT_INFO) != 0;
        boolean supportPrint = (supported & MediaObject.SUPPORT_PRINT) != 0;
        supportPrint &= PrintHelper.systemSupportsPrint();

        setMenuItemVisible(menu, R.id.action_delete, supportDelete);
        if (selectedItems) {
            setMenuItemVisible(menu, R.id.action_rotate_ccw, false /* supportRotate */);
            setMenuItemVisible(menu, R.id.action_rotate_cw, false /* supportRotate */);
            setMenuItemVisible(menu, R.id.action_crop, false /* supportCrop */);
            setMenuItemVisible(menu, R.id.action_trim, false /* supportTrim */);
            setMenuItemVisible(menu, R.id.action_mute, false /* supportMute */);
            setMenuItemVisible(menu, R.id.action_show_on_map, false /* supportShowOnMap */);
            setMenuItemVisible(menu, R.id.print, false /* supportPrint */);
        } else {
            setMenuItemVisible(menu, R.id.action_rotate_ccw, supportRotate);
            setMenuItemVisible(menu, R.id.action_rotate_cw, supportRotate);
            setMenuItemVisible(menu, R.id.action_crop, supportCrop);
            setMenuItemVisible(menu, R.id.action_trim, supportTrim);
            setMenuItemVisible(menu, R.id.action_mute, supportMute);
            setMenuItemVisible(menu, R.id.action_show_on_map, supportShowOnMap);
            setMenuItemVisible(menu, R.id.print, supportPrint);
        }
        // Hide panorama until call to updateMenuForPanorama corrects it
        setMenuItemVisible(menu, R.id.action_share_panorama, false);
        setMenuItemVisible(menu, R.id.action_share, supportShare);
        setMenuItemVisible(menu, R.id.action_setas, supportSetAs);
        setMenuItemVisible(menu, R.id.action_edit, supportEdit);
        // setMenuItemVisible(menu, R.id.action_simple_edit, supportEdit);
        setMenuItemVisible(menu, R.id.action_details, supportInfo);
        /* SPRD: Drm feature start @{ */
        MenuExecutorUtils.getInstance().updateDrmMenuOperation(menu, supported);
        /* SPRD: Drm feature end @} */
    }

    public static void updateMenuForPanorama(Menu menu, boolean shareAsPanorama360,
                                             boolean disablePanorama360Options) {
        setMenuItemVisible(menu, R.id.action_share_panorama, shareAsPanorama360);
        if (disablePanorama360Options) {
            setMenuItemVisible(menu, R.id.action_rotate_ccw, false);
            setMenuItemVisible(menu, R.id.action_rotate_cw, false);
        }
    }

    private static void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private Path getSingleSelectedPath() {
        ArrayList<Path> ids = mSelectionManager.getSelected(true);
        /* SPRD: Modify 20160316 for bug542981, if count of selected image is more than 1, do nothing. @{
         * Utils.assertTrue(ids.size() == 1);
         */
        // SPRD: [coverity] CID 124569 Dereference null return value for bug 553908
        if (ids == null || ids.size() != 1) {
            return null;
        }
        /* @} */
        return ids.get(0);
    }

    private Intent getIntentBySingleSelectedPath(String action) {
        DataManager manager = null;
        if (mActivity instanceof AbstractGalleryActivity) {
            manager = ((AbstractGalleryActivity) mActivity).getDataManager();
        } else if (mActivity instanceof GalleryActivity2) {
            manager = ((GalleryActivity2) mActivity).getDataManager();
        }
        Utils.checkNotNull(manager);
        Path path = getSingleSelectedPath();
        /* SPRD: Modify 20160316 for bug542981, if count of selected image is more than 1, do nothing. @{ */
        if (path == null) {
            return null;
        }
        /* @} */
        String mimeType = getMimeType(manager.getMediaType(path));
        Uri uri = GalleryUtils.transFileToContentType(manager.getContentUri(path), mActivity);
        return new Intent(action).setDataAndType(uri, mimeType);
    }

    private void onMenuClicked(int action, ProgressListener listener) {
        onMenuClicked(action, listener, false, true);
    }

    public void onMenuClicked(int action, ProgressListener listener,
                              boolean waitOnStop, boolean showDialog) {
        int title = -1;
        int message = -1;
        boolean isShowDialog = showDialog;
        switch (action) {
            case R.id.action_select_all:
                if (mSelectionManager.inSelectAllMode()) {
                    mSelectionManager.deSelectAll();
                } else {
                    mSelectionManager.selectAll();
                }
                return;
            case R.id.action_crop: {
                /* SPRD: Modify 20160316 for bug542981, if count of selected image is more than 1, do nothing. @{ */
                Intent intent = getIntentBySingleSelectedPath(CropActivity.CROP_ACTION);
                if (intent != null) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("crop_in_gallery", true);
                    intent.putExtras(bundle);
                    mActivity.startActivity(intent);
                }
                /* @} */
                return;
            }
            case R.id.action_edit: {
                /* SPRD: Modify 20160316 for bug542981, if count of selected image is more than 1, do nothing. @{ */
//                Intent intent = getIntentBySingleSelectedPath(Intent.ACTION_EDIT);
//                if (intent != null) {
//                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                    ((Activity) mActivity).startActivity(Intent.createChooser(intent, null));
//                }
                /* @} */
                MediaItem item = null;
                DataManager manager = null;
                if (mActivity instanceof AbstractGalleryActivity) {
                    manager = ((AbstractGalleryActivity) mActivity).getDataManager();
                } else if (mActivity instanceof GalleryActivity2) {
                    manager = ((GalleryActivity2) mActivity).getDataManager();
                }
                Utils.checkNotNull(manager);
                try {
                    item = (MediaItem) manager.getMediaObject(getSingleSelectedPath());
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                GalleryUtils.launchEditor(mActivity, item, -1);
                return;
            }
            case R.id.action_setas: {
                /* SPRD: Modify 20160316 for bug542981, if count of selected image is more than 1, do nothing. @{ */
                Intent intent = getIntentBySingleSelectedPath(Intent.ACTION_ATTACH_DATA);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra("mimeType", intent.getType());
                    Activity activity = mActivity;
                    activity.startActivity(Intent.createChooser(
                            intent, activity.getString(R.string.set_as)));
                }
                /* @} */
                return;
            }
            case R.id.action_delete:
                title = R.string.delete;
                message = R.string.wait_for_deleting;
                isShowDialog = false;
                break;
            case R.id.action_rotate_cw:
                title = R.string.rotate_right;
                message = R.string.wait_for_rotating;
                break;
            case R.id.action_rotate_ccw:
                title = R.string.rotate_left;
                message = R.string.wait_for_rotating;
                break;
            case R.id.action_show_on_map:
                title = R.string.show_on_map;
                break;
            case R.id.action_trash_restore:
                title = R.string.trash_restore;
                message = R.string.wait_for_restoring;
                isShowDialog = false;
                break;
            case R.id.action_trash_delete:
                title = R.string.delete;
                message = R.string.wait_for_deleting;
                break;
            case R.id.action_move_out_things:
                title = R.string.move_out_of_this_classification;
                message = R.string.moving_out;
                break;
            case R.id.action_move_out_people:
                title = R.string.move_out_of_this_people;
                message = R.string.moving_out;
                break;
            default:
                return;
        }
        startAction(action, title, message, listener, waitOnStop, isShowDialog);
    }

    private class ConfirmDialogListener implements OnClickListener, OnCancelListener {
        private final int mActionId;
        private final ProgressListener mListener;

        public ConfirmDialogListener(int actionId, ProgressListener listener) {
            mActionId = actionId;
            mListener = listener;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (mListener != null) {
                    mListener.onConfirmDialogDismissed(true);
                }
                onMenuClicked(mActionId, mListener);
            } else {
                if (mListener != null) {
                    mListener.onConfirmDialogDismissed(false);
                }
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            if (mListener != null) {
                mListener.onConfirmDialogDismissed(false);
            }
        }
    }

    public void onMenuClicked(MenuItem menuItem, String confirmMsg,
                              final ProgressListener listener) {
        final int action = menuItem.getItemId();

        if (confirmMsg != null) {
            if (listener != null) {
                listener.onConfirmDialogShown();
            }
            ConfirmDialogListener cdl = new ConfirmDialogListener(action, listener);
            /* SPRD: fix bug 378183, dialog doesn't dismiss
            new AlertDialog.Builder(mActivity.getAndroidContext())
                    .setMessage(confirmMsg)
                    .setOnCancelListener(cdl)
                    .setPositiveButton(R.string.ok, cdl)
                    .setNegativeButton(R.string.cancel, cdl)
                    .create().show();
            */
            /* SPRD: dialog doesn't dismiss @{ */
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                return;
            }
            mAlertDialog = new AlertDialog.Builder(mActivity)
                    .setMessage(confirmMsg)
                    .setOnCancelListener(cdl)
                    .setPositiveButton(R.string.ok, cdl)
                    .setNegativeButton(R.string.cancel, cdl)
                    .setCancelable(false)
                    .create();
            mAlertDialog.show();
            /* @} */
        } else {
            onMenuClicked(action, listener);
        }
    }

    public void startAction(int action, int title, ProgressListener listener) {
        startAction(action, title, -1, listener, false, true);
    }

    public void startAction(final int action, final int title, final int message, final ProgressListener listener,
                            final boolean waitOnStop, final boolean showDialog) {
        final ArrayList<Path> ids = mSelectionManager.getSelected(false);
        ThreadPool threadPool = null;
        if (mActivity instanceof AbstractGalleryActivity) {
            threadPool = ((AbstractGalleryActivity) mActivity).getBatchServiceThreadPoolIfAvailable();
        } else if (mActivity instanceof GalleryActivity2) {
            threadPool = ((GalleryActivity2) mActivity).getBatchServiceThreadPoolIfAvailable();
        }
        Utils.checkNotNull(threadPool);
        final ThreadPool finalThreadPool = threadPool;

        ArrayList<String> storageList;
        if ((action == R.id.action_delete
                || action == R.id.action_trash_restore
                || action == R.id.action_rotate_ccw
                || action == R.id.action_rotate_cw)
                && (storageList = findInvalidatePermissionStorage(ids)).size() > 0) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    stopTaskAndDismissDialog();
                    if (showDialog && ids != null) {
                        if (action == R.id.action_delete) {
                            mDialog = createProgressDialog(mActivity, title, message, 0, false);
                        } else {
                            mDialog = createProgressDialog(mActivity, title, message, ids.size(), !mSelectionManager.getIsAlbumSet());
                        }
                        mDialog.show();
                    } else {
                        mDialog = null;
                    }
                    MediaOperation operation = new MediaOperation(action, ids, listener);
                    mTask = finalThreadPool.submit(operation, null);
                    mWaitOnStop = waitOnStop;
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(mActivity, null);
                }
            };
            if (mActivity instanceof GalleryActivity
                    || mActivity instanceof GalleryActivity2) {
                SdCardPermission.requestSdcardPermission(mActivity, storageList, (SdCardPermissionAccessor) mActivity,
                        sdCardPermissionListener);
            }
        } else {
            stopTaskAndDismissDialog();
            if (showDialog && ids != null) {
                if (action == R.id.action_delete) {
                    mDialog = createProgressDialog(mActivity, title, message, 0, false);
                } else {
                    mDialog = createProgressDialog(mActivity, title, message, ids.size(), !mSelectionManager.getIsAlbumSet());
                }
                mDialog.show();
            } else {
                mDialog = null;
            }
            MediaOperation operation = new MediaOperation(action, ids, listener);
            mTask = threadPool.submit(operation, null);
            mWaitOnStop = waitOnStop;
        }
    }

    public void startSingleItemAction(int action, Path targetPath) {
        ThreadPool threadPool = null;
        if (mActivity instanceof AbstractGalleryActivity) {
            threadPool = ((AbstractGalleryActivity) mActivity).getBatchServiceThreadPoolIfAvailable();
        } else if (mActivity instanceof GalleryActivity2) {
            threadPool = ((GalleryActivity2) mActivity).getBatchServiceThreadPoolIfAvailable();
        }
        Utils.checkNotNull(threadPool);

        ArrayList<Path> ids = new ArrayList<Path>(1);
        ids.add(targetPath);
        mDialog = null;
        MediaOperation operation = new MediaOperation(action, ids, null);
        threadPool.submit(operation, null);
        mWaitOnStop = false;
    }

    public static String getMimeType(int type) {
        switch (type) {
            /* SPRD: add to support play gif @{ */
            case MediaObject.MEDIA_TYPE_GIF:
                return GalleryUtils.MIME_TYPE_IMAGE_GIF;
            /* @} */
            /* SPRD: fix bug 387548, WBMP don't support edit @{ */
            case MediaObject.MEDIA_TYPE_IMAGE_WBMP:
                return GalleryUtils.MIME_TYPE_IMAGE_WBMP;
            /* @} */
            case MediaObject.MEDIA_TYPE_VIDEO:
                return GalleryUtils.MIME_TYPE_VIDEO;
            default:
                return GalleryUtils.MIME_TYPE_IMAGE;
        }
    }

    private boolean execute(
            DataManager manager, JobContext jc, int cmd, Path path) {
        boolean result = true;
        Log.v(TAG, "Execute cmd: " + cmd + " for " + path);
        long startTime = System.currentTimeMillis();

        switch (cmd) {
            case R.id.action_delete:
                manager.delete(path);
                break;
            case R.id.action_rotate_cw:
                manager.rotate(path, 90);
                break;
            case R.id.action_rotate_ccw:
                manager.rotate(path, -90);
                break;
            case R.id.action_toggle_full_caching: {
                MediaObject obj = manager.getMediaObject(path);
                int cacheFlag = obj.getCacheFlag();
                if (cacheFlag == MediaObject.CACHE_FLAG_FULL) {
                    cacheFlag = MediaObject.CACHE_FLAG_SCREENNAIL;
                } else {
                    cacheFlag = MediaObject.CACHE_FLAG_FULL;
                }
                obj.cache(cacheFlag);
                break;
            }
            case R.id.action_show_on_map: {
                MediaItem item = (MediaItem) manager.getMediaObject(path);
                double latlng[] = new double[2];
                item.getLatLong(latlng);
                if (GalleryUtils.isValidLocation(latlng[0], latlng[1])) {
                    GalleryUtils.showOnMap(mActivity, latlng[0], latlng[1]);
                }
                break;
            }
            case R.id.action_trash_restore: {
                manager.restore(path);
                break;
            }
            case R.id.action_trash_delete: {
                manager.delete(path);
                break;
            }
            case R.id.action_move_out_things: {
                manager.moveOutThings(path);
                break;
            }
            case R.id.action_move_out_people: {
                manager.moveOutPeople(path);
                break;
            }
            default:
                throw new AssertionError();
        }
        Log.v(TAG, "It takes " + (System.currentTimeMillis() - startTime) +
                " ms to execute cmd for " + path);
        return result;
    }

    private class MediaOperation implements Job<Void> {
        private final ArrayList<Path> mItems;
        private final int mOperation;
        private final ProgressListener mListener;

        public MediaOperation(int operation, ArrayList<Path> items,
                              ProgressListener listener) {
            mOperation = operation;
            mItems = items;
            mListener = listener;
        }

        @Override
        public Void run(JobContext jc) {
            int index = 0;
            DataManager manager = null;
            if (mActivity instanceof AbstractGalleryActivity) {
                manager = ((AbstractGalleryActivity) mActivity).getDataManager();
            } else if (mActivity instanceof GalleryActivity2) {
                manager = ((GalleryActivity2) mActivity).getDataManager();
            }
            Utils.checkNotNull(manager);

            int result = EXECUTION_RESULT_SUCCESS;
            boolean isDeleteOperation = R.id.action_delete == mOperation;
            try {
                onProgressStart(mListener);
                if (mItems.size() > 1 && (R.id.action_rotate_cw == mOperation || R.id.action_rotate_ccw == mOperation)) {
                    manager.setBatchRotateListener(new DataManager.BatchListener() {
                        @Override
                        public void updateIndex(int index) {
                            onProgressUpdate(index, mListener);
                        }
                    });
                    switch (mOperation) {
                        case R.id.action_rotate_cw:
                            Log.d(TAG, " Batch right rotate !");
                            manager.batchRotate(mItems, 90);
                            break;
                        case R.id.action_rotate_ccw:
                            Log.d(TAG, " Batch left rotate !");
                            manager.batchRotate(mItems, -90);
                            break;
                        default:
                            break;
                    }
                } else if (R.id.action_delete == mOperation) {
                    //manager.batchDelete(mItems);
                    manager.delete(mItems);
                } else {
                    for (Path id : mItems) {
                        // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
                        // if current operation is delete cluster. @{
                        if (isDeleteOperation) {
                            if ("cluster".equals(id.getPrefix())) {
                                ClusterAlbumSet.setClusterDeleteOperation(true);
                            }
                        }
                        // @}
                        if (jc.isCancelled()) {
                            result = EXECUTION_RESULT_CANCEL;
                            break;
                        }
                        if (!execute(manager, jc, mOperation, id)) {
                            result = EXECUTION_RESULT_FAIL;
                        }
                        onProgressUpdate(++index, mListener);
                    }
                }
            } catch (Throwable th) {
                Log.e(TAG, "failed to execute operation " + mOperation, th);
                if (mOperation == R.id.action_trash_restore) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mToast = ToastUtil.showMessage(mActivity, mToast, R.string.trash_restore_fail, Toast.LENGTH_LONG);
                        }
                    });
                }
            } finally {
                // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
                // if current operation is delete cluster. @{
                if (isDeleteOperation) {
                    ClusterAlbumSet.setClusterDeleteOperation(false);
                }
                // @}
                onProgressComplete(result, mListener);
            }
            return null;
        }
    }

    /* SPRD: fix bug 378183, dialog doesn't dismiss @{ */
    public void dissmissDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }
    /* @} */

    public void onMenuClicked(final int action, String confirmMsg,
                              final ProgressListener listener) {
        if (confirmMsg != null) {
            if (listener != null) {
                listener.onConfirmDialogShown();
            }
            ConfirmDialogListener cdl = new ConfirmDialogListener(action, listener);
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                return;
            }
            mAlertDialog = new AlertDialog.Builder(mActivity)
                    .setMessage(confirmMsg)
                    .setOnCancelListener(cdl)
                    .setPositiveButton(R.string.ok, cdl)
                    .setNegativeButton(R.string.cancel, cdl)
                    .setCancelable(false)
                    .create();
            mAlertDialog.show();
        } else {
            onMenuClicked(action, listener);
        }
    }

    public static void launchShareIntent(final Activity context, final MediaItem current) {
        if (current == null || context == null) {
            return;
        }

        if (StandardFrameworks.getInstances().isSupportShareAsVideo()
                && (current.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE
                || current.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_VHDR
                || current.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_VFDR)) {
            View view = LayoutInflater.from(context).inflate(R.layout.dialog_share_as_video, null);
            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.share)
                    .setView(view).create();
            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    switch (view.getId()) {
                        case R.id.share_as_image:
                            lockScreen(context);
                            Intent shareIntent = createShareIntent(context, current);
                            context.startActivity(shareIntent);
                            break;
                        case R.id.share_as_video:
                            shareAsVideoEx(context, current);
                            break;
                        default:
                            break;
                    }
                }
            };
            view.findViewById(R.id.share_as_image).setOnClickListener(clickListener);
            view.findViewById(R.id.share_as_video).setOnClickListener(clickListener);
            dialog.show();
        } else {
            lockScreen(context);
            Intent shareIntent = createShareIntent(context, current);
            context.startActivity(shareIntent);
        }
    }

    private static void lockScreen(Activity context) {
        if (context instanceof AbstractGalleryActivity) {
            ((AbstractGalleryActivity) context).lockScreen();
        } else if (context instanceof GalleryActivity2) {
            ((GalleryActivity2) context).lockScreen();
        }
    }

    private static Intent createShareIntent(Context context, MediaObject mediaObject) {
        String msgShareTo = context.getResources().getString(R.string.share);
        int type = mediaObject.getMediaType();
        Uri contentUri = GalleryUtils.transFileToContentType(mediaObject.getContentUri(), context);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(MenuExecutor.getMimeType(type))
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent = Intent.createChooser(intent, msgShareTo);
        return intent;
    }

    private static void shareAsVideoEx(final Activity context, final MediaItem current) {
        String filePath = current.getFilePath();
        if (filePath == null ||
                GalleryStorageUtil.isInInternalStorage(filePath) ||
                SdCardPermission.hasStoragePermission(filePath)) {
            shareAsVideo(context, current);
        } else {
            SdCardPermissionListener listener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    shareAsVideo(context, current);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(context,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.i(TAG, " access permission failed");
                                }
                            });
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            SdCardPermission.requestSdcardPermission(context, storagePaths,
                    (context instanceof GalleryActivity) ? (GalleryActivity) context : (GalleryActivity2) context,
                    listener);
        }
    }

    private static void shareAsVideo(final Activity context, final MediaItem current) {
        AudioImageEncoder.with(context)
                .load(current.getFilePath(), current.getJpegSize())
                .rotation(current.getRotation())
                .listen(new AudioImageEncoder.Listener() {
                    @Override
                    public void onPreExecute() {
                        Log.d(TAG, "AudioImageEncoder onPreExecute");
                    }

                    @Override
                    public void onPostExecute(Uri uri) {
                        Log.d(TAG, "AudioImageEncoder onPostExecute uri = " + uri);
                        lockScreen(context);
                        String msgShareTo = context.getResources().getString(R.string.share);
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .setType("video/*")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, msgShareTo));
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.d(TAG, "AudioImageEncoder onError");
                        Toast.makeText(context, R.string.converse_video_failed, Toast.LENGTH_SHORT).show();
                        t.printStackTrace();
                    }

                    @Override
                    public void onCanceled() {
                        Log.d(TAG, "AudioImageEncoder onCanceled");
                    }
                }).start();
    }

    private ArrayList<String> findInvalidatePermissionStorage(ArrayList<Path> data) {
        DataManager manager = null;
        if (mActivity instanceof AbstractGalleryActivity) {
            manager = ((AbstractGalleryActivity) mActivity).getDataManager();
        } else if (mActivity instanceof GalleryActivity2) {
            manager = ((GalleryActivity2) mActivity).getDataManager();
        }
        Utils.checkNotNull(manager);

        ArrayList<String> filePaths = new ArrayList<>();
        String itemFilePath;
        for (Path id : data) {
            MediaItem item = (MediaItem) manager.getMediaObject(id);
            if (item == null) {
                continue;
            }
            if (item instanceof TrashItem) {
                itemFilePath = ((TrashItem) item).localPath;
            } else {
                itemFilePath = item.getFilePath();
            }

            if (!GalleryStorageUtil.isInInternalStorage(itemFilePath)
                    && !SdCardPermission.hasStoragePermission(itemFilePath)) {
                String storageName = SdCardPermission.getStorageName(itemFilePath);
                if (!filePaths.contains(storageName)) {
                    filePaths.add(storageName);
                }
            }
        }
        return filePaths;
    }
}

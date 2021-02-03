package com.android.gallery3d.v2.option;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.data.DeleteManager;
import com.android.gallery3d.v2.data.ImageItem;
import com.android.gallery3d.v2.data.LabelItem;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.trash.data.TrashItem;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.gallery3d.drm.MenuExecutorUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ActionModeHandler implements ActionMode.Callback {
    private static final String TAG = ActionModeHandler.class.getSimpleName();

    private static final int MAX_SELECTED_ITEMS_FOR_SHARE_INTENT = 300;

    public static final int ENTER_SELECTION_MODE = 1;
    public static final int LEAVE_SELECTION_MODE = 2;
    public static final int SELECT_IMAGE_OR_VIDEO_MODE = 3;

    private static final int MSG_PROGRESS_START = 1;
    private static final int MSG_PROGRESS_ING = 2;
    private static final int MSG_PROGRESS_END = 3;

    private final Handler mMainHandler;
    private Future<Void> mTask;
    private ProgressDialog mDialog;

    public interface OnSelectionModeChangeListener {
        void onSelectionModeChanged(int mode);
    }

    public interface OnDetailsClickListener {
        void onDetailsClicked(MediaItem mediaItem);
    }

    public interface OnDrmDetailsClickListener {
        void onDrmDetailsClicked(MediaItem mediaItem);
    }

    public interface OnAddToAlbumClickListener {
        void onAddToAlbumClicked(ArrayList<String> itemList);
    }

    private GalleryActivity2 mActivity;
    private ActionMode mActionMode;
    private Toast mToast;

    private boolean mIsActive;
    private Menu mMenu;

    private OnSelectionModeChangeListener mOnSelectionModeChangeListener;
    private OnDetailsClickListener mOnDetailsClickListener;
    private OnDrmDetailsClickListener mOnDrmDetailsClickListener;
    private OnAddToAlbumClickListener mOnAddToAlbumClickListener;

    private final SelectionManager mSelectionManager;
    private TextView mSelectTitle;

    public ActionModeHandler(Activity activity, OnDetailsClickListener onDetailsClickListener, MediaSet mediaSet) {
        this.mActivity = (GalleryActivity2) activity;
        this.mSelectionManager = new SelectionManager(mediaSet);
        this.mMainHandler = new MainHandler(this);
        this.mOnDetailsClickListener = onDetailsClickListener;
    }

    public void setOnSelectionModeChangeListener(OnSelectionModeChangeListener onSelectionModeChangeListener) {
        mOnSelectionModeChangeListener = onSelectionModeChangeListener;
    }

    public void setOnDrmDetailsClickListener(OnDrmDetailsClickListener onDrmDetailsClickListener) {
        mOnDrmDetailsClickListener = onDrmDetailsClickListener;
    }

    public void setOnAddToAlbumClickListener(OnAddToAlbumClickListener onAddToAlbumClickListener) {
        mOnAddToAlbumClickListener = onAddToAlbumClickListener;
    }

    public void startActionMode() {
        Log.d(TAG, "startActionMode");
        mActionMode = mActivity.startSupportActionMode(this);
        View customView = LayoutInflater.from(mActivity).inflate(R.layout.action_mode_v2, null);
        mSelectTitle = customView.findViewById(R.id.selected_count);
        mActionMode.setCustomView(customView);
    }

    public void finishActionMode() {
        Log.d(TAG, "finishActionMode");
        if (mActionMode != null) {
            mActionMode.finish();
            mMenu.close();
            mActionMode = null;
        }
    }

    public void resume() {
        mSelectionManager.resume();
        if (mDialog != null) {
            mDialog.show();
        }
    }

    public void pause() {
        mSelectionManager.pause();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.hide();
        }
        //如果 action mode 进入后台, 即 activity pause 了, 则取消  action mode
        if (isActive()) {
            finishActionMode();
        }
    }

    public void destroy() {
        stopTask();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        Log.d(TAG, "onCreateActionMode");
        mode.getMenuInflater().inflate(R.menu.menu_options, menu);
        MenuExecutorUtils.getInstance().createDrmMenuItem(menu, mActivity);
        mMenu = menu;
        mIsActive = true;
        if (mOnSelectionModeChangeListener != null) {
            mOnSelectionModeChangeListener.onSelectionModeChanged(ENTER_SELECTION_MODE);
        }
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        Log.d(TAG, "onActionItemClicked");
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onActionItemClicked ignore");
            return true;
        }
        ArrayList<Path> selectList = mSelectionManager.getSelectedItems();
        if (item.getItemId() != R.id.action_delete
                && item.getItemId() != R.id.action_trash_restore
                && item.getItemId() != R.id.action_trash_delete
                && item.getItemId() != R.id.action_move_out_things
                && item.getItemId() != R.id.action_move_out_people) {
            if (item.getItemId() == R.id.action_share) {
                if (selectList.size() <= MAX_SELECTED_ITEMS_FOR_SHARE_INTENT) {
                    finishActionMode();
                }
            } else {
                finishActionMode();
            }
        }
        startAction(item.getItemId(), selectList);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        Log.d(TAG, "onDestroyActionMode");
        mIsActive = false;
        if (mOnSelectionModeChangeListener != null) {
            mOnSelectionModeChangeListener.onSelectionModeChanged(LEAVE_SELECTION_MODE);
        }
    }

    public boolean isActive() {
        return mIsActive;
    }

    public void onImageSelected(ImageItem item) {
        this.mSelectionManager.onImageSelected(item);
        updateTitle(this.mSelectionManager.getSelectedCount());
    }

    public void onLabelSelected(LabelItem item) {
        this.mSelectionManager.onLabelSelected(item);
        updateTitle(this.mSelectionManager.getSelectedCount());
    }

    public void onSelectNon() {
        this.mSelectionManager.onSelectNon();
        updateTitle(this.mSelectionManager.getSelectedCount());
    }

    private void updateTitle(int count) {
        if (mSelectTitle == null) {
            return;
        }
        mSelectTitle.setText(String.format(mActivity.getString(R.string.already_select_count), count));
        mSelectionManager.updateMenu(mMenu);
    }

    private void details(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaItem mediaItem = (MediaItem) manager.getMediaObject(selectList.get(0));
            if (mediaItem != null) {
                if (mOnDetailsClickListener != null) {
                    mOnDetailsClickListener.onDetailsClicked(mediaItem);
                }
            }
        }
    }

    private void showDrmInfo(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaItem mediaItem = (MediaItem) manager.getMediaObject(selectList.get(0));
            if (mediaItem != null) {
                if (mOnDrmDetailsClickListener != null) {
                    mOnDrmDetailsClickListener.onDrmDetailsClicked(mediaItem);
                }
            }
        }
    }

    private void showOnMap(Path path) {
        MediaItem item = (MediaItem) mActivity.getDataManager().getMediaObject(path);
        double latlng[] = new double[2];
        item.getLatLong(latlng);
        if (GalleryUtils.isValidLocation(latlng[0], latlng[1])) {
            GalleryUtils.showOnMap(mActivity, latlng[0], latlng[1]);
        }
    }

    private void edit(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaItem mediaItem = (MediaItem) manager.getMediaObject(selectList.get(0));
            if (mediaItem != null) {
                GalleryUtils.launchEditor(mActivity, mediaItem, -1);
            }
        }
    }

    private void setAs(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaItem mediaItem = (MediaItem) manager.getMediaObject(selectList.get(0));
            if (mediaItem == null) {
                return;
            }
            String mimeType = MenuExecutor.getMimeType(manager.getMediaType(mediaItem.getPath()));
            Uri uri = GalleryUtils.transFileToContentType(manager.getContentUri(mediaItem.getPath()), mActivity);
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("mimeType", intent.getType());
            mActivity.startActivity(Intent.createChooser(
                    intent, mActivity.getString(R.string.set_as)));
        }
    }

    private void crop(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaItem mediaItem = (MediaItem) manager.getMediaObject(selectList.get(0));
            if (mediaItem == null) {
                return;
            }
            String mimeType = MenuExecutor.getMimeType(manager.getMediaType(mediaItem.getPath()));
            Uri uri = GalleryUtils.transFileToContentType(manager.getContentUri(mediaItem.getPath()), mActivity);
            Intent intent = new Intent(CropActivity.CROP_ACTION).setDataAndType(uri, mimeType);
            Bundle bundle = new Bundle();
            bundle.putBoolean("crop_in_gallery", true);
            intent.putExtras(bundle);
            mActivity.startActivity(intent);
        }
    }

    private void print(List<Path> selectList) {
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaObject mediaObject = manager.getMediaObject(selectList.get(0));
            if (mediaObject != null && mediaObject instanceof MediaItem) {
                mActivity.printImage(mediaObject.getContentUri());
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private void share(List<Path> selectList) {
        if (selectList.size() > MAX_SELECTED_ITEMS_FOR_SHARE_INTENT) {
            mToast = ToastUtil.showMessage(mActivity, mToast,
                    mActivity.getString(R.string.can_not_share, MAX_SELECTED_ITEMS_FOR_SHARE_INTENT), Toast.LENGTH_LONG);
            return;
        }
        if (selectList.size() == 1) {
            DataManager manager = mActivity.getDataManager();
            MediaObject mediaObject = manager.getMediaObject(selectList.get(0));
            if (mediaObject != null && mediaObject instanceof MediaItem) {
                MenuExecutor.launchShareIntent(mActivity, (MediaItem) mediaObject);
            }
        } else {
            Intent intent = computeSharingIntent(selectList);
            mActivity.startActivity(Intent.createChooser(intent, mActivity.getString(R.string.share)));
        }
    }

    private void addToAlbum(List<Path> selectList) {
        ArrayList<String> itemList = new ArrayList<>();
        for (Path path : selectList) {
            itemList.add(path.toString());
        }
        if (mOnAddToAlbumClickListener != null) {
            mOnAddToAlbumClickListener.onAddToAlbumClicked(itemList);
        }
    }

    private Intent computeSharingIntent(List<Path> selectList) {
        final ArrayList<Uri> uris = new ArrayList<>();
        DataManager manager = mActivity.getDataManager();
        int type = 0;
        final Intent intent = new Intent();
        for (Path path : selectList) {
            int support = manager.getSupportedOperations(path);
            type |= manager.getMediaType(path);
            if ((support & MediaObject.SUPPORT_SHARE) != 0) {
                uris.add(manager.getContentUri(path));
            }
        }

        final int size = uris.size();
        if (size > 0) {
            final String mimeType = MenuExecutor.getMimeType(type);
            if (size > 1) {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE).setType(mimeType);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            } else {
                intent.setAction(Intent.ACTION_SEND).setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }

    private void startAction(final int itemId, final ArrayList<Path> data) {
        int titleId = -1;
        int msgId = -1;
        String confirmMsg = null;
        switch (itemId) {
            case R.id.action_add_to_album:
                addToAlbum(data);
                return;
            case R.id.action_share:
                share(data);
                return;
            case R.id.action_edit:
                edit(data);
                return;
            case R.id.action_crop:
                crop(data);
                return;
            case R.id.action_setas:
                setAs(data);
                return;
            case R.id.action_details:
                details(data);
                return;
            case R.string.action_drm_info:
                showDrmInfo(data);
                return;
            case R.id.print:
                print(data);
                return;
            case R.id.action_delete:
                titleId = R.string.delete;
                msgId = R.string.wait_for_deleting;
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.delete_selection, mSelectionManager.getSelectedCount());
                break;
            case R.id.action_rotate_ccw:
                titleId = R.string.rotate_left;
                msgId = R.string.wait_for_rotating;
                break;
            case R.id.action_rotate_cw:
                titleId = R.string.rotate_right;
                msgId = R.string.wait_for_rotating;
                break;
            case R.id.action_show_on_map:
                titleId = R.string.show_on_map;
                msgId = R.string.show_on_map;
                break;
            case R.id.action_trash_restore:
                titleId = R.string.trash_restore;
                msgId = R.string.wait_for_restoring;
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.restore_selection, mSelectionManager.getSelectedCount());
                break;
            case R.id.action_trash_delete:
                titleId = R.string.delete;
                msgId = R.string.wait_for_deleting;
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.delete_selection, mSelectionManager.getSelectedCount());
                break;
            case R.id.action_move_out_things:
                titleId = R.string.move_out_of_this_classification;
                msgId = R.string.moving_out;
                confirmMsg = mActivity.getString(R.string.move_out_classification_dialog_title);
                break;
            case R.id.action_move_out_people:
                titleId = R.string.move_out_of_this_people;
                msgId = R.string.moving_out;
                confirmMsg = mActivity.getString(R.string.move_out_people_dialog_title);
                break;
            default:
                return;
        }
        startDialogAction(itemId, data, titleId, msgId, confirmMsg);
    }

    private void stopTask() {
        if (mTask != null) {
            Log.d(TAG, "stopTask");
            mTask.cancel();
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
            }
            mTask = null;
            mDialog = null;
        }
    }

    private void startDialogAction(final int itemId, final ArrayList<Path> data, final int titleId, final int msgId, String confirmMsg) {
        if (confirmMsg != null) {
            if (itemId == R.id.action_delete) {
//                mActivity.getDataManager().buildDeleteList(data);
                mActivity.pauseDiscoverTask();
                if (isIncludeDrmFile(data)) {
                    confirmMsg = mActivity.getString(R.string.delete_drm_file);
                }
                if(isIncludeLargeFile(data)){
                    confirmMsg = mActivity.getString(R.string.delete_large_size_file);
                }
            }
            new AlertDialog.Builder(mActivity)
                    .setMessage(confirmMsg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finishActionMode();
                            executeAction(itemId, data, titleId, msgId);
                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
//                    mActivity.getDataManager().abortBuildDeleteItemTask();
                    mActivity.resumeDiscoverTask();
                }
            })
                    .create()
                    .show();
        } else {
            executeAction(itemId, data, titleId, msgId);
        }
    }

    private void executeAction(final int itemId, final ArrayList<Path> data, final int titleId, final int msgId) {
        ArrayList<String> storageList;
        if ((itemId == R.id.action_delete
                || itemId == R.id.action_trash_restore
                || itemId == R.id.action_rotate_ccw
                || itemId == R.id.action_rotate_cw)
                && (storageList = findInvalidatePermissionStorage(data)).size() > 0) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    stopTask();
                    mDialog = createDialog(mActivity, titleId, msgId, data.size(), true);
                    mDialog.show();
                    Operation operation = new Operation(itemId, data, mProgressListener);
                    mTask = mActivity.getBatchServiceThreadPoolIfAvailable().submit(operation);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(mActivity, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
//                            mActivity.getDataManager().abortBuildDeleteItemTask();
                            mActivity.resumeDiscoverTask();
                        }
                    });
                }
            };
            SdCardPermission.requestSdcardPermission(mActivity, storageList, mActivity, sdCardPermissionListener);
        } else {
            stopTask();
            mDialog = createDialog(mActivity, titleId, msgId, data.size(), true);
            mDialog.show();
            Operation operation = new Operation(itemId, data, mProgressListener);
            mTask = mActivity.getBatchServiceThreadPoolIfAvailable().submit(operation);
        }
    }

    private ProgressListener mProgressListener = new ProgressListener() {
        @Override
        public void onProgressStart() {
            Log.d(TAG, "onProgressStart");
        }

        @Override
        public void onProgressing(int index) {
            Log.d(TAG, "onProgressing index = " + index);
            if (mDialog != null) {
                mDialog.setProgress(index);
            }
        }

        @Override
        public void onProgressEnd() {
            Log.d(TAG, "onProgressEnd");
            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
            }
        }
    };

    private class Operation implements ThreadPool.Job<Void> {
        private final int mItemId;
        private final ArrayList<Path> mData;
        private final ProgressListener mProgressListener;

        public Operation(int itemId, ArrayList<Path> data, ProgressListener progressListener) {
            this.mItemId = itemId;
            this.mData = data;
            this.mProgressListener = progressListener;
        }

        @Override
        public Void run(ThreadPool.JobContext jc) {
            int index = 0;
            try {
                if (mItemId == R.id.action_trash_delete
                        || mItemId == R.id.action_trash_restore) {
                    //最近删除 : 删除或恢复时, 不更新数据
                    TrashManager.getDefault().setBusy(true);
                }
                onProgressStart(this.mProgressListener);
                if (this.mData.size() > 1 && (mItemId == R.id.action_rotate_ccw || mItemId == R.id.action_rotate_cw)) {
                    mActivity.getDataManager().setBatchRotateListener(new DataManager.BatchListener() {
                        @Override
                        public void updateIndex(int index) {
                            onProgressing(mProgressListener, index);
                        }
                    });
                    switch (mItemId) {
                        case R.id.action_rotate_cw:
                            mActivity.getDataManager().batchRotate(mData, 90);
                            break;
                        case R.id.action_rotate_ccw:
                            mActivity.getDataManager().batchRotate(mData, -90);
                            break;
                        default:
                            break;
                    }
                } else if (mItemId == R.id.action_delete) {
                    mActivity.getDataManager().delete(mData);
//                    mActivity.getDataManager().deleteUntilReady();
                } else {
                    for (Path path : this.mData) {
                        if (jc.isCancelled()) {
                            break;
                        }
                        execute(path);
                        onProgressing(this.mProgressListener, ++index);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "failed to execute operation " + mItemId, t);
                if (mItemId == R.id.action_trash_restore) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mToast = ToastUtil.showMessage(mActivity, mToast, R.string.trash_restore_fail, Toast.LENGTH_LONG);
                        }
                    });
                }
            } finally {
                if (mItemId == R.id.action_trash_delete
                        || mItemId == R.id.action_trash_restore) {
                    //最近删除 : 删除或恢复完再刷新数据
                    TrashManager.getDefault().setBusy(false);
                    DeleteManager.getDefault().onContentDirty();
                } else if (mItemId == R.id.action_delete) {
                    mActivity.resumeDiscoverTask();
                }
                onProgressEnd(this.mProgressListener);
            }
            return null;
        }

        private void execute(Path path) {
            long startTime = System.currentTimeMillis();
            switch (mItemId) {
                case R.id.action_show_on_map:
                    showOnMap(path);
                    break;
                case R.id.action_delete:
                    mActivity.getDataManager().delete(path);
                    break;
                case R.id.action_rotate_ccw:
                    mActivity.getDataManager().rotate(path, -90);
                    break;
                case R.id.action_rotate_cw:
                    mActivity.getDataManager().rotate(path, 90);
                    break;
                case R.id.action_trash_restore:
                    mActivity.getDataManager().restore(path);
                    break;
                case R.id.action_trash_delete:
                    mActivity.getDataManager().delete(path);
                    break;
                case R.id.action_move_out_things:
                    mActivity.getDataManager().moveOutThings(path);
                    break;
                case R.id.action_move_out_people:
                    mActivity.getDataManager().moveOutPeople(path);
                    break;
                default:
                    break;
            }
            Log.d(TAG, "It takes " + (System.currentTimeMillis() - startTime) + "ms to execute for " + path);
        }
    }

    private void onProgressStart(ProgressListener l) {
        if (l == null) {
            return;
        }
        mMainHandler.obtainMessage(MSG_PROGRESS_START, l).sendToTarget();
    }

    private void onProgressing(ProgressListener l, int index) {
        if (l == null) {
            return;
        }
        mMainHandler.obtainMessage(MSG_PROGRESS_ING, index, 0, l).sendToTarget();
    }

    private void onProgressEnd(ProgressListener l) {
        if (l == null) {
            return;
        }
        mMainHandler.obtainMessage(MSG_PROGRESS_END, l).sendToTarget();
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PROGRESS_START:
                ((ProgressListener) msg.obj).onProgressStart();
                break;
            case MSG_PROGRESS_ING:
                ((ProgressListener) msg.obj).onProgressing(msg.arg1);
                break;
            case MSG_PROGRESS_END:
                ((ProgressListener) msg.obj).onProgressEnd();
                break;
            default:
                break;
        }
    }

    private interface ProgressListener {
        void onProgressStart();

        void onProgressing(int index);

        void onProgressEnd();
    }

    private class MainHandler extends SynchronizedHandler {
        private final WeakReference<ActionModeHandler> mWeakReference;

        public MainHandler(ActionModeHandler modeHandler) {
            super(null);
            mWeakReference = new WeakReference<>(modeHandler);
        }

        @Override
        public void handleMessage(Message msg) {
            ActionModeHandler modeHandler = mWeakReference.get();
            if (modeHandler != null) {
                modeHandler.handleMessage(msg);
            }
        }
    }

    private static ProgressDialog createDialog(Context context, int titleId, int msgId, int progressMax, boolean isIndeterminate) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setTitle(titleId);
        dialog.setCancelable(false);
        if (isIndeterminate) {
            dialog.setIndeterminate(true);
            String msg = null;
            try {
                if (msgId != -1) {
                    msg = context.getResources().getString(msgId);
                }
            } catch (Resources.NotFoundException e) {
            }
            dialog.setMessage(msg);
        } else {
            dialog.setMax(progressMax);
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        }
        return dialog;
    }

    private ArrayList<String> findInvalidatePermissionStorage(ArrayList<Path> data) {
        ArrayList<String> filePaths = new ArrayList<>();
        String itemFilePath;
        for (Path id : data) {
            MediaItem item = (MediaItem) mActivity.getDataManager().getMediaObject(id);
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

    private boolean isIncludeDrmFile(ArrayList<Path> data) {
        for (Path id : data) {
            MediaItem item = (MediaItem) mActivity.getDataManager().getMediaObject(id);
            if (item == null) {
                continue;
            }
            if (item.mIsDrmFile) {
                return true;
            }
        }
        return false;
    }
    private boolean isIncludeLargeFile(ArrayList<Path> data) {
        for (Path id : data) {
            MediaItem item = (MediaItem) mActivity.getDataManager().getMediaObject(id);
            if (item == null) {
                continue;
            }
            if (item.getSize() >= MediaItem.DELTE_FILE_LARGE_SIZE) {
                return true;
            }
        }
        return false;
    }
}

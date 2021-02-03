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

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import androidx.print.PrintHelper;
import androidx.drawerlayout.widget.DrawerLayout;

import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.app.IStorageUtil.StorageChangedListener;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.PanoramaViewHelper;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;

import java.io.FileNotFoundException;

public class AbstractGalleryActivity extends Activity implements GalleryContext {
    private static final String TAG = "AbstractGalleryActivity";
    private GLRootView mGLRootView;
    private View mCoverView;
    private StateManager mStateManager;
    private GalleryActionBar mActionBar;
    private OrientationManager mOrientationManager;
    private TransitionStore mTransitionStore = new TransitionStore();
    private boolean mDisableToggleStatusBar;
    private PanoramaViewHelper mPanoramaViewHelper;
    // SPRD: Add for bug623901, use HandlerThread to avoid ANR
    private HandlerThread mHandlerThread = new HandlerThread("print");

    private AlertDialog mAlertDialog = null;
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getExternalCacheDir() != null) {
                onStorageReady();
            }
        }
    };
    private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);

    private boolean mIsPaused;
    private boolean mIsGetContent;
    private boolean mIsPick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOrientationManager = new OrientationManager(this);
        toggleStatusBarByOrientation();
        getWindow().setBackgroundDrawable(null);
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        // SPRD: Add for bug623901, use HandlerThread to avoid ANR
        mHandlerThread.start();
        doBindBatchService();

        Intent intent = getIntent();
        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(intent.getAction())) {
            mIsGetContent = true;
        }
        if (Intent.ACTION_PICK.equalsIgnoreCase(intent.getAction())) {
            mIsPick = true;
        }
        GalleryStorageUtil.addStorageChangeListener(mStorageChangedListener);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            android.util.Log.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            //SPRD:Bug631024 Open VdieoPlayer from email and then excute MultiWindow,the tip is wrong.
            GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mGLRootView == null) {
            return;
        }
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Log.d(TAG, "onConfigurationChanged config=" + config);
        // SPRD: Modify 20160216 for bug533949, NPE happen if mStateManager is null
        // so use getStatgeManager() instead to avoid NPE @{
        // mStateManager.onConfigurationChange(config);
        getStateManager().onConfigurationChange(config);
        // @}
        // SPRD: fix bug 396652,the item doesn't been selected when change configuration
        // getGalleryActionBar().onConfigurationChanged();
        invalidateOptionsMenu();
        toggleStatusBarByOrientation();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return getStateManager().createOptionsMenu(menu);
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public DataManager getDataManager() {
        return ((GalleryApp) getApplication()).getDataManager();
    }

    @Override
    public ThreadPool getThreadPool() {
        return ((GalleryApp) getApplication()).getThreadPool();
    }

    public synchronized StateManager getStateManager() {
        if (mStateManager == null) {
            mStateManager = new StateManager(this);
        }
        return mStateManager;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public View getCoverView() {
        return mCoverView;
    }

    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = findViewById(R.id.gl_root_view);
        mCoverView = findViewById(R.id.cover_view);
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        /* SPRD: fix bug526890,remove tip of no external storage @{
        if (getExternalCacheDir() == null) {
            OnCancelListener onCancel = new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_external_storage_title)
                    .setMessage(R.string.no_external_storage)
                    .setNegativeButton(android.R.string.cancel, onClick)
                    .setOnCancelListener(onCancel);
            if (ApiHelper.HAS_SET_ICON_ATTRIBUTE) {
                setAlertDialogIconAttribute(builder);
            } else {
                builder.setIcon(android.R.drawable.ic_dialog_alert);
            }
            mAlertDialog = builder.show();
            registerReceiver(mMountReceiver, mMountFilter);
        }
        @} */
        if (mGLRootView == null) {
            return;
        }
        mPanoramaViewHelper.onStart();

        Log.d(TAG, "onStart");
        mGLRootView.lockRenderThread();
        try {
            getStateManager().start();
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setAlertDialogIconAttribute(
            AlertDialog.Builder builder) {
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mGLRootView == null) {
            return;
        }
        if (mAlertDialog != null) {
            unregisterReceiver(mMountReceiver);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        mPanoramaViewHelper.onStop();

        mGLRootView.lockRenderThread();
        try {
            getStateManager().stop();
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mIsPaused = false;
        if (mGLRootView == null) {
            return;
        }
        // SPRD:bug 627975 for video, toast error
        GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        mGLRootView.lockRenderThread();
        try {
            getStateManager().resume();
            getDataManager().resume();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        mGLRootView.onResume();
        mOrientationManager.resume();
        registerContentObserver();
    }

    // SPRD:bug 627975 for video, toast error
    protected int getToastFlag() {
        return GalleryUtils.DONT_SUPPORT_VIEW_PHOTOS;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mIsPaused = true;
        if (mGLRootView == null) {
            return;
        }
        mOrientationManager.pause();
        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().pause();
            getDataManager().pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
        unregisterContentObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy.");
        GalleryStorageUtil.removeStorageChangeListener(mStorageChangedListener);
        doUnbindBatchService();
        // SPRD: Add for bug623901, use HandlerThread to avoid ANR
        mHandlerThread.quitSafely();
        if (mGLRootView == null) {
            return;
        }
        if (mGLRootView != null) {
            mGLRootView.lockRenderThread();
        }
        try {
            getStateManager().destroy();
        } finally {
            if (mGLRootView != null) {
                mGLRootView.unlockRenderThread();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mGLRootView == null) {
            return;
        }
        mGLRootView.lockRenderThread();
        try {
            getStateManager().notifyActivityResult(
                    requestCode, resultCode, data);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed -->");
        // send the back event to the top sub-state
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            getStateManager().onBackPressed();
        } finally {
            root.unlockRenderThread();
        }
    }

    public GalleryActionBar getGalleryActionBar() {
        if (mActionBar == null) {
            mActionBar = new GalleryActionBar(this);
            /** SPRD:SPRD:473267 M porting add video entrance & related bug-fix @{*/
        } else {
            // SPRD: fix bug 382201,title error
            mActionBar.updateGalleryActionBar(this);
        }
        /**@}*/
        return mActionBar;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            return getStateManager().itemSelected(item);
        } finally {
            root.unlockRenderThread();
        }
    }

    protected void disableToggleStatusBar() {
        mDisableToggleStatusBar = true;
    }

    // Shows status bar in portrait view, hide in landscape view
    private void toggleStatusBarByOrientation() {
        if (mDisableToggleStatusBar) {
            return;
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

    public PanoramaViewHelper getPanoramaViewHelper() {
        return mPanoramaViewHelper;
    }

    public boolean isFullscreen() {
        return (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    private BatchService mBatchService;
    private boolean mBatchServiceIsBound = false;
    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBatchService = ((BatchService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mBatchService = null;
        }
    };

    private void doBindBatchService() {
        bindService(new Intent(this, BatchService.class), mBatchServiceConnection, Context.BIND_AUTO_CREATE);
        mBatchServiceIsBound = true;
    }

    private void doUnbindBatchService() {
        if (mBatchServiceIsBound) {
            // Detach our existing connection.
            unbindService(mBatchServiceConnection);
            mBatchServiceIsBound = false;
        }
    }

    public ThreadPool getBatchServiceThreadPoolIfAvailable() {
        if (mBatchServiceIsBound && mBatchService != null) {
            return mBatchService.getThreadPool();
        } else {
            throw new RuntimeException("Batch service unavailable");
        }
    }

    public void printSelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }

        String path = ImageLoader.getLocalPathFromUri(this, uri);
        if (path != null) {
            Uri localUri = Uri.parse(path);
            path = localUri.getLastPathSegment();
        } else {
            path = uri.getLastPathSegment();
        }
        PrintHelper printer = new PrintHelper(this);
        if (path == null && uri != null) {
            path = "unknownName";
        }
        try {
            // SPRD: Modify 20160218 for bug531005, set scale mode SCALE_MODE_FIT
            // to make sure image is completely printed @{
            printer.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            printer.setOrientation(PrintHelper.ORIENTATION_PORTRAIT);
            // @}
            Log.d(TAG, "<printSelectedImage> printBitmap start");
            /* SPRD: Add for bug623901, use HandlerThread and get looper to be used for print @{ */
            int tid = mHandlerThread.getThreadId();
            Looper looper = mHandlerThread.getLooper();
            Log.d(TAG, "<printSelectedImage> mHanderThread id:" + tid + ", looper:" + looper);
            GalleryUtils.printerBitmap(printer, path, uri,
                    new PrintHelper.OnPrintFinishCallback() {
                        @Override
                        public void onFinish() {
                            Log.d(TAG, "<printSelectedImage> printBitmap finish");
                        }
                    }, looper);

            /* @} */
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Error printing an image", fnfe);
        }
    }


    private final StorageChangedListener mStorageChangedListener = new StorageChangedListener() {

        @Override
        public void onStorageChanged(final String path, final String action) {
            Log.d(TAG, "StorageChanged: path = " + path + " action = " + action);
            new Handler(getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    AbstractGalleryActivity.this.onStorageChanged(path, action);
                }
            });
        }
    };

    public void onStorageChanged(String path, String action) {
        if (action == null || path == null) {
            return;
        }
        if (action.equals(Intent.ACTION_MEDIA_EJECT) && path.equals("/storage/emulated/0")) {
            finish();
        }

    }
    /* usb storage changed */

    public void setRootsDrawerOpen(boolean open) {
    }

    protected DrawerLayout getDrawerLayout() {
        return null;
    }

    protected boolean isSideBarOpened() {
        return false;
    }

    protected String getAlbumSetTitle() {
        return getString(R.string.side_bar_album);
    }

    protected Toolbar getNewToolbar() {
        return null;
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public void setStatusBarColor(int color) {
    }

    public void setStatusBarTranslucent() {
    }

    public boolean isGetContent() {
        return mIsGetContent;
    }

    public boolean isPick() {
        return mIsPick;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyEvent.KEYCODE_MENU == event.getKeyCode() && !getStateManager().dispatchMenuKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (KeyEvent.KEYCODE_VOLUME_UP == keyCode || KeyEvent.KEYCODE_VOLUME_DOWN == keyCode) {
            return mStateManager.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (KeyEvent.KEYCODE_VOLUME_UP == keyCode || KeyEvent.KEYCODE_VOLUME_DOWN == keyCode) {
            return mStateManager.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    public void lockScreen() {
        mStateManager.lockScreen();
    }

    private ContentObserver mRotationLockObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            int orientation = Settings.System.getInt((AbstractGalleryActivity.this).getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0);
            if (orientation == 1) {
                (AbstractGalleryActivity.this).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                (AbstractGalleryActivity.this).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    };

    public void registerContentObserver() {
        this.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                true, mRotationLockObserver);
        if (Settings.System.getInt(this.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 1) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public void unregisterContentObserver() {
        this.getContentResolver().unregisterContentObserver(
                mRotationLockObserver);
    }
}

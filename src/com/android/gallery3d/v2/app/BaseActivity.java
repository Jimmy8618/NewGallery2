package com.android.gallery3d.v2.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.print.PrintHelper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.android.gallery3d.app.BatchService;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.IStorageUtil;
import com.android.gallery3d.app.OrientationManager;
import com.android.gallery3d.app.TransitionStore;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.PanoramaViewHelper;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.discover.DiscoverTask;
import com.android.gallery3d.v2.media.extras.MediaExtrasManager;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.trash.TrashMonitor;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.sprd.frameworks.StandardFrameworks;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.List;

public abstract class BaseActivity extends AppCompatActivity implements IStorageUtil.StorageChangedListener {
    private static final String TAG = BaseActivity.class.getSimpleName();

    public static final String ACTION_REVIEW = "com.android.camera.action.REVIEW";

    private DiscoverTask mDiscoverTask;

    private WeakReference<BasePageFragment> mPageFragmentWeakReference;

    private AlertDialog mPermissionDeniedDialog;

    private OrientationManager mOrientationManager;

    private PanoramaViewHelper mPanoramaViewHelper;

    private TransitionStore mTransitionStore = new TransitionStore();

    private HandlerThread mHandlerThread;

    private BatchService mBatchService;

    private boolean mBatchServiceIsBound = false;

    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (service == null) {
                return;
            }
            mBatchService = ((BatchService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mBatchService = null;
        }
    };

    private ContentObserver mScreenRotationObserver;

    private MediaExtrasManager mMediaExtrasManager;

    private TrashMonitor mTrashMonitor;

    public abstract int getContentViewLayoutId();

    public abstract void initViews(@Nullable Bundle savedInstanceState);

    public abstract void loadData();

    private boolean clearAppTasks() {
        String TAG = this.getClass().getSimpleName();
        if (isTaskRoot()) {
            Log.d(TAG, this + " is the root of task " + getTaskId() + ", don't need to clear task!");
            return false;
        }
        boolean isRemoveTask = false;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> taskList = am.getAppTasks();

        Intent currentIntent = getIntent();
        Log.d(TAG, "clearAppTasks: currentIntent=" + currentIntent);
        for (ActivityManager.AppTask task : taskList) {
            try {
                ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
                Intent rootIntent = taskInfo.baseIntent;
                Log.d(TAG, "task id: " + taskInfo.id + ", rootIntent=" + rootIntent);
                if (currentIntent.getComponent().equals(rootIntent.getComponent())
                        && !currentIntent.filterEquals(rootIntent)) {
                    Log.d(TAG, "clearAppTasks task id: " + taskInfo.id);
                    task.finishAndRemoveTask();
                    isRemoveTask = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return isRemoveTask;
    }

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(this.getClass().getSimpleName(), "onCreate");
        if (GalleryUtils.isMonkey()) {
            if (clearAppTasks()) {
                finish();
                return;
            }
        }
        //适配刘海屏/*Bug 1127958*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        //
        setStatusBarVisibleLight();
        mHandlerThread = new HandlerThread("Print");
        mHandlerThread.start();
        mOrientationManager = new OrientationManager(this);
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        mScreenRotationObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                onScreenRotationChanged();
            }
        };
        doBindBatchService();
        //Step1 set content view
        setContentView(getContentViewLayoutId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (this.isInMultiWindowMode()) {
                Log.d(TAG, " onCreate , MultiWindowMode .");
                GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
                return;
            }
        }
        //Step2 init views
        initViews(savedInstanceState);
        //Step3 check permission
        if (PermissionUtil.hasPermissions(this)) {
            //load data if has permission
            loadData();
        } else {
            PermissionUtil.requestPermissions(this);
        }

        mMediaExtrasManager = new MediaExtrasManager();
        GalleryStorageUtil.addStorageChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mPanoramaViewHelper.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        mOrientationManager.resume();
        registerScreenRotationObserver();
        if (GalleryUtils.isSupportRecentlyDelete()) {
            TrashManager.getDefault().onResume();
            if (mTrashMonitor == null) {
                mTrashMonitor = new TrashMonitor();
                mTrashMonitor.start();
            }
        }

        resumeDiscoverTask();

        getDataManager().resume();

        if (PermissionUtil.hasPermissions(this)) {
            mMediaExtrasManager.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPanoramaViewHelper.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mOrientationManager.pause();
        unregisterScreenRotationObserver();
        if (GalleryUtils.isSupportRecentlyDelete()) {
            TrashManager.getDefault().onPause();
            if (mTrashMonitor != null) {
                mTrashMonitor.terminate();
                mTrashMonitor = null;
            }
        }

        pauseDiscoverTask();

        getDataManager().pause();

        mMediaExtrasManager.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        if (mPermissionDeniedDialog != null && mPermissionDeniedDialog.isShowing()) {
            mPermissionDeniedDialog.dismiss();
            mPermissionDeniedDialog = null;
        }
        doUnbindBatchService();
        GalleryStorageUtil.removeStorageChangeListener(this);
    }

    @Override
    public void onStorageChanged(String path, String status) {
        Log.d(TAG, "onStorageChanged: " + path + ", " + status);
        if (mTrashMonitor != null) {
            mTrashMonitor.notifyDirty();
        }
    }

    public void setPageFragment(BasePageFragment page) {
        mPageFragmentWeakReference = new WeakReference<>(page);
    }

    public boolean isBackConsumed() {
        if (mPageFragmentWeakReference != null && mPageFragmentWeakReference.get() != null) {
            return mPageFragmentWeakReference.get().isBackConsumed();
        }
        return false;
    }

    public void setStatusBarColor(int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(getColor(resId));
        }
    }

    public void setStatusBarVisibleLight() {//设置状态栏字体为黑色, 设置状态栏可见
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    public void setStatusBarVisibleWhite() {//设置状态栏字体为白色, 设置状态栏可见
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public void setStatusBarHideWhite() {//设置状态栏字体为白色, 设置状态栏不可见
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(this.getClass().getSimpleName(), "onRequestPermissionsResult");
        boolean hasPermission = true;
        if (grantResults.length == 0) {
            hasPermission = false;
        } else {
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    hasPermission = false;
                    break;
                }
            }
        }
        if (mPermissionDeniedDialog != null && mPermissionDeniedDialog.isShowing()) {
            mPermissionDeniedDialog.dismiss();
            mPermissionDeniedDialog = null;
        }
        if (hasPermission) {
            loadData();
        } else {
            mPermissionDeniedDialog = PermissionUtil.showPermissionErrorDialog(this);
        }
    }

    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    public boolean isFullscreen() {
        return (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

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

    public ThreadPool getThreadPool() {
        return GalleryAppImpl.getApplication().getThreadPool();
    }

    public PanoramaViewHelper getPanoramaViewHelper() {
        return mPanoramaViewHelper;
    }

    public void printImage(Uri uri) {
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
        if (path == null) {
            path = "unKnownName";
        }
        try {
            printer.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            printer.setOrientation(PrintHelper.ORIENTATION_PORTRAIT);
            int tid = mHandlerThread.getThreadId();
            Looper looper = mHandlerThread.getLooper();
            GalleryUtils.printerBitmap(printer, path, uri,
                    new PrintHelper.OnPrintFinishCallback() {
                        @Override
                        public void onFinish() {
                        }
                    }, looper);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error printing an image", e);
        }
    }

    public DataManager getDataManager() {
        return GalleryAppImpl.getApplication().getDataManager();
    }

    public boolean isMainIntent() {
        return !Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                && !Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction())
                && !Intent.ACTION_VIEW.equalsIgnoreCase(getIntent().getAction())
                && !ACTION_REVIEW.equalsIgnoreCase(getIntent().getAction())
                && !isWidgetGetAlbumIntent();
    }

    public boolean isGetContentIntent() {
        return Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction());
    }

    public boolean isViewActionIntent() {
        return (Intent.ACTION_VIEW.equalsIgnoreCase(getIntent().getAction())
                || ACTION_REVIEW.equalsIgnoreCase(getIntent().getAction()));
    }

    public boolean isWidgetGetAlbumIntent() {
        return getIntent().getBooleanExtra(Constants.KEY_BUNDLE_WIDGET_GET_ALBUM, false);
    }

    private void onScreenRotationChanged() {
        int rotation = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        Log.d(TAG, "onScreenRotationChanged rotation = " + rotation);
        if (1 == rotation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public void lockScreen() {
        Log.d(TAG, "lockScreen");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    public void unlockScreen() {
        Log.d(TAG, "unlockScreen");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void registerScreenRotationObserver() {
        if (1 == Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true, mScreenRotationObserver);
    }

    private void unregisterScreenRotationObserver() {
        getContentResolver().unregisterContentObserver(mScreenRotationObserver);
    }

    public void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        if (menu == null) {
            return;
        }
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    public int getTabPosition() {
        return 0;
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        if (isInMultiWindowMode) {
            GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
        }
    }

    protected int getToastFlag() {
        return GalleryUtils.DONT_SUPPORT_VIEW_PHOTOS;
    }

    public void resumeDiscoverTask() {
        if (GalleryUtils.isSupportV2UI()
                && StandardFrameworks.getInstances().isSupportAIEngine()
                && PermissionUtil.hasPermissions(this)) {
            if (mDiscoverTask == null) {
                mDiscoverTask = new DiscoverTask(new Handler());
            }
            mDiscoverTask.onResume();
        }
    }

    public void pauseDiscoverTask() {
        if (mDiscoverTask != null) {
            mDiscoverTask.onPause();
        }
    }
}

/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;

import androidx.legacy.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener;

import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.fw.StorageEventListener;
import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.gadget.PhotoAppWidgetProvider;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.sidebar.SideBarAdapter;
import com.android.gallery3d.sidebar.SideBarItem;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ImageCache;
import com.android.gallery3d.v2.data.CameraMergeAlbum;
import com.android.gallery3d.v2.data.SecureCameraAlbum;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.AlbumSetLoadingListener;
import com.sprd.gallery3d.app.GalleryPermissionsActivity;
import com.sprd.gallery3d.app.PermissionsActivity;
import com.sprd.gallery3d.app.PickPhotosPermissionsActivity;
import com.sprd.gallery3d.drm.GalleryActivityUtils;
import com.sprd.gallery3d.tools.LargeImageProcessingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public final class GalleryActivity extends AbstractGalleryActivity implements OnCancelListener, SideBarAdapter.OnSideBarItemClickListener,
        SideBarAdapter.OnSideBarItemChangeListener, SdCardPermissionAccessor {
    public static final String EXTRA_SLIDESHOW = "slideshow";
    public static final String EXTRA_DREAM = "dream";
    public static final String EXTRA_CROP = "crop";

    public static final String ACTION_REVIEW = "com.android.camera.action.REVIEW";
    public static final String KEY_GET_CONTENT = "get-content";
    public static final String KEY_GET_ALBUM = "get-album";
    public static final String KEY_TYPE_BITS = "type-bits";
    public static final String KEY_MEDIA_TYPES = "mediaTypes";
    public static final String KEY_DISMISS_KEYGUARD = "dismiss-keyguard";
    public static final String MIME_TYPE = "mime_type";
    public static final String DATA = "_data";

    private static final String TAG = "GalleryActivity";
    private Dialog mVersionCheckDialog;
    private static GalleryActivity sLastActivity;
    private boolean mHasCriticalPermissions;

    private Toolbar mToolbar;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Toolbar mRootsToolbar;
    private View mRootsDrawer;
    private ListView mSideBar;
    private SideBarAdapter mSideBarAdapter;
    private View mSplashView;

    /* monitor storage change */
    private StorageManager mStorageManager;
    private Object mStorageListener;
    private HashMap<String, String> mOTGdevicesInfos = new HashMap<String, String>();


    private String mAlbumSetTitle;
    private boolean mIsSideBarOpened = false;

    private SprdAlbumSetDataLoader mAllLoader;
    private SprdAlbumSetDataLoader mPhotoLoader;
    private SprdAlbumSetDataLoader mVideoLoader;

    private static final String KEY_SIDE_BAR_ITEM = "key-side-bar-item";
    private static final String KEY_REQUEST_PERMISSION = "request-permission";
    private Bundle mSavedInstanceState;
    private boolean mIsRequestPermission;

    private SdCardPermissionListener mSdCardPermissionListener;

    private class MyStorageInfoListener implements StorageEventListener {

        @Override
        public void onVolumeStateChanged(String volumeName, String volumePath,
                                         boolean is_sd, int state) {
            if (mStorageListener == null) {
                return;
            }
            if (!is_sd) {
                if (state == StandardFrameworks.STATE_MOUNTED) {
                    if (mOTGdevicesInfos.containsKey(volumePath)) {
                        return;
                    }
                    Log.d(TAG, "addOtgDevice volumeName= " + volumeName + " volumePath = " + volumePath);
                    if (volumePath != null) {
                        mOTGdevicesInfos.put(volumePath, volumeName);
                    }
                    onSideBarDataSetChanged();
                    getDataManager().setOtgDeviceInfos(mOTGdevicesInfos);
                } else if (state == StandardFrameworks.STATE_UNMOUNTED) {
                    Log.d(TAG, "removeOtgDevice volumePath = " + volumePath + "  volumeName = " + volumeName);
                    int oldSzie = mOTGdevicesInfos.size();
                    if (mOTGdevicesInfos.containsKey(volumePath)) {
                        mOTGdevicesInfos.remove(volumePath);
                    }
                    int newSize = mOTGdevicesInfos.size();
                    if (oldSzie > newSize && !GalleryActivity.this.isPaused()) {
                        Toast.makeText(GalleryActivity.this,
                                getResources().getString(R.string.remove_usbdisk), Toast.LENGTH_LONG)
                                .show();
                    }
                    onSideBarDataSetChanged();
                    getDataManager().setOtgDeviceInfos(mOTGdevicesInfos);
                }

            }
        }
    }

    private boolean clearAppTasks() {
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
    protected void onCreate(Bundle savedInstanceState) {
        GalleryUtils.start(this.getClass(), "onCreate");
        super.onCreate(savedInstanceState);
        initAlbumSet();
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        /* SPRD: Modify 20150609 Spreadst of bug444059, avoid ANR in monkey test cause by too many threads run @{ */
        /*if (GalleryUtils.isMonkey()) {
            Log.d(TAG, "onCreate sLastActivity=" + sLastActivity + ", current activity:" + this);
            if (sLastActivity != null) {
                Log.e(TAG, "GalleryActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
                if (sLastActivity.getGLRoot() != null && sLastActivity.getGLRoot().isFreezed()) {
                    Log.d(TAG, "GLRootView for last activity is unfreezed");
                    sLastActivity.getGLRoot().unfreeze();
                }
                sLastActivity = null;
            }
            sLastActivity = this;
        }*/
        if (GalleryUtils.isMonkey()) {
            if (clearAppTasks()) {
                finish();
                return;
            }
        } else {
            ImageCache.getDefault(this).restore();
        }
        /* @} */
/*        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);*/
        if (getIntent().getBooleanExtra(KEY_DISMISS_KEYGUARD, false)) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        // Translucent status bar
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        setContentView(R.layout.main);
        /* SPRD: bug 631765,in MultiWindowMode,not to check permissions @} */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (this.isInMultiWindowMode()) {
                Log.d(TAG, " onCreate , MultiWindowMode .");
                GalleryUtils.killActivityInMultiWindow(this, getToastFlag());
                return;
            }
        }
        mToolbar = findViewById(R.id.toolbar);
        mSplashView = findViewById(R.id.splash_cover);
        /* @} */
        mIsRequestPermission = savedInstanceState != null && savedInstanceState.getBoolean(KEY_REQUEST_PERMISSION);
        /* SPRD: add check gallery permissions @{*/
        checkPermissions();

        Log.i(TAG, "onCreate: " + this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mStorageManager = getSystemService(StorageManager.class);
            mStorageListener = StandardFrameworks.getInstances().registerStorageManagerListener(
                    mStorageManager, new MyStorageInfoListener());
        }
        checkOTGdevices();

        if (mHasCriticalPermissions) {
            mToolbar.setVisibility(View.VISIBLE);
            mSplashView.setVisibility(View.GONE);
        } else {
            mToolbar.setVisibility(View.GONE);
            mSplashView.setVisibility(View.VISIBLE);
        }

        // getDataManager().setActivity(GalleryActivity.this);
        /* @} */
        Log.i(TAG, " oncreate  =  " + savedInstanceState + "   " + mHasCriticalPermissions);
        if (mHasCriticalPermissions) {
            if (savedInstanceState != null) {
                getStateManager().restoreFromState(savedInstanceState);
            } else {
                initializeByIntent();
            }
        } else {
            mSavedInstanceState = savedInstanceState;
        }

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mRootsDrawer = findViewById(R.id.drawer_roots);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.drawable.ic_hamburger_alpha, R.string.ok, R.string.cancel);
        mDrawerLayout.setDrawerListener(mDrawerListener);

        mToolbar.setTitleTextAppearance(this,
                //android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title);
                R.style.Toolbar_Title);
//        setActionBar(mToolbar);

        mRootsToolbar = findViewById(R.id.roots_toolbar);
        mRootsToolbar.setTitleTextAppearance(this,
                //android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title);
                R.style.Toolbar_Title);
        mRootsToolbar.setNavigationIcon(R.drawable.ic_back_gray);
        mRootsToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRootsDrawerOpen(false);
            }
        });
        mRootsToolbar.setTitle(R.string.tab_photos);

        setToolbarMargins(0, GalleryUtils.getStatusBarHeight(this), 0, 0);

        mSideBar = findViewById(R.id.id_lv);
        String sidebarItemKey = null;
        if (savedInstanceState != null) {
            sidebarItemKey = savedInstanceState.getString(KEY_SIDE_BAR_ITEM);
            Log.d(TAG, "onCreate saved sidebarItemKey=" + sidebarItemKey);
        }
        loadSideBar(sidebarItemKey == null ? SideBarItem.ALBUM : sidebarItemKey);
        mIsSideBarOpened = false;

        // SPRD:bug575864,open Gallery check widget,need update or not
        checkUpdateWidget();
        GalleryUtils.end(this.getClass(), "onCreate");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy " + this);
        if (mAllLoader != null) {
            mAllLoader.pause();
        }
        if (mPhotoLoader != null) {
            mPhotoLoader.pause();
        }
        if (mVideoLoader != null) {
            mVideoLoader.pause();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StandardFrameworks.getInstances().unregisterStorageManagerListener(
                    mStorageManager, mStorageListener);
            mStorageListener = null;
        }
        super.onDestroy();
    }

    public void initAllKindData() {
        // mAllLoader = initData(FilterUtils.CLUSTER_BY_ALL);
        // mPhotoLoader = initData(FilterUtils.CLUSTER_BY_PHOTO);
        // mVideoLoader = initData(FilterUtils.CLUSTER_BY_VIDEO);
    }

    private SprdAlbumSetDataLoader initData(int clusterType) {
        final String mediaPath = FilterUtils.switchClusterPath(DataManager.TOP_SET_PATH, clusterType);
        MediaSet mediaSet = getDataManager().getMediaSet(mediaPath);
        SprdAlbumSetDataLoader albumSetDataLoader = new SprdAlbumSetDataLoader(
                this, mediaSet, 256);
        albumSetDataLoader.setLoadingListener(new AlbumSetLoadingListener() {
            @Override
            public void onLoadingWill() {

            }

            @Override
            public void onLoadingStarted() {
                Log.d(TAG, "initData mediaPath = " + mediaPath + " onLoadingStarted.");
            }

            @Override
            public void onLoadingFinished(boolean loadingFailed) {
                Log.d(TAG, "initData mediaPath = " + mediaPath + " onLoadingFinished, loadingFailed = " + loadingFailed);
            }
        });
        albumSetDataLoader.resume();
        return albumSetDataLoader;
    }

    private void initAlbumSet() {
        if (!PermissionUtil.hasPermissions(this)) {
            return;
        }
        String action = getIntent().getAction();
        if (Intent.ACTION_MAIN.equalsIgnoreCase(action)) {
            String mediaSetPath = getDataManager().getTopSetPath(DataManager.INCLUDE_ALL);
            if (mediaSetPath == null) {
                return;
            }
            MediaSet mediaSet = getDataManager().getMediaSet(mediaSetPath);
            if (mediaSet == null) {
                return;
            }
            Log.d(TAG, "initAlbumSet mediaSet = " + mediaSet);
            mediaSet.reload();
        }
    }

    private void initializeByIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action)) {
            startGetContent(intent);
        } else if (Intent.ACTION_PICK.equalsIgnoreCase(action)) {
            // We do NOT really support the PICK intent. Handle it as
            // the GET_CONTENT. However, we need to translate the type
            // in the intent here.
            Log.w(TAG, "action PICK is not supported");
            String type = Utils.ensureNotNull(intent.getType());
            if (type.startsWith("vnd.android.cursor.dir/")) {
                if (type.endsWith("/image")) {
                    intent.setType("image/*");
                }
                if (type.endsWith("/video")) {
                    intent.setType("video/*");
                }
            }
            startGetContent(intent);
        } else if (Intent.ACTION_VIEW.equalsIgnoreCase(action)
                || ACTION_REVIEW.equalsIgnoreCase(action)) {
            startViewAction(intent);
            getStateManager().setQuitStatus(false);
        } else {
            if (GalleryUtils.isSprdPhotoEdit()) {
                if (intent.getData() != null) {
                    intent.setAction(Intent.ACTION_VIEW);
                    startViewAction(intent);
                    getStateManager().setQuitStatus(false);
                }
            } else {
                startDefaultPage();
            }
        }
    }

    public void startDefaultPage() {
        getStateManager().setQuitStatus(true);
        PicasaSource.showSignInReminder(this);
        Bundle data = new Bundle();
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
        getStateManager().startState(SprdAlbumSetPage.class, data);
        mVersionCheckDialog = PicasaSource.getVersionCheckDialog(this);
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.setOnCancelListener(this);
        }
    }

    private void startGetContent(Intent intent) {
        Bundle data = intent.getExtras() != null
                ? new Bundle(intent.getExtras())
                : new Bundle();
        /* SPRD: Drm feature start @{ */
        GalleryActivityUtils.getInstance().startGetContentSetAs(intent, data);
        /* SPRD: Drm feature end @} */
        data.putBoolean(KEY_GET_CONTENT, true);
        int typeBits = GalleryUtils.determineTypeBits(this, intent);
        data.putInt(KEY_TYPE_BITS, typeBits);
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(typeBits));
        getStateManager().startState(SprdAlbumSetPage.class, data);
    }

    private String getContentType(Intent intent) {
        String type = intent.getType();
        if (type != null) {
            return GalleryUtils.MIME_TYPE_PANORAMA360.equals(type)
                    ? MediaItem.MIME_TYPE_JPEG : type;
        }

        Uri uri = intent.getData();
        try {
            return LargeImageProcessingUtils.getType(uri, this);
            //SPRD: remove for bug 500764
//            return getContentResolver().getType(uri);
        } catch (Throwable t) {
            Log.w(TAG, "get type fail", t);
            return null;
        }
    }

    private void startViewAction(Intent intent) {
        Boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);
        Log.d(TAG, "startViewAction slideshow=" + slideshow);
        if (slideshow) {
//            getActionBar().hide();
            DataManager manager = getDataManager();
            Path path = manager.findPathByUri(intent.getData(), intent.getType());
            if (path == null || manager.getMediaObject(path)
                    instanceof MediaItem) {
                path = Path.fromString(
                        manager.getTopSetPath(DataManager.INCLUDE_IMAGE));
            }
            Bundle data = new Bundle();
            Log.d(TAG, "startViewAction path=" + path);
            data.putString(SlideshowPage.KEY_SET_PATH, path.toString());
            data.putBoolean(SlideshowPage.KEY_RANDOM_ORDER, true);
            data.putBoolean(SlideshowPage.KEY_REPEAT, true);
            if (intent.getBooleanExtra(EXTRA_DREAM, false)) {
                data.putBoolean(SlideshowPage.KEY_DREAM, true);
            }
            getStateManager().startState(SlideshowPage.class, data);
        } else {
            Bundle data = new Bundle();
            DataManager dm = getDataManager();
            Uri uri = intent.getData();
            String contentType = getContentType(intent);
            if (contentType == null) {
                Toast.makeText(this,
                        R.string.no_such_item, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Log.d(TAG, "startViewAction uri=" + uri + ", contentType=" + contentType);
            if (uri == null) {
                int typeBits = GalleryUtils.determineTypeBits(this, intent);
                data.putInt(KEY_TYPE_BITS, typeBits);
                data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH,
                        getDataManager().getTopSetPath(typeBits));
                getStateManager().startState(SprdAlbumSetPage.class, data);
            } else if (contentType.startsWith(
                    ContentResolver.CURSOR_DIR_BASE_TYPE)) {
                int mediaType = intent.getIntExtra(KEY_MEDIA_TYPES, 0);
                if (mediaType != 0) {
                    uri = uri.buildUpon().appendQueryParameter(
                            KEY_MEDIA_TYPES, String.valueOf(mediaType))
                            .build();
                }
                Path setPath = dm.findPathByUri(uri, null);
                MediaSet mediaSet = null;
                if (setPath != null) {
                    mediaSet = (MediaSet) dm.getMediaObject(setPath);
                }
                if (mediaSet != null) {
                    if (mediaSet.isLeafAlbum()) {
                        data.putString(SprdAlbumPage.KEY_MEDIA_PATH, setPath.toString());
                        data.putString(SprdAlbumPage.KEY_PARENT_MEDIA_PATH,
                                dm.getTopSetPath(DataManager.INCLUDE_ALL));
                        getStateManager().startState(SprdAlbumPage.class, data);
                    } else {
                        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, setPath.toString());
                        getStateManager().startState(SprdAlbumSetPage.class, data);
                    }
                } else {
                    startDefaultPage();
                }
            } else {
                /* SPRD: Drm feature start @{ */
                if (neededChangetoContent(uri)) {
                    uri = changeToContent(uri);
                }
                Path itemPath = dm.findPathByUri(uri, contentType);
                Log.d(TAG, "startViewAction itemPath=" + itemPath);
                /**SPRD:473267 M porting add video entrance & related bug-fix
                 Modify 20150106 of bug 390428,video miss after crop @{ */
                Path albumPath = dm.getDefaultSetOf(false, itemPath, intent.getAction());
                Log.d(TAG, "startViewAction albumPath=" + albumPath + ", camera_album = " + intent.getBooleanExtra(PhotoPage.KEY_CAMERA_ALBUM, false));
                /**@}*/

                if (intent.getBooleanExtra(PhotoPage.KEY_CAMERA_ALBUM, false) && albumPath != null) {
                    String ap = albumPath.toString();
                    if (ap != null) {
                        albumPath = CameraMergeAlbum.PATH;
                        data.putBoolean(PhotoPage.KEY_CAMERA_ALBUM, true);
                        boolean isSecureCamera = intent.getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false);
                        long[] photoIds = intent.getLongArrayExtra(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS);
                        if (isSecureCamera && photoIds != null) {
                            albumPath = SecureCameraAlbum.PATH;
                            Log.d(TAG, "onViewAction use SecureCameraAlbum");
                        }
                        data.putBoolean(PhotoPage.KEY_SECURE_CAMERA, isSecureCamera);
                        data.putLongArray(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS, photoIds);
                        data.putLong(PhotoPage.KEY_SECURE_CAMERA_ENTER_TIME, photoIds != null ? -1L : intent.getLongExtra(PhotoPage.KEY_SECURE_CAMERA_ENTER_TIME, -1L));
                        Log.d(TAG, "startViewAction redirect albumPath=" + albumPath);
                    }
                }

                if (!GalleryUtils.isValidMtpUri(this, uri)) {
                    Toast.makeText(this, R.string.fail_to_load, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                /* SPRD: Drm feature end @} */
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());
                /* SPRD: Modify 562234 for support edit image when open from other applications @{
                 * data.putBoolean(PhotoPage.KEY_READONLY, true);
                 */
                data.putBoolean(PhotoPage.KEY_READONLY,
                        intent.getBooleanExtra(PhotoPage.KEY_READONLY, !intent.getBooleanExtra(PhotoPage.KEY_CAMERA_ALBUM, false)));
                /* @} */
                // TODO: Make the parameter "SingleItemOnly" public so other
                //       activities can reference it.
                boolean singleItemOnly = (albumPath == null)
                        || intent.getBooleanExtra("SingleItemOnly", false);
                if (!singleItemOnly) {
                    data.putString(PhotoPage.KEY_MEDIA_SET_PATH, albumPath.toString());
                    // when FLAG_ACTIVITY_NEW_TASK is set, (e.g. when intent is fired
                    // from notification), back button should behave the same as up button
                    // rather than taking users back to the home screen
                    if (intent.getBooleanExtra(PhotoPage.KEY_TREAT_BACK_AS_UP, false)
                        /*|| ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0)*/) {
                        data.putBoolean(PhotoPage.KEY_TREAT_BACK_AS_UP, true);
                    }
                } else {
                    data.putBoolean(PhotoPage.SINGLE_ITEM_ONLY, true);
                }
                data.putBoolean(PhotoPage.KEY_START_FROM_WIDGET, intent.getBooleanExtra(PhotoPage.KEY_START_FROM_WIDGET, false));
                /* SPRD: fix bug 488355,crashed when we saw some specific picture's info rmation @{ */
                try {
                    getStateManager().startState(SinglePhotoPage.class, data);
                } catch (Exception e) {
                    Toast.makeText(this,
                            R.string.fail_to_load, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                /* @} */
            }
        }
    }

    @Override
    protected void onResume() {
        //Utils.assertTrue(getStateManager().getStateCount() > 0);
        GalleryUtils.start(this.getClass(), "onResume");
        super.onResume();
        /* SPRD:bug 543815 if no critical permissions,ActivityState count is 0,when fixed screen @{ */
        Log.i(TAG, " onResume  =  " + mHasCriticalPermissions);
        if (mHasCriticalPermissions) {
            if (getStateManager().getStateCount() <= 0) {
                finish();
                return;
            }
            if (mVersionCheckDialog != null) {
                mVersionCheckDialog.show();
            }
        }

        if (!getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false)) {
            setShowWhenLocked(false);
        }
        GalleryUtils.end(this.getClass(), "onResume");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, " GalleryActivity onStop ");
        if (!GalleryUtils.isMonkey()) {
            ImageCache.getDefault(this).commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.dismiss();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mVersionCheckDialog) {
            mVersionCheckDialog = null;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        final boolean isTouchPad = (event.getSource()
                & InputDevice.SOURCE_CLASS_POSITION) != 0;
        if (isTouchPad) {
            float maxX = event.getDevice().getMotionRange(MotionEvent.AXIS_X).getMax();
            float maxY = event.getDevice().getMotionRange(MotionEvent.AXIS_Y).getMax();
            View decor = getWindow().getDecorView();
            float scaleX = decor.getWidth() / maxX;
            float scaleY = decor.getHeight() / maxY;
            float x = event.getX() * scaleX;
            //x = decor.getWidth() - x; // invert x
            float y = event.getY() * scaleY;
            //y = decor.getHeight() - y; // invert y
            MotionEvent touchEvent = MotionEvent.obtain(event.getDownTime(),
                    event.getEventTime(), event.getAction(), x, y, event.getMetaState());
            return dispatchTouchEvent(touchEvent);
        }
        return super.onGenericMotionEvent(event);
    }

    /**
     * SPRD: check intent URI scheme is content or not. @{
     *
     * @param uri URI content or file
     * @return true if URI scheme is content, else false
     */
    private boolean neededChangetoContent(Uri uri) {
        return uri.getScheme().compareTo("content") != 0;
    }

    /**
     * SPRD: get content URI by file path @{
     *
     * @param uri
     * @return URI of content
     */
    private Uri changeToContent(Uri uri) {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[]{
                ImageColumns._ID
        };
        String selection = ImageColumns.DATA + '=' + "\"" + uri.getPath() + "\"";

        Cursor cursor = null;
        long id = -1;
        try {
            cursor = getContentResolver().query(query, projection, selection, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "ChangetoContent error:" + e.toString());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (id != -1) {
            uri = Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(String.valueOf(id))
                    .build();
        }
        return uri;
    }

    /**
     * @}
     */

    /* SPRD: add check gallery permissions @{*/
    private void checkPermissions() {
        boolean isPickIntent = Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction());

        if (isPickIntent && GalleryUtils.checkStoragePermissions(this)) {
            mHasCriticalPermissions = true;
        } else // SPRD: Modify for bug576760, also check access location permission
        {
            mHasCriticalPermissions = GalleryUtils.checkStoragePermissions(this) && GalleryUtils.checkLocationPermissions(this);
        }
        if (!mHasCriticalPermissions) {
            Class<?> target;
            if (isPickIntent) {
                target = PickPhotosPermissionsActivity.class;
            } else {
                target = GalleryPermissionsActivity.class;
            }
            if (!isFinishing() && !mIsRequestPermission) {
                GalleryUtils.requestPermission(this, target, PermissionsActivity.START_FROM_GALLERY);
                mIsRequestPermission = true;
            }

        }

    }
    /* @} */

    /* SPRD:bug575864,open Gallery check widget,need update or not @{ */
    private void checkUpdateWidget() {
        final AppWidgetManager appWidgetManager = AppWidgetManager
                .getInstance(getApplicationContext());
        int[] conversationWidgetIds = null;
        if (appWidgetManager != null) {
            conversationWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(
                    getApplicationContext(), PhotoAppWidgetProvider.class));
        }
        if (conversationWidgetIds != null && conversationWidgetIds.length > 0) {
            Log.d(TAG, "has " + conversationWidgetIds.length + " gallery widget,update widget ");
            Intent updateWidget = new Intent();
            updateWidget.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateWidget.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, conversationWidgetIds);
            sendBroadcast(updateWidget);
        } else {
            Log.d(TAG, " no gallery widget in Launcher");
        }
    }
    /* @} */

    private DrawerListener mDrawerListener = new DrawerListener() {
        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
            //Log.d(TAG, "zeiyou onDrawerSlide slideOffset = " + slideOffset);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            mDrawerToggle.onDrawerOpened(drawerView);
            mIsSideBarOpened = true;
            //Log.d(TAG, "zeiyou onDrawerOpened !!!!");
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            mDrawerToggle.onDrawerClosed(drawerView);
            mIsSideBarOpened = false;
            //Log.d(TAG, "zeiyou onDrawerClosed !!!!");
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            mDrawerToggle.onDrawerStateChanged(newState);
            //Log.d(TAG, "zeiyou onDrawerSlide newState = " + newState);
        }
    };

    @Override
    public void setRootsDrawerOpen(boolean open) {
        if (open) {
            mDrawerLayout.openDrawer(mRootsDrawer);
        } else {
            mDrawerLayout.closeDrawer(mRootsDrawer);
        }
    }

    private void loadSideBar(String itemKey) {
        List<SideBarItem> items = new ArrayList<SideBarItem>();

        // all
        items.add(new SideBarItem(SideBarItem.ALL, getResources().getString(R.string.side_bar_all_photos), R.drawable.all_photos_icon, R.drawable.all_photos_selected_icon));
        // photo
        items.add(new SideBarItem(SideBarItem.PHOTO, getResources().getString(R.string.side_bar_photo), R.drawable.photo_icon, R.drawable.photo_selected_icon));
        // video
        items.add(new SideBarItem(SideBarItem.VIDEO, getResources().getString(R.string.side_bar_video), R.drawable.video_icon, R.drawable.video_selected_icon));
        // album
        items.add(new SideBarItem(SideBarItem.ALBUM, getResources().getString(R.string.side_bar_album), R.drawable.album_icon, R.drawable.album_selected_icon));

        Iterator iter = mOTGdevicesInfos.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String path = (String) entry.getKey();
            String otgName = (String) entry.getValue();
            items.add(new SideBarItem(path, otgName, R.drawable.otg_icon, R.drawable.otg_selected_icon));
        }

        for (SideBarItem item : items) {
            if (item.getKey().equals(itemKey)) {
                item.setSelected(true);
                mRootsToolbar.setTitle(item.getTitle());
                if (mOTGdevicesInfos != null && mOTGdevicesInfos.containsKey(itemKey)) {
                    getDataManager().setOtgDeviceCurrentPath(itemKey);
                } else {
                    getDataManager().setOtgDeviceCurrentPath(null);
                }
                break;
            }
        }
        mSideBarAdapter = new SideBarAdapter(this, items);
        mSideBarAdapter.setOnSideBarItemClickListener(this);
        mSideBarAdapter.setOnSideBarItemChangeListener(this);
        mSideBar.setAdapter(mSideBarAdapter);
    }

    private void onSideBarDataSetChanged() {
        if (mSideBar == null || mSideBar.getAdapter() == null) {
            return;
        }
        boolean hasItemSelected = false;
        SideBarAdapter adapter = (SideBarAdapter) mSideBar.getAdapter();
        List<SideBarItem> sideBarItems = adapter.getSideBarItems();
        for (SideBarItem item : sideBarItems) {
            if (SideBarItem.ALL.equals(item.getKey())
                    || SideBarItem.PHOTO.equals(item.getKey())
                    || SideBarItem.VIDEO.equals(item.getKey())
                    || SideBarItem.ALBUM.equals(item.getKey())) {
                continue;
            }
            sideBarItems.remove(item);
        }
        for (SideBarItem item : sideBarItems) {
            if (item.isSelected()) {
                if (item.getKey().equals(SideBarItem.ALBUM)) {
                    getDataManager().setOtgDeviceCurrentPath(null);
                }
                hasItemSelected = true;
                break;
            }
        }

        Iterator iter = mOTGdevicesInfos.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String path = (String) entry.getKey();
            String otgName = (String) entry.getValue();
            sideBarItems.add(new SideBarItem(path, otgName, R.drawable.otg_icon,
                    R.drawable.otg_selected_icon));
        }

        if (!hasItemSelected) {
            for (SideBarItem item : sideBarItems) {
                if (SideBarItem.ALBUM.equals(item.getKey())) {
                    item.setSelected(true);
                    mToolbar.setTitle(item.getTitle());
                    mRootsToolbar.setTitle(item.getTitle());
                    break;
                }
            }
            onSideBarClicked(SideBarItem.ALBUM, true);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSideBarClicked(String key, boolean changed) {
        // TODO Auto-generated method stub
        setRootsDrawerOpen(false);
        if (changed) {
            if (getStateManager().getStateCount() <= 0) {
                finish();
                return;
            }
            getStateManager().getTopState().onSideBarClicked(key);
        }
    }

    @Override
    protected DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }

    @Override
    protected Toolbar getNewToolbar() {
        return (Toolbar) findViewById(R.id.toolbar2);
    }

    @Override
    public void onSideBarItemChanged(SideBarItem item) {
        mAlbumSetTitle = item.getTitle();
        if (getStateManager().isStackEmpty() || !(getStateManager().getTopState() instanceof SprdAlbumSetPage)) {
            return;
        }
        mToolbar.setTitle(item.getTitle());
        mRootsToolbar.setTitle(item.getTitle());
        getStateManager().getTopState().onSideBarItemChanged(item);
    }

    @Override
    public boolean isSideBarOpened() {
        return mIsSideBarOpened;
    }

    @Override
    protected String getAlbumSetTitle() {
        return mAlbumSetTitle;
    }

    public boolean isImageViewIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        return Intent.ACTION_GET_CONTENT.equalsIgnoreCase(action) || Intent.ACTION_PICK
                .equalsIgnoreCase(action) || Intent.ACTION_VIEW.equalsIgnoreCase(action) ||
                ACTION_REVIEW.equalsIgnoreCase(action);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSideBarAdapter != null) {
            outState.putString(KEY_SIDE_BAR_ITEM, mSideBarAdapter.getSelectedItemKey());
        }
        outState.putBoolean(KEY_REQUEST_PERMISSION, mIsRequestPermission);
    }


    private void checkOTGdevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            HashMap<String, String> volumnInfo = StandardFrameworks.getInstances().getOtgVolumesInfo(mStorageManager);
            Iterator iter = volumnInfo.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                String volumeName = (String) entry.getKey();
                String volumePath = (String) entry.getValue();
                if (volumeName != null && volumePath != null) {
                    Log.d(TAG, "volumeName is : " + volumeName + " volumePath is: " + volumePath);
                    mOTGdevicesInfos.put(volumePath, volumeName);
                }
            }
        }
        if (mOTGdevicesInfos.size() > 0) {
            getDataManager().setOtgDeviceInfos(mOTGdevicesInfos);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, " onActivityResult  requestCode  = " + requestCode + " resultCode = " + resultCode + ", data=" + data);
        mIsRequestPermission = false;
        if (requestCode == PermissionsActivity.START_PERIMISSION_SUCESS
                && resultCode != PermissionsActivity.START_PERIMISSION_FAIL) {
            if (data == null) {
                checkPermissions();
                return;
            }
            if (data != null && data.getData() != null) {
                if (!GalleryUtils.isValidUri(this, data.getData())) {
                    Toast.makeText(this, R.string.fail_to_load, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            if (mSavedInstanceState != null) {
                getStateManager().restoreFromState(mSavedInstanceState);
            } else {
                initializeByIntent();
            }
            mSplashView.setVisibility(View.GONE);
        } else if (requestCode == SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE) {
            if (data == null || data.getData() == null) {
                if (mSdCardPermissionListener != null) {
                    mSdCardPermissionListener.onSdCardPermissionDenied();
                    mSdCardPermissionListener = null;
                }
            } else {
                Uri uri = data.getData();
                //
                String documentId = DocumentsContract.getTreeDocumentId(uri);
                if (!documentId.endsWith(":") || "primary:".equals(documentId)) {
                    if (mSdCardPermissionListener != null) {
                        mSdCardPermissionListener.onSdCardPermissionDenied();
                        mSdCardPermissionListener = null;
                    }
                    return;
                }
                //
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);

                if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                    String path = SdCardPermission.getInvalidatePermissionStoragePath(0);
                    SdCardPermission.saveStorageUriPermission(path, uri.toString());
                    SdCardPermission.removeInvalidatePermissionStoragePath(0);
                    Log.d(TAG, "onActivityResult uri = " + uri + ", storage = " + path);
                }

                if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                    Intent accessIntent = SdCardPermission.getAccessStorageIntent(
                            SdCardPermission.getInvalidatePermissionStoragePath(0)
                    );
                    if (accessIntent == null) {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionDenied();
                            mSdCardPermissionListener = null;
                        }
                    } else {
                        startActivityForResult(accessIntent, SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE);
                    }
                } else {
                    if (mSdCardPermissionListener != null) {
                        mSdCardPermissionListener.onSdCardPermissionAllowed();
                        mSdCardPermissionListener = null;
                    }
                }
            }
        } else if (resultCode == PermissionsActivity.START_PERIMISSION_FAIL) {
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void setStatusBarColor(int color) {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);

        setToolbarMargins(0, 0, 0, 0);
    }

    @Override
    public void setStatusBarTranslucent() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        // window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        setToolbarMargins(0, GalleryUtils.getStatusBarHeight(this), 0, 0);
    }

    private void setToolbarMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mToolbar.getLayoutParams();
        lp.setMargins(left, top, right, bottom);

        LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams) mRootsToolbar.getLayoutParams();
        lp2.setMargins(left, top, right, bottom);
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }
}

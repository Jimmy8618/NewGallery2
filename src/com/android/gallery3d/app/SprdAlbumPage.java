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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.AlbumData;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.ItemInfo;
import com.android.gallery3d.data.LabelInfo;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumPageView;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.PhotoFallbackEffect;
import com.android.gallery3d.ui.RelativePosition;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SprdRecyclerPageView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.gallery3d.drm.MenuExecutorUtils;
import com.sprd.gallery3d.drm.SomePageUtils;

import java.lang.ref.WeakReference;


public class SprdAlbumPage extends ActivityState implements GalleryActionBar.ClusterRunner,
        SelectionManager.SelectionListener, MediaSet.SyncListener, GalleryActionBar.OnAlbumModeSelectedListener,
        AlbumPageView.OnItemClickListener, AlbumPageView.OnLabelClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = "SprdAlbumPage";

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_PARENT_MEDIA_PATH = "parent-media-path";
    public static final String KEY_SET_CENTER = "set-center";
    public static final String KEY_AUTO_SELECT_ALL = "auto-select-all";
    public static final String KEY_SHOW_CLUSTER_MENU = "cluster-menu";
    public static final String KEY_EMPTY_ALBUM = "empty-album";
    public static final String KEY_RESUME_ANIMATION = "resume_animation";

    private static final int REQUEST_SLIDESHOW = 1;
    public static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_DO_ANIMATION = 3;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;

    private static final float USER_DISTANCE_METER = 0.3f;

    //    private boolean mIsActive = false;
//    private AlbumSlotRenderer mAlbumView;
    private Path mMediaSetPath;
    private String mParentMediaSetString;
//    private SlotView mSlotView;

    private Toolbar mToolbar;
    /* SPRD: bug 512691 Overlap between GalleryActionBar and mSlotView if  switch screen repeatedly @{ */
    private int mActionBarHeightPortrait = 0;
    private int mActionBarHeightLandscape = 0;
    /* @} */

    private SprdAlbumDataLoader mAlbumDataAdapter;

    protected SelectionManager mSelectionManager;

    private boolean mGetContent;
    private boolean mShowClusterMenu;

    private ActionModeHandler mActionModeHandler;
    private int mFocusIndex = 0;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private MediaSet mMediaSet;
    private boolean mShowDetails;
    private float mUserDistance; // in pixel
    private Future<Integer> mSyncTask = null;
    private boolean mLaunchedFromPhotoPage;
    private boolean mInCameraApp;
    private boolean mInCameraAndWantQuitOnPause;

    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;
    private int mSyncResult;
    private boolean mLoadingFailed;
    private RelativePosition mOpenCenter = new RelativePosition();

    private Handler mHandler;
    private static final int MSG_PICK_PHOTO = 0;
    private static final int MSG_PLAY_SLIDESHOW = 1;
    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private static final int REQUEST_SLIDESHOW_MUSIC = 7;
    private static final int REQUEST_SLIDESHOW_RINGTONE = 8;
    public static final int NONE_MUSIC = 0;
    public static final int SELECT_MUSIC = 1;
    public static final int USER_DEFINED_MUSIC = 2;
    private int mPos;
    private int mUserSelected = 0;
    /* @} */
    private AlbumPageView mAlbumPageView;

    private PhotoFallbackEffect mResumeEffect;

    private boolean mNeedUpdate = false;

    private Menu mMenu;

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_background;
    }


    // This are the transitions we want:
    //
    // +--------+           +------------+    +-------+    +----------+
    // | Camera |---------->| Fullscreen |--->| Album |--->| AlbumSet |
    // |  View  | thumbnail |   Photo    | up | Page  | up |   Page   |
    // +--------+           +------------+    +-------+    +----------+
    //     ^                      |               |            ^  |
    //     |                      |               |            |  |         close
    //     +----------back--------+               +----back----+  +--back->  app
    //
    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            if (mLaunchedFromPhotoPage) {
                mActivity.getTransitionStore().putIfNotPresent(
                        PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                        PhotoPage.MSG_ALBUMPAGE_RESUMED);
            }
            // TODO: fix this regression
            // mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
            if (mInCameraApp) {
                super.onBackPressed();
            } else {
                onUpPressed();
            }
        }
    }

    private void onUpPressed() {
        if (!mIsActive) {
            return;
        }

        if (mInCameraApp) {
            GalleryUtils.startGalleryActivity(mActivity);
        } else if (mActivity.getStateManager().getStateCount() > 1) {
            mAlbumPageView.clearData();
            super.onBackPressed();
        } else if (mParentMediaSetString != null) {
            Bundle data = new Bundle(getData());
            data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, mParentMediaSetString);
            mActivity.getStateManager().switchState(
                    this, SprdAlbumSetPage.class, data);
        }
    }

    private void pickPhoto(int slotIndex) {
        pickPhoto(slotIndex, false);
    }

    private void pickPhoto(int slotIndex, boolean startInFilmstrip) {
        if (!mIsActive) {
            return;
        }

        if (!startInFilmstrip) {
            // Launch photos in lights out mode
            mActivity.getGLRoot().setLightsOutMode(true);
        }

        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null) {
            return; // Item not ready yet, ignore the click
        }
        Log.d(TAG, "item path:" + item.getFilePath());
        if (mGetContent) {
            onGetContent(item);
        } else if (mLaunchedFromPhotoPage) {
            TransitionStore transitions = mActivity.getTransitionStore();
            transitions.put(
                    PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_PICKED);
            transitions.put(PhotoPage.KEY_INDEX_HINT, slotIndex);
            onBackPressed();
        } else {
            // Get into the PhotoPage.
            // mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
            Bundle data = new Bundle();
            data.putInt(PhotoPage.KEY_INDEX_HINT, slotIndex);
//            data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
//                    mSlotView.getSlotRect(slotIndex, mRootPane));
            data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                    mMediaSetPath.toString());
            data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
                    item.getPath().toString());
            data.putInt(PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_STARTED);
            data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP,
                    startInFilmstrip);
            data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, mMediaSet.isCameraRoll());
            /* SPRD: Modify for bug569701, do not support edit if read_only is true @ */
            data.putBoolean(PhotoPage.KEY_READONLY,
                    getData().getBoolean(PhotoPage.KEY_READONLY, false));
            /* @} */
            data.putBoolean(PhotoPage.KEY_START_FROM_WIDGET, false);
            if (startInFilmstrip) {
                mActivity.getStateManager().switchState(this, FilmstripPage.class, data);
            } else {
                mActivity.getStateManager().startStateForResult(
                        SinglePhotoPage.class, REQUEST_PHOTO, data);
            }
        }
    }

    private void onGetContent(final MediaItem item) {
        DataManager dm = mActivity.getDataManager();
        Activity activity = mActivity;
        /* SPRD: Drm feature start @{ */
        if (SomePageUtils.getInstance()
                .canGetFromDrm(mActivity.getAndroidContext(), mGetContentForSetAs, item)) {
            return;
        }
        /* SPRD: Drm feature end @} */
        if (mData.getString(GalleryActivity.EXTRA_CROP) != null) {
            Uri uri = dm.getContentUri(item.getPath());
            Intent intent = new Intent(CropActivity.CROP_ACTION, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    .putExtras(getData());
            if (mData.getParcelable(MediaStore.EXTRA_OUTPUT) == null) {
                intent.putExtra(CropExtras.KEY_RETURN_DATA, true);
            }
            activity.startActivity(intent);
            activity.finish();
        } else {
            Intent intent = new Intent(null, item.getContentUri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }
    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.newClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, newPath);
        if (mShowClusterMenu) {
            Context context = mActivity.getAndroidContext();
            data.putString(SprdAlbumSetPage.KEY_SET_TITLE, mMediaSet.getName());
            data.putString(SprdAlbumSetPage.KEY_SET_SUBTITLE,
                    GalleryActionBar.getClusterByTypeString(context, clusterType));
        }

        // mAlbumView.savePositions(PositionRepository.getInstance(mActivity));
        mActivity.getStateManager().startStateForResult(
                SprdAlbumSetPage.class, REQUEST_DO_ANIMATION, data);
    }

    @Override
    protected void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);
        initializeViews();
        initializeData(data);
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        /* SPRD: Drm feature start @{ */
        mGetContentForSetAs = data.getBoolean("key-set-as", false);
        /* SPRD: Drm feature end @} */
        mShowClusterMenu = data.getBoolean(KEY_SHOW_CLUSTER_MENU, false);
        mDetailsSource = new MyDetailsSource();
        Context context = mActivity.getAndroidContext();
        mUserSelected = GalleryUtils.getSelected(context);
        if (data.getBoolean(KEY_AUTO_SELECT_ALL)) {
            mSelectionManager.selectAll();
        }

        mLaunchedFromPhotoPage =
                mActivity.getStateManager().hasStateClass(FilmstripPage.class);
        mInCameraApp = data.getBoolean(PhotoPage.KEY_APP_BRIDGE, false);

        mHandler = new MySynchronizedHandler(mActivity.getGLRoot(), this);
        mToolbar = mActivity.findViewById(R.id.toolbar);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SprdAlbumPage> mSprdAlbumPage;

        public MySynchronizedHandler(GLRoot root, SprdAlbumPage sprdAlbumPage) {
            super(root);
            mSprdAlbumPage = new WeakReference<>(sprdAlbumPage);
        }

        @Override
        public void handleMessage(Message message) {
            SprdAlbumPage sprdAlbumPage = mSprdAlbumPage.get();
            if (sprdAlbumPage != null) {
                sprdAlbumPage.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        switch (message.what) {
            case MSG_PICK_PHOTO: {
                pickPhoto(message.arg1);
                break;
            }
            case MSG_PLAY_SLIDESHOW: {
                if (mMenu != null) {
                    mMenu.clear();
                    mMenu.close();
                }
                mInCameraAndWantQuitOnPause = false;
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH,
                        mMediaSetPath.toString());
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                break;
            }
            default:
                throw new AssertionError(message.what);
        }
    }

    @Override
    protected void onWillResumeFromPhotoPage() {
        super.onWillResumeFromPhotoPage();
        Log.d(TAG, "onWillResumeFromPhotoPage");
        // GalleryUtils.animatorIn(mToolbar, mAlbumPageView, 1000);
        GalleryUtils.animatorAlpha(mActivity.getCoverView(), 300);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mIsActive = true;
        //SPRD: bug 618217, Select item of Music dialog no change
        mUserSelected = GalleryUtils.getSelected(mActivity.getAndroidContext());
        mResumeEffect = mActivity.getTransitionStore().get(KEY_RESUME_ANIMATION);
//        if (mResumeEffect != null) {
//            mAlbumView.setSlotFilter(mResumeEffect);
//            mResumeEffect.setPositionProvider(mPositionProvider);
//            mResumeEffect.start();
//        }

//        setContentPane(mRootPane);

        boolean enableHomeButton = (mActivity.getStateManager().getStateCount() > 1) |
                mParentMediaSetString != null;
        GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        // SPRD: Add 20141211 Spreadst of bug379599, show title of album in actionbar
        if (mMediaSet != null) {
            actionBar.setTitle(mMediaSet.getName());
        }
        actionBar.setDisplayOptions(enableHomeButton, false);
        if (!mGetContent) {
            actionBar.enableAlbumModeMenu(GalleryActionBar.ALBUM_GRID_MODE_SELECTED, this);
        }

        // Set the reload bit here to prevent it exit this page in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mLoadingFailed = false;
        mAlbumPageView.resume();
        mAlbumDataAdapter.resume();

//        mAlbumView.resume();
//        mAlbumView.setPressedIndex(-1);
        mActionModeHandler.resume();
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(this);
        }
        mInCameraAndWantQuitOnPause = mInCameraApp;

        mToolbar.setVisibility(View.VISIBLE);
        if (mMediaSet.getName() != null) {
            mToolbar.setTitle(mMediaSet.getName());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mToolbar.setOverflowIcon(mActivity.getDrawable(R.drawable.photo_control_option_press));
        }
        mActivity.setActionBar(mToolbar);
        if (mActivity.getNewToolbar() != null) {
            mActivity.getNewToolbar().setVisibility(View.GONE);
        }

        mToolbar.setNavigationIcon(R.drawable.ic_back_gray);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUpPressed();
            }
        });
        mAlbumPageView.setVisibility(View.VISIBLE);
        if (mNeedUpdate) {
            mNeedUpdate = false;
            mAlbumPageView.forceUpdate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mIsActive = false;
        mHandler.removeMessages(MSG_PLAY_SLIDESHOW);
        if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        }
//        mAlbumView.setSlotFilter(null);
        mAlbumPageView.pause();
        mActionModeHandler.pause();
        mAlbumDataAdapter.pause();
//        mAlbumView.pause();
        if (!DetailsHelper.SHOW_IN_ACTIVITY) {
            DetailsHelper.pause();
        }
        if (!mGetContent) {
            mActivity.getGalleryActionBar().disableAlbumModeMenu(true);
        }

        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
        Log.d(TAG, "onPause mActivity.isPaused = " + mActivity.isPaused() + ", mActivity.isFinishing = " + mActivity.isFinishing());
        if (!mActivity.isPaused() && !mActivity.isFinishing()) {
            mAlbumPageView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAlbumPageView.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mAlbumPageView.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAlbumDataAdapter != null) {
            mAlbumDataAdapter.setLoadingListener(null);
        }
        mActionModeHandler.destroy();
    }

    private void initializeViews() {
        mAlbumPageView = mActivity.findViewById(R.id.album_page_view);
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);
        mAlbumPageView.setSelectionManager(mSelectionManager);
        mAlbumPageView.setOnItemClickListener(this);
        mAlbumPageView.setOnLabelClickListener(this);
        mAlbumPageView.setActivity(mActivity);
//        Config.SprdAlbumPage config = Config.SprdAlbumPage.get(mActivity);
//        mSlotView = new SlotView(mActivity, config.slotViewSpec);
//        mAlbumView = new AlbumSlotRenderer(mActivity, mSlotView,
//                mSelectionManager, config.placeholderColor);
//        mSlotView.setSlotRenderer(mAlbumView);
//        mRootPane.addComponent(mSlotView);
//        mSlotView.setListener(new SlotView.SimpleListener() {
//            @Override
//            public void onDown(int index) {
//                SprdAlbumPage.this.onDown(index);
//            }
//
//            @Override
//            public void onUp(boolean followedByLongPress) {
//                SprdAlbumPage.this.onUp(followedByLongPress);
//            }
//
//            @Override
//            public void onSingleTapUp(int slotIndex) {
//                SprdAlbumPage.this.onSingleTapUp(slotIndex);
//            }
//
//            @Override
//            public void onLongTap(int slotIndex) {
//                SprdAlbumPage.this.onLongTap(slotIndex);
//            }
//        });
        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
        mAlbumPageView.setActionModeHandler(mActionModeHandler);
    }

    private void initializeData(Bundle data) {
        mMediaSetPath = Path.fromString(data.getString(KEY_MEDIA_PATH));
        mParentMediaSetString = data.getString(KEY_PARENT_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mMediaSetPath);
        if (mMediaSet == null) {
            Utils.fail("MediaSet is null. Path = %s", mMediaSetPath);
        }
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumDataAdapter = new SprdAlbumDataLoader(mActivity, mMediaSet);
        mAlbumDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumDataAdapter.setMediaSetDataChangedListener(new MyMediaSetDataChangedListener());
//        mAlbumView.setModel(mAlbumDataAdapter);
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, /*mRootPane*/null, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
//        mAlbumView.setHighlightItemPath(null);
//        mSlotView.invalidate();
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        MenuInflater inflator = getSupportMenuInflater();
        if (mGetContent) {
            inflator.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(GalleryActivity.KEY_TYPE_BITS,
                    DataManager.INCLUDE_IMAGE);
            actionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else {
            inflator.inflate(R.menu.album, menu);
            actionBar.setTitle(mMediaSet.getName());

            FilterUtils.setupMenuItems(actionBar, mMediaSetPath, true);

            /*menu.findItem(R.id.action_group_by).setVisible(mShowClusterMenu);
            menu.findItem(R.id.action_camera).setVisible(
                    MediaSetUtils.isCameraSource(mMediaSetPath)
                    && GalleryUtils.isCameraAvailable(mActivity));*/

        }
        mMenu = menu;
        actionBar.setSubtitle(null);
        return true;
    }

//    private void prepareAnimationBackToFilmstrip(int slotIndex) {
//        if (mAlbumDataAdapter == null || !mAlbumDataAdapter.isActive(slotIndex)) return;
//        MediaItem item = mAlbumDataAdapter.get(slotIndex);
//        if (item == null) return;
//        TransitionStore transitions = mActivity.getTransitionStore();
//        transitions.put(PhotoPage.KEY_INDEX_HINT, slotIndex);
//        transitions.put(PhotoPage.KEY_OPEN_ANIMATION_RECT,
//                mSlotView.getSlotRect(slotIndex, mRootPane));
//    }

//    private void switchToFilmstrip() {
//        if (mAlbumDataAdapter.size() < 1) return;
//        int targetPhoto = mSlotView.getVisibleStart();
//        prepareAnimationBackToFilmstrip(targetPhoto);
//        if(mLaunchedFromPhotoPage) {
//            onBackPressed();
//        } else {
//            pickPhoto(targetPhoto, true);
//        }
//    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
/*            case android.R.id.home: {
                onUpPressed();
                return true;
            }*/
            case R.id.action_cancel:
                mActivity.getStateManager().finishState(this);
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            /*case R.id.action_group_by: {
                mActivity.getGalleryActionBar().showClusterDialog(this);
                return true;
            }*/
            case R.id.action_slideshow: {
                if (mAlbumPageView.inSelectionMode()) {
                    return true;
                }
                if (!mHandler.hasMessages(MSG_PLAY_SLIDESHOW) && mActivity.getStateManager().isTopStateClass
                        (SprdAlbumPage.class) && mIsActive) {
                    mHandler.sendEmptyMessageDelayed(MSG_PLAY_SLIDESHOW, 250);
                }
                return true;
            }
            /* SPRD: Fix Bug 535131, add slide music feature @{ */
            case R.id.action_slideshow_music: {
                if (mAlbumPageView.inSelectionMode()) {
                    return true;
                }

                showSelectMusicDialog();
                return true;
            }
            /* @} */
            case R.id.action_details: {
                if (!DetailsHelper.SHOW_IN_ACTIVITY) {
                    if (mShowDetails) {
                        hideDetails();
                    } else {
                        showDetails();
                    }
                } else {
                    GalleryUtils.startDetailsActivity(mActivity, mDetailsSource);
                }
                return true;
            }
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(mActivity);
                return true;
            }
            default:
                return MenuExecutorUtils.getInstance().showHideDrmDetails(SprdAlbumPage.this, item.getItemId());
        }
    }

    @Override
    protected void onStateResult(int request, int result, Intent data) {
        switch (request) {
            case REQUEST_SLIDESHOW: {
                // data could be null, if there is no images in the album
                if (data == null) {
                    return;
                }
                mFocusIndex = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
//                mSlotView.setCenterIndex(mFocusIndex);
                break;
            }
            case REQUEST_PHOTO: {
                if (data == null) {
                    return;
                }
                mFocusIndex = data.getIntExtra(PhotoPage.KEY_RETURN_INDEX_HINT, 0);
//                mSlotView.makeSlotVisible(mFocusIndex);
                mAlbumPageView.scrollToPosition(mFocusIndex);
                break;
            }
            case REQUEST_DO_ANIMATION: {
//                mSlotView.startRisingAnimation();
                break;
            }
            /* SPRD: Fix Bug 535131, add slide music feature @{ */
            case REQUEST_SLIDESHOW_RINGTONE: {
                if (result == Activity.RESULT_OK && data != null) {
                    mActivity.getAndroidContext();
                    Uri uri = data.getExtras().getParcelable(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    /* SPRD:Modify for bug590332 when setting slide music and delete all ringtones ,resume gallery,the gallery will crash by old bug579217@{ */
                    if (uri == null) {
                        mPos = NONE_MUSIC;
                        GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), null);
                        GalleryUtils.saveSelected(mActivity.getAndroidContext(), NONE_MUSIC);
                        mUserSelected = NONE_MUSIC;
                        return;
                    }
                    /* Bug590332 End @{ */
                    GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), uri.toString());
                    // SPRD: bug 566432, bug 569414 User defined cannot choose music
                    GalleryUtils.saveSelected(mActivity.getAndroidContext(), SELECT_MUSIC);
                    mUserSelected = SELECT_MUSIC;
                }
                break;
            }
            case REQUEST_SLIDESHOW_MUSIC: {
                if (result == Activity.RESULT_OK && data != null && data.getData() != null) {
                    mActivity.getAndroidContext();
                    Uri uri = data.getData();
                    if (uri != null) {
                        if (uri.getAuthority().equals("com.android.providers.media.documents")) {
                            final String docId = DocumentsContract.getDocumentId(uri);
                            final String[] split = docId.split(":");
                            final String id = split[1];
                            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                    .appendPath(split[1]).build();
                        }
                    }
                    GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), uri.toString());
                    // SPRD: bug 566432, bug 569414 User defined cannot choose music
                    GalleryUtils.saveSelected(mActivity.getAndroidContext(), USER_DEFINED_MUSIC);
                    mUserSelected = USER_DEFINED_MUSIC;
                }
                break;
            }
            /* @} */
        }
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                mAlbumPageView.leaveSelectionMode();
//                mRootPane.invalidate();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
//                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange() {
        int count = mSelectionManager.getSelectedCount();
//        String format = mActivity.getResources().getQuantityString(
//                R.plurals.number_of_items_selected, count);
//        mActionModeHandler.setTitle(String.format(format, count));
        mActionModeHandler.setTitle(String.valueOf(count));
        mActionModeHandler.updateSupportedOperation();
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                + resultCode);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                mSyncResult = resultCode;
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    showSyncErrorIfNecessary(mLoadingFailed);
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    // Show sync error toast when all the following conditions are met:
    // (1) both loading and sync are done,
    // (2) sync result is error,
    // (3) the page is still active, and
    // (4) no photo is shown or loading fails.
    private void showSyncErrorIfNecessary(boolean loadingFailed) {
        if ((mLoadingBits == 0) && (mSyncResult == MediaSet.SYNC_RESULT_ERROR) && mIsActive
                && (loadingFailed || (mAlbumDataAdapter.size() == 0))) {
            Toast.makeText(mActivity, R.string.sync_album_error,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setLoadingBit(int loadTaskBit) {
        mLoadingBits |= loadTaskBit;
    }

    private void clearLoadingBit(int loadTaskBit) {
        mLoadingBits &= ~loadTaskBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumDataAdapter.size() == 0) {
                Intent result = new Intent();
                result.putExtra(KEY_EMPTY_ALBUM, true);
                setStateResult(Activity.RESULT_OK, result);
                mActivity.getStateManager().finishState(this);
            }
        }
    }

    private class MyMediaSetDataChangedListener implements SprdAlbumDataLoader.MediaSetDataChangedListener {
        @Override
        public void onMediaSetDataChangeStarted() {
            if (mAlbumPageView != null) {
                mAlbumPageView.onMediaSetDataChangeStarted();
            }
        }

        @Override
        public void onMediaSetDataChanged(AlbumData data) {
            if (mAlbumPageView != null) {
                mAlbumPageView.onMediaSetDataChanged(data);
            }
        }

        @Override
        public void onMediaSetDataChangeFinished() {
            if (mAlbumPageView != null) {
                mAlbumPageView.onMediaSetDataChangeFinished();
            }
        }
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
            mLoadingFailed = false;
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
            mLoadingFailed = loadingFailed;
            //SPRD: bug506152 data can't update when delete the last image
//            mSlotView.invalidate();
            // SPRD: Modify 20151230 for bug518277, selected count do not update after
            //  receiving incoming files via bluetooth. @{
            // We have to notify SelectionManager about data change.
            boolean inSelectionMode = (mSelectionManager != null && mSelectionManager
                    .inSelectionMode());
            int itemCount = mMediaSet != null ? mMediaSet.getMediaItemCount() : 0;
            if (itemCount == 0) {
                mAlbumPageView.clearData();
            }
            mSelectionManager.onSourceContentChanged();
            if (itemCount > 0 && inSelectionMode) {
                mActionModeHandler.updateSupportedOperation();
                mActionModeHandler.updateSelectionMenu();
            }
            // @}
            showSyncErrorIfNecessary(loadingFailed);
            if (mIsActive && mMediaSet.getName() != null && !mMediaSet.getName().equals(mToolbar.getTitle())) {
                mToolbar.setTitle(mMediaSet.getName());
            }
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;
        private MediaItem mItem;

        @Override
        public int size() {
            return mAlbumDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mIndex = mAlbumDataAdapter.findItem(id);
            mItem = mAlbumDataAdapter.get(mIndex);
            Log.d(TAG, "MyDetailsSource setIndex mIndex = " + mIndex + " id = " + id + " mItem = " + mItem);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mItem;
            if (item != null) {
                // mAlbumView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

    @Override
    public void onAlbumModeSelected(int mode) {
        if (mode == GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED) {
//            switchToFilmstrip();
        }
    }

    /* SPRD: Drm feature start @{ */
    private boolean mGetContentForSetAs;

    public void showDrmDetails() {
        if (mShowDetails) {
            hideDetails();
            return;
        }
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, /*mRootPane*/null, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.reloadDrmDetails(true);
        mDetailsHelper.show();
    }
    /* SPRD: Drm feature end @} */

    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private void showSelectMusicDialog() {
        final Context context = mActivity.getAndroidContext();
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        final String musicUri = GalleryUtils.getSlideMusicUri(context);

        int pos = 0;
        /* SPRD: bug 566432, User defined cannot choose music @{ */
        if (musicUri != null && !musicUri.isEmpty()) {
            pos = mUserSelected;
        }
        /* @} */
        CharSequence[] items = {
                context.getText(R.string.none), context.getText(R.string.select_music)
        };

        dialog.setTitle(R.string.slideshow_music);
        final OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                Activity activity = mActivity;
                mPos = mUserSelected;
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        break;
                    case NONE_MUSIC:
                        mPos = NONE_MUSIC;
                        /* SPRD: bug 571672,580807 can't remove background music @{ */
                        GalleryUtils.setSlideMusicUri(context, null);
                        GalleryUtils.saveSelected(mActivity.getAndroidContext(), NONE_MUSIC);
                        /* @} */
                        mUserSelected = NONE_MUSIC;
                        break;
                    case SELECT_MUSIC:
                        mPos = SELECT_MUSIC;
                        intent.setAction(RingtoneManager.ACTION_RINGTONE_PICKER);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                        // Modify for bug571604, use TYPE_ALL instead of TYPE_ALARM
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_RINGTONE);
                        if (musicUri != null) {
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(musicUri));
                        }
                        activity.startActivityForResult(intent, REQUEST_SLIDESHOW_RINGTONE);
                        break;
                    case USER_DEFINED_MUSIC:
                        mPos = USER_DEFINED_MUSIC;
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        /* SPRD: bug 566432, User defined cannot choose music */
                        intent.setType("audio/*,application/ogg,application/x-ogg");
                        // SPRD:bug 590498, DRM file can not be selected to slide music
                        intent.putExtra("applyForSlideMusic", true);
                        // intent.setType("audio/*");
                        // intent.setType("application/ogg");
                        // intent.setType("application/x-ogg");
                        /* @} */
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/ogg", "application/x-ogg"});
                        activity.startActivityForResult(intent, REQUEST_SLIDESHOW_MUSIC);
                        break;
                }
                dialog.dismiss();
            }
        };

        dialog.setSingleChoiceItems(items, pos, listener);
        dialog.setPositiveButton(R.string.cancel, listener);
        dialog.create().show();
    }

    /* @} */

    /* SPRD: bug 512691 Overlap between GalleryActionBar and mSlotView if  switch screen repeatedly @{ */
    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mActionBarHeightPortrait = mActivity.getGalleryActionBar().getHeight();
        } else {
            mActionBarHeightLandscape = mActivity.getGalleryActionBar().getHeight();
        }
    }
    /* @} */

    @Override
    public void onItemClick(final ItemInfo itemInfo) {

        AlertDialog.OnClickListener drmOnClickListener = new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                pickPhoto(itemInfo.getIndexInAlbumPage());
            }
        };

        if (itemInfo.getMediaItem().getMediaType() != MediaObject.MEDIA_TYPE_VIDEO && SomePageUtils.getInstance()
                .checkPressedIsDrm(mActivity, itemInfo.getMediaItem(), drmOnClickListener, null, null, mGetContent)) {
            mNeedUpdate = true;
            return;
        }
        if (itemInfo.getMediaItem().getMediaType() == MediaObject.MEDIA_TYPE_VIDEO && itemInfo.getMediaItem().mIsDrmFile) {
            mNeedUpdate = true;
        }

        pickPhoto(itemInfo.getIndexInAlbumPage());
    }

    @Override
    public void onItemLongClick(ItemInfo itemInfo) {
        MediaItem item = itemInfo.getMediaItem();
        mSelectionManager.setAutoLeaveSelectionMode(false);
        mSelectionManager.toggle(item.getPath());
    }

    @Override
    public void onLabelClick(LabelInfo labelInfo, SprdRecyclerPageView.OnLabelClickEventHandledListener l) {
        for (int i = 0; i < labelInfo.getChildItemSize(); i++) {
            mSelectionManager.toggle(labelInfo.getChildItem(i).getMediaItem().getPath(), labelInfo.isSelected());
        }
        mSelectionManager.updateMenu();
    }

    @Override
    protected boolean dispatchMenuKeyEvent(KeyEvent event) {
        return !mSelectionManager.inSelectionMode();
    }
}

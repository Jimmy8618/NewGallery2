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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;

import androidx.drawerlayout.widget.DrawerLayout;

import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.AlbumSetData;
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
import com.android.gallery3d.sidebar.SideBarItem;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSetPageView;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SprdRecyclerPageView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.HelpUtils;
import com.android.gallery3d.util.ToastUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.AlbumSetLoadingListener;
import com.sprd.gallery3d.app.VideoActivity;
import com.sprd.gallery3d.drm.MenuExecutorUtils;
import com.sprd.gallery3d.drm.SomePageUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class SprdAlbumSetPage extends ActivityState implements
        SelectionManager.SelectionListener, GalleryActionBar.ClusterRunner,
        /*EyePosition.EyePositionListener,*/ MediaSet.SyncListener, AlbumSetPageView.OnItemClickListener, AlbumSetPageView.OnLabelClickListener,
        AlbumSetPageView.OnPickAlbumListener, AlbumSetPageView.OnFirstLoadListener {
    @SuppressWarnings("unused")
    private static final String TAG = "SprdAlbumSetPage";

    private static final int MSG_PICK_ALBUM = 1;
    private static final int MSG_PICK_PHOTO = 2;

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_SET_TITLE = "set-title";
    public static final String KEY_SET_SUBTITLE = "set-subtitle";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";
    public static final String KEY_SET_FORCE_DIRTY = "key-set-force-dirty";

    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_DO_ANIMATION = 1;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;

//    private boolean mIsActive = false;
//    private SlotView mSlotView;
//    private AlbumSetSlotRenderer mAlbumSetView;
//    private Config.SprdAlbumSetPage mConfig;

    private String mTopSetPath = null;
    private MediaSet mMediaSet;
    private String mTitle;
    private String mSubtitle;
    private boolean mShowClusterMenu;
    private GalleryActionBar mActionBar;
    private int mSelectedAction;

    private Toolbar mToolbar;

    protected SelectionManager mSelectionManager;
    private SprdAlbumSetDataLoader mAlbumSetDataAdapter;

    private boolean mGetContent;
    private boolean mGetAlbum;
    private ActionModeHandler mActionModeHandler;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private boolean mShowDetails;
    //    private EyePosition mEyePosition;
    private Handler mHandler;

    // The eyes' position of the user, the origin is at the center of the
    // device and the unit is in pixels.
    private float mX;
    private float mY;
    private float mZ;

    private Future<Integer> mSyncTask = null;

    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;

    private View mNoPhotos;
    private boolean mShowedEmptyToastForSelf = false;

    private int lastSelectAlbumIndex;
    private int lastSelectPhotoIndex;
    private PopupWindow mPopupWindow;
    private AlbumSetPageView mAlbumSetPageView;
    private MenuItem mHideAlbumItem;
    private MenuItem mShowOnlyLocalAlbumItem;

    private boolean mGetContentForSetAs;
    private boolean mNeedUpdate = false;

    private boolean mHidePageWhenPause;

    private int mCurrentNoPhotoTitle = R.string.side_bar_photo;

    private boolean mIsQuit = false;
    private Toast mToast;
    /**
     * only these album {@link DataManager#getAllBucketId()} is local album
     */
    private boolean mHasNonLocalAlbum = false;

    @Override
    protected int getBackgroundColorId() {
        return R.color.albumset_background;
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed --->");
        mIsQuit = true;
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else if (mSelectionManager.inAlbumSelectionMode()) {

//            mAlbumSetView.mIsAlbumItemCountLoaded = false;
            mSelectionManager.leaveAlbumSelectionMode();
        } else if ((mActivity instanceof GalleryActivity) && mActivity.isSideBarOpened()) {
            mIsQuit = false;
            mActivity.setRootsDrawerOpen(false);
        } else {
            super.onBackPressed();
        }
    }

    private static boolean albumShouldOpenInFilmstrip(MediaSet album) {
        /* SPRD: fix bug 393908,If the Album Only contain one Drm File ,it will enter SprdAlbumPage @{ */
        /*if (SomePageUtils.getInstance().checkIsDrmFile(album)) {
            return false;
        }*/
        /* @} */
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> list = (itemCount == 1) ? album.getMediaItem(0, 1) : null;
        // open in film strip only if there's one item in the album and the item exists
        return (list != null && !list.isEmpty());
    }

    WeakReference<Toast> mEmptyAlbumToast = null;

    private void showEmptyAlbumToast(int toastLength) {
        Toast toast;
        if (mEmptyAlbumToast != null) {
            toast = mEmptyAlbumToast.get();
            if (toast != null) {
                toast.show();
                return;
            }
        }
        /* SPRD: bug 473267 add video entrance */
        /* old bug info: bug 379862,toast information is incorrect @{ */
        // toast = Toast.makeText(mActivity, R.string.empty_album, toastLength);
        if (mActivity instanceof VideoActivity) {
            toast = Toast.makeText(mActivity, R.string.empty_album_video, toastLength);
        } else {
            toast = Toast.makeText(mActivity, R.string.empty_album, toastLength);
        }
        /* @} */
        mEmptyAlbumToast = new WeakReference<Toast>(toast);
        toast.show();
    }

    private void hideEmptyAlbumToast() {
        if (mEmptyAlbumToast != null) {
            Toast toast = mEmptyAlbumToast.get();
            if (toast != null) {
                toast.cancel();
            }
        }
    }

//    private void pickAlbum(int slotIndex) {
//        if (!mIsActive) return;
//
//        /* SPRD: fix bug 399240,IllegalArgumentException in monkey test @{ */
//        MediaSet targetSet;
//        try {
//            targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
//        } catch (Exception e) {
//            Log.d(TAG, "exception when pick albume");
//            targetSet = null;
//        }
//        /* @} */
//        if (targetSet == null) return; // Content is dirty, we shall reload soon
//        if (targetSet.getTotalMediaItemCount() == 0) {
//            showEmptyAlbumToast(Toast.LENGTH_SHORT);
//            return;
//        }
//        hideEmptyAlbumToast();
//
//        String mediaPath = targetSet.getPath().toString();
//
//        Bundle data = new Bundle(getData());
//        int[] center = new int[2];
//        getSlotCenter(slotIndex, center);
//        data.putIntArray(SprdAlbumPage.KEY_SET_CENTER, center);
//        if (mGetAlbum && targetSet.isLeafAlbum()) {
//            Activity activity = mActivity;
//            Intent result = new Intent()
//                    .putExtra(AlbumPicker.KEY_ALBUM_PATH, targetSet.getPath().toString());
//            activity.setResult(Activity.RESULT_OK, result);
//            activity.finish();
//        } else if (targetSet.getSubMediaSetCount() > 0) {
//            data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, mediaPath);
//            mActivity.getStateManager().startStateForResult(
//                    SprdAlbumSetPage.class, REQUEST_DO_ANIMATION, data);
//        } else {
//            if (!mGetContent && albumShouldOpenInFilmstrip(targetSet)) {
//                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
//                        mSlotView.getSlotRect(slotIndex, mRootPane));
//                data.putInt(PhotoPage.KEY_INDEX_HINT, 0);
//                data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
//                        mediaPath);
//                // SPRD: fix bug 389187,can't enter gallery
//                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
//                        targetSet.getCoverMediaItem().getPath().toString());
//                data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, true);
//                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetSet.isCameraRoll());
//                /* SPRD: Modify for bug569701, do not support edit if read_only is true @ */
//                data.putBoolean(PhotoPage.KEY_READONLY,
//                        getData().getBoolean(PhotoPage.KEY_READONLY, false));
//                /* @} */
//                mActivity.getStateManager().startStateForResult(
//                        FilmstripPage.class, SprdAlbumPage.REQUEST_PHOTO, data);
//                return;
//            }
//            data.putString(SprdAlbumPage.KEY_MEDIA_PATH, mediaPath);
//
//            // We only show cluster menu in the first SprdAlbumPage in stack
//            boolean inAlbum = mActivity.getStateManager().hasStateClass(SprdAlbumPage.class);
//            data.putBoolean(SprdAlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum);
//            mActivity.getStateManager().startStateForResult(
//                    SprdAlbumPage.class, REQUEST_DO_ANIMATION, data);
//        }
//    }

//    private void onDown(int index) {
//        mAlbumSetView.setPressedIndex(index);
//    }

//    private void onUp(boolean followedByLongPress) {
//        if (followedByLongPress) {
//            // Avoid showing press-up animations for long-press.
//            mAlbumSetView.setPressedIndex(-1);
//        } else {
//            mAlbumSetView.setPressedUp();
//        }
//    }

//    public void onLongTap(int slotIndex) {
//        if (mGetContent || mGetAlbum) return;
//        if (mSelectionManager.inAlbumSelectionMode()) return;
//        MediaSet set = mAlbumSetDataAdapter.getMediaSet(slotIndex);
//        if (set == null) return;
//        mSelectionManager.setAutoLeaveSelectionMode(true);
//        mSelectionManager.toggle(set.getPath());
//        mSlotView.invalidate();
//    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(KEY_SELECTED_CLUSTER_TYPE, clusterType);
        Log.d(TAG, "doCluster basePath=" + basePath + ", newPath=" + newPath + ", clusterType=" + clusterType);
        mActivity.getStateManager().switchState(this, SprdAlbumSetPage.class, data);
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
//        initializeViews();
//        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mGetContentForSetAs = data.getBoolean("key-set-as", false);
        mGetAlbum = data.getBoolean(GalleryActivity.KEY_GET_ALBUM, false);
        mTitle = data.getString(SprdAlbumSetPage.KEY_SET_TITLE);
        mSubtitle = data.getString(SprdAlbumSetPage.KEY_SET_SUBTITLE);
//        mEyePosition = new EyePosition(context, this);
        mDetailsSource = new MyDetailsSource();
        mActionBar = mActivity.getGalleryActionBar();
        mSelectedAction = data.getInt(SprdAlbumSetPage.KEY_SELECTED_CLUSTER_TYPE,
                FilterUtils.CLUSTER_BY_ALBUM);
        Log.d(TAG, "onCreate mSelectedAction=" + mSelectedAction);
        initializeViews();
        initializeData(data);

        mHandler = new MySynchronizedHandler(mActivity.getGLRoot(), this);
        mToolbar = mActivity.findViewById(R.id.toolbar);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SprdAlbumSetPage> mSprdAlbumSetPage;

        public MySynchronizedHandler(GLRoot root, SprdAlbumSetPage sprdAlbumSetPage) {
            super(root);
            mSprdAlbumSetPage = new WeakReference<>(sprdAlbumSetPage);
        }

        @Override
        public void handleMessage(Message message) {
            SprdAlbumSetPage sprdAlbumSetPage = mSprdAlbumSetPage.get();
            if (sprdAlbumSetPage != null) {
                sprdAlbumSetPage.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        switch (message.what) {
            case MSG_PICK_ALBUM: {
                try {
//                            pickAlbum(message.arg1);
                } catch (Exception e) {
                    Log.d(TAG, "exception when pickAlbum");
                }
                break;
            }
            case MSG_PICK_PHOTO: {
                try {
//                            pickPhoto(message.arg1, message.arg2);
                } catch (Exception e) {
                    Log.d(TAG, "exception when pickPhoto");
                }
                break;
            }
            default:
                throw new AssertionError(message.what);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupCameraButton();
        mActionModeHandler.destroy();
    }

    private boolean setupCameraButton() {
        // if (!GalleryUtils.isCameraAvailable(mActivity)) return false;
        RelativeLayout galleryRoot = mActivity
                .findViewById(R.id.gallery_root);
        if (galleryRoot == null) {
            return false;
        }

        /*mCameraButton = new Button(mActivity);
        mCameraButton.setText(R.string.camera_label);
        mCameraButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.frame_overlay_gallery_camera, 0, 0);
        mCameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                GalleryUtils.startCameraActivity(mActivity);
            }
        });*/
        mNoPhotos = LayoutInflater.from(mActivity).inflate(R.layout.no_photo_or_video_tip, null);
        /*RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        galleryRoot.addView(mCameraButton, lp);*/
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        galleryRoot.addView(mNoPhotos, lp);
        return true;
    }

    private void cleanupCameraButton() {
        if (mNoPhotos == null) {
            return;
        }
        if (!mIsQuit) {
            RelativeLayout galleryRoot = mActivity
                    .findViewById(R.id.gallery_root);
            if (galleryRoot == null) {
                return;
            }
            galleryRoot.removeView(mNoPhotos);
            mNoPhotos = null;
        }
    }

    private void showCameraButton() {
        if (mNoPhotos == null && !setupCameraButton()) {
            return;
        }
        ((TextView) mNoPhotos.findViewById(R.id.title)).setText(String.format(mActivity.getString(R.string.no_photo_or_video), mActivity.getString(mCurrentNoPhotoTitle)));
        mNoPhotos.setVisibility(View.VISIBLE);
        mAlbumSetPageView.setVisibility(View.GONE);
    }

    private void hideCameraButton() {
        if (mIsQuit) {
            return;
        }
        if (mNoPhotos == null) {
            return;
        }
        if (mAlbumSetDataAdapter.size() != 0) {
            mNoPhotos.setVisibility(View.GONE);
            mAlbumSetPageView.setVisibility(View.VISIBLE);
        }
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumSetDataAdapter.size() == 0 || (mAlbumSetDataAdapter.size() == 1
                    && (mAlbumSetDataAdapter.getMediaSet(0) != null && mAlbumSetDataAdapter.getMediaSet(0).getMediaItemCount() == 0))) {
                // If this is not the top of the gallery folder hierarchy,
                // tell the parent SprdAlbumSetPage instance to handle displaying
                // the empty album toast, otherwise show it within this
                // instance
                if (mActivity.getStateManager().getStateCount() > 1) {
                    Intent result = new Intent();
                    result.putExtra(SprdAlbumPage.KEY_EMPTY_ALBUM, true);
                    setStateResult(Activity.RESULT_OK, result);
                    mActivity.getStateManager().finishState(this);
                } else {
                    mShowedEmptyToastForSelf = true;
                    // SPRD: do not show toast, just show camera button
                    // showEmptyAlbumToast(Toast.LENGTH_LONG);
                    // mSlotView.invalidate();
                    showCameraButton();
                }
                return;
            }
        }
        // Hide the empty album toast if we are in the root instance of
        // SprdAlbumSetPage and the album is no longer empty (for instance,
        // after a sync is completed and web albums have been synced)
        if (mShowedEmptyToastForSelf) {
            mShowedEmptyToastForSelf = false;
            hideEmptyAlbumToast();
            hideCameraButton();
        }
    }

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    protected void onWillFinishActivity() {
        super.onWillFinishActivity();
        Log.d(TAG, "onWillFinishActivity");
        mHidePageWhenPause = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mIsActive = false;
        if (mSelectionManager.inSelectionMode() || mSelectionManager.inAlbumSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
            mSelectionManager.leaveAlbumSelectionMode();
        }
        mAlbumSetPageView.pause();
        mAlbumSetDataAdapter.pause();
//        mAlbumSetView.pause();
        mActionModeHandler.pause();
//        mEyePosition.pause();
        if (!DetailsHelper.SHOW_IN_ACTIVITY) {
            DetailsHelper.pause();
        }
        // Call disableClusterMenu to avoid receiving callback after paused.
        // Don't hide menu here otherwise the list menu will disappear earlier than
        // the action bar, which is janky and unwanted behavior.
        mActionBar.disableClusterMenu(false);
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
        Log.d(TAG, "onPause mHidePageWhenPause=" + mHidePageWhenPause + ", mActivity.isPaused() = " + mActivity.isPaused()
                + ", mGetAlbum=" + mGetAlbum + ", mGetContent=" + mGetContent);
        if ((mHidePageWhenPause && !mActivity.isPaused()) && (!mGetAlbum && !mGetContent)) {
            Log.d(TAG, "onPause hide mAlbumSetPageView");
            mAlbumSetPageView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAlbumSetPageView.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mAlbumSetPageView.stop();
    }

    @Override
    protected void onWillResumeFromPhotoPage() {
        super.onWillResumeFromPhotoPage();
        Log.d(TAG, "onWillResumeFromPhotoPage");
        // GalleryUtils.animatorIn(mToolbar, mAlbumSetPageView, 1000);
        GalleryUtils.animatorAlpha(mActivity.getCoverView(), 300);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mHidePageWhenPause = true;
        mIsActive = true;
        mIsQuit = false;
//        setContentPane(mRootPane);

        // Set the reload bit here to prevent it exit this page in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mAlbumSetPageView.resume();
        mAlbumSetDataAdapter.resume();
        Log.d(TAG, "onResume mTopSetPath=" + mTopSetPath + ", buck path : " + mActivity.getDataManager().getBuckPath());
        if (mTopSetPath != null &&
                mActivity.getDataManager().getBuckPath() != null &&
                !mTopSetPath.contains(mActivity.getDataManager().getBuckPath())) {
            mAlbumSetDataAdapter.reloadData();
        }

        if (mAlbumSetPageView.inSelectionMode()) {
            mAlbumSetPageView.leaveSelectionMode();
        }
//        mAlbumSetView.resume();
//        mEyePosition.resume();
        mActionModeHandler.resume();
        if (mShowClusterMenu) {
            mActionBar.enableClusterMenu(mSelectedAction, this);
        }
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(SprdAlbumSetPage.this);
        }

        if (mToolbar != null) {
            mToolbar.setVisibility(View.VISIBLE);
            String title = mActivity.getAlbumSetTitle();
            if (title != null) {
                mToolbar.setTitle(title);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mToolbar.setOverflowIcon(mActivity.getDrawable(R.drawable.photo_control_option_press));
            }
            mActivity.setActionBar(mToolbar);
            if (mActivity.getNewToolbar() != null) {
                mActivity.getNewToolbar().setVisibility(View.GONE);
            }
            if (mActivity.isGetContent() || mActivity.isPick()) {
                mToolbar.setNavigationIcon(null);
            } else {
                mToolbar.setNavigationIcon(R.drawable.ic_hamburger);
            }
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActivity.setRootsDrawerOpen(true);
                }
            });
        }
        mAlbumSetPageView.setVisibility(View.VISIBLE);
        if (mNoPhotos != null) {
            mNoPhotos.setVisibility(View.GONE);
        }
        if (mNeedUpdate) {
            mNeedUpdate = false;
            mAlbumSetPageView.forceUpdate();
        }
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(SprdAlbumSetPage.KEY_MEDIA_PATH);
        mTopSetPath = mediaPath;
        Log.d(TAG, "initializeData mediaPath=" + mediaPath);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter = new SprdAlbumSetDataLoader(
                mActivity, mMediaSet, DATA_CACHE_SIZE);
        if (mActivity.getDataManager().getOtgDeviceCurrentPath() != null) {
            data.putBoolean(KEY_SET_FORCE_DIRTY, true);
        }
        if (data.getBoolean(KEY_SET_FORCE_DIRTY, false)) {
            mAlbumSetDataAdapter.sourceReforceDirty();
        }
        mAlbumSetDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSetDataAdapter.setMediaSetDataChangedListener(new MyMediaSetDataChangedListener());
//        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void initializeViews() {
        mAlbumSetPageView = mActivity.findViewById(R.id.album_home_view);
        mAlbumSetPageView.setActivity(mActivity);
        mAlbumSetPageView.setOnItemClickListener(mGetAlbum ? null : this);
        mAlbumSetPageView.setOnLabelClickListener(mGetAlbum ? null : this);
        mAlbumSetPageView.setOnPickAlbumListener(mGetAlbum ? this : null);
        mAlbumSetPageView.setOnFirstLoadListener(this);
        if (mGetAlbum) {
            mAlbumSetPageView.enterPickAlbumMode();
        }
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);
        mAlbumSetPageView.setSelectionManager(mSelectionManager);

//        mConfig = Config.SprdAlbumSetPage.get(mActivity);
//        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
//        mAlbumSetView = new AlbumSetSlotRenderer(
//                mActivity, mSelectionManager, mSlotView, mConfig.labelSpec,
//                mConfig.placeholderColor);
//        mAlbumSetView.setClusterType(mSelectedAction);
//        mSlotView.setSlotRenderer(mAlbumSetView);
//        mSlotView.setListener(new SlotView.SimpleListener() {
//            @Override
//            public void onDown(int index) {
//                SprdAlbumSetPage.this.onDown(index);
//            }
//
//            @Override
//            public void onUp(boolean followedByLongPress) {
//                SprdAlbumSetPage.this.onUp(followedByLongPress);
//            }
//
//            @Override
//            public void onSingleTapUp(int slotIndex) {
//                SprdAlbumSetPage.this.onSingleTapUp(slotIndex);
//            }
//
//            @Override
//            public void onLongTap(int slotIndex) {
//                SprdAlbumSetPage.this.onLongTap(slotIndex);
//            }
//
//            public void onSingleTapUp(int albumIndex, int photoIndex) {
//                Log.d(TAG, "onSingleTapUp albumIndex="+albumIndex+", photoIndex="+photoIndex);
//                SprdAlbumSetPage.this.onSingleTapUp(albumIndex, photoIndex);
//            }
//
//            @Override
//            public void onLongTap(int albumIndex, int photoIndex) {
//                Log.d(TAG, "onLongTap albumIndex="+albumIndex+", photoIndex="+photoIndex);
//                if (mSelectionManager.inAlbumSelectionMode()) return;
//                SprdAlbumSetPage.this.onLongTap(albumIndex, photoIndex);
//            }
//        });

        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
        mAlbumSetPageView.setActionModeHandler(mActionModeHandler);
//        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Activity activity = mActivity;
        final boolean inAlbum = mActivity.getStateManager().hasStateClass(SprdAlbumPage.class);
        /* SPRD: bug 473267 add video entrance */
        /* old bug info: bug 378976,menu's message is wrong when enter video */
        final int selectItemAlbumOrVideo = (mActivity instanceof VideoActivity) ? R.string.select_video
                : R.string.select_album;
        /* @} */

        MenuInflater inflater = getSupportMenuInflater();

        if (mGetContent) {
            inflater.inflate(R.menu.pickup, menu);
            int typeBits = mData.getInt(
                    GalleryActivity.KEY_TYPE_BITS, DataManager.INCLUDE_IMAGE);
            mActionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else if (mGetAlbum) {
            inflater.inflate(R.menu.pickup, menu);
            /* SPRD: bug 473267 add video entrance */
            /* old bug info: bug 378976,menu's message is wrong when enter video @{ */
            // mActionBar.setTitle(R.string.select_album);
            mActionBar.setTitle(selectItemAlbumOrVideo);
            /* @} */
        } else {
            inflater.inflate(R.menu.albumset, menu);
            boolean wasShowingClusterMenu = mShowClusterMenu;
            mShowClusterMenu = !inAlbum;
            /* SPRD: Add 20150119 Spreadst of bug395853, do this before "update title". @{ */
            if (mShowClusterMenu != wasShowingClusterMenu) {
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                } else {
                    mActionBar.disableClusterMenu(true);
                }
            }
            /* @} */
            boolean selectAlbums = !inAlbum &&
                    mActionBar.getClusterTypeAction() == FilterUtils.CLUSTER_BY_ALBUM;
            MenuItem selectItem = menu.findItem(R.id.action_select);
            /* SPRD: bug 473267 add video entrance */
            /* old bug info: bug 378976,menu's message is wrong when enter video @{ */
            // selectItem.setTitle(activity.getString(
            //        selectAlbums ? R.string.select_album : R.string.select_group));
            selectItem.setTitle(activity.getString(
                    selectAlbums ? selectItemAlbumOrVideo : R.string.select_group));
            /* @} */
            MenuItem cameraItem = menu.findItem(R.id.action_camera);
            cameraItem.setVisible(false/*GalleryUtils.isCameraAvailable(activity)*/);

            FilterUtils.setupMenuItems(mActionBar, mMediaSet.getPath(), false);

            Intent helpIntent = HelpUtils.getHelpIntent(activity);

            mHideAlbumItem = menu.findItem(R.id.action_hide_albums);
            mShowOnlyLocalAlbumItem = menu.findItem(R.id.action_show_local_albums);
            //是否勾选了仅显示本地相册
            boolean isShowOnlyLocalAlbums = mSelectionManager.getLocalAlbumsFlags();
            mShowOnlyLocalAlbumItem.setChecked(isShowOnlyLocalAlbums);
            if (mSelectionManager.alreadyHaveHideAlbum()) {
                mHasNonLocalAlbum = true;
            }
            //存在非本地相册, 就使能隐藏相册
            mHideAlbumItem.setEnabled(mHasNonLocalAlbum);
            if (isShowOnlyLocalAlbums) {
                //如果勾选了仅显示本地相册, 就禁用隐藏相册
                mHideAlbumItem.setEnabled(false);
            }

            Log.d(TAG, "onCreateActionBar --> mSelectedAction = " + mSelectedAction);
            if ((mMediaSet.getSubMediaSetCount() == 0 && !mSelectionManager.getLocalAlbumsFlags() && !mSelectionManager.isAlbumHided())
                    || mSelectedAction != FilterUtils.CLUSTER_BY_ALBUM) {
                mHideAlbumItem.setVisible(false);
                mShowOnlyLocalAlbumItem.setVisible(false);
            }

            MenuItem helpItem = menu.findItem(R.id.action_general_help);
            helpItem.setVisible(helpIntent != null);
            if (helpIntent != null) {
                helpItem.setIntent(helpIntent);
            }
            if (StandardFrameworks.getInstances().isSupportCover()) {
                MenuItem settingItem = menu.findItem(R.id.action_settings);
                if (settingItem != null) {
                    settingItem.setVisible(true);
                }
            } else {
                MenuItem settingItem = menu.findItem(R.id.action_settings);
                if (settingItem != null) {
                    settingItem.setVisible(false);
                }
            }
            mActionBar.setTitle(mTitle);
            mActionBar.setSubtitle(mSubtitle);
            /* SPRD: Modify 20150119 Spreadst of bug395853, do this before "update title". @{
            if (mShowClusterMenu != wasShowingClusterMenu) {
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                } else {
                    mActionBar.disableClusterMenu(true);
                }
            }
              @} */
        }
        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        Activity activity = mActivity;
        switch (item.getItemId()) {
            case R.id.action_cancel:
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return true;
            case R.id.action_select:
                /* SPRD: bug 473267 add video entrance */
                /* old bug info: bug 384812, do not allow to select item when no albums. @{ */
                if (mAlbumSetDataAdapter.size() <= 0) {
                    if (activity instanceof VideoActivity) {
                        Toast.makeText(activity, activity.getText(R.string.empty_album_video),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, activity.getText(R.string.no_albums_alert),
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                //showAlbumMenuPop();
                /* @} */
//                mSelectionManager.setAutoLeaveSelectionMode(false);
//                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_details:
                if (mAlbumSetDataAdapter.size() != 0) {
                    if (!DetailsHelper.SHOW_IN_ACTIVITY) {
                        if (mShowDetails) {
                            hideDetails();
                        } else {
                            showDetails();
                        }
                    } else {
                        GalleryUtils.startDetailsActivity(mActivity, mDetailsSource);
                    }
                } else {
                    Toast.makeText(activity,
                            activity.getText(R.string.no_albums_alert),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_settings:
                Log.i(TAG, "onItemSelected action_settings ");
                Intent intent = new Intent();
                intent.setClassName("com.android.gallery3d", "com.android.gallery3d.app.FlipSettingsActivity");
                mActivity.startActivity(intent);
                return true;
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(activity);
                return true;
            }
            case R.id.action_confirm: {
                saveAlbumsData();
                return true;
            }
            /* SPRD: fix bug 488870 Remove unused menu @{
            case R.id.action_manage_offline: {
                Bundle data = new Bundle();
                String mediaPath = mActivity.getDataManager().getTopSetPath(
                    DataManager.INCLUDE_ALL);
                data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, mediaPath);
                mActivity.getStateManager().startState(ManageCachePage.class, data);
                return true;
            }
            case R.id.action_sync_picasa_albums: {
                PicasaSource.requestSync(activity);
                return true;
            }
            case R.id.action_settings: {
                activity.startActivity(new Intent(activity, GallerySettings.class));
                return true;
            } */
            /* @} */
            case R.id.action_hide_albums: { //mHideAlbumItem
                if (mAlbumSetPageView.inSelectionMode()) {
                    return true;
                }
                mActionModeHandler.setEditAlbumFlag(true);
                mSelectionManager.enterAlbumSelectionMode();
                mSelectionManager.setEditAblumFlags(true);
//                mAlbumSetView.mIsAlbumItemCountLoaded = false;
                mAlbumSetDataAdapter.reloadData();
                mAlbumSetPageView.enterAlbumSelectionMode();
                return true;
            }
            case R.id.action_show_local_albums: { //mShowOnlyLocalAlbumItem
                if (mAlbumSetPageView.inSelectionMode()) {
                    return true;
                }
                item.setChecked(!item.isChecked());
                mSelectionManager.setLocalAlbumsFlags(item.isChecked());
                mHideAlbumItem.setEnabled(!item.isChecked());
                mAlbumSetDataAdapter.reloadData();
                return true;
            }
            default:
                return MenuExecutorUtils.getInstance().showHideDrmDetails(SprdAlbumSetPage.this, item.getItemId());
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        if (data != null && data.getBooleanExtra(SprdAlbumPage.KEY_EMPTY_ALBUM, false)) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
        }
        switch (requestCode) {
            case REQUEST_DO_ANIMATION: {
//                mSlotView.startRisingAnimation();
            }
        }
    }

    private String getSelectedString() {
        int count = mSelectionManager.getSelectedCount();
        /*int action = mActionBar.getClusterTypeAction();
        int string = action == FilterUtils.CLUSTER_BY_ALBUM
                ? R.plurals.number_of_albums_selected
                : R.plurals.number_of_groups_selected;
        String format = mActivity.getResources().getQuantityString(string, count);
        return String.format(format, count);*/
        Log.d(TAG, "getSelectedString count=" + count);
        return String.valueOf(count);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionBar.disableClusterMenu(true);
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                DrawerLayout layout = mActivity.getDrawerLayout();
                if (null != layout) {
                    layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                }
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                }
                if (mAlbumSetPageView.inSelectionMode()) {
                    mAlbumSetPageView.leaveSelectionMode();
                }
                if (mAlbumSetPageView.inAlbumSelectionMode()) {
                    mAlbumSetPageView.leaveAlbumSelectionMode();
                    mAlbumSetDataAdapter.reloadData();
                }
//                mAlbumSetView.mIsAlbumItemCountLoaded = false;
                mSelectionManager.setEditAblumFlags(false);
//                mAlbumSetDataAdapter.reloadData();
//                mRootPane.invalidate();
                DrawerLayout layout = mActivity.getDrawerLayout();
                if (null != layout) {
                    layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                }
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
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
//        mAlbumSetView.setHighlightItemPath(null);
//        mSlotView.invalidate();
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

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        if (resultCode == MediaSet.SYNC_RESULT_ERROR) {
            Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result="
                    + resultCode);
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    if (resultCode == MediaSet.SYNC_RESULT_ERROR && mIsActive) {
                        Log.w(TAG, "failed to load album set");
                    }
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    @Override
    public void onItemClick(final ItemInfo itemInfo) {
        if (itemInfo.isMore()) {
            pickAlbum(itemInfo.getMediaSet());
        } else {
            AlertDialog.OnClickListener onClickListener = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    pickPhoto(itemInfo.getMediaSet(), itemInfo.getMediaItem(), itemInfo.getIndexInOneGroup());
                }
            };

            if (itemInfo.getMediaItem().getMediaType() != MediaObject.MEDIA_TYPE_VIDEO && SomePageUtils.getInstance()
                    .checkPressedIsDrm(mActivity, itemInfo.getMediaItem(), onClickListener, null, null, mGetContent)) {
                mNeedUpdate = true;
                return;
            }
            if (itemInfo.getMediaItem().getMediaType() == MediaObject.MEDIA_TYPE_VIDEO && itemInfo.getMediaItem().mIsDrmFile) {
                mNeedUpdate = true;
            }
            pickPhoto(itemInfo.getMediaSet(), itemInfo.getMediaItem(), itemInfo.getIndexInOneGroup());
        }
    }

    @Override
    public void onItemLongClick(ItemInfo itemInfo) {
        if (!mIsActive) {
            return;
        }
        MediaItem item = itemInfo.getMediaItem();
        if (item == null) {
            return;
        }
        mSelectionManager.setAutoLeaveSelectionMode(false);
        if (!mSelectionManager.toggle(item.getPath())) {
            MediaSet targetSet = itemInfo.getMediaSet();
            mSelectionManager.removeAlbumPath(targetSet.getPath());
        }
        //mSelectionManager.logSelectedItem();
    }

    @Override
    public void onLabelClick(LabelInfo labelInfo, SprdRecyclerPageView.OnLabelClickEventHandledListener l) {
        MediaSet targetSet = labelInfo.getMediaSet();
        if (targetSet == null) {
            return;
        }
        if (mSelectionManager.isLocalAlbum(targetSet.getPath())
                && mSelectionManager.inAlbumSelectionMode()) {
            return;
        }

        Log.d(TAG, "onLabelClick mediaSet = " + targetSet.getName() + ", getMediaItem 0 ~ " + targetSet.getMediaItemCount());
        if (targetSet.getMediaItemCount() < 600) {
            ArrayList<MediaItem> items = targetSet.getMediaItem(0, targetSet.getMediaItemCount());
            Log.d(TAG, "onLabelClick mediaSet = " + targetSet.getName() + " E.");
            if (items == null || items.isEmpty()) {
                return;
            }
            mSelectionManager.toggleAlbum(targetSet.getPath(), items);
        } else {
            SelectAllItemTask task = new SelectAllItemTask(mActivity, targetSet, mSelectionManager, l);
            task.execute();
        }
    }

    private static class SelectAllItemTask extends AsyncTask<Void, Void, ArrayList<MediaItem>> {
        private Context mContext;
        private ProgressDialog mPD;
        private MediaSet mMediaSet;
        private SelectionManager mSelectionManager;
        private SprdRecyclerPageView.OnLabelClickEventHandledListener mListener;

        public SelectAllItemTask(Context context, MediaSet mediaSet, SelectionManager selectionManager
                , SprdRecyclerPageView.OnLabelClickEventHandledListener l) {
            mContext = context;
            mMediaSet = mediaSet;
            mSelectionManager = selectionManager;
            mListener = l;
            mPD = new ProgressDialog(mContext);
            mPD.setMessage(mContext.getString(R.string.dealing));
            mPD.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mPD.show();
        }

        @Override
        protected ArrayList<MediaItem> doInBackground(Void... params) {
            Log.d("SelectAllItemTask", "doInBackground");
            long t = System.currentTimeMillis();
            ArrayList<MediaItem> items = mMediaSet.getMediaItem(0, mMediaSet.getMediaItemCount());
            long d = System.currentTimeMillis() - t;
            if (d < 500) {
                try {
                    Thread.sleep(500 - d);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return items;
        }

        @Override
        protected void onPostExecute(ArrayList<MediaItem> mediaItems) {
            super.onPostExecute(mediaItems);
            Log.d("SelectAllItemTask", "onPostExecute");
            if (mPD != null && mPD.isShowing()) {
                mPD.dismiss();
            }
            if (mediaItems == null || mediaItems.isEmpty()) {
                return;
            }
            mSelectionManager.toggleAlbum(mMediaSet.getPath(), mediaItems);
            if (mListener != null) {
                mListener.onEventHandled();
            }
        }
    }

    @Override
    public void onPickAlbum(LabelInfo labelInfo) {
        pickAlbum(labelInfo.getMediaSet());
    }

    @Override
    public void onFirstLoad() {
        if (mHideAlbumItem == null || mShowOnlyLocalAlbumItem == null || mMediaSet == null || mSelectedAction != FilterUtils.CLUSTER_BY_ALBUM) {
            return;
        }
        if (mMediaSet.getSubMediaSetCount() > 0) {
            mHideAlbumItem.setVisible(true);
            mShowOnlyLocalAlbumItem.setVisible(true);
        }
    }

    private class MyMediaSetDataChangedListener implements SprdAlbumSetDataLoader.MediaSetDataChangedListener {
        @Override
        public void onMediaSetDataChangeStarted() {
            if (mAlbumSetPageView != null) {
                mAlbumSetPageView.onMediaSetDataChangeStarted();
            }
        }

        @Override
        public void onMediaSetDataChanged(AlbumSetData data) {
            if (mAlbumSetPageView != null) {
                mHasNonLocalAlbum = false;
                Path path = data.getMediaSet().getPath();
                if (!mHasNonLocalAlbum && !mSelectionManager.isLocalAlbum(path)) {
                    mHasNonLocalAlbum = true;
                }
                mAlbumSetPageView.onMediaSetDataChanged(data);
            }
        }

        @Override
        public void onMediaSetDataChangeFinished() {
            if (mAlbumSetPageView != null) {
                mAlbumSetPageView.onMediaSetDataChangeFinished();
                if (mHideAlbumItem != null) {
                    if (mSelectionManager.alreadyHaveHideAlbum()) {
                        mHasNonLocalAlbum = true;
                    }
                    //是否勾选了仅显示本地相册
                    boolean isShowOnlyLocalAlbums = mSelectionManager.getLocalAlbumsFlags();
                    //存在非本地相册, 就使能隐藏相册
                    mHideAlbumItem.setEnabled(mHasNonLocalAlbum);
                    if (isShowOnlyLocalAlbums) {
                        //如果勾选了仅显示本地相册, 就禁用隐藏相册
                        mHideAlbumItem.setEnabled(false);
                    }
                }
            }
        }
    }

    private class MyLoadingListener implements AlbumSetLoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(final boolean loadingFailed) {
            Log.d(TAG, "onLoadingFinished loadingFailed=" + loadingFailed);
            if (mAlbumSetDataAdapter.size() == 0) {
                mAlbumSetPageView.clearData();
            }
            clearLoadingBit(BIT_LOADING_RELOAD);
            boolean inSelectionMode = (mSelectionManager != null
                    && mSelectionManager.inSelectionMode());
            int setCount = mMediaSet != null ? mMediaSet.getSubMediaSetCount() : 0;
            mSelectionManager.onSourceContentChanged();
            if (setCount > 0 && mSelectedAction == FilterUtils.CLUSTER_BY_ALBUM) {
                if (mHideAlbumItem != null) {
                    mHideAlbumItem.setVisible(true);
                }
                if (mShowOnlyLocalAlbumItem != null) {
                    mShowOnlyLocalAlbumItem.setVisible(true);
                }
            } else if (setCount == 0 && !mSelectionManager.getLocalAlbumsFlags() && !mSelectionManager.isAlbumHided()) {
                if (mHideAlbumItem != null) {
                    mHideAlbumItem.setVisible(false);
                }
                if (mShowOnlyLocalAlbumItem != null) {
                    mShowOnlyLocalAlbumItem.setVisible(false);
                }
            }
            if (setCount > 0 && inSelectionMode) {
                mActionModeHandler.updateSupportedOperation();
                mActionModeHandler.updateSelectionMenu();
            }
        }

        @Override
        public void onLoadingWill() {
            hideEmptyAlbumToast();
            hideCameraButton();
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;
        private MediaItem mItem;

        @Override
        public int size() {
            return mAlbumSetDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mItem = (MediaItem) mActivity.getDataManager().getMediaObject(id);
            if (mItem == null || mItem.getPath() != id) {
                mIndex = -1;
            }
            mIndex = lastSelectAlbumIndex;
            Log.d(TAG, "MyDetailsSource setIndex mIndex = " + mIndex + " id = " + id + " mItem = " + mItem);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mItem;
            if (item != null) {
                // mAlbumSetView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

//    private void pickPhoto(int albumIndex, int photoIndex) {
//        if (!mIsActive) return;
//
//        /* SPRD: fix bug 399240,IllegalArgumentException in monkey test @{ */
//        MediaSet targetSet;
//        try {
//            targetSet = mAlbumSetDataAdapter.getMediaSet(albumIndex);
//        } catch (Exception e) {
//            Log.d(TAG, "exception when pick albume");
//            targetSet = null;
//        }
//        /* @} */
//        Log.d(TAG, "pickPhoto " + (targetSet == null ? "targetSet==null" : "targetSet" +
//                ".getTotalMediaItemCount()=" + targetSet.getTotalMediaItemCount()));
//        if (targetSet == null) return; // Content is dirty, we shall reload soon
//        if (targetSet.getTotalMediaItemCount() == 0) {
//            showEmptyAlbumToast(Toast.LENGTH_SHORT);
//            return;
//        }
//        hideEmptyAlbumToast();
//
//        String mediaSetPath = targetSet.getPath().toString();
//        Log.d(TAG, "pickPhoto mediaSetPath=" + mediaSetPath);
//
//        boolean startInFilmstrip = false;
//        ArrayList<MediaItem> items = targetSet.getMediaItem(photoIndex, 1);
//        if (items.size() > 0) {
//            MediaItem item = (MediaItem) items.get(0);
//            Log.d(TAG, "pickPhoto item=" + item);
//            Bundle data = new Bundle(getData());
//            data.putInt(PhotoPage.KEY_INDEX_HINT, photoIndex);
////            data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
////                    mSlotView.getSlotRect(albumIndex, mRootPane));
//            data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
//                    mediaSetPath.toString());
//            Log.d(TAG, "pickPhoto item.getPath()=" + item.getPath().toString());
//            data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
//                    item.getPath().toString());
//            data.putInt(PhotoPage.KEY_ALBUMPAGE_TRANSITION,
//                    PhotoPage.MSG_ALBUMPAGE_STARTED);
//            data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP,
//                    startInFilmstrip);
//            data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, mMediaSet.isCameraRoll());
//            /* SPRD: Modify for bug569701, do not support edit if read_only is true @ */
//            data.putBoolean(PhotoPage.KEY_READONLY,
//                    getData().getBoolean(PhotoPage.KEY_READONLY, false));
//            /* @} */
//            if (startInFilmstrip) {
//                mActivity.getStateManager().switchState(this, FilmstripPage.class, data);
//            } else {
//                mActivity.getStateManager().startStateForResult(
//                        SinglePhotoPage.class, SprdAlbumPage.REQUEST_PHOTO, data);
//            }
//        } else {
//            Log.d(TAG, "The number of media items is 0");
//            return;
//        }
//    }


//    public void onSingleTapUp(int albumIndex, int photoIndex) {
//        if (!mIsActive) return;
//
//        if (mSelectionManager.inSelectionMode()||mSelectionManager.inAlbumSelectionMode()) {
//            MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(albumIndex);
//            if (targetSet == null) return; // Content is dirty, we shall reload soon
//            if (photoIndex == -1) {
//                //select whole album
//                ArrayList<MediaItem> items = targetSet.getMediaItem(0, targetSet
//                        .getMediaItemCount());
//                if (items == null || items.isEmpty()) return;
//                if(mSelectionManager.isLocalAlbum(targetSet.getPath())
//                        &&mSelectionManager.inAlbumSelectionMode()) return;
//                mSelectionManager.toggleAlbum(targetSet.getPath(), items);
//            } else {
//                if(mSelectionManager.inAlbumSelectionMode())return;
//                //select a single photo
//                ArrayList<MediaItem> items = targetSet.getMediaItem(photoIndex, 1);
//                if (items == null || items.isEmpty()) return;
//                MediaItem item = items.get(0);
//                if (item == null) return;
//                if (isMoreItem(albumIndex, photoIndex)) {
//                    mSelectionManager.leaveSelectionMode();
//                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, albumIndex, 0)
//                            , 0);
//                    return;
//                }
//                boolean result = mSelectionManager.toggle(item.getPath());
//                if (!result) {
//                    //unselect photo
//                    mSelectionManager.removeAlbumPath(targetSet.getPath());
//                }
//            }
//            mSlotView.invalidate();
//        } else {
//            if(mSelectionManager.inAlbumSelectionMode()) return;
//            // Show pressed-up animation for the single-tap.
//            mAlbumSetView.setPressedIndex(albumIndex);
//            mAlbumSetView.setPressedUp();
//            /*mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0),
//                    FadeTexture.DURATION);*/
//            Log.d(TAG, "onSingleTapUp send MSG_PICK_PHOTO albumIndex=" + albumIndex + ", " +
//                    "photoIndex=" + photoIndex);
//            if (isMoreItem(albumIndex, photoIndex)) {
//                /* SPRD: Modify for bug598638, send Message without 180ms delay for improving performance @{
//                 * original code:
//            	mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, albumIndex, 0),
//                        FadeTexture.DURATION);
//                */
//                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, albumIndex, 0), 0);
//                /* @} */
//            } else {
//                /* SPRD: Modify for bug598638, send Message without 180ms delay for improving performance @{
//                 * original code:
//                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_PHOTO, albumIndex, photoIndex),
//                        FadeTexture.DURATION);
//                */
//                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_PHOTO, albumIndex, photoIndex), 0);
//                /* @} */
//            }
//        }
//    }

//    public void onLongTap(int albumIndex, int photoIndex) {
//        if (!mIsActive) return;
//        lastSelectAlbumIndex = albumIndex;
//        lastSelectPhotoIndex = photoIndex;
//        MediaSet set = mAlbumSetDataAdapter.getMediaSet(albumIndex);
//        if (set == null) return;
//        MediaItem item = set.getMediaItem(photoIndex, 1).get(0);
//        if (item == null) return;
//        mSelectionManager.setAutoLeaveSelectionMode(false);
//        mSelectionManager.toggle(item.getPath());
//        mSlotView.invalidate();
//    }

//    private boolean isMoreItem(int albumIndex, int photoIndex) {
//
//        MediaSet targetAlbum;
//        try {
//            targetAlbum = mAlbumSetDataAdapter.getMediaSet(albumIndex);
//        } catch (Exception e) {
//            return false;
//        }
//        if (targetAlbum == null) {
//            return false;
//        }
//
//        boolean isMore = false;
//        int maxThumbCount = mSlotView.getSlotViewSpec().maxThumbImageCount;
//        if (targetAlbum.getMediaItemCount() > maxThumbCount && photoIndex == (maxThumbCount - 1)) {
//            isMore = true;
//        } else {
//            isMore = false;
//        }
//        return isMore;
//    }

    private void saveAlbumsData() {
        mActionModeHandler.setEditAlbumFlag(false);
        mSelectionManager.setSelectAlbumItems();
        mSelectionManager.setEditAblumFlags(false);
        mSelectionManager.leaveAlbumSelectionMode();
//        mAlbumSetView.mIsAlbumItemCountLoaded = false;
        mAlbumSetDataAdapter.reloadData();
//        mSlotView.invalidate();
    }

    private void showAlbumMenuPop() {
        View contentView = View.inflate(mActivity, R.layout.popup_hide_albums, null);
        final CheckBox checkBox = contentView.findViewById(R.id.select_local_albums);
        checkBox.setVisibility(View.VISIBLE);
        final TextView editAlbums = contentView.findViewById(R.id.edit_albums);
        boolean localAlbum = mSelectionManager.getLocalAlbumsFlags();
        checkBox.setChecked(localAlbum);
        checkBox.setEnabled(false);
        checkBox.setClickable(false);
        if (localAlbum) {
            editAlbums.setEnabled(false);
            editAlbums.setClickable(false);
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.edit_albums:
                        mActionModeHandler.setEditAlbumFlag(true);
                        mSelectionManager.enterAlbumSelectionMode();
                        //  setupEditAlbumControls();
                        mSelectionManager.setEditAblumFlags(true);
//                        mAlbumSetView.mIsAlbumItemCountLoaded = false;
                        mAlbumSetDataAdapter.reloadData();
//                        mSlotView.invalidate();
                        mAlbumSetPageView.enterAlbumSelectionMode();
                        dismissMenuPop();
                        break;
                    case R.id.show_local_albums:
                        if (checkBox.isChecked()) {
                            checkBox.setChecked(false);
                            editAlbums.setEnabled(true);
                            editAlbums.setClickable(true);
                            mSelectionManager.setLocalAlbumsFlags(false);
                        } else {
                            checkBox.setChecked(true);
                            mSelectionManager.setLocalAlbumsFlags(true);
                            editAlbums.setEnabled(false);
                            editAlbums.setClickable(false);
                        }
                        mAlbumSetDataAdapter.reloadData();
//                        mSlotView.invalidate();
                        break;
                    default:
                        break;
                }
            }
        };
        contentView.findViewById(R.id.edit_albums).setOnClickListener(listener);
        contentView.findViewById(R.id.show_local_albums).setOnClickListener(listener);
        contentView.findViewById(R.id.select_local_albums).setOnClickListener(listener);

        mPopupWindow = new PopupWindow(contentView, LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, true);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.showAtLocation(contentView, Gravity.RIGHT | Gravity.TOP, 0, 0);
    }

    private void dismissMenuPop() {
        if (mPopupWindow != null) {
            mPopupWindow.dismiss();
        }
    }

    @Override
    protected void onSideBarClicked(String key) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onSideBarClicked key = " + key);
        boolean mNeedForceDirty = false;
        mAlbumSetPageView.setCluster(key);

        // OTG
        HashMap<String, String> otgDeviceInfos = mActivity.getDataManager().getOtgDeviceInfos();
        if (otgDeviceInfos != null && otgDeviceInfos.containsKey(key) && (!key.equals(mActivity.getDataManager().getOtgDeviceCurrentPath()))) {
            mActivity.getDataManager().setOtgDeviceCurrentPath(key);
            key = SideBarItem.ALBUM;
            mNeedForceDirty = true;
        } else if (null != mActivity.getDataManager().getOtgDeviceCurrentPath()) {
            mActivity.getDataManager().setOtgDeviceCurrentPath(null);
            mAlbumSetDataAdapter.reloadData();
        }

        if (SideBarItem.ALL.equals(key)) {// All
            doCluster(FilterUtils.CLUSTER_BY_ALL, mNeedForceDirty);
        } else if (SideBarItem.PHOTO.equals(key)) {// Photo
            doCluster(FilterUtils.CLUSTER_BY_PHOTO, mNeedForceDirty);
        } else if (SideBarItem.VIDEO.equals(key)) {// Video
            doCluster(FilterUtils.CLUSTER_BY_VIDEO, mNeedForceDirty);
        } else if (SideBarItem.ALBUM.equals(key)) {// Album
            doCluster(FilterUtils.CLUSTER_BY_ALBUM, mNeedForceDirty);
        }
    }

    public void doCluster(int clusterType, boolean forceDirty) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(KEY_SELECTED_CLUSTER_TYPE, clusterType);
        data.putBoolean(KEY_SET_FORCE_DIRTY, forceDirty);
        mActivity.getStateManager().switchState(this, SprdAlbumSetPage.class, data);
    }

    private void pickPhoto(MediaSet album, MediaItem item, int photoIndex) {
        Log.d(TAG, "pickPhoto album is " + (album != null ? album.getName() : null) + ", item is "
                + item.getFilePath() + ", mIsActive = " + mIsActive);
        if (album == null || !mIsActive) {
            return;
        }
        if (album.getTotalMediaItemCount() == 0) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
            return;
        }
        hideEmptyAlbumToast();

        if (mGetContent) {
            onGetContent(item);
        } else {
            String mediaSetPath = album.getPath().toString();

            boolean startInFilmstrip = false;
            Bundle data = new Bundle(getData());
            data.putInt(PhotoPage.KEY_INDEX_HINT, photoIndex);
//            data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
//                    mSlotView.getSlotRect(albumIndex, mRootPane));
            data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                    mediaSetPath.toString());
            data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
                    item.getPath().toString());
            data.putInt(PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_STARTED);
            data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP,
                    startInFilmstrip);
            data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, mMediaSet.isCameraRoll());
            data.putBoolean(PhotoPage.KEY_READONLY,
                    getData().getBoolean(PhotoPage.KEY_READONLY, false));
            data.putBoolean(PhotoPage.KEY_START_FROM_WIDGET, false);
            if (getData().getBoolean(PhotoPage.KEY_CAMERA_ALBUM, false)) {
                data.remove(PhotoPage.KEY_CAMERA_ALBUM);
            }
            if (startInFilmstrip) {
                mActivity.getStateManager().switchState(this, FilmstripPage.class, data);
            } else {
                mActivity.getStateManager().startStateForResult(
                        SinglePhotoPage.class, SprdAlbumPage.REQUEST_PHOTO, data);
            }
        }
    }

    private void pickAlbum(MediaSet targetAlbum) {
        if (!mIsActive) {
            return;
        }

        if (targetAlbum == null) {
            return;
        }
        if (targetAlbum.getTotalMediaItemCount() == 0) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
            return;
        }
        hideEmptyAlbumToast();

        String mediaPath = targetAlbum.getPath().toString();

        Bundle data = new Bundle(getData());
        int[] center = new int[2];
//        getSlotCenter(slotIndex, center);
//        data.putIntArray(SprdAlbumPage.KEY_SET_CENTER, center);
        if (mGetAlbum && targetAlbum.isLeafAlbum()) {
            Activity activity = mActivity;
            Intent result = new Intent()
                    .putExtra(AlbumPicker.KEY_ALBUM_PATH, targetAlbum.getPath().toString());
            activity.setResult(Activity.RESULT_OK, result);
            activity.finish();
        } else if (targetAlbum.getSubMediaSetCount() > 0) {
            data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, mediaPath);
            mActivity.getStateManager().startStateForResult(
                    SprdAlbumSetPage.class, REQUEST_DO_ANIMATION, data);
        } else {
            if (!mGetContent && albumShouldOpenInFilmstrip(targetAlbum)) {
//                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
//                        mSlotView.getSlotRect(slotIndex, mRootPane));
                data.putInt(PhotoPage.KEY_INDEX_HINT, 0);
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                        mediaPath);
                // SPRD: fix bug 389187,can't enter gallery
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH,
                        targetAlbum.getCoverMediaItem().getPath().toString());
                data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, true);
                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetAlbum.isCameraRoll());
                /* SPRD: Modify for bug569701, do not support edit if read_only is true @ */
                data.putBoolean(PhotoPage.KEY_READONLY,
                        getData().getBoolean(PhotoPage.KEY_READONLY, false));
                /* @} */
                mActivity.getStateManager().startStateForResult(
                        FilmstripPage.class, SprdAlbumPage.REQUEST_PHOTO, data);
                return;
            }
            data.putString(SprdAlbumPage.KEY_MEDIA_PATH, mediaPath);

            // We only show cluster menu in the first SprdAlbumPage in stack
            boolean inAlbum = mActivity.getStateManager().hasStateClass(SprdAlbumPage.class);
            data.putBoolean(SprdAlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum);
            mActivity.getStateManager().startStateForResult(
                    SprdAlbumPage.class, REQUEST_DO_ANIMATION, data);
        }
    }

    @Override
    protected void onSideBarItemChanged(SideBarItem item) {
        if (SideBarItem.VIDEO.equals(item.getKey())) {
            mCurrentNoPhotoTitle = R.string.side_bar_video;
        } else {
            mCurrentNoPhotoTitle = R.string.side_bar_photo;
        }
        if (mHideAlbumItem == null || mShowOnlyLocalAlbumItem == null) {
            return;
        }
        if (mSelectedAction == FilterUtils.CLUSTER_BY_ALBUM && (mMediaSet != null
                && (mMediaSet.getSubMediaSetCount() > 0
                || mSelectionManager.getLocalAlbumsFlags() || mSelectionManager.isAlbumHided()))) {
            mHideAlbumItem.setVisible(true);
            mShowOnlyLocalAlbumItem.setVisible(true);
        } else {
            mHideAlbumItem.setVisible(false);
            mShowOnlyLocalAlbumItem.setVisible(false);
        }
    }

    private void onGetContent(final MediaItem item) {
        DataManager dm = mActivity.getDataManager();
        Activity activity = mActivity;
        if (!item.isValidMimeType()) {
            mToast = ToastUtil.showMessage(activity, mToast,
                    R.string.cant_select_file, Toast.LENGTH_SHORT);
            return;
        }
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

    /* SPRD: Drm feature start @{ */
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

    @Override
    protected boolean dispatchMenuKeyEvent(KeyEvent event) {
        return !(mAlbumSetPageView.inAlbumSelectionMode() || mAlbumSetPageView.inItemSelectionMode());
    }
}

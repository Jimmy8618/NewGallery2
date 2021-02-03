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
import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ComboAlbum;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.FilterDeleteSet;
import com.android.gallery3d.data.FilterSource;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.SecureAlbum;
import com.android.gallery3d.data.SecureSource;
import com.android.gallery3d.data.SnailAlbum;
import com.android.gallery3d.data.SnailItem;
import com.android.gallery3d.data.SnailSource;
import com.android.gallery3d.data.UriImage;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.msg.MimeTypeMsg;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoControlBottomBar;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.util.UsageStatistics;
import com.android.gallery3d.v2.data.GLVideoStateMsg;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.gallery3d.blending.ReplaceActivity;
import com.sprd.gallery3d.burstphoto.BurstActivity;
import com.sprd.gallery3d.drm.MenuExecutorUtils;
import com.sprd.gallery3d.drm.PhotoPageUtils;
import com.sprd.gallery3d.drm.SomePageUtils;
import com.sprd.gallery3d.refocusimage.BokehSaveManager;
import com.sprd.gallery3d.refocusimage.RefocusEditActivity;
import com.sprd.refocus.RefocusUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class PhotoPage extends ActivityState implements
        PhotoView.Listener, AppBridge.Server, ShareActionProvider.OnShareTargetSelectedListener,
        PhotoPageBottomControls.Delegate, GalleryActionBar.OnAlbumModeSelectedListener, ActionBarTopControls.ActionBarListener,
        PhotoControlBottomBar.OnPhotoControlBottomBarMenuClickListener, PhotoVoiceProgress.TimeListener, BokehSaveManager.BokehSaveCallBack,
        UriImage.OnUriImageListener {
    private static final String TAG = "PhotoPage";

    private static final int MSG_HIDE_BARS = 1;
    private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
    private static final int MSG_UPDATE_ACTION_BAR = 5;
    private static final int MSG_UNFREEZE_GLROOT = 6;
    private static final int MSG_WANT_BARS = 7;
    private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
    private static final int MSG_ON_CAMERA_CENTER = 9;
    private static final int MSG_ON_PICTURE_CENTER = 10;
    private static final int MSG_REFRESH_IMAGE = 11;
    private static final int MSG_UPDATE_PHOTO_UI = 12;
    private static final int MSG_UPDATE_DEFERRED = 14;
    private static final int MSG_UPDATE_SHARE_URI = 15;
    private static final int MSG_UPDATE_PANORAMA_UI = 16;
    private static final int MSG_STORAGE_CHANGED = 17;
    // SPRD: bug 624616 ,Slide to DRM image, should Consume authority
    private static final int MSG_FINISH_STATE = 18;
    public static final int MSG_CONSUME_DRM_RIGHTS = 19;
    private static final int MSG_UPDATE_OPERATIONS = 20;
    private static final int MSG_UPDATE_THUMB_FILE_FLAG = 21;
    private static final int MSG_BOKEH_SAVE_DONE = 22;
    private static final int MSG_BOKEH_PICTURE_DONE = 23;
    private static final int MSG_BOKEH_SAVE_ERROR = 24;

    private static final int HIDE_BARS_TIMEOUT = 3500;
    private static final int UNFREEZE_GLROOT_TIMEOUT = 250;
    private static final int THUMB_WAIT_TIME_OUT = 8000;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_CROP = 2;
    private static final int REQUEST_CROP_PICASA = 3;
    private static final int REQUEST_EDIT = 4;
    private static final int REQUEST_PLAY_VIDEO = 5;
    private static final int REQUEST_TRIM = 6;
    private static final int REQUEST_EDIT_REFOCUS = 10;
    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private static final int REQUEST_SLIDESHOW_MUSIC = 7;
    private static final int REQUEST_SLIDESHOW_RINGTONE = 8;
    private static final int REQUEST_BLENDING = 9;
    public static final int NONE_MUSIC = 0;
    public static final int SELECT_MUSIC = 1;
    public static final int USER_DEFINED_MUSIC = 2;
    private int mPos;
    private int mUserSelected = 0;
    /* @} */
    private static final String ACTION_REFOCUS_EDIT = "com.android.sprd.gallery3d.refocusedit";
    private static final String ACTION_DISTANCE_EDIT = "com.android.sprd.gallery3d.distance";
    private static final String ACTION_IMAGEBLENDING_EDIT = "com.android.sprd.gallery3d.imageblending";

    public static final String KEY_START_FROM_WIDGET = "start-from-widget";
    public static final String KEY_MEDIA_SET_PATH = "media-set-path";
    public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
    public static final String KEY_INDEX_HINT = "index-hint";
    public static final String KEY_OPEN_ANIMATION_RECT = "open-animation-rect";
    public static final String KEY_APP_BRIDGE = "app-bridge";
    public static final String KEY_TREAT_BACK_AS_UP = "treat-back-as-up";
    public static final String KEY_START_IN_FILMSTRIP = "start-in-filmstrip";
    public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";
    public static final String KEY_SHOW_WHEN_LOCKED = "show_when_locked";
    public static final String KEY_IN_CAMERA_ROLL = "in_camera_roll";
    public static final String KEY_READONLY = "read-only";
    public static final String SINGLE_ITEM_ONLY = "SingleItemOnly";
    public static final String CURRENT_ITEM_THUMBNAIL = "current_item_thumbnail";


    public static final String KEY_ALBUMPAGE_TRANSITION = "albumpage-transition";
    public static final int MSG_ALBUMPAGE_NONE = 0;
    public static final int MSG_ALBUMPAGE_STARTED = 1;
    public static final int MSG_ALBUMPAGE_RESUMED = 2;
    public static final int MSG_ALBUMPAGE_PICKED = 4;

    public static final String ACTION_NEXTGEN_EDIT = "action_nextgen_edit";
    public static final String ACTION_SIMPLE_EDIT = "action_simple_edit";

    public static final String KEY_CAMERA_ALBUM = "camera_album";
    public static final String KEY_SECURE_CAMERA = "secure_camera";
    public static final String KEY_IS_SECURE_CAMERA = "isSecureCamera";
    public static final String KEY_SECURE_CAMERA_ENTER_TIME = "secure_camera_enter_time";

    private GalleryApp mApplication;
    private SelectionManager mSelectionManager;

    private PhotoView mPhotoView;
    private PhotoPage.Model mModel;
    private DetailsHelper mDetailsHelper;
    private boolean mShowDetails;

    private Toolbar mToolbar;
    private Toolbar mToolbarnew;
    private final Animation mToolbarnewAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mToolbarnewAnimOut = new AlphaAnimation(1f, 0f);

    private PhotoControlBottomBar mPhotoControlBottomBar;
    private final Animation mPhotoControlBottomBarAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mPhotoControlBottomBarAnimOut = new AlphaAnimation(1f, 0f);

    /**
     * SPRD:Bug474615 Playback loop mode @{
     */
    private static final String FLAG_GALLERY = "startByGallery";
    /**
     * @}
     */
    // mMediaSet could be null if there is no KEY_MEDIA_SET_PATH supplied.
    // E.g., viewing a photo in gmail attachment
    private FilterDeleteSet mMediaSet;

    // The mediaset used by camera launched from secure lock screen.
    private SecureAlbum mSecureAlbum;

    private int mCurrentIndex = 0;
    private Handler mHandler;
    private boolean mShowBars = true;
    private volatile boolean mActionBarAllowed = true;
    private GalleryActionBar mActionBar;
    private boolean mIsMenuVisible;
    private boolean mHaveImageEditor;
    private PhotoPageBottomControls mBottomControls;
    //actionbar
    private ActionBarTopControls mTopControls;
    private MediaItem mCurrentPhoto = null;
    private MenuExecutor mMenuExecutor;
    //    private boolean mIsActive;
    private boolean mShowSpinner;
    private String mSetPathString;
    // This is the original mSetPathString before adding the camera preview item.
    private boolean mReadOnlyView = false;
    private boolean mSecureCamera = false;
    private boolean mCameraAlbum = false;
    private boolean mStartFromWidget = false;
    private long mSecureCameraEnterTime = -1L;
    private String mOriginalSetPathString;
    private AppBridge mAppBridge;
    private SnailItem mScreenNailItem;
    private SnailAlbum mScreenNailSet;
    private OrientationManager mOrientationManager;
    private boolean mTreatBackAsUp;
    private boolean mSingleItemOnly;
    private boolean mStartInFilmstrip;
    private boolean mHasCameraScreennailOrPlaceholder = false;
    private boolean mRecenterCameraOnResume = true;
    private boolean mDrmFirstOpen;

    // These are only valid after the panorama callback
    private boolean mIsPanorama;
    private boolean mIsPanorama360;

    private long mCameraSwitchCutoff = 0;
    private boolean mSkipUpdateCurrentPhoto = false;
    private static final long CAMERA_SWITCH_CUTOFF_THRESHOLD_MS = 300;

    private static final long DEFERRED_UPDATE_MS = 250;
    private boolean mDeferredUpdateWaiting = false;
    private long mDeferUpdateUntil = Long.MAX_VALUE;

    // The item that is deleted (but it can still be undeleted before commiting)
    private Path mDeletePath;
    private boolean mDeleteIsFocus;  // whether the deleted item was in focus

    private final Uri[] mNfcPushUris = new Uri[1];

    private final MyMenuVisibilityListener mMenuVisibilityListener =
            new MyMenuVisibilityListener();

    private int mLastSystemUiVis = 0;
    private PhotoVoiceProgress mPhotoVoiceProgress;
    private AudioManager mAudioManager;

    //popup
    private PopupWindow mPopupWindow;
    private PopupWindow mSlideMusicWindow;

    private boolean mIsFlip = false;
    private boolean mSelectVolueUp = false;
    private boolean mSelectVolueDown = false;
    private SharedPreferences mPref;

    private SprdCameraUtil mCameraUtil;
    private boolean mhasImage = true;
    private BokehSaveManager mBokehSaveManager;
    private long[] mSecureItemIds;
    private ProgressBar mProgressBar;
    private Toast mToast;
    private boolean mSupportBlur;
    private boolean mSupportBokeh;

    private static boolean isGmsVersion = GalleryUtils.isSprdPhotoEdit();
    private boolean mPlayFlag = true;

    /*
     * SPRD: bug 474655 Trim and mute video is supported or not. default open.
     * old bug info:skip trim and mute menu item for bug 273733
     * @{
     */
    private final boolean mTrimvideoEnable = System.getProperty("ro.config.trimvideo", "disable") == "enable";
    /* @} */
    private final PanoramaSupportCallback mUpdatePanoramaMenuItemsCallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_PANORAMA_UI, isPanorama360 ? 1 : 0, 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final PanoramaSupportCallback mRefreshBottomControlsCallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, isPanorama ? 1 : 0, isPanorama360 ? 1 : 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final PanoramaSupportCallback mUpdateShareURICallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_SHARE_URI, isPanorama360 ? 1 : 0, 0, mediaObject)
                        .sendToTarget();
            }
        }
    };

    @Override
    public void onUriImageInitialized() {
        Log.d(TAG, "onUriImageInitialized");
        if (mModel.isRefocusNoBokeh(0)) {
            mPhotoView.setScaleState(false);
        } else {
            mPhotoView.setScaleState(true);
        }
        refreshBottomControlsWhenReady();
    }

    /* usb storage changed */
    public interface Model extends PhotoView.Model {
        void resume();

        void pause();

        boolean isEmpty();

        void setCurrentPhoto(Path path, int indexHint);
    }

    private class MyMenuVisibilityListener implements OnMenuVisibilityListener {
        @Override
        public void onMenuVisibilityChanged(boolean isVisible) {
            mIsMenuVisible = isVisible;
            refreshHidingMessage();
        }
    }

    @Override
    protected int getBackgroundColorId() {
        return R.color.photo_background;
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
            if (mShowDetails) {
                mDetailsHelper.layout(left, mActionBar.getHeight(), right, bottom);
            }
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        GalleryUtils.start(this.getClass(), "onCreate");
        super.onCreate(data, restoreState);
        mSupportBlur = RefocusUtils.isSupportBlur();
        mSupportBokeh = RefocusUtils.isSupportBokeh();
        mPhotoControlBottomBar = mActivity.findViewById(R.id.photo_control_bottom_bar);
        mPhotoControlBottomBar.setOnPhotoControlBottomBarMenuClickListener(this);
        mProgressBar = mActivity.findViewById(R.id.thumb_loading);
//        mDrmFirstOpen = true;
        mActionBar = mActivity.getGalleryActionBar();
        mSelectionManager = new SelectionManager(mActivity, true);
        mMenuExecutor = new MenuExecutor(mActivity, mActivity.getGLRoot(), mSelectionManager);
        mUserSelected = GalleryUtils.getSelected(mActivity.getAndroidContext());
        Log.i(TAG, "onCreate " + this);

        // Add for bug535110 new feature,  support play audio picture
        mAudioManager = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        mPhotoView = new PhotoView(mActivity, null, mActivity.getGLRoot());
        mPhotoView.setListener(this);
        mPhotoView.setActionBar(mActionBar);
        mRootPane.addComponent(mPhotoView);

        mApplication = (GalleryApp) mActivity.getApplication();
        mBokehSaveManager = BokehSaveManager.getInstance();
        mOrientationManager = mActivity.getOrientationManager();
        mActivity.getGLRoot().setOrientationSource(mOrientationManager);
        /* SPRD: Drm feature start @{ */
        PhotoPageUtils.getInstance().getFirstPickIsDrmPhoto();
        /* SPRD: Drm feature end @} */
        mHandler = new MySynchronizedHandler(mActivity.getGLRoot(), this);
        mCameraUtil = new SprdCameraUtil(mActivity, mHandler, mPhotoView);
        mSetPathString = data.getString(KEY_MEDIA_SET_PATH);
        mReadOnlyView = data.getBoolean(KEY_READONLY);
        mSecureCamera = data.getBoolean(KEY_SECURE_CAMERA, false);
        mCameraAlbum = data.getBoolean(KEY_CAMERA_ALBUM, false);
        mStartFromWidget = data.getBoolean(KEY_START_FROM_WIDGET, false);
        if (mSecureCamera) {
            mSecureCameraEnterTime = data.getLong(KEY_SECURE_CAMERA_ENTER_TIME);
            Log.d(TAG, "onCreate mSecureCameraEnterTime = " + mSecureCameraEnterTime);
        }
        mOriginalSetPathString = mSetPathString;
//        setupNfcBeamPush();
        String itemPathString = data.getString(KEY_MEDIA_ITEM_PATH);
        Path itemPath = itemPathString != null ?
                Path.fromString(data.getString(KEY_MEDIA_ITEM_PATH)) :
                null;
        mTreatBackAsUp = data.getBoolean(KEY_TREAT_BACK_AS_UP, false);
        mSingleItemOnly = data.getBoolean(SINGLE_ITEM_ONLY, false);
        mStartInFilmstrip = data.getBoolean(KEY_START_IN_FILMSTRIP, false);
        boolean inCameraRoll = data.getBoolean(KEY_IN_CAMERA_ROLL, false);
        mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);

        mToolbar = mActivity.findViewById(R.id.toolbar);
        mToolbarnew = mActivity.findViewById(R.id.toolbar2);
        mToolbarnew.setVisibility(View.VISIBLE);
        mActivity.setActionBar(mToolbarnew);
        mToolbar.setVisibility(View.GONE);
        if (mSetPathString != null) {
            if (mCameraAlbum) {
                if (mSecureCamera) {
                    // secure camera mode, hide icon
                    mToolbarnew.setNavigationIcon(null);
                    mToolbarnew.setNavigationContentDescription(null);
                } else {
                    mToolbarnew.setNavigationIcon(R.drawable.ic_gallery_white);
                    mToolbarnew.setNavigationContentDescription(R.string.app_name);
                }
            } else {
                mToolbarnew.setNavigationIcon(R.drawable.ic_back_white);
                mToolbarnew.setNavigationContentDescription(null);
            }
        }
        if (isGmsVersion) {
            mToolbarnew.setNavigationIcon(R.drawable.ic_back_white);
        }
        mToolbarnew.setTitle("");
        mToolbarnew.setTitleTextColor(Color.TRANSPARENT);

        if (mSetPathString != null) {
            mShowSpinner = true;
            mAppBridge = data.getParcelable(KEY_APP_BRIDGE);
            if (mAppBridge != null) {
                mShowBars = false;
                mHasCameraScreennailOrPlaceholder = true;
                mAppBridge.setServer(this);

                // Get the ScreenNail from AppBridge and register it.
                int id = SnailSource.newId();
                Path screenNailSetPath = SnailSource.getSetPath(id);
                Path screenNailItemPath = SnailSource.getItemPath(id);
                mScreenNailSet = (SnailAlbum) mActivity.getDataManager()
                        .getMediaObject(screenNailSetPath);
                mScreenNailItem = (SnailItem) mActivity.getDataManager()
                        .getMediaObject(screenNailItemPath);
                mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

                if (data.getBoolean(KEY_SHOW_WHEN_LOCKED, false)) {
                    // Set the flag to be on top of the lock screen.
                    mFlags |= FLAG_SHOW_WHEN_LOCKED;
                }

                // Don't display "empty album" action item for capture intents.
                if (!mSetPathString.equals("/local/all/0")) {
                    // Check if the path is a secure album.
                    if (SecureSource.isSecurePath(mSetPathString)) {
                        mSecureAlbum = (SecureAlbum) mActivity.getDataManager()
                                .getMediaSet(mSetPathString);
                        mShowSpinner = false;
                    }
                    mSetPathString = "/filter/empty/{" + mSetPathString + "}";
                }

                // Combine the original MediaSet with the one for ScreenNail
                // from AppBridge.
                mSetPathString = "/combo/item/{" + screenNailSetPath +
                        "," + mSetPathString + "}";

                // Start from the screen nail.
                itemPath = screenNailItemPath;
            } else if (inCameraRoll && GalleryUtils.isCameraAvailable(mActivity)) {
                mSetPathString = "/combo/item/{" + FilterSource.FILTER_CAMERA_SHORTCUT +
                        "," + mSetPathString + "}";
                mCurrentIndex++;
                mHasCameraScreennailOrPlaceholder = true;
            }

            MediaSet originalSet = mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mHasCameraScreennailOrPlaceholder && originalSet instanceof ComboAlbum) {
                // Use the name of the camera album rather than the default
                // ComboAlbum behavior
                ((ComboAlbum) originalSet).useNameOfChild(1);
            }
            mSelectionManager.setSourceMediaSet(originalSet);
            mSetPathString = "/filter/delete/{" + mSetPathString + "}";
            mMediaSet = (FilterDeleteSet) mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mMediaSet == null) {
                Log.w(TAG, "failed to restore " + mSetPathString);
                return;
            }
            if (mSecureCamera) {
                mSecureItemIds = data.getLongArray(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS);
                mMediaSet.addSecureItems(mSecureItemIds);
            }
            if (itemPath == null) {
                int mediaItemCount = mMediaSet.getMediaItemCount();
                if (mediaItemCount > 0) {
                    if (mCurrentIndex >= mediaItemCount) {
                        mCurrentIndex = 0;
                    }
                    itemPath = mMediaSet.getMediaItem(mCurrentIndex, 1)
                            .get(0).getPath();
                } else {
                    // Bail out, PhotoPage can't load on an empty album
                    return;
                }
            }

            MediaItem item = (MediaItem) mActivity.getDataManager().getMediaObject(itemPath);
            mDrmFirstOpen = item != null && item.mIsDrmFile;

            PhotoDataAdapter pda = new PhotoDataAdapter(
                    mActivity, mPhotoView, mMediaSet, itemPath, mCurrentIndex,
                    mAppBridge == null ? -1 : 0,
                    mAppBridge != null && mAppBridge.isPanorama(),
                    mAppBridge != null && mAppBridge.isStaticCamera(),
                    mSecureCameraEnterTime);
            mModel = pda;
            mPhotoView.setModel(mModel);

            pda.setDataListener(new PhotoDataAdapter.DataListener() {

                @Override
                public void onPhotoChanged(int index, Path item) {
                    int oldIndex = mCurrentIndex;
                    mCurrentIndex = index;

                    if (mHasCameraScreennailOrPlaceholder) {
                        if (mCurrentIndex > 0) {
                            mSkipUpdateCurrentPhoto = false;
                        }

                        if (oldIndex == 0 && mCurrentIndex > 0
                                && !mPhotoView.getFilmMode()) {
                            mPhotoView.setFilmMode(true);
                            if (mAppBridge != null) {
                                UsageStatistics.onEvent("CameraToFilmstrip",
                                        UsageStatistics.TRANSITION_SWIPE, null);
                            }
                        } else if (oldIndex == 2 && mCurrentIndex == 1) {
                            mCameraSwitchCutoff = SystemClock.uptimeMillis() +
                                    CAMERA_SWITCH_CUTOFF_THRESHOLD_MS;
                            mPhotoView.stopScrolling();
                        } else if (oldIndex >= 1 && mCurrentIndex == 0) {
                            mPhotoView.setWantPictureCenterCallbacks(true);
                            mSkipUpdateCurrentPhoto = true;
                        }
                    }
                    if (!mSkipUpdateCurrentPhoto) {
                        if (item != null) {
                            MediaItem photo = mModel.getMediaItem(0);
                            if (photo != null) {
                                updateCurrentPhoto(photo);
                            }
                        }
                        updateBars();
                    }
                    // Reset the timeout for the bars after a swipe
                    refreshHidingMessage();
                }

                @Override
                public void onLoadingFinished(boolean loadingFailed) {
                    if (!mModel.isEmpty()) {
                        MediaItem photo = mModel.getMediaItem(0);
                        if (photo != null) {
                            updateCurrentPhoto(photo);
                        }
                    } else if (mIsActive) {
                        // We only want to finish the PhotoPage if there is no
                        // deletion that the user can undo.
                        mActionBar.getMenu().clear();
                        mActionBar.getMenu().close();
                        mhasImage = false;
                        if (mMediaSet.getNumberOfDeletions() == 0) {
                            if (mStartFromWidget) {
                                android.util.Log.d(TAG, "onLoadingFinished: ");
                                Bundle data = new Bundle();
                                data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH,
                                        mActivity.getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
                                mActivity.getStateManager().switchState(PhotoPage.this, SprdAlbumSetPage.class, data);
                            } else {
                                mActivity.getStateManager().finishState(PhotoPage.this);
                            }
                        }
                    }
                }

                @Override
                public void onLoadingStarted() {
                }
            });
        } else {
            // Get default media set by the URI
            MediaItem mediaItem = (MediaItem)
                    mActivity.getDataManager().getMediaObject(itemPath);
            mDrmFirstOpen = mediaItem != null && mediaItem.mIsDrmFile;
            if (mediaItem != null) {
                mModel = new SinglePhotoDataAdapter(mActivity, mPhotoView, mediaItem);
                mPhotoView.setModel(mModel);
                if (mediaItem instanceof UriImage) {
                    ((UriImage) mediaItem).init(this);
                }
                updateCurrentPhoto(mediaItem);
            }
            mShowSpinner = false;
        }

        mPhotoView.setFilmMode(mStartInFilmstrip && mMediaSet.getMediaItemCount() > 1);
        RelativeLayout galleryRoot = mActivity
                .findViewById(mAppBridge != null ? R.id.content : R.id.gallery_root);
        if (galleryRoot != null) {
            if (mSecureAlbum == null) {
                mBottomControls = new PhotoPageBottomControls(this, mActivity, galleryRoot);
                mTopControls = new ActionBarTopControls(this, mActivity, galleryRoot);
            }
        }

        ((GLRootView) mActivity.getGLRoot()).setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        int diff = mLastSystemUiVis ^ visibility;
                        mLastSystemUiVis = visibility;
                        if ((diff & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                                && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            showBars();
                        }
                    }
                });

        mToolbarnewAnimIn.setDuration(200);
        mToolbarnewAnimOut.setDuration(200);
        mPhotoControlBottomBarAnimIn.setDuration(200);
        mPhotoControlBottomBarAnimOut.setDuration(200);
        mPhotoControlBottomBar.setVisibility(View.VISIBLE);
        mPref = mActivity.getSharedPreferences("flip_values", Context.MODE_PRIVATE);
        mIsFlip = mPref.getBoolean("flip_values", false);
        if (mIsFlip) {
            mCameraUtil.initialSurfaceView();
        }
        GalleryUtils.end(this.getClass(), "onCreate");
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private WeakReference<PhotoPage> mPhotoPage;

        public MySynchronizedHandler(GLRoot root, PhotoPage photoPage) {
            super(root);
            mPhotoPage = new WeakReference<>(photoPage);
        }

        @Override
        public void handleMessage(Message message) {
            PhotoPage photoPage = mPhotoPage.get();
            if (photoPage != null) {
                photoPage.handleMySynchronizedHandlerMsg(message);
            }
        }

    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        switch (message.what) {
            case MSG_HIDE_BARS: {
                hideBars();
                break;
            }
            case MSG_REFRESH_BOTTOM_CONTROLS: {
                if (mCurrentPhoto == message.obj && mBottomControls != null) {
                    mIsPanorama = message.arg1 == 1;
                    mIsPanorama360 = message.arg2 == 1;
                    mBottomControls.refresh();
                }
                break;
            }
            case MSG_ON_FULL_SCREEN_CHANGED: {
                if (mAppBridge != null) {
                    mAppBridge.onFullScreenChanged(message.arg1 == 1);
                }
                break;
            }
            case MSG_UPDATE_ACTION_BAR: {
                updateBars();
                break;
            }
            case MSG_WANT_BARS: {
                wantBars();
                break;
            }
            case MSG_UNFREEZE_GLROOT: {
                mActivity.getGLRoot().unfreeze();
                break;
            }
            case MSG_UPDATE_DEFERRED: {
                long nextUpdate = mDeferUpdateUntil - SystemClock.uptimeMillis();
                if (nextUpdate <= 0) {
                    mDeferredUpdateWaiting = false;
                    updateUIForCurrentPhoto();
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, nextUpdate);
                }
                break;
            }
            case MSG_ON_CAMERA_CENTER: {
                mSkipUpdateCurrentPhoto = false;
                boolean stayedOnCamera = false;
                if (!mPhotoView.getFilmMode()) {
                    stayedOnCamera = true;
                } else if (SystemClock.uptimeMillis() < mCameraSwitchCutoff &&
                        mMediaSet.getMediaItemCount() > 1) {
                    mPhotoView.switchToImage(1);
                } else {
                    if (mAppBridge != null) {
                        mPhotoView.setFilmMode(false);
                    }
                    stayedOnCamera = true;
                }

                if (stayedOnCamera) {
                    if (mAppBridge == null && mMediaSet.getTotalMediaItemCount() > 1) {
                        launchCamera();
                                /* We got here by swiping from photo 1 to the
                                   placeholder, so make it be the thing that
                                   is in focus when the user presses back from
                                   the camera app */
                        mPhotoView.switchToImage(1);
                    } else {
                        updateBars();
                        updateCurrentPhoto(mModel.getMediaItem(0));
                    }
                }
                break;
            }
            case MSG_ON_PICTURE_CENTER: {
                if (!mPhotoView.getFilmMode() && mCurrentPhoto != null
                        && (mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0) {
                    mPhotoView.setFilmMode(true);
                }
                break;
            }
            case MSG_REFRESH_IMAGE: {
                final MediaItem photo = mCurrentPhoto;
                mCurrentPhoto = null;
                updateCurrentPhoto(photo);
                break;
            }
            case MSG_UPDATE_PHOTO_UI: {
                updateUIForCurrentPhoto();
                break;
            }
            case MSG_UPDATE_SHARE_URI: {
                if (mCurrentPhoto == message.obj) {
                    boolean isPanorama360 = message.arg1 != 0;
                    Uri contentUri = mCurrentPhoto.getContentUri();
                    Intent panoramaIntent = null;
                    if (isPanorama360) {
                        panoramaIntent = createSharePanoramaIntent(contentUri);
                    }
                    Intent shareIntent = createShareIntent(mCurrentPhoto);

                    mActionBar.setShareIntents(panoramaIntent, shareIntent, PhotoPage.this);
                    setNfcBeamPushUri(contentUri);
                }
                break;
            }
            case MSG_UPDATE_PANORAMA_UI: {
                if (mCurrentPhoto == message.obj) {
                    boolean isPanorama360 = message.arg1 != 0;
                    updatePanoramaUI(isPanorama360);
                }
                break;
            }
            case MSG_FINISH_STATE: {
                mActivity.getStateManager().finishState(PhotoPage.this);
                break;
            }
            case MSG_STORAGE_CHANGED: {
                /* storage changed */
                String path = (String) message.obj;
//                mActivity.onStorageChanged(path, false);
                break;
            }
            /* SPRD: Drm feature start @{ */
            case MSG_CONSUME_DRM_RIGHTS: {
                if (PhotoPageUtils.getInstance().cosumeDrmRights(message, mActivity)) {
                    throw new AssertionError(message.what);
                }
                break;
            }
            /* SPRD: Drm feature end @} */
            case MSG_UPDATE_OPERATIONS: {
                updateProgressAndOpera();
                break;
            }
            case MSG_UPDATE_THUMB_FILE_FLAG: {
                MediaItem thumbMediaItem = (MediaItem) message.obj;
                updateFileFlag(thumbMediaItem, true);
                break;
            }
            case MSG_BOKEH_SAVE_DONE: {
                if (mActivity.isFinishing()) {
                    return;
                }
                MediaItem mediaItem = (MediaItem) message.obj;
                if (mediaItem == null) {
                    return;
                }
                mModel.needReDecode(mediaItem);
                if (mCurrentPhoto != mediaItem) {
                    Log.d(TAG, "bokehSaveDone, currentPhoto change, don't pre update bokeh button");
                    return;
                }
                if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
                    mediaItem.bokehDonechangeFileFlag(LocalImage.IMG_TYPE_MODE_BOKEH_HDR);
                } else {
                    mediaItem.bokehDonechangeFileFlag(LocalImage.IMG_TYPE_MODE_BOKEH);
                }

                Log.d(TAG, "pre update bokeh button");
                updateProgressAndOpera();
                break;
            }
            case MSG_BOKEH_PICTURE_DONE: {
                if (mActivity.isFinishing()) {
                    return;
                }
                Bundle bundle = (Bundle) message.obj;
                String filePath = bundle.getString("filePath", "unknown");
                byte[] bokehPicture = bundle.getByteArray("bokehPicture");
                MediaItem mediaItem = mBokehSaveManager.getMediaItemByPath(filePath);
                if (mediaItem == null || bokehPicture == null || bokehPicture.length == 0) {
                    Log.d(TAG, "data error");
                    return;
                }
                if (mCurrentPhoto != mediaItem) {
                    Log.d(TAG, "pictureBokehDone, currentPhoto change, don't update");
                    return;
                }
                mModel.updateBokehPicture(mediaItem, bokehPicture);
                break;

            }
            case MSG_BOKEH_SAVE_ERROR: {
                MediaItem mediaItem = (MediaItem) message.obj;
                updateFileFlag(mediaItem, false);
                break;
            }
            default:
                break;
        }
    }

    private void updateProgressAndOpera() {
        if (mCurrentPhoto == null) {
            return;
        }
        boolean showProgress;
        int mediaType = mCurrentPhoto.getMediaType();
        if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_THUMB) {
            showProgress = true;
            updateMenuOperations();
        } else if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
            showProgress = mSupportBokeh;
            updateMenuOperations();
        } else if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_HDR) {
            showProgress = false;
            refreshBottomControlsWhenReady();
            updateMenuOperations();
        } else {
            showProgress = false;
        }
        mProgressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPictureCenter(boolean isCamera) {
        isCamera = isCamera || (mHasCameraScreennailOrPlaceholder && mAppBridge == null);
        mPhotoView.setWantPictureCenterCallbacks(false);
        mHandler.removeMessages(MSG_ON_CAMERA_CENTER);
        mHandler.removeMessages(MSG_ON_PICTURE_CENTER);
        mHandler.sendEmptyMessage(isCamera ? MSG_ON_CAMERA_CENTER : MSG_ON_PICTURE_CENTER);
    }

    @Override
    public boolean canDisplayBottomControls() {
        return mIsActive && !mPhotoView.canUndo();
    }

    @Override
    public boolean canDisplayBottomControl(int control, View view) {
        if (mCurrentPhoto == null) {
            return false;
        }
        switch (control) {
            case R.id.photopage_bottom_control_edit:
                return false;/*mHaveImageEditor && mShowBars && !mReadOnlyView
                        && !mPhotoView.getFilmMode()
                        && (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_EDIT) != 0
                        && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE;*/
            case R.id.photopage_bottom_control_panorama:
                return mIsPanorama;
            /* SPRD: Add for bug535110 new feature,  support play audio picture @{ */
            case R.id.icon_button:
                return setupViewIcon(view);
            /* @} */
            case R.id.photopage_bottom_control_tiny_planet:
                return mHaveImageEditor && mShowBars
                        && mIsPanorama360 && !mPhotoView.getFilmMode();

            /* SPRD : fix bug 604671 show voice photo is different from camera. @{*/
            case R.id.photo_voice_progress:
                MediaItem mediaItem = mCurrentPhoto;
                long jpegSize = mediaItem.getJpegSize();
                if (jpegSize != 0) {
                    mPhotoVoiceProgress = (PhotoVoiceProgress) view;
                    if (isGmsVersion && mPlayFlag) {
                        mPlayFlag = false;
                        String photoVoice = mediaItem.getFilePath();
                        playPhotoVoiceEx(photoVoice, mediaItem);
                    }
                }
                return false;
            case R.id.photopage_bottom_control_image2:
                if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_hdr_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                }
                return false;
            /* @} */
            case R.id.photopage_bottom_control_image1:
                if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_ai_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                } else if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_VHDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_hdr_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                } else if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_RAW) {
                    ((ImageView) view).setImageResource(R.drawable.ic_raw_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }


    private boolean setupViewIcon(View view) {
        Button button;
        if (view instanceof Button) {
            button = (Button) view;
        } else {
            return false;
        }

        MediaItem mediaItem = mCurrentPhoto;
        int mediaType = mCurrentPhoto.getMediaType();

        Resources resources = mActivity.getResources();
        switch (mediaType) {
            case MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER:
                int burstCount = mediaItem.getBurstCount();
                String name = resources.getString(R.string.continuous_shooting);
                button.setBackground(resources.getDrawable(R.drawable.ic_burst_gallery_sprd));
                button.setText(name + " (" + String.valueOf(burstCount) + ")");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE:
            case MediaObject.MEDIA_TYPE_IMAGE_VHDR:
                long jpegSize = mediaItem.getJpegSize();
                Log.d(TAG, "updateCurrentPhoto jpegSize= " + jpegSize);
                String value = mActivity.getResources().getString(R.string.voice_photo);
                button.setBackground(resources.getDrawable(R.drawable.ic_voice));
                button.setText(value);
                return jpegSize > 0;

            case MediaObject.MEDIA_TYPE_IMAGE_BOKEH:
            case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR:
                if (!mSupportBokeh) {
                    return false;
                }
                String BokehString = mActivity.getResources().getString(R.string.refocus_image);
                button.setBackground(resources.getDrawable(R.drawable.ic_aperture_gallery_sprd));
                button.setText(BokehString);
                Log.d(TAG, "updateCurrentPhoto bokeh button.");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_BLUR:
                if (!mSupportBlur) {
                    return false;
                }
                String blurString = mActivity.getResources().getString(R.string.refocus_image);
                button.setBackground(resources.getDrawable(R.drawable.ic_aperture_gallery_sprd));
                button.setText(blurString);
                Log.d(TAG, "updateCurrentPhoto blur button.");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO:
                button.setBackground(resources.getDrawable(R.drawable.ic_motion_photo_open_album_sprd));
                button.setText(R.string.prefered_motion_photo);
                return true;
        }
        return false;

    }

    @Override
    public void onBottomControlClicked(int control) {
        switch (control) {
            case R.id.photopage_bottom_control_edit:
                launchPhotoEditor();
                return;
            case R.id.photopage_bottom_control_panorama:
                mActivity.getPanoramaViewHelper()
                        .showPanorama(mCurrentPhoto.getContentUri());
                return;
            case R.id.photopage_bottom_control_tiny_planet:
                launchTinyPlanet();
                return;
            /* SPRD: Add for bug535110 new feature,  support play audio picture @{ */
            case R.id.icon_button:
                MediaItem mediaItem = mCurrentPhoto;
                int mediaType = mediaItem.getMediaType();
                if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE ||
                        mediaType == MediaObject.MEDIA_TYPE_IMAGE_VHDR) {
                    String photoVoice = mediaItem.getFilePath();
                    Log.d(TAG, "updateCurrentPhoto   photoVoice = " + photoVoice);
                    playPhotoVoiceEx(photoVoice, mediaItem);
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BLUR
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR) {
                    launchRefocusActivity();
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
                    launchBurstActivity();
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO) {
                    GalleryUtils.launchMotionActivity(mActivity, mCurrentPhoto);
                }
                return;
            /* @} */
            default:
                return;
        }
    }

    //actionbar
    @Override
    public void onTopControlClicked(int id) {
        // TODO Auto-generated method stub
        switch (id) {
            case R.id.photopage_top_control_edit:
//             launchPhotoEditor();
                return;
            case R.id.photopage_top_control_share:
                Log.i(TAG, " onTopControlClicked photopage_top_control_share");
                // launchShareIntent();
                return;
            case R.id.photopage_top_control_quit:
//        	 GLRoot root = mActivity.getGLRoot();
//             root.lockRenderThread();
//             try {
//            	 onUpPressed();
//             } finally {
//                 root.unlockRenderThread();
//             }
                return;
            case R.id.photopage_top_control_more:
//        	 showMenuPop();
                return;
            default:
                return;
        }
    }

    private void launchShareIntent() {
        Log.i(TAG, " onTopControlClicked mCurrentPhoto = " + mCurrentPhoto);
        Uri contentUri = mCurrentPhoto.getContentUri();
        // Intent shareIntent = createShareIntent(mCurrentPhoto);
        //shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        setupNfcBeamPush();
        setNfcBeamPushUri(contentUri);
        // mActivity.startActivity(shareIntent);
        // mActionBar.setShareIntents(null, shareIntent, PhotoPage.this);
        MenuExecutor.launchShareIntent(mActivity, mModel.getMediaItem(0));
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) {
            return;
        }

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mActivity);
        if (adapter != null) {
            adapter.setBeamPushUris(mNfcPushUris, mActivity);
            adapter.setBeamPushUrisCallback(null /*new CreateBeamUrisCallback() {
                @Override
                public Uri[] createBeamUris(NfcEvent event) {
                    return mNfcPushUris;
                }
            }*/, mActivity);
        }
    }

    private void setNfcBeamPushUri(Uri uri) {
        mNfcPushUris[0] = uri;
    }

    private Intent createShareIntent(MediaObject mediaObject) {

        String msgShareTo = mActivity.getResources().getString(R.string.share);
        int type = mediaObject.getMediaType();
        Uri contentUri = GalleryUtils.transFileToContentType(mediaObject.getContentUri(), mActivity);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(MenuExecutor.getMimeType(type))
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent = Intent.createChooser(intent, msgShareTo);
        return intent;
    }

    private static Intent createSharePanoramaIntent(Uri contentUri) {
        return new Intent(Intent.ACTION_SEND)
                .setType(GalleryUtils.MIME_TYPE_PANORAMA360)
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private void overrideTransitionToEditor() {
        mActivity.overridePendingTransition(android.R.anim.fade_in,
                android.R.anim.fade_out);
    }

    private void launchTinyPlanet() {
        // Deep link into tiny planet
        MediaItem current = mModel.getMediaItem(0);
        Intent intent = new Intent(FilterShowActivity.TINY_PLANET_ACTION);
        intent.setClass(mActivity, FilterShowActivity.class);
        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                mActivity.isFullscreen());
        mActivity.startActivityForResult(intent, REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void launchCamera() {
        mRecenterCameraOnResume = false;
        GalleryUtils.startCameraActivity(mActivity);
    }

    private void launchPhotoEditor() {
        MediaItem current = mModel.getMediaItem(0);
//        if (current == null || (current.getSupportedOperations()
//                & MediaObject.SUPPORT_EDIT) == 0) {
//            return;
//        }
//
//        Intent intent = new Intent(ACTION_NEXTGEN_EDIT);
//
//        intent.setDataAndType(current.getContentUri(), current.getMimeType())
//                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        if (mActivity.getPackageManager()
//                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
//            intent.setAction(Intent.ACTION_EDIT);
//        }
//        // SPRD: bug 635695, After edit, voice image cant play voice
//        ifEditVoicePhoto(current, intent);
//        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
//                mActivity.isFullscreen());
//        ((Activity) mActivity).startActivityForResult(Intent.createChooser(intent, null),
//                REQUEST_EDIT);
        GalleryUtils.launchEditor(mActivity, current, REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void launchSimpleEditor() {
        MediaItem current = mModel.getMediaItem(0);
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_SIMPLE_EDIT);

        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (mActivity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                mActivity.isFullscreen());
        mActivity.startActivityForResult(Intent.createChooser(intent, null),
                REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void requestDeferredUpdate() {
        mDeferUpdateUntil = SystemClock.uptimeMillis() + DEFERRED_UPDATE_MS;
        if (!mDeferredUpdateWaiting) {
            mDeferredUpdateWaiting = true;
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, DEFERRED_UPDATE_MS);
        }
    }

    private void updateUIForCurrentPhoto() {
        if (mCurrentPhoto == null) {
            return;
        }

        // If by swiping or deletion the user ends up on an action item
        // and zoomed in, zoom out so that the context of the action is
        // more clear
        if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setWantPictureCenterCallbacks(true);
        }

        updateMenuOperations();
        refreshBottomControlsWhenReady();
        if (mShowDetails) {
            mDetailsHelper.reloadDetails();
        }
        if ((mSecureAlbum == null)
                && (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_SHARE) != 0) {
//            mCurrentPhoto.getPanoramaSupport(mUpdateShareURICallback);
        }
    }

    private void updateCurrentPhoto(MediaItem photo) {
        if (mCurrentPhoto == photo) {
            return;
        }
        // Add for bug535110 new feature,  support play audio picture
        playPhotoVoiceEx(null, null);
        //如果滑动切换了图片, 则停止播放视频
        getGLRoot().playGLVideo(null);
        mCurrentPhoto = photo;

        /* SPRD: bug 624616 ,Slide to DRM image, should Consume authority @{ */
        if ((!mDrmFirstOpen || mStartFromWidget) && photo.getName() != null && photo.getName().endsWith(".dcf")
                && photo.getMediaType() != MediaObject.MEDIA_TYPE_VIDEO) {
            AlertDialog.OnClickListener confirmListener = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    PhotoPageUtils.getInstance().updateDrmCurrentPhoto(mCurrentPhoto, mHandler);
                    updateUIForCurrentPhoto();
                }
            };
            AlertDialog.OnClickListener cancelListener = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mHandler.sendEmptyMessage(MSG_FINISH_STATE);
                }
            };
            DialogInterface.OnKeyListener onKeyListener = new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    Log.i(TAG, " updateCurrentPhoto keyCode =  " + keyCode);
                    int aciton = event.getAction();
                    if (aciton == KeyEvent.ACTION_UP) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            mHandler.sendEmptyMessage(MSG_FINISH_STATE);
                        }
                    }
                    return false;
                }
            };
            SomePageUtils.getInstance().checkPressedIsDrm(mActivity,
                    photo, confirmListener, cancelListener, onKeyListener, false);
        }
        /* @} */

        /* SPRD: Drm feature start @{ */
        if ((mDrmFirstOpen && !mStartFromWidget) && photo.getName() != null && photo.getName().endsWith(".dcf")) {
            PhotoPageUtils.getInstance().updateDrmCurrentPhoto(photo, mHandler);
            mDrmFirstOpen = false;
        }
        /* SPRD: Drm feature end @} */
        if (mPhotoView.getFilmMode()) {
            requestDeferredUpdate();
        } else {
            updateUIForCurrentPhoto();
            // SPRD: Add 20141211 Spreadst of bug379599, update title when current photo changed
            updateTitle(false);
        }
    }

    private void updateMenuOperations() {
        Menu menu = mActionBar.getMenu();
        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) {
            return;
        }

        MenuItem item = menu.findItem(R.id.action_slideshow);
        if (item != null) {
            item.setVisible((mSecureAlbum == null) && canDoSlideShow());
        }
        /* SPRD: Fix Bug 535131, add slide music feature @{ */
        MenuItem itemMusic = menu.findItem(R.id.action_slideshow_music);
        if (itemMusic != null) {
            itemMusic.setVisible((mSecureAlbum == null) && canDoSlideShow());
        }
        /* @} */
        if (mCurrentPhoto == null) {
            return;
        }
        int mediaType = mCurrentPhoto.getMediaType();
        //播放motion photo时,按钮状态切换
        MenuItem motionPlay = menu.findItem(R.id.action_play_motion_photo);
        if (motionPlay != null) {
            motionPlay.setVisible(mCurrentPhoto.isMotionPhoto());
            motionPlay.setIcon(getGLRoot().isGLVideoPlaying() ?
                    R.drawable.ic_ab_motion_photo_pause : R.drawable.ic_ab_motion_photo_play);
            motionPlay.setTitle(getGLRoot().isGLVideoPlaying() ?
                    R.string.stop_motion_photo : R.string.play_motion_photo);
        }

        MenuItem itemSetAs = menu.findItem(R.id.action_setas);
        if (itemSetAs != null) {
            itemSetAs.setEnabled(!((mCurrentPhoto instanceof UriImage) && GalleryUtils.isMtpUri(mCurrentPhoto.getContentUri())));
        }

        int supportedOperations = mCurrentPhoto.getSupportedOperations();
        if (mReadOnlyView) {
            /* SPRD: fix bug 391857, Open Drm pictures from the download list don't support edit @{ */
            // supportedOperations ^= MediaObject.SUPPORT_EDIT;
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            /* @} */
        }
        if (mSecureAlbum != null) {
            supportedOperations &= MediaObject.SUPPORT_DELETE;
        } else {
            mCurrentPhoto.getPanoramaSupport(mUpdatePanoramaMenuItemsCallback);
            if (!mHaveImageEditor) {
                supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            }
        }
        /* SPRD: gif don't support editor @{ */
        if (mediaType == MediaObject.MEDIA_TYPE_GIF) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        /* @} */
        /* SPRD: fix bug 387548, WBMP don't support edit @{ */
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_WBMP) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        /* @} */
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BLUR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
            supportedOperations &= ~MediaObject.SUPPORT_ROTATE;
            supportedOperations &= ~MediaObject.SUPPORT_CROP;
        }
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
            supportedOperations &= ~MediaObject.SUPPORT_SHARE;
        }

        if ((mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR)
                && mSupportBokeh && !mSecureCamera) {
            supportedOperations |= MediaObject.SUPPORT_BLENDING;
        }
        if (!isGmsVersion) {
            if (mSingleItemOnly && !mCurrentPhoto.mIsDrmFile
                    && mModel.getLoadingState(mCurrentIndex) == Model.LOADING_COMPLETE) {
                supportedOperations = MediaObject.SUPPORT_SETAS | MediaObject.SUPPORT_PRINT;
            }
        }
        if (mSingleItemOnly) {
            supportedOperations &= ~MediaObject.SUPPORT_DELETE;
        }
        if (mSecureCamera) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            supportedOperations &= ~MediaObject.SUPPORT_SHARE;
        }
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_THUMB) {
            supportedOperations = 0;
        }
        MenuExecutor.updateMenuOperation(menu, supportedOperations, true);
        if (mPhotoControlBottomBar != null) {
            mPhotoControlBottomBar.updateMenuOperation(supportedOperations);
        }
    }

    private boolean canDoSlideShow() {
        if (mMediaSet == null || mCurrentPhoto == null) {
            return false;
        }
//        if (mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
//            return false;
//        }
        /* SPRD: add to support play gif,and bug 620182,add support for video Thumbnail @{ */
        return mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_UNKNOWN;
    }

    //////////////////////////////////////////////////////////////////////////
    //  Action Bar show/hide management
    //////////////////////////////////////////////////////////////////////////

    private void showBars() {
        if (!mIsActive || mShowBars) {
            return;
        }
        mShowBars = true;
        mOrientationManager.unlockOrientation();
        showToolBar(true);
        showStatusBar();
        // mActionBar.show();
        //mActivity.getGLRoot().setLightsOutMode(false);
        refreshHidingMessage();
        refreshBottomControlsWhenReady();
    }

    private void hideBars() {
        if (!mIsActive || !mShowBars) {
            return;
        }
        mShowBars = false;
        showToolBar(false);
        hideStatusBar();
        // mActionBar.hide();
        //mActivity.getGLRoot().setLightsOutMode(true);
        mHandler.removeMessages(MSG_HIDE_BARS);
        refreshBottomControlsWhenReady();
    }

    private void showToolBar(boolean show) {
        mToolbarnew.clearAnimation();
        mPhotoControlBottomBar.clearAnimation();
        if (show) {
            mToolbarnewAnimIn.reset();
            mToolbarnew.startAnimation(mToolbarnewAnimIn);
            mToolbarnew.setVisibility(View.VISIBLE);

            mPhotoControlBottomBarAnimIn.reset();
            mPhotoControlBottomBar.startAnimation(mPhotoControlBottomBarAnimIn);
            mPhotoControlBottomBar.setVisibility(View.VISIBLE);
        } else {
            mToolbarnewAnimOut.reset();
            mToolbarnew.startAnimation(mToolbarnewAnimOut);
            mToolbarnew.setVisibility(View.GONE);

            mPhotoControlBottomBarAnimOut.reset();
            mPhotoControlBottomBar.startAnimation(mPhotoControlBottomBarAnimOut);
            mPhotoControlBottomBar.setVisibility(View.GONE);
        }
    }

    private void showTopControls() {
        if (!mShowBars) {
            mTopControls.showTopControls();
        } else {
            mTopControls.hideTopControls();
        }
    }

    private void refreshHidingMessage() {
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (!mIsMenuVisible && !mPhotoView.getFilmMode()) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
        }
    }

    private boolean canShowBars() {
        // No bars if we are showing camera preview.
        if (mAppBridge != null && mCurrentIndex == 0
                && !mPhotoView.getFilmMode()) {
            return false;
        }

        // No bars if it's not allowed.
        if (!mActionBarAllowed) {
            return false;
        }

        Configuration config = mActivity.getResources().getConfiguration();
        return config.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH;
    }

    private void wantBars() {
        if (canShowBars()) {
            showBars();
        }
    }

    private void toggleBars() {
        if (mShowBars) {
            hideBars();
        } else {
            if (canShowBars()) {
                showBars();
            }
        }
    }

    private void updateBars() {
        if (!canShowBars()) {
            hideBars();
        }
    }

    @Override
    protected void onBackPressed() {
        Menu menu = mActionBar.getMenu();
        if (menu != null) {
            menu.clear();
            menu.close();
        }
        // showBars();
        if (mShowDetails) {
            hideDetails();
        } else if (mAppBridge == null || !switchWithCaptureAnimation(-1)) {
            // We are leaving this page. Set the result now.
            setResult();
            if (mStartInFilmstrip && !mPhotoView.getFilmMode()) {
                mPhotoView.setFilmMode(true);
            } else if (mTreatBackAsUp) {
                onUpPressed();
            } else {
                super.onBackPressed();
            }
        }
    }

    private void onUpPressed() {
        if (!mIsActive) {
            return;
        }

        if ((mStartInFilmstrip || mAppBridge != null)
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setFilmMode(true);
            return;
        }
        Menu menu = mActionBar.getMenu();
        if (menu != null) {
            menu.clear();
            menu.close();
        }
        if (mActivity.getStateManager().getStateCount() > 1) {
            setResult();
            super.onBackPressed();
            return;
        }
        if (mOriginalSetPathString == null) {
            return;
        }

        if (mAppBridge == null) {
            // We're in view mode so set up the stacks on our own.
            Bundle data = new Bundle(getData());
            data.putString(SprdAlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(SprdAlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));
            mActivity.getStateManager().switchState(this, SprdAlbumPage.class, data);
        } else {
            GalleryUtils.startGalleryActivity(mActivity);
        }
    }

    private void setResult() {
        Intent result = null;
        result = new Intent();
        result.putExtra(KEY_RETURN_INDEX_HINT, mCurrentIndex);
        setStateResult(Activity.RESULT_OK, result);
    }

    //////////////////////////////////////////////////////////////////////////
    //  AppBridge.Server interface
    //////////////////////////////////////////////////////////////////////////

    @Override
    public void setCameraRelativeFrame(Rect frame) {
        mPhotoView.setCameraRelativeFrame(frame);
    }

    @Override
    public boolean switchWithCaptureAnimation(int offset) {
        return mPhotoView.switchWithCaptureAnimation(offset);
    }

    @Override
    public void setSwipingEnabled(boolean enabled) {
        mPhotoView.setSwipingEnabled(enabled);
    }

    @Override
    public void notifyScreenNailChanged() {
        mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());
        mScreenNailSet.notifyChange();
    }

    @Override
    public void addSecureAlbumItem(boolean isVideo, int id) {
        mSecureAlbum.addMediaItem(isVideo, id);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        if (!mhasImage) {
            return true;
        }
        mActionBar.createActionBarMenu(R.menu.photo, menu);
        /*
         * SPRD: bug 474655 Trim and mute video is supported or not.
         * old bug info:skip trim and mute menu item for bug 273733
         * @{
         */
        if (!mTrimvideoEnable) {
            menu.removeItem(R.id.action_trim);
            menu.removeItem(R.id.action_mute);
        }
        if (mSecureCamera) {
            menu.removeItem(R.id.action_slideshow);
            menu.removeItem(R.id.action_slideshow_music);
            menu.removeItem(R.id.action_setas);
        }
        /* @} */
        mHaveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        updateMenuOperations();
        showBars();
        // SPRD: Modify 20141211 Spreadst of bug379599, remove following code and set title in onResume()
        // mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : "");
        return true;
    }

    private final MenuExecutor.ProgressListener mConfirmDialogListener =
            new MenuExecutor.ProgressListener() {
                @Override
                public void onProgressUpdate(int index) {
                }

                @Override
                public void onProgressComplete(int result) {
                }

                @Override
                public void onConfirmDialogShown() {
                    mHandler.removeMessages(MSG_HIDE_BARS);
                }

                @Override
                public void onConfirmDialogDismissed(boolean confirmed) {
                    refreshHidingMessage();
                }

                @Override
                public void onProgressStart() {
                }
            };

    private void switchToGrid() {
        if (mActivity.getStateManager().hasStateClass(SprdAlbumPage.class)) {
            onUpPressed();
        } else {
            if (mOriginalSetPathString == null) {
                return;
            }
            Bundle data = new Bundle(getData());
            data.putString(SprdAlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(SprdAlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));

            // We only show cluster menu in the first SprdAlbumPage in stack
            // TODO: Enable this when running from the camera app
            boolean inAlbum = mActivity.getStateManager().hasStateClass(SprdAlbumPage.class);
            data.putBoolean(SprdAlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum
                    && mAppBridge == null);

            data.putBoolean(PhotoPage.KEY_APP_BRIDGE, mAppBridge != null);

            // Account for live preview being first item
            mActivity.getTransitionStore().put(KEY_RETURN_INDEX_HINT,
                    mAppBridge != null ? mCurrentIndex - 1 : mCurrentIndex);

            if (mHasCameraScreennailOrPlaceholder && mAppBridge != null) {
                mActivity.getStateManager().startState(SprdAlbumPage.class, data);
            } else {
                mActivity.getStateManager().switchState(this, SprdAlbumPage.class, data);
            }
        }
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        if (mModel == null) {
            return true;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);

        // This is a shield for monkey when it clicks the action bar
        // menu when transitioning from filmstrip to camera
        if (current instanceof SnailItem) {
            return true;
        }
        // TODO: We should check the current photo against the MediaItem
        // that the menu was initially created for. We need to fix this
        // after PhotoPage being refactored.
        if (current == null) {
            // item is not ready, ignore
            return true;
        }
        int currentIndex = mModel.getCurrentIndex();
        Path path = current.getPath();

        DataManager manager = mActivity.getDataManager();
        int action = item.getItemId();
        String confirmMsg = null;
        switch (action) {
            case android.R.id.home: {
                onUpPressed();
                return true;
            }
            case R.id.action_slideshow: {
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath().toString());
                data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
                data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            /* SPRD: Fix Bug 535131, add slide music feature @{ */
            case R.id.action_slideshow_music: {
                showSelectMusicDialog();
                return true;
            }
            /* @} */
            case R.id.action_crop: {
                Activity activity = mActivity;
                Intent intent = new Intent(CropActivity.CROP_ACTION);
                intent.setClass(activity, CropActivity.class);
                intent.setDataAndType(manager.getContentUri(path), current.getMimeType())
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra("from_photo_page", true);
                intent.putExtra("crop_in_gallery", true);
                activity.startActivityForResult(intent, PicasaSource.isPicasaImage(current)
                        ? REQUEST_CROP_PICASA
                        : REQUEST_CROP);
                return true;
            }
            case R.id.action_trim: {
                Intent intent = new Intent(mActivity, TrimVideo.class);
                intent.setData(manager.getContentUri(path));
                // We need the file path to wrap this into a RandomAccessFile.
                intent.putExtra(KEY_MEDIA_ITEM_PATH, current.getFilePath());
                mActivity.startActivityForResult(intent, REQUEST_TRIM);
                return true;
            }
            case R.id.action_mute: {
                MuteVideo muteVideo = new MuteVideo(current.getFilePath(),
                        manager.getContentUri(path), mActivity);
                muteVideo.muteInBackground();
                return true;
            }
            case R.id.action_edit: {
                launchPhotoEditor();
                return true;
            }
            case R.id.action_simple_edit: {
                launchSimpleEditor();
                return true;
            }
            case R.id.action_details: {
                if (!DetailsHelper.SHOW_IN_ACTIVITY) {
                    if (mShowDetails) {
                        hideDetails();
                    } else {
                        showDetails();
                    }
                } else {
                    GalleryUtils.startDetailsActivity(mActivity, new MyDetailsSource());
                }
                return true;
            }
            case R.id.print: {
                /* SPRD: bug 627612,use non-existent image to print ,crash @{ */
                if (current != null && current instanceof UriImage) {
                    Uri uri = current.getContentUri();
                    Log.d(TAG, "print UriImage, and uri is : " + uri);
                    if (!isValidUri(mActivity, uri)) {
                        Toast.makeText(mActivity, R.string.fail_to_load_image, Toast.LENGTH_SHORT).show();
                        mActivity.finish();
                        return true;
                    }
                }
                /* @} */
                mActivity.printSelectedImage(manager.getContentUri(path));
                return true;
            }
            case R.id.action_delete:
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.delete_selection, 1);
            case R.id.action_setas:
                /* SPRD: bug 627612,use non-existent image to set as wallpaper,crash @{ */
                if (current != null && current instanceof UriImage) {
                    Uri uri = current.getContentUri();
                    Log.d(TAG, "action_setas UriImage, and uri is : " + uri);
                    if (!isValidUri(mActivity, uri) || !GalleryUtils.isValidMtpUri(mActivity, uri)) {
                        Toast.makeText(mActivity, R.string.fail_to_load_image, Toast.LENGTH_SHORT).show();
                        mActivity.finish();
                        return true;
                    }
                }
                /* @} */
                lockScreen();
            case R.id.action_rotate_ccw:
            case R.id.action_rotate_cw:
            case R.id.action_show_on_map:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, confirmMsg, mConfirmDialogListener);
                return true;
            case R.id.action_share: {
                launchShareIntent();
                return true;
            }
            case R.id.action_play_motion_photo:
                if (getGLRoot().isGLVideoPlaying()) {
                    getGLRoot().stopGLVideo();
                } else {
                    playMotionPhoto(current);
                }
                return true;
            default:
                /* SPRD: Drm feature start @{ */
                return MenuExecutorUtils.getInstance().showHideDrmDetails(PhotoPage.this,
                        item.getItemId(), currentIndex);
            /* SPRD: Drm feature end @} */
        }
    }

    /* SPRD: bug 627612,use non-existent image to set as wallpaper,crash @{ */
    private boolean isValidUri(Activity activity, Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            AssetFileDescriptor f = activity.getContentResolver().openAssetFileDescriptor(uri, "r");
            f.close();
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "cannot open uri: " + uri, e);
            return false;
        }
    }
    /* @} */

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
    }

    private void showDetails() {
        // SPRD: Modify 20160114 for bug522691, add to avoid WindowLeaked Exception if GalleryActivity is finished @{
        if (isDestroyed() || isFinishing()) {
            Log.d(TAG, "<showDetails> PhotoPage has been destroyed.");
            return;
        }
        // @}
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, new MyDetailsSource());
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Callbacks from PhotoView
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onSingleTapUp(int x, int y) {
        if (mAppBridge != null) {
            if (mAppBridge.onSingleTapUp(x, y)) {
                return;
            }
        }

        MediaItem item = mModel.getMediaItem(0);
        if (item == null || item == mScreenNailItem) {
            // item is not ready or it is camera preview, ignore
            return;
        }

        boolean clickCenter = false;
        boolean clickRefocus = false;
        int w = mPhotoView.getWidth();
        int h = mPhotoView.getHeight();
        int iconMargin = mPhotoView.getIconMargin();
        int supported = item.getSupportedOperations();
        boolean playVideo = ((supported & MediaItem.SUPPORT_PLAY) != 0);
        boolean unlock = ((supported & MediaItem.SUPPORT_UNLOCK) != 0);
        boolean goBack = ((supported & MediaItem.SUPPORT_BACK) != 0);
        boolean launchCamera = ((supported & MediaItem.SUPPORT_CAMERA_SHORTCUT) != 0);
        // determine if the point is at center (1/6) of the photo view.
        // (The position of the "play" icon is at center (1/6) of the photo)
        clickCenter = (Math.abs(x - w / 2) * 12 <= w) && (Math.abs(y - h / 2) * 12 <= h);
        //clickRefocus = (Math.abs(x - w / 2) * 12 <= w) && (Math.abs(y - h / 2 - iconMargin) * 12 <= h);

        if (playVideo && clickCenter) {
            /* SPRD:Bug474646 Add Drm feature,modify bug old 394118,Video File is Drm need to dispaly a toast @{ */
            final MediaItem itemDrm = item;
            AlertDialog.OnClickListener onClickListener = new AlertDialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    playVideo(mActivity, itemDrm);
                }
            };
            if (!SomePageUtils.getInstance().checkPressedIsDrm(mActivity, itemDrm, onClickListener,
                    null, null, false)) {
                /* SPRD:474646 Add Drm feature,Drm feature end @} */
                if (mSecureAlbum == null) {
                    playVideo(mActivity, item);
                } else {
                    mActivity.getStateManager().finishState(this);
                }
                /* @} */
            }
        } else if (goBack) {
            onBackPressed();
        } else if (unlock) {
            Intent intent = new Intent(mActivity, GalleryActivity.class);
            intent.putExtra(GalleryActivity.KEY_DISMISS_KEYGUARD, true);
            mActivity.startActivity(intent);
        } else if (launchCamera) {
            launchCamera();
        } else {
            toggleBars();
        }
    }

    @Override
    public void onActionBarAllowed(boolean allowed) {
        mActionBarAllowed = allowed;
        mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
    }

    @Override
    public void onActionBarWanted() {
        mHandler.sendEmptyMessage(MSG_WANT_BARS);
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        Message m = mHandler.obtainMessage(
                MSG_ON_FULL_SCREEN_CHANGED, full ? 1 : 0, 0);
        m.sendToTarget();
    }

    @Override
    public int onPrepareDeleteImage(Path path, int offset) {
        onCommitDeleteImage();
        mDeleteIsFocus = (offset == 0);
        mMediaSet.addDeletion(path, mCurrentIndex + offset);
        return mCurrentIndex;
    }

    @Override
    public void onSdCardPermissionAllowed(Path path, int currentIndex, int offset) {
        onCommitDeleteImage();
        mDeletePath = path;
        mDeleteIsFocus = (offset == 0);
        mMediaSet.addDeletion(path, currentIndex + offset);
    }

    @Override
    public void onSdCardPermissionDenied(Path path, int offset) {
        mhasImage = true;
        if (mDeleteIsFocus) {
            mModel.setFocusHintPath(path);
        }
        mMediaSet.removeDeletion(path);
    }

    // How we do delete/undo:
    //
    // When the user choose to delete a media item, we just tell the
    // FilterDeleteSet to hide that item. If the user choose to undo it, we
    // again tell FilterDeleteSet not to hide it. If the user choose to commit
    // the deletion, we then actually delete the media item.
    @Override
    public void onDeleteImage(Path path, int offset) {
        onCommitDeleteImage();  // commit the previous deletion
        mDeletePath = path;
        mDeleteIsFocus = (offset == 0);
        mMediaSet.addDeletion(path, mCurrentIndex + offset);
    }

    @Override
    public void onUndoDeleteImage() {
        if (mDeletePath == null) {
            return;
        }
        // If the deletion was done on the focused item, we want the model to
        // focus on it when it is undeleted.
        mhasImage = true;
        if (mDeleteIsFocus) {
            mModel.setFocusHintPath(mDeletePath);
        }
        mMediaSet.removeDeletion(mDeletePath);
        mDeletePath = null;
    }

    @Override
    public void onCommitDeleteImage() {
        if (mDeletePath == null) {
            return;
        }
        mMenuExecutor.startSingleItemAction(R.id.action_delete, mDeletePath);
        mDeletePath = null;
    }

    public void playVideo(Activity activity, MediaItem item) {
        try {
            String type = item.getMimeType();
            if (type == null) {
                type = "video/*";
            }
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(item.getPlayUri(), type)
                    .putExtra(Intent.EXTRA_TITLE, item.getName())
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(KEY_IS_SECURE_CAMERA, mSecureCamera)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true);
            if (mSecureCamera || (item.mIsDrmFile && !item.mIsDrmSupportTransfer)) {
                //不支持转发的DRM就使用自带视频播放器
                intent.setClass(mActivity, MovieActivity.class);
            }
            /**SPRD:Bug474615 Playback loop mode @{*/
            intent.putExtra(FLAG_GALLERY, true);
            /** @}*/
            activity.startActivityForResult(intent, REQUEST_PLAY_VIDEO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void startImageBlendingActivity(Activity activity, Uri uri, MediaItem item) {
        if (activity == null || uri == null || item == null) {
            return;
        }
        Intent intent = new Intent(mActivity, ReplaceActivity.class);
        intent.putExtra("path", item.getFilePath());
        intent.putExtra(KEY_SECURE_CAMERA, mSecureCamera);
        intent.setDataAndType(uri, "blendingImage/jpeg");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            Log.d(TAG, "imageblendingActivity");
            activity.startActivityForResult(intent, REQUEST_BLENDING);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "imageblending activity previously detected but cannot be found", e);
        }
    }

    public void startDistanceActivity(Uri uri, MediaItem item) {
        if (uri == null || item == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(ACTION_DISTANCE_EDIT);
        intent.setDataAndType(uri, "distance/jpeg");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            Log.d(TAG, "startDistanceActivity");
            mActivity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Distance activity previously detected but cannot be found", e);
        }
    }

    private void setCurrentPhotoByIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Path path = mApplication.getDataManager()
                .findPathByUri(intent.getData(), intent.getType());
        if (path != null) {
            /**SPRD:473267 M porting add video entrance & related bug-fix
             Modify 20150106 of bug 390428,video miss after crop @{ */
            Path albumPath = mApplication.getDataManager().getDefaultSetOf(true, path, intent.getAction());
            /**@}*/
            if (albumPath == null) {
                return;
            }
            if (!albumPath.equalsIgnoreCase(mOriginalSetPathString)) {
                // If the edited image is stored in a different album, we need
                // to start a new activity state to show the new image
                Bundle data = new Bundle(getData());
                data.putString(KEY_MEDIA_SET_PATH, albumPath.toString());
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path.toString());
                mActivity.getStateManager().startState(SinglePhotoPage.class, data);
                return;
            }
            mModel.setCurrentPhoto(path, mCurrentIndex);
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            // This is a reset, not a canceled
            return;
        }
        mRecenterCameraOnResume = false;
        switch (requestCode) {
            case REQUEST_BLENDING:
            case REQUEST_EDIT:
                setCurrentPhotoByIntent(data);
                break;
            case REQUEST_CROP:
                if (resultCode == Activity.RESULT_OK) {
                    setCurrentPhotoByIntent(data);
                }
                break;
            case REQUEST_CROP_PICASA: {
                if (resultCode == Activity.RESULT_OK) {
                    Context context = mActivity.getAndroidContext();
                    String message = context.getString(R.string.crop_saved,
                            context.getString(R.string.folder_edited_online_photos));
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case REQUEST_SLIDESHOW: {
                if (data == null) {
                    break;
                }
                String path = data.getStringExtra(SlideshowPage.KEY_ITEM_PATH);
                int index = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                if (path != null) {
                    mModel.setCurrentPhoto(Path.fromString(path), index);
                }
                break;
            }
            /* SPRD: Fix Bug 535131, add slide music feature @{ */
            case REQUEST_SLIDESHOW_RINGTONE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mActivity.getAndroidContext();
                    Uri uri = data.getExtras().getParcelable(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    /* SPRD:Modify for bug590332 when setting slide music and delete all ringtones ,resume gallery,the gallery will crash by old bug579217 @{ */
                    if (uri == null) {
                        mPos = NONE_MUSIC;
                        GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), null);
                        GalleryUtils.saveSelected(mActivity.getAndroidContext(), NONE_MUSIC);
                        mUserSelected = NONE_MUSIC;
                        return;
                    }
                    /* Bug590332 End @{ */
                    Log.d(TAG, " REQUEST_SLIDESHOW_RINGTONE Uri = " + uri);
                    GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), uri.toString());
                    // SPRD: bug 566432, bug 569414 User defined cannot choose music
                    GalleryUtils.saveSelected(mActivity.getAndroidContext(), SELECT_MUSIC);
                    mUserSelected = SELECT_MUSIC;
                }
                break;
            }
            case REQUEST_SLIDESHOW_MUSIC: {
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    mActivity.getAndroidContext();
                    Uri uri = data.getData();
                    Log.i(TAG, " REQUEST_SLIDESHOW_MUSIC uri = " + uri);
                    GalleryUtils.setSlideMusicUri(mActivity.getAndroidContext(), GalleryUtils.transformUri(uri, mActivity).toString());

                    // SPRD: bug 566432, bug 569414 User defined cannot choose music
                    GalleryUtils.saveSelected(mActivity.getAndroidContext(), USER_DEFINED_MUSIC);
                    mUserSelected = USER_DEFINED_MUSIC;
                }
                break;
            }
            /* @} */
            /**SPRD:Bug474635 exit the video player interface add by oldBug 283113 Update video index.@{*/
            case REQUEST_PLAY_VIDEO: {
                if (data == null) {
                    break;
                }
                setCurrentPhotoByIntent(data);
                break;
            }
            /** @}*/
            case REQUEST_EDIT_REFOCUS: {
                Uri uri = data == null ? null : data.getData();
                if (uri != null && mSecureItemIds != null) {
                    Log.d(TAG, "onStateResult add saved edited picture -> " + uri);
                    long _id = -1;
                    try {
                        _id = ContentUris.parseId(uri);
                    } catch (Exception ignored) {
                    }
                    if (_id > 0) {
                        mSecureItemIds = Arrays.copyOf(mSecureItemIds, mSecureItemIds.length + 1);
                        mSecureItemIds[mSecureItemIds.length - 1] = _id;
                        if (mMediaSet != null) {
                            mMediaSet.addSecureItems(mSecureItemIds);
                        }
                    }
                }
                break;
            }

        }
    }


    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause " + this);
        //如果onPause了, 则停止播放视频
        getGLRoot().stopGLVideo();
        EventBus.getDefault().unregister(this);
        mIsActive = false;
        mPhotoControlBottomBar.setVisibility(View.GONE);
        if (mCurrentPhoto != null && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
            ((LocalImage) mCurrentPhoto).setBurstCountUpdateListener(null);
        }

        mActivity.getGLRoot().unfreeze();
        mHandler.removeMessages(MSG_UNFREEZE_GLROOT);
        mHandler.removeMessages(MSG_CONSUME_DRM_RIGHTS);

        if (!DetailsHelper.SHOW_IN_ACTIVITY) {
            DetailsHelper.pause();
        }
        // Hide the detail dialog on exit
        if (mShowDetails) {
            hideDetails();
        }
        if (mModel != null) {
            mModel.pause();
        }
        mPhotoView.pause();
        mHandler.removeMessages(MSG_HIDE_BARS);
        mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
        refreshBottomControlsWhenReady();
        mActionBar.removeOnMenuVisibilityListener(mMenuVisibilityListener);
        if (mShowSpinner) {
            mActionBar.disableAlbumModeMenu(true);
        }
        onCommitDeleteImage();
        // SPRD:if PhotoPage is pause,Delete dialog dismiss
        mMenuExecutor.dissmissDialog();
        mMenuExecutor.pause();
        if (mIsFlip) {
            mCameraUtil.closeCameraAPI2_2();
        }
        if (mMediaSet != null) {
            mMediaSet.clearDeletion();
        }
        // SPRD: Add for bug535110 new feature,  support play audio picture
        releasePlayer();

//		mTopControls.hideTopControls();
    }

    @Override
    public void onCurrentImageUpdated() {
        mActivity.getGLRoot().unfreeze();
    }

    @Override
    public void onFilmModeChanged(boolean enabled) {
        refreshBottomControlsWhenReady();
        if (mShowSpinner) {
            if (enabled) {
                mActionBar.enableAlbumModeMenu(
                        GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
            } else {
                mActionBar.disableAlbumModeMenu(true);
            }
        }
        if (enabled) {
            mHandler.removeMessages(MSG_HIDE_BARS);
            UsageStatistics.onContentViewChanged(
                    UsageStatistics.COMPONENT_GALLERY, "FilmstripPage");
        } else {
            refreshHidingMessage();
            if (mAppBridge == null || mCurrentIndex > 0) {
                UsageStatistics.onContentViewChanged(
                        UsageStatistics.COMPONENT_GALLERY, "SinglePhotoPage");
            } else {
                UsageStatistics.onContentViewChanged(
                        UsageStatistics.COMPONENT_CAMERA, "Unknown"); // TODO
            }
        }
        // SPRD: Add 20141211 Spreadst of bug379599, update title when switching mode
        updateTitle(enabled);
    }

    private void transitionFromAlbumPageIfNeeded() {
        TransitionStore transitions = mActivity.getTransitionStore();

        int albumPageTransition = transitions.get(
                KEY_ALBUMPAGE_TRANSITION, MSG_ALBUMPAGE_NONE);

        if (albumPageTransition == MSG_ALBUMPAGE_NONE && mAppBridge != null
                && mRecenterCameraOnResume) {
            // Generally, resuming the PhotoPage when in Camera should
            // reset to the capture mode to allow quick photo taking
            mCurrentIndex = 0;
            mPhotoView.resetToFirstPicture();
        } else {
            int resumeIndex = transitions.get(KEY_INDEX_HINT, -1);
            if (resumeIndex >= 0) {
                if (mHasCameraScreennailOrPlaceholder) {
                    // Account for preview/placeholder being the first item
                    resumeIndex++;
                }
                if (resumeIndex < mMediaSet.getMediaItemCount()) {
                    mCurrentIndex = resumeIndex;
                    mModel.moveTo(mCurrentIndex);
                }
            }
        }

        if (albumPageTransition == MSG_ALBUMPAGE_RESUMED) {
            mPhotoView.setFilmMode(mStartInFilmstrip || mAppBridge != null);
        } else if (albumPageTransition == MSG_ALBUMPAGE_PICKED) {
            mPhotoView.setFilmMode(false);
        }
    }

    @Override
    protected void onResume() {
        GalleryUtils.start(this.getClass(), "onResume");
        super.onResume();
        Log.d(TAG, "onResume " + this);
        EventBus.getDefault().register(this);
        if (mToolbarnew.getVisibility() == View.VISIBLE) {
            mPhotoControlBottomBar.setVisibility(View.VISIBLE);
            mShowBars = true;

            mToolbarnew.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mCameraAlbum && mActivity.getStateManager().getStateCount() == 1 && !mTreatBackAsUp) {
                        mActivity.finish();
                    } else {
                        if (mCameraAlbum && mBokehSaveManager.isBokehSaveing()) {
                            Toast.makeText(mActivity, R.string.blur_image_wait, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        onUpPressed();
                    }
                }
            });
        }
        if (mModel == null) {
            mActivity.getStateManager().finishState(this);
            return;
        }
        if (mCurrentPhoto != null && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
            ((LocalImage) mCurrentPhoto).setBurstCountUpdateListener(new LocalImage.BurstCountUpdateListener() {
                @Override
                public void onBurstCountUpdate(int burstCount) {
                    refreshBottomControlsWhenReady();
                }
            });
        }

        //SPRD: bug 618217, Select item of Music dialog no change
        mUserSelected = GalleryUtils.getSelected(mActivity.getAndroidContext());
        transitionFromAlbumPageIfNeeded();

        mActivity.getGLRoot().freeze();
        mIsActive = true;
        setContentPane(mRootPane);
        // hideBars();
        mModel.resume();
        mPhotoView.resume();
        /* SPRD: Modify 20141211 Spreadst of bug379599, ActionBar options initialized by updateTitle(). @{ */
        // mActionBar.setDisplayOptions(
        //      ((mSecureAlbum == null) && (mSetPathString != null)), false);
        updateTitle(mPhotoView.getFilmMode());
        /* @} */
        mActionBar.addOnMenuVisibilityListener(mMenuVisibilityListener);
        refreshBottomControlsWhenReady();
        if (mShowSpinner && mPhotoView.getFilmMode()) {
            mActionBar.enableAlbumModeMenu(
                    GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
        }
        if (!mShowBars) {
            // mActionBar.hide();
            //mActivity.getGLRoot().setLightsOutMode(true);
        }
        boolean haveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        if (haveImageEditor != mHaveImageEditor) {
            mHaveImageEditor = haveImageEditor;
            updateMenuOperations();
        }
        if (mIsFlip) {
            mCameraUtil.showSurfaceView();
            mCameraUtil.openCamera();
        }
        mRecenterCameraOnResume = true;
        mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);
        GalleryUtils.end(this.getClass(), "onResume");
    }


    @Override
    protected void onDestroy() {
        mRootPane.removeComponent(mPhotoView);
        // SPRD: add to support play gif
        mPhotoView.destory();
//        mToolbar.setVisibility(View.VISIBLE);
//        mActivity.setActionBar(mToolbar);
//        mToolbarnew.setVisibility(View.GONE);
        /* SPRD: Drm feature start @{ */
        PhotoPageUtils.getInstance().onDrmDestroy();
        /* SPRD: Drm feature end @} */
        if (mAppBridge != null) {
            mAppBridge.setServer(null);
            mScreenNailItem.setScreenNail(null);
            mAppBridge.detachScreenNail();
            mAppBridge = null;
            mScreenNailSet = null;
            mScreenNailItem = null;
        }
        mActivity.getGLRoot().setOrientationSource(null);
        if (mBottomControls != null) {
            mBottomControls.cleanup();
        }
        if (mTopControls != null) {
            mTopControls.cleanup();
        }

        // Remove all pending messages.
        mHandler.removeCallbacksAndMessages(null);
        Log.i(TAG, "onDestroy " + this);
        ((GLRootView) mActivity.getGLRoot()).setOnSystemUiVisibilityChangeListener(null);
        mProgressBar.setVisibility(View.GONE);
        mBokehSaveManager.quit();
        super.onDestroy();
    }

    private class MyDetailsSource implements DetailsSource {

        @Override
        public MediaDetails getDetails() {
            return mModel.getMediaItem(0).getDetails();
        }

        @Override
        public int size() {
            return mMediaSet != null ? mMediaSet.getMediaItemCount() : 1;
        }

        @Override
        public int setIndex() {
            return mModel.getCurrentIndex();
        }
    }

    @Override
    public void onAlbumModeSelected(int mode) {
        if (mode == GalleryActionBar.ALBUM_GRID_MODE_SELECTED) {
            switchToGrid();
        }
    }

    @Override
    public void refreshBottomControlsWhenReady() {
        if (mBottomControls == null) {
            return;
        }
        MediaObject currentPhoto = mCurrentPhoto;
        if (currentPhoto == null) {
            mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, 0, 0, currentPhoto).sendToTarget();
        } else {
            currentPhoto.getPanoramaSupport(mRefreshBottomControlsCallback);
        }
    }

    private void updatePanoramaUI(boolean isPanorama360) {
        Menu menu = mActionBar.getMenu();

        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) {
            return;
        }

        MenuExecutor.updateMenuForPanorama(menu, isPanorama360, isPanorama360);

        if (isPanorama360) {
            MenuItem item = menu.findItem(R.id.action_share);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                item.setTitle(mActivity.getResources().getString(R.string.share_as_photo));
            }
        } else if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_SHARE) != 0) {
            MenuItem item = menu.findItem(R.id.action_share);
//            if (item != null) {
//                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//                item.setTitle(mActivity.getResources().getString(R.string.share));
//            }
        }
    }

    @Override
    public void onUndoBarVisibilityChanged(boolean visible) {
        refreshBottomControlsWhenReady();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
        final long timestampMillis = mCurrentPhoto.getDateInMs();
        final String mediaType = getMediaTypeString(mCurrentPhoto);
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_GALLERY,
                UsageStatistics.ACTION_SHARE,
                mediaType,
                timestampMillis > 0
                        ? System.currentTimeMillis() - timestampMillis
                        : -1);
        return false;
    }

    private static String getMediaTypeString(MediaItem item) {
        if (item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
            return "Video";
        } else if (item.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE) {
            return "Photo";
        } else {
            return "Unknown:" + item.getMediaType();
        }
    }

    /* SPRD: Drm feature start @{ */
    public void showDrmDetails(int index) {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, new MyDetailsSource());
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

    /**
     * SPRD: Add 20141211 Spreadst of bug379599, update title by mode value. @{
     *
     * @param isFilmMode
     */
    private void updateTitle(boolean isFilmMode) {
        if (isFilmMode) {
            mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : "");
            mActionBar.setDisplayOptions(
                    ((mSecureAlbum == null) && (mSetPathString != null)), false);
        } else {
            mActionBar.setTitle(mCurrentPhoto != null ? mCurrentPhoto.getName() : "");
            mActionBar.setDisplayOptions(
                    ((mSecureAlbum == null) && (mSetPathString != null)), true);
        }
    }
    /* @} */

    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private void showSelectMusicDialog() {
        if (mConfirmDialogListener != null) {
            mConfirmDialogListener.onConfirmDialogShown();
        }
        final Context context = mActivity.getAndroidContext();
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        final String musicUri = GalleryUtils.getSlideMusicUri(context);
        int pos = 0;
        // /* SPRD: bug 566432, bug 569414 User defined cannot choose music @{ */
        if (musicUri != null && !musicUri.isEmpty()) {
            pos = mUserSelected;
        }
        //  /* @} */
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
//                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
//                                activity.getString(R.string.slideshow_music));
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
                        /* SPRD: bug 566432, User defined cannot choose music @{ */
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
                if (mConfirmDialogListener != null) {
                    mConfirmDialogListener.onConfirmDialogDismissed(true);
                }
            }
        };
        DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                if (mConfirmDialogListener != null) {
                    mConfirmDialogListener.onConfirmDialogDismissed(true);
                }
            }
        };
        dialog.setSingleChoiceItems(items, pos, listener);
        dialog.setPositiveButton(R.string.cancel, listener);
        dialog.setOnCancelListener(cancelListener);
        dialog.create().show();
    }
    /* @} */

    /* SPRD: Add for bug535110 new feature,  support play audio picture @{ */
    private MediaPlayer mMediaPlayer;

    private FileInputStream mFis;

    private void releasePlayer() {
        if (mMediaPlayer != null) {
            Log.e(TAG, "releasePlayer");
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            //SPRD : fix bug 604671 show voice photo is different from camera.
            if (mPhotoVoiceProgress != null) {
                mPhotoVoiceProgress.stopShowTime();
            }
            if (mFis != null) {
                Utils.closeSilently(mFis);
                mFis = null;
            }
            abandonAudioFocus();
        }
    }

    @Override
    public int getTime() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    private void playPhotoVoice(String path, MediaItem mediaItem) {
        if (null == mMediaPlayer) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0) {
                    //SPRD : fix bug 604671 show voice photo is different from camera.
                    if (mPhotoVoiceProgress != null) {
                        float duration = arg0.getDuration();
                        int currentPosition = arg0.getCurrentPosition();
                        Log.d(TAG, "onCompletion: " + duration + "--" + currentPosition);
                        mPhotoVoiceProgress.setTime((int) Math.max(1, (duration + PhotoVoiceProgress.TIME_DELTA) / 1000));
                        mPhotoVoiceProgress.setTimeListener(null);
                    }
                    abandonAudioFocus();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
                    //SPRD : fix bug 604671 show voice photo is different from camera.
                    if (mPhotoVoiceProgress != null) {
                        mPhotoVoiceProgress.stopShowTime();
                    }
                    abandonAudioFocus();
                    return false;
                }
            });
        }

        if (path != null && mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.reset();
                //SPRD : fix bug 604671 show voice photo is different from camera.
                if (mPhotoVoiceProgress != null) {
                    mPhotoVoiceProgress.stopShowTime();
                }
                abandonAudioFocus();
                if (mFis != null) {
                    Utils.closeSilently(mFis);
                    mFis = null;
                }
                Log.e(TAG, "playPhotoVoice isPlaying , reset stop play");
                return;
            }
            File voiceFile = new File(path);
            if (!voiceFile.exists()) {
                Log.e(TAG, "playPhotoVoice path = " + path + " does not exist!");
                return;
            }
            try {
                if (mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                    mToast = ToastUtil.showMessage(mActivity, mToast, R.string.play_audio_failed, Toast.LENGTH_SHORT);
                    return;
                }
                if (GalleryStorageUtil.isInInternalStorage(path)) {
                    mFis = new FileInputStream(path);
                } else {
                    mFis = (FileInputStream) SdCardPermission.createExternalInputStream(path);
                }
                FileDescriptor fd = mFis.getFD();
                mPhotoVoiceProgress.setTimeListener(this);
                mMediaPlayer.reset();
                mMediaPlayer.setDataSource(fd, mediaItem.getJpegSize(), mFis.available());
                mMediaPlayer.prepare();
                //SPRD : fix bug 604671 show voice photo is different from camera.
                if (mPhotoVoiceProgress != null) {
                    mPhotoVoiceProgress.setTotalTime(mMediaPlayer.getDuration());
                    mPhotoVoiceProgress.setFocusable(false);
                    mPhotoVoiceProgress.setClickable(false);
                    mPhotoVoiceProgress.startShowTime();
                }
                mMediaPlayer.start();
            } catch (Exception e) {
                Log.e(TAG, "playPhotoVoice Exception e = " + e.toString());
                if (mFis != null) {
                    Utils.closeSilently(mFis);
                    mFis = null;
                }
            }
            Log.e(TAG, "playPhotoVoice play path = " + path);
        } else {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                //SPRD : fix bug 604671 show voice photo is different from camera.
                if (mPhotoVoiceProgress != null) {
                    mPhotoVoiceProgress.stopShowTime();
                }
                abandonAudioFocus();
                if (mFis != null) {
                    Utils.closeSilently(mFis);
                    mFis = null;
                }
            }
        }
    }

    private void playPhotoVoiceEx(final String path, final MediaItem mediaItem) {
        Log.d(TAG, "playPhotoVoiceEx: path=" + path);
        if (path == null ||
                GalleryStorageUtil.isInInternalStorage(path) ||
                SdCardPermission.hasStoragePermission(path)) {
            playPhotoVoice(path, mediaItem);
        } else {
            SdCardPermissionListener listener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    playPhotoVoice(path, mediaItem);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(mActivity,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.i(TAG, " access permission failed");
                                }
                            });
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(path);
            SdCardPermission.requestSdcardPermission(mActivity, storagePaths, (GalleryActivity) mActivity, listener);
        }
    }

    OnAudioFocusChangeListener afChangeListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mMediaPlayer != null) {
                        mMediaPlayer.reset();
                        //SPRD : fix bug 604671 show voice photo is different from camera.
                        if (mPhotoVoiceProgress != null) {
                            mPhotoVoiceProgress.stopShowTime();
                        }
                        if (mFis != null) {
                            Utils.closeSilently(mFis);
                            mFis = null;
                        }
                    }
            }
        }

    };

    private void abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(afChangeListener);
    }
    /* @} */


    /*set wallpaper/slidemusic */

    private Intent createWallpagerIntet(MediaObject mediaObject) {
        int mimeType = mediaObject.getMediaType();
        Path path = mediaObject.getPath();
        Intent intent = new Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(mediaObject.getContentUri(),
                MenuExecutor.getMimeType(mimeType));
        return intent;
    }

    private void showMenuPop() {
        View contentView = View.inflate(mActivity,
                R.layout.popup_menu_item, null);
        View.OnClickListener listener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.popup_menu_details:
                        if (mShowDetails) {
                            hideDetails();
                        } else {
                            showDetails();
                        }
                        break;
                    case R.id.popup_menu_slideshow:
                        GLRoot root = mActivity.getGLRoot();
                        root.lockRenderThread();
                        try {
                            MediaItem current = mModel.getMediaItem(0);

                            if (current == null) {
                                // item is not ready, ignore
                                break;
                            }
                            int currentIndex = mModel.getCurrentIndex();
                            Path path = current.getPath();
                            Bundle data = new Bundle();
                            data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath().toString());
                            data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
                            data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
                            data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                            mActivity.getStateManager().startStateForResult(
                                    SlideshowPage.class, REQUEST_SLIDESHOW, data);
                        } finally {
                            root.unlockRenderThread();
                        }
                        break;
                    case R.id.popup_menu_backgroudmusic:
                        showSlideMusicPop();
                        break;
                    case R.id.popup_menu_setAs:
                        Intent intent = createWallpagerIntet(mCurrentPhoto);
                        Log.d(TAG, "  popup_menu_setAs intent = " + intent);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.putExtra("mimeType", intent.getType());
                            mActivity.startActivity(Intent.createChooser(
                                    intent, mActivity.getString(R.string.set_as)));
                        }
                        break;
                    default:
                        break;
                }

                dismissMenuPop();
            }
        };
        contentView.findViewById(R.id.popup_menu_details).setOnClickListener(listener);
        contentView.findViewById(R.id.popup_menu_slideshow).setOnClickListener(listener);
        contentView.findViewById(R.id.popup_menu_backgroudmusic).setOnClickListener(listener);
        contentView.findViewById(R.id.popup_menu_setAs).setOnClickListener(listener);


        mPopupWindow = new PopupWindow(contentView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
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


    private void showSlideMusicPop() {

        View contentView = View.inflate(mActivity, R.layout.popup_slidemusic_menu, null);

        Intent intent = new Intent();

        View.OnClickListener listener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                switch (v.getId()) {
                    case R.id.slide_system_button:
                        setSystemRingtone();
                        break;
                    case R.id.slide_user_button:
                        setDefinedRingtone();
                        break;
                    default:
                        break;
                }
                dismissSlideMusicPop();
            }
        };

        contentView.findViewById(R.id.slide_system_button).setOnClickListener(listener);
        contentView.findViewById(R.id.slide_user_button).setOnClickListener(listener);

        mSlideMusicWindow = new PopupWindow(contentView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, true);
        mSlideMusicWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        mSlideMusicWindow.setFocusable(true);
        mSlideMusicWindow.setOutsideTouchable(true);
        mSlideMusicWindow.showAtLocation(contentView, Gravity.BOTTOM, 0, 0);

    }

    private void dismissSlideMusicPop() {
        if (mSlideMusicWindow != null) {
            mSlideMusicWindow.dismiss();
        }

    }

    private void setSystemRingtone() {
        Intent intent = new Intent();
        mPos = SELECT_MUSIC;
        intent.setAction(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.setComponent(new ComponentName("com.android.providers.media",
                "com.android.providers.media.RingtonePickerActivity"));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                mActivity.getString(R.string.slidemusic));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_RINGTONE);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                GalleryUtils.getSlideMusicUri(mActivity));
        mActivity.startActivityForResult(intent, REQUEST_SLIDESHOW_RINGTONE);
    }

    private void setDefinedRingtone() {
        Intent intent = new Intent();
        mPos = USER_DEFINED_MUSIC;
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.setType("application/ogg");
        intent.setType("application/x-ogg");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                GalleryUtils.getSlideMusicUri(mActivity));
        mActivity.startActivityForResult(intent, REQUEST_SLIDESHOW_MUSIC);
    }

    private void launchBurstActivity() {
        Intent intent = new Intent(mActivity, BurstActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setDataAndType(mCurrentPhoto.getContentUri(), mCurrentPhoto.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra("media_type", mCurrentPhoto.getMediaType());
        mActivity.startActivity(intent);
    }

    private void launchRefocusActivity() {
        Log.d(TAG, "launchRefocusActivity");
        if (mActivity == null || mCurrentPhoto == null) {
            return;
        }
        int refocusPhotoWidth = mCurrentPhoto.getWidth();
        int refocusPhotoHeight = mCurrentPhoto.getHeight();
        String path = mCurrentPhoto.getFilePath();
        Intent intent = new Intent(mActivity, RefocusEditActivity.class)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setDataAndType(mCurrentPhoto.getContentUri(), mCurrentPhoto.getMimeType())
                .putExtra(RefocusEditActivity.SRC_PATH, path)
                .putExtra(RefocusEditActivity.SRC_WIDTH, refocusPhotoWidth)
                .putExtra(RefocusEditActivity.SRC_HEIGHT, refocusPhotoHeight)
                .putExtra(KEY_SECURE_CAMERA, mSecureCamera);
        try {
            if (mSecureCamera) {
                mActivity.startActivityForResult(intent, REQUEST_EDIT_REFOCUS);
            } else {
                mActivity.startActivity(intent);
            }

        } catch (Exception e) {
            Log.e(TAG, "launchRefocusActivity failed", e);
        }
    }

    @Override
    public void onShareClick(View view) {
        if (mModel == null) {
            return;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }

        launchShareIntent();
    }

    @Override
    public void onEditClick(View view) {
        if (mModel == null) {
            return;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }

        launchPhotoEditor();
    }

    @Override
    public void onDeleteClick(View view) {
        if (mModel == null) {
            return;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }
        if (!mIsActive) {
            return;
        }

        Path path = current.getPath();
        String confirmMsg = mActivity.getResources().getQuantityString(
                R.plurals.delete_selection, 1);
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(path);
        mMenuExecutor.onMenuClicked(R.id.action_delete, confirmMsg, mConfirmDialogListener);
    }

    @Override
    public void onDetailsClick(View view) {
        if (mModel == null) {
            return;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }

        if (!DetailsHelper.SHOW_IN_ACTIVITY) {
            if (mShowDetails) {
                hideDetails();
            } else {
                showDetails();
            }
        } else {
            GalleryUtils.startDetailsActivity(mActivity, new MyDetailsSource());
        }
    }

    @Override
    public void onDistanceClick(View view) {
        if (mModel == null) {
            return;
        }
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }
        startDistanceActivity(current.getPlayUri(), current);
    }

    @Override
    public void onImageBlendingClick(View view) {
        if (mModel == null) {
            return;
        }
        MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }
        startImageBlendingActivity(mActivity, current.getContentUri(), current);
    }

    @Override
    public void onTrashRestoreClick(View view) {

    }

    @Override
    public void onTrashDeleteClick(View view) {

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void handleMimeTypeMsg(MimeTypeMsg msg) {
        refreshBottomControlsWhenReady();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (mIsFlip) {
            Log.i(TAG, " onKeyDown keyCode = " + keyCode);
            if (KeyEvent.KEYCODE_VOLUME_UP == keyCode) {
                mSelectVolueUp = true;
                mSelectVolueDown = false;
                mCameraUtil.setVolueKey(mSelectVolueUp, mSelectVolueDown);
                return true;
            } else if (KeyEvent.KEYCODE_VOLUME_DOWN == keyCode) {
                mSelectVolueUp = false;
                mSelectVolueDown = true;
                mCameraUtil.setVolueKey(mSelectVolueUp, mSelectVolueDown);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (mIsFlip) {
            if (KeyEvent.KEYCODE_VOLUME_UP == keyCode || KeyEvent.KEYCODE_VOLUME_DOWN == keyCode) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    private void hideStatusBar() {
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void showStatusBar() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void reloadPicture() {
        MediaItem mediaItem = mModel.getMediaItem(0);
        if (mediaItem == null) {
            return;
        }
        boolean bokehThumb = (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_THUMB);
        if (mHandler.hasMessages(MSG_UPDATE_THUMB_FILE_FLAG)) {
            mHandler.removeMessages(MSG_UPDATE_THUMB_FILE_FLAG);
        }
        if (bokehThumb) {
            Message message = mHandler.obtainMessage();
            message.what = MSG_UPDATE_THUMB_FILE_FLAG;
            message.obj = mediaItem;
            mHandler.sendMessageDelayed(message, THUMB_WAIT_TIME_OUT);
        }
        mHandler.sendEmptyMessage(MSG_UPDATE_OPERATIONS);
    }

    @Override
    public void reloadNoBokehRefocusPicture() {
        Log.d(TAG, "FullPicture reload,it is refocus img, no Save bokeh");
        mHandler.sendEmptyMessage(MSG_UPDATE_OPERATIONS);
        MediaItem mediaItem = mModel.getMediaItem(0);
        if (mActivity.isFinishing() || mediaItem == null) {
            return;
        }
        try {
            if (mSupportBokeh) {
                Log.i(TAG, "reloadNoBokehRefocusPicture type =  " + mediaItem.getMediaType());
                if (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
                    RefocusUtils.setBokehType(mCurrentPhoto.getMediaType());
                }
                mBokehSaveManager.startBokehSave(mActivity, mediaItem, this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void bokehSaveDone(String filePath) {
        MediaItem mediaItem = mBokehSaveManager.getMediaItemByPath(filePath);
        if (mediaItem == null) {
            return;
        }
        Message msg = mHandler.obtainMessage(MSG_BOKEH_SAVE_DONE);
        msg.obj = mediaItem;
        mHandler.sendMessage(msg);
    }

    @Override
    public void pictureBokehDone(String filePath, byte[] bokehPicture) {
        Bundle bundle = new Bundle();
        bundle.putByteArray("bokehPicture", bokehPicture);
        bundle.putString("filePath", filePath);
        Message msg = mHandler.obtainMessage(MSG_BOKEH_PICTURE_DONE);
        msg.obj = bundle;
        mHandler.sendMessage(msg);
    }

    @Override
    public void bokehSaveError(String filePath) {
        MediaItem mediaItem = mBokehSaveManager.getMediaItemByPath(filePath);
        if (mediaItem == null) {
            return;
        }
        Message msg = mHandler.obtainMessage(MSG_BOKEH_SAVE_ERROR);
        msg.obj = mediaItem;
        mHandler.sendMessage(msg);
    }

    private void updateFileFlag(MediaItem mediaItem, boolean isThumb) {
        if (mediaItem == null) {
            return;
        }
        Uri uri = mediaItem.getContentUri();
        if (uri == null) {
            return;
        }
        int mediaType = mediaItem.getMediaType();
        if ((isThumb && mediaType != MediaObject.MEDIA_TYPE_IMAGE_THUMB) ||
                (!isThumb && mediaType == MediaObject.MEDIA_TYPE_IMAGE_THUMB)) {
            return;
        }

        Log.d(TAG, "updateFileFlag, start, isThumb = " + isThumb);
        ContentValues values = new ContentValues();
        // update file_flag to IMG_TYPE_MODE_NORMAL
        values.put("file_flag", LocalImage.IMG_TYPE_MODE_NORMAL);
        mApplication.getContentResolver().update(uri, values, null, null);
        Log.d(TAG, "updateFileFlag, end");
        Toast.makeText(mActivity, R.string.blur_save_fail, Toast.LENGTH_SHORT).show();
    }

    private GLRoot getGLRoot() {
        return mActivity.getGLRoot();
    }

    //长按播放motion photo
    @Override
    public void onLongPress() {
        if (mCurrentPhoto != null && mCurrentPhoto.isMotionPhoto()) {
            playMotionPhoto(mCurrentPhoto);
        }
    }

    //播放motion photo
    private void playMotionPhoto(MediaItem item) {
        getGLRoot().playGLVideo(item);
    }

    //当播放状态切换后, 会收到消息
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGLVideoStateChanged(GLVideoStateMsg msg) {
        Log.d(TAG, "onGLVideoStateChanged");
        updateMenuOperations();
    }
}

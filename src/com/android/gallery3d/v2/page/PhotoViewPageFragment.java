package com.android.gallery3d.v2.page;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.StateTransitionAnimation;
import com.android.gallery3d.app.ActionBarTopControls;
import com.android.gallery3d.app.AppBridge;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.app.MuteVideo;
import com.android.gallery3d.app.OrientationManager;
import com.android.gallery3d.app.PhotoPageBottomControls;
import com.android.gallery3d.app.PhotoVoiceProgress;
import com.android.gallery3d.app.SprdCameraUtil;
import com.android.gallery3d.app.TransitionStore;
import com.android.gallery3d.app.TrimVideo;
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
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.msg.MimeTypeMsg;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoControlBottomBar;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PreparePageFadeoutTexture;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.util.UsageStatistics;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.data.GLVideoStateMsg;
import com.android.gallery3d.v2.data.PhotoViewPageDataAdapter;
import com.android.gallery3d.v2.data.SinglePhotoViewPageDataAdapter;
import com.android.gallery3d.v2.discover.data.PeopleMergeAlbum;
import com.android.gallery3d.v2.discover.data.ThingsAlbum;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.trash.data.TrashItem;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class PhotoViewPageFragment extends BasePageFragment implements PhotoPageBottomControls.Delegate,
        PhotoVoiceProgress.TimeListener, PhotoView.Listener, AppBridge.Server,
        ShareActionProvider.OnShareTargetSelectedListener, PhotoControlBottomBar.OnPhotoControlBottomBarMenuClickListener,
        BokehSaveManager.BokehSaveCallBack, ActionBarTopControls.ActionBarListener,
        BasePageFragment.PageDataBackListener, UriImage.OnUriImageListener {
    private static final String TAG = PhotoViewPageFragment.class.getSimpleName();

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
    private static final int MSG_FINISH_STATE = 18;
    public static final int MSG_CONSUME_DRM_RIGHTS = 19;
    private static final int MSG_UPDATE_OPERATIONS = 20;
    private static final int MSG_UPDATE_FILE_FLAG = 21;
    private static final int MSG_BOKEH_SAVE_DONE = 22;
    private static final int MSG_BOKEH_PICTURE_DONE = 23;
    private static final int MSG_BOKEH_SAVE_ERROR = 24;

    private static final int HIDE_BARS_TIMEOUT = 3500;
    private static final int THUMB_WAIT_TIME_OUT = 8000;
    private static final long CAMERA_SWITCH_CUTOFF_THRESHOLD_MS = 300;
    private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_CROP = 2;
    private static final int REQUEST_CROP_PICASA = 3;
    private static final int REQUEST_EDIT = 4;
    private static final int REQUEST_PLAY_VIDEO = 5;
    private static final int REQUEST_TRIM = 6;
    private static final int REQUEST_EDIT_REFOCUS = 10;

    public static final String KEY_ALBUMPAGE_TRANSITION = "albumpage-transition";
    public static final int MSG_ALBUMPAGE_NONE = 0;
    public static final int MSG_ALBUMPAGE_STARTED = 1;
    public static final int MSG_ALBUMPAGE_RESUMED = 2;
    public static final int MSG_ALBUMPAGE_PICKED = 4;

    public static final String ACTION_SIMPLE_EDIT = "action_simple_edit";
    private static final String FLAG_GALLERY = "startByGallery";

    private static final String ACTION_REFOCUS_EDIT = "com.android.sprd.gallery3d.refocusedit";
    private static final String ACTION_DISTANCE_EDIT = "com.android.sprd.gallery3d.distance";
    private static final String ACTION_IMAGEBLENDING_EDIT = "com.android.sprd.gallery3d.imageblending";

    private Toolbar mToolbar;
    private boolean mIsMenuVisible;
    private Handler mHandler;

    private GLRoot mGLRoot;

    private PhotoView mPhotoView;
    private boolean mStartInFilmstrip;
    private MediaItem mCurrentPhoto = null;
    private PhotoPageBottomControls mBottomControls;
    private boolean mIsPanorama;
    private boolean mIsPanorama360;
    private boolean mShowDetails;
    private DetailsHelper mDetailsHelper;
    private SecureAlbum mSecureAlbum;
    private String mSetPathString;
    private boolean mRecenterCameraOnResume = true;
    private boolean mHasCameraScreennailOrPlaceholder = false;

    private AppBridge mAppBridge;
    private long mDeferUpdateUntil = Long.MAX_VALUE;
    private static final long DEFERRED_UPDATE_MS = 250;
    private boolean mDeferredUpdateWaiting = false;
    private boolean mSkipUpdateCurrentPhoto = false;
    private long mCameraSwitchCutoff = 0;

    private FilterDeleteSet mMediaSet;
    private PhotoViewPageFragment.Model mModel;

    private BokehSaveManager mBokehSaveManager;
    private long[] mSecureItemIds;

    private boolean mIsActive;
    private boolean mShowBars = true;
    private Menu mMenu;
    private boolean mhasImage = true;
    private final boolean mTrimvideoEnable = System.getProperty("ro.config.trimvideo", "disable") == "enable";

    private OrientationManager mOrientationManager;

    private boolean mDrmFirstOpen;
    private boolean mStartFromWidget = false;
    private final Uri[] mNfcPushUris = new Uri[1];
    private boolean mSupportBokeh;
    private boolean mSupportBlur;

    private ProgressBar mProgressBar;
    private GalleryApp mApplication;

    private int mCurrentIndex = 0;
    private volatile boolean mActionBarAllowed = true;

    private PhotoControlBottomBar mPhotoControlBottomBar;

    private final Animation mToolbarAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mToolbarAnimOut = new AlphaAnimation(1f, 0f);
    private final Animation mPhotoControlBottomBarAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mPhotoControlBottomBarAnimOut = new AlphaAnimation(1f, 0f);

    private boolean mReadOnlyView = false;
    private boolean mHaveImageEditor;

    private static boolean isGmsVersion = GalleryUtils.isSprdPhotoEdit();
    private boolean mPlayFlag = true;
    private boolean mSingleItemOnly;
    private boolean mSecureCamera = false;
    private boolean mCameraAlbum = false;
    private long mSecureCameraEnterTime = -1L;

    private MediaPlayer mMediaPlayer;
    private PhotoVoiceProgress mPhotoVoiceProgress;
    private FileInputStream mFis;
    private AudioManager mAudioManager;
    private Toast mToast;

    private ActionBarTopControls mTopControls;

    private SnailItem mScreenNailItem;
    private SnailAlbum mScreenNailSet;

    private int mUserSelected = 0;
    private int mPos;

    private Path mDeletePath;
    private boolean mDeleteIsFocus;
    private MenuExecutor mMenuExecutor;
    private SelectionManager mSelectionManager;

    private static final int REQUEST_SLIDESHOW_MUSIC = 7;
    private static final int REQUEST_SLIDESHOW_RINGTONE = 8;
    private static final int REQUEST_BLENDING = 9;
    private static final int NONE_MUSIC = 0;
    private static final int SELECT_MUSIC = 1;
    private static final int USER_DEFINED_MUSIC = 2;

    private String mOriginalSetPathString;

    private PopupWindow mPopupWindow;
    private PopupWindow mSlideMusicWindow;

    private final MyMenuVisibilityListener mMenuVisibilityListener =
            new MyMenuVisibilityListener();

    private SprdCameraUtil mCameraUtil;
    private boolean mTreatBackAsUp;
    private boolean mShowSpinner;

    private int mLastSystemUiVis = 0;
    private boolean mIsFlip = false;
    private SharedPreferences mPref;
    private float[] mBackgroundColor;

    private boolean mComingAnimation;

    private StateTransitionAnimation.Transition mNextTransition =
            StateTransitionAnimation.Transition.PhotoIncoming;
    private StateTransitionAnimation mIntroAnimation;

    private boolean mNeedUpdate = false;
    private AlertDialog mDialog;

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

    public interface Model extends PhotoView.Model {
        void resume();

        void pause();

        boolean isEmpty();

        void setCurrentPhoto(Path path, int indexHint);
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
            if (mShowDetails) {
                mDetailsHelper.layout(left, mToolbar.getHeight(), right, bottom);
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        mComingAnimation = true;
        setHasOptionsMenu(true);
        setCoverVisible(true);
        setStatusBarColor(R.color.transparent);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_view_page, container, false);
        setNavigationVisible(false);

        mGLRoot = v.findViewById(R.id.gl_root_view);

        mToolbar = v.findViewById(R.id.photo_view_toolbar);
        mToolbar.setTitle("");
        mToolbar.setOverflowIcon(getActivity().getDrawable(R.drawable.ic_more_vert_white));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mToolbar.getLayoutParams();
        params.topMargin = GalleryUtils.getStatusBarHeight(getActivity());
        mToolbar.setLayoutParams(params);

        ((GalleryActivity2) getActivity()).setOtherNavigation(mToolbar);
        setStatusBarVisibleWhite();

        if (isNextPage()) {//隐藏底部Tab
            setTabsVisible(false);
        }
        ///
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(getActivity().getResources().getColor(R.color.photo_background));
        mSupportBlur = RefocusUtils.isSupportBlur();
        mSupportBokeh = RefocusUtils.isSupportBokeh();
        mPhotoControlBottomBar = v.findViewById(R.id.photo_control_bottom_bar);
        mPhotoControlBottomBar.setOnPhotoControlBottomBarMenuClickListener(this);
        mProgressBar = v.findViewById(R.id.thumb_loading);
        mSelectionManager = new SelectionManager(getActivity(), true);
        mMenuExecutor = new MenuExecutor(getActivity(), mGLRoot, mSelectionManager);
        mUserSelected = GalleryUtils.getSelected(getActivity());
        mAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        mPhotoView = new PhotoView(getActivity(), v, mGLRoot);
        mPhotoView.setListener(this);
        mPhotoView.setToolbar(mToolbar);
        mRootPane.addComponent(mPhotoView);

        mApplication = GalleryAppImpl.getApplication();
        mBokehSaveManager = BokehSaveManager.getInstance();
        mOrientationManager = ((GalleryActivity2) getActivity()).getOrientationManager();
        mGLRoot.setOrientationSource(mOrientationManager);
        PhotoPageUtils.getInstance().getFirstPickIsDrmPhoto();
        mHandler = new MySynchronizedHandler(mGLRoot, this);
        mCameraUtil = new SprdCameraUtil(getActivity(), mHandler, mPhotoView);

        mSetPathString = getArguments().getString(Constants.KEY_BUNDLE_MEDIA_SET_PATH);
        mReadOnlyView = getArguments().getBoolean(Constants.KEY_BUNDLE_MEDIA_ITEM_READ_ONLY);
        mSecureCamera = getArguments().getBoolean(Constants.KEY_SECURE_CAMERA, false);
        mCameraAlbum = getArguments().getBoolean(Constants.KEY_CAMERA_ALBUM, false);
        mStartFromWidget = getArguments().getBoolean(Constants.KEY_START_FROM_WIDGET, false);
        if (mSecureCamera) {
            mSecureCameraEnterTime = getArguments().getLong(Constants.KEY_SECURE_CAMERA_ENTER_TIME);
            Log.d(TAG, "onCreateView mSecureCameraEnterTime = " + mSecureCameraEnterTime);
        }
        mOriginalSetPathString = mSetPathString;

        String itemPathString = getArguments().getString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH);
        Path itemPath = itemPathString != null ?
                Path.fromString(getArguments().getString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH)) :
                null;
        mTreatBackAsUp = getArguments().getBoolean(Constants.KEY_TREAT_BACK_AS_UP, false);
        mSingleItemOnly = getArguments().getBoolean(Constants.SINGLE_ITEM_ONLY, false);
        mStartInFilmstrip = getArguments().getBoolean(Constants.KEY_START_IN_FILMSTRIP, false);
        boolean inCameraRoll = getArguments().getBoolean(Constants.KEY_IN_CAMERA_ROLL, false);
        mCurrentIndex = getArguments().getInt(Constants.KEY_BUNDLE_MEDIA_ITEM_INDEX, 0);
        String gmsVersion = StandardFrameworks.getInstances().getStringSystemProperties("ro.com.google.gmsversion", null);
        boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
        int itemCount = getArguments().getInt(Constants.KEY_BUNDLE_ALBUM_PAGE_ITEM_COUNT, 0);

        if (mSetPathString != null) {
            if (mCameraAlbum) {
                //When deleting pictures in background, we need active update albums
                DataManager.from(getContext()).onContentDirty();
                if (mSecureCamera) {
                    // secure camera mode, hide icon
                    mToolbar.setNavigationIcon(null);
                } else {
                    if (gmsVersion != null && !gmsVersion.isEmpty()
                            && !isLowRam) {
                        mToolbar.setNavigationIcon(null);
                    } else {
                        mToolbar.setNavigationIcon(R.drawable.ic_gallery_white);
                    }
                }
            }
        }

        Log.d(TAG, "onCreateView: mSetPathString = " + mSetPathString+", itemPath = " + itemPath);
        if (mSetPathString != null && !GalleryUtils.isSprdPhotoEdit()) {
            mShowSpinner = true;
            mAppBridge = getArguments().getParcelable(Constants.KEY_APP_BRIDGE);
            if (mAppBridge != null) {
                mShowBars = false;
                mHasCameraScreennailOrPlaceholder = true;
                mAppBridge.setServer(this);

                // Get the ScreenNail from AppBridge and register it.
                int id = SnailSource.newId();
                Path screenNailSetPath = SnailSource.getSetPath(id);
                Path screenNailItemPath = SnailSource.getItemPath(id);
                mScreenNailSet = (SnailAlbum) getDataManager()
                        .getMediaObject(screenNailSetPath);
                mScreenNailItem = (SnailItem) getDataManager()
                        .getMediaObject(screenNailItemPath);
                mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

                // Don't display "empty album" action item for capture intents.
                if (!mSetPathString.equals("/local/all/0")) {
                    // Check if the path is a secure album.
                    if (SecureSource.isSecurePath(mSetPathString)) {
                        mSecureAlbum = (SecureAlbum) getDataManager()
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
            } else if (inCameraRoll && GalleryUtils.isCameraAvailable(getActivity())) {
                mSetPathString = "/combo/item/{" + FilterSource.FILTER_CAMERA_SHORTCUT +
                        "," + mSetPathString + "}";
                mCurrentIndex++;
                mHasCameraScreennailOrPlaceholder = true;
            }

            MediaSet originalSet = getDataManager()
                    .getMediaSet(mSetPathString);
            if (mHasCameraScreennailOrPlaceholder && originalSet instanceof ComboAlbum) {
                // Use the name of the camera album rather than the default
                // ComboAlbum behavior
                ((ComboAlbum) originalSet).useNameOfChild(1);
            }
            mSelectionManager.setSourceMediaSet(originalSet);
            mSetPathString = "/filter/delete/{" + mSetPathString + "}";
            mMediaSet = (FilterDeleteSet) getDataManager()
                    .getMediaSet(mSetPathString);
            if (mMediaSet == null) {
                Log.w(TAG, "failed to restore " + mSetPathString);
                return v;
            }
            if (mSecureCamera) {
                mSecureItemIds = getArguments().getLongArray(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS);
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
                    return v;
                }
            }

            MediaItem item = (MediaItem) getDataManager().getMediaObject(itemPath);
            mDrmFirstOpen = item != null && item.mIsDrmFile;

            PhotoViewPageDataAdapter pda = new PhotoViewPageDataAdapter(
                    (GalleryActivity2) getActivity(), mPhotoView, mGLRoot, mMediaSet, itemPath, mCurrentIndex,
                    mAppBridge == null ? -1 : 0,
                    mAppBridge != null && mAppBridge.isPanorama(),
                    mAppBridge != null && mAppBridge.isStaticCamera(),
                    mSecureCameraEnterTime, itemCount);
            mModel = pda;
            mPhotoView.setModel(mModel);

            pda.setDataListener(new PhotoViewPageDataAdapter.DataListener() {

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
                    if (!isDetailsPageShown()) {
                        refreshHidingMessage();
                    }
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
                        getMenu().clear();
                        getMenu().close();
                        mhasImage = false;
                        if (mMediaSet.getNumberOfDeletions() == 0) {
                            if (mStartFromWidget) {
                                Log.d(TAG, "onLoadingFinished: mStartFromWidget");
                                if (getGalleryActivity2() != null) {
                                    getGalleryActivity2().onGalleryIconClicked();
                                }
                            } else {
                                onBackPressed();
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
            // UNISOC added for bug 1219278, avoid gallery crash.
            MediaItem mediaItem = null;
            if (itemPath != null) {
                mediaItem = (MediaItem) getDataManager().getMediaObject(itemPath);
            }
            mDrmFirstOpen = mediaItem != null && mediaItem.mIsDrmFile;
            if (mediaItem != null) {
                mModel = new SinglePhotoViewPageDataAdapter((GalleryActivity2) getActivity(), mPhotoView, mGLRoot, mediaItem);
                mPhotoView.setModel(mModel);
                if (mediaItem instanceof UriImage) {
                    ((UriImage) mediaItem).init(this);
                }
                updateCurrentPhoto(mediaItem);
                mShowSpinner = false;
            } else {
                Log.e(TAG, "onCreateView mediaItem is null in SinglePhotoViewPageDataAdapter");
            }
        }

        mPhotoView.setFilmMode(mStartInFilmstrip && mMediaSet.getMediaItemCount() > 1);
        RelativeLayout galleryRoot = v.findViewById(mAppBridge != null ? R.id.content : R.id.gallery_root);
        if (galleryRoot != null) {
            if (mSecureAlbum == null) {
                mBottomControls = new PhotoPageBottomControls(this, getActivity(), galleryRoot);
                mTopControls = new ActionBarTopControls(this, getActivity(), galleryRoot);
            }
        }

        ((GLRootView) mGLRoot).setOnSystemUiVisibilityChangeListener(
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

        mToolbarAnimIn.setDuration(200);
        mToolbarAnimOut.setDuration(200);
        mPhotoControlBottomBarAnimIn.setDuration(200);
        mPhotoControlBottomBarAnimOut.setDuration(200);
        mPhotoControlBottomBar.setVisibility(View.VISIBLE);
        mPref = getActivity().getSharedPreferences("flip_values", Context.MODE_PRIVATE);
        mIsFlip = mPref.getBoolean("flip_values", false);
        if (mIsFlip) {
            mCameraUtil.initialSurfaceView();
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        ((GLRootView) mGLRoot).onResume();
        setPageFragment(this);
        EventBus.getDefault().register(this);
        if (mToolbar.getVisibility() == View.VISIBLE) {
            mPhotoControlBottomBar.setVisibility(View.VISIBLE);
            mShowBars = true;
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCameraAlbum && mBokehSaveManager.isBokehSaveing()) {
                        Toast.makeText(getActivity(), R.string.blur_image_wait, Toast.LENGTH_SHORT).show();
                    } else if (mCameraAlbum) {
                        if (getGalleryActivity2() != null) {
                            getGalleryActivity2().onGalleryIconClicked();
                        }
                    } else {
                        onBackPressed();
                    }
                }
            });
        } else {
            setStatusBarHideWhite();
        }

        if (mComingAnimation) {
            mComingAnimation = false;
            PreparePageFadeoutTexture.prepareFadeOutTexture((GalleryActivity2) getActivity(), mGLRoot, mRootPane);
            RawTexture fade = ((GalleryActivity2) getActivity()).getTransitionStore().get(PreparePageFadeoutTexture.KEY_FADE_TEXTURE);
            mIntroAnimation = new StateTransitionAnimation(mNextTransition, fade);
        }

        if (mCurrentPhoto != null && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
            if (mCurrentPhoto instanceof LocalImage) {
                ((LocalImage) mCurrentPhoto).setBurstCountUpdateListener(new LocalImage.BurstCountUpdateListener() {
                    @Override
                    public void onBurstCountUpdate(int burstCount) {
                        refreshBottomControlsWhenReady();
                    }
                });
            }
        }
        mUserSelected = GalleryUtils.getSelected(getActivity());
        transitionFromAlbumPageIfNeeded();
        mGLRoot.freeze();

        mIsActive = true;

        if (mIntroAnimation != null) {
            GalleryActivity2 activity = (GalleryActivity2) getActivity();
            if (activity != null && activity.isMainIntent()) {
                mRootPane.setIntroAnimation(mIntroAnimation);
            }
            mIntroAnimation = null;
        }

        mRootPane.setBackgroundColor(mBackgroundColor);
        mGLRoot.setContentPane(mRootPane);

        if (mModel == null) {
            mToast = ToastUtil.showMessage(getActivity(), mToast, R.string.fail_to_load, Toast.LENGTH_SHORT);
            return;
        }

        if (PermissionUtil.hasPermissions(getContext())) {
            mModel.resume();
            mPhotoView.resume();
            updateTitle(mPhotoView.getFilmMode());
            if (getSupportActionBar() != null) {
                getSupportActionBar().addOnMenuVisibilityListener(mMenuVisibilityListener);
            }
            refreshBottomControlsWhenReady();
            boolean haveImageEditor = GalleryUtils.isEditorAvailable(getActivity(), "image/*");
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
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
        setPageFragment(this);
        refreshHidingMessage();
        //从详情界面返回, 刷一下界面
        mGLRoot.requestRender();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        //如果onPause了, 则停止播放视频
        mGLRoot.stopGLVideo();
        ((GLRootView) mGLRoot).onPause();
        //setPageFragment(null);
        EventBus.getDefault().unregister(this);
        mIsActive = false;
        mPhotoControlBottomBar.setVisibility(View.GONE);
        if (mCurrentPhoto != null && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
            if (mCurrentPhoto instanceof LocalImage) {
                ((LocalImage) mCurrentPhoto).setBurstCountUpdateListener(null);
            }
        }
        mGLRoot.unfreeze();
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
        if (getSupportActionBar() != null) {
            getSupportActionBar().removeOnMenuVisibilityListener(mMenuVisibilityListener);
        }
        onCommitDeleteImage();
        mMenuExecutor.dissmissDialog();
        mMenuExecutor.pause();
        if (mIsFlip) {
            mCameraUtil.closeCameraAPI2_2();
        }
        if (mMediaSet != null) {
            mMediaSet.clearDeletion();
        }
        releasePlayer();
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
        //如果onHide了, 则停止播放视频
        mGLRoot.stopGLVideo();
        //setPageFragment(null);
        mHandler.removeMessages(MSG_HIDE_BARS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setNavigationVisible(true);
        ((GalleryActivity2) getActivity()).setOtherNavigation(null);
        setStatusBarVisibleLight();

        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }
        dismissSlideMusicPop();

        if (mModel != null && mModel instanceof PhotoViewPageDataAdapter) {
            ((PhotoViewPageDataAdapter) mModel).setDataListener(null);
        }
        mRootPane.removeComponent(mPhotoView);
        if (mRootPane.isAttachedToRoot()) {
            Log.d(TAG, "onDestroyView detachFromRoot");
            mRootPane.detachFromRoot();
        } else {
            Log.e(TAG, "onDestroyView not detachFromRoot");
        }
        mPhotoView.destory();
        PhotoPageUtils.getInstance().onDrmDestroy();
        if (mAppBridge != null) {
            mAppBridge.setServer(null);
            mScreenNailItem.setScreenNail(null);
            mAppBridge.detachScreenNail();
            mAppBridge = null;
            mScreenNailSet = null;
            mScreenNailItem = null;
        }
        mGLRoot.setOrientationSource(null);
        if (mBottomControls != null) {
            mBottomControls.cleanup();
        }
        if (mTopControls != null) {
            mTopControls.cleanup();
        }

        // Remove all pending messages.
        mHandler.removeCallbacksAndMessages(null);
        ((GLRootView) mGLRoot).setOnSystemUiVisibilityChangeListener(null);
        mProgressBar.setVisibility(View.GONE);
        mBokehSaveManager.quit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        setCoverVisible(false);
        //setStatusBarColor(R.color.colorPrimaryDark); //在GalleryActivity2.java中onAnimationEnd方法里设置
        setStatusBarVisibleLight();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mhasImage) {
            return;
        }
        inflater.inflate(R.menu.photo_view_menu, menu);
        mMenu = menu;
        MenuExecutorUtils.getInstance().createDrmMenuItem(menu, getActivity());

        if (!mTrimvideoEnable) {
            menu.removeItem(R.id.action_trim);
            menu.removeItem(R.id.action_mute);
        }
        if (mSecureCamera) {
            menu.removeItem(R.id.action_slideshow);
            menu.removeItem(R.id.action_slideshow_music);
            menu.removeItem(R.id.action_setas);
        }
        mHaveImageEditor = GalleryUtils.isEditorAvailable(getActivity(), "image/*");
        updateMenuOperations();
        showBars();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mModel == null) {
            return true;
        }
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);

        if (current instanceof SnailItem) {
            return true;
        }
        if (current == null) {
            return true;
        }
        int currentIndex = mModel.getCurrentIndex();
        Path path = current.getPath();

        DataManager manager = ((GalleryActivity2) getActivity()).getDataManager();
        int action = item.getItemId();
        String confirmMsg = null;
        switch (action) {
            case R.id.action_slideshow: {
                if (!GalleryUtils.isMonkey()) {
                    slideShow(currentIndex, path);
                }
                return true;
            }
            case R.id.action_slideshow_music: {
                showSelectMusicDialog();
                return true;
            }
            case R.id.action_crop: {
                Intent intent = new Intent(CropActivity.CROP_ACTION);
                intent.setClass(getActivity(), CropActivity.class);
                intent.setDataAndType(manager.getContentUri(path), current.getMimeType())
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra("from_photo_page", true);
                intent.putExtra("crop_in_gallery", true);
                startActivityForResult(intent, PicasaSource.isPicasaImage(current)
                        ? REQUEST_CROP_PICASA
                        : REQUEST_CROP);
                return true;
            }
            case R.id.action_trim: {
                Intent intent = new Intent(getActivity(), TrimVideo.class);
                intent.setData(manager.getContentUri(path));
                intent.putExtra(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, current.getFilePath());
                startActivityForResult(intent, REQUEST_TRIM);
                return true;
            }
            case R.id.action_mute: {
                MuteVideo muteVideo = new MuteVideo(current.getFilePath(),
                        manager.getContentUri(path), getActivity());
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
                    GalleryUtils.startDetailsActivity(getActivity(), new MyDetailsSource());
                }
                return true;
            }
            case R.id.print: {
                if (current != null && current instanceof UriImage) {
                    Uri uri = current.getContentUri();
                    Log.d(TAG, "print UriImage, and uri is : " + uri);
                    if (!isValidUri(uri)) {
                        Toast.makeText(getActivity(), R.string.fail_to_load_image, Toast.LENGTH_SHORT).show();
                        getActivity().finish();
                        return true;
                    }
                }
                ((GalleryActivity2) getActivity()).printImage(manager.getContentUri(path));
                return true;
            }
            case R.id.action_delete:
                confirmMsg = getActivity().getResources().getQuantityString(
                        R.plurals.delete_selection, 1);
                return true;
            case R.id.action_setas:
                if (current != null && current instanceof UriImage) {
                    Uri uri = current.getContentUri();
                    Log.d(TAG, "action_setas UriImage, and uri is : " + uri);
                    if (!isValidUri(uri) || !GalleryUtils.isValidMtpUri(getActivity(), uri)) {
                        Toast.makeText(getActivity(), R.string.fail_to_load_image, Toast.LENGTH_SHORT).show();
                        getActivity().finish();
                        return true;
                    }
                }
                if (current == null) {
                    return true;
                }
                setAs(current.getPath());
                return true;
            case R.id.action_rotate_ccw:
            case R.id.action_rotate_cw:
            case R.id.action_show_on_map:
                return true;
            case R.id.action_share: {
                launchShareIntent();
                return true;
            }
            case R.id.action_move_out_things:
                onMoveOutThingsClicked();
                return true;
            case R.id.action_move_out_people:
                onMoveOutPeopleClicked();
                return true;
            case R.id.action_play_motion_photo:
                if (mGLRoot.isGLVideoPlaying()) {
                    mGLRoot.stopGLVideo();
                } else {
                    playMotionPhoto(current);
                }
                return true;
            default:
                return MenuExecutorUtils.getInstance().showHideDrmDetails(this,
                        item.getItemId(), currentIndex);
        }
    }

    private ActionBar getSupportActionBar() {
        if (getActivity() != null) {
            return ((GalleryActivity2) getActivity()).getSupportActionBar();
        }
        return null;
    }

    private Menu getMenu() {
        return mMenu;
    }

    private void refreshHidingMessage() {
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (!mIsMenuVisible && !mPhotoView.getFilmMode()) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
        }
    }

    private class MyMenuVisibilityListener implements ActionBar.OnMenuVisibilityListener {
        @Override
        public void onMenuVisibilityChanged(boolean isVisible) {
            mIsMenuVisible = isVisible;
            refreshHidingMessage();
        }
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private WeakReference<PhotoViewPageFragment> mWeakReference;

        public MySynchronizedHandler(GLRoot root, PhotoViewPageFragment photoViewPageFragment) {
            super(root);
            mWeakReference = new WeakReference<>(photoViewPageFragment);
        }

        @Override
        public void handleMessage(Message message) {
            PhotoViewPageFragment photoViewPageFragment = mWeakReference.get();
            if (photoViewPageFragment != null) {
                photoViewPageFragment.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            Log.d(TAG, "handleMySynchronizedHandlerMsg: parent activity is null or finishing. message.what = " + message.what);
            return;
        }
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
                mGLRoot.unfreeze();
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

                    //mActionBar.setShareIntents(panoramaIntent, shareIntent, PhotoPage.this);
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
                onBackPressed();
                break;
            }
            case MSG_STORAGE_CHANGED: {
                String path = (String) message.obj;
                break;
            }
            case MSG_CONSUME_DRM_RIGHTS: {
                if (PhotoPageUtils.getInstance().cosumeDrmRights(message, getActivity())) {
                    throw new AssertionError(message.what);
                }
                break;
            }
            case MSG_UPDATE_OPERATIONS: {
                updateProgressAndOpera();
                break;
            }
            case MSG_UPDATE_FILE_FLAG: {
                MediaItem thumbMediaItem = (MediaItem) message.obj;
                updateThumbFileFlag(thumbMediaItem);
                break;
            }
            case MSG_BOKEH_SAVE_DONE: {
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
                } else if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY){
                    mediaItem.bokehDonechangeFileFlag(LocalImage.IMG_TYPE_MODE_BOKEH_FDR);
                } else {
                    mediaItem.bokehDonechangeFileFlag(LocalImage.IMG_TYPE_MODE_BOKEH);
                }
                Log.d(TAG, "pre update bokeh button");
                updateProgressAndOpera();
                break;
            }
            case MSG_BOKEH_PICTURE_DONE: {
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

    private void hideBars() {
        if (!mIsActive || !mShowBars) {
            return;
        }
        mShowBars = false;
        showToolBar(false);
        setStatusBarHideWhite();
        mHandler.removeMessages(MSG_HIDE_BARS);
        refreshBottomControlsWhenReady();
    }

    private void showBars() {
        if (!mIsActive || mShowBars) {
            return;
        }
        mShowBars = true;
        //mOrientationManager.unlockOrientation();
        showToolBar(true);
        setStatusBarVisibleWhite();
        refreshHidingMessage();
        refreshBottomControlsWhenReady();
    }

    private void updateBars() {
        if (!canShowBars()) {
            hideBars();
        }
    }

    private void wantBars() {
        if (canShowBars()) {
            showBars();
        }
    }

    private void updateUIForCurrentPhoto() {
        if (mCurrentPhoto == null) {
            return;
        }

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
        }

        Activity activity = getActivity();
        if (activity != null && activity.getWindow() != null) {
            if (mCurrentPhoto.mIsDrmFile) {
                Log.d(TAG, "updateUIForCurrentPhoto: disable screen capture for security");
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                Log.d(TAG, "updateUIForCurrentPhoto: enable screen capture");
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    private void launchCamera() {
        mRecenterCameraOnResume = false;
        GalleryUtils.startCameraActivity(getActivity());
    }

    private void updateCurrentPhoto(MediaItem photo) {
        if (mCurrentPhoto == photo) {
            return;
        }
        playPhotoVoiceEx(null, null);
        //如果滑动切换了图片, 则停止播放视频
        mGLRoot.playGLVideo(null);
        mCurrentPhoto = photo;

        if ((!mDrmFirstOpen || mStartFromWidget) && photo.getName() != null && photo.getName().endsWith(".dcf")
                && photo.getMediaType() != MediaObject.MEDIA_TYPE_VIDEO) {
            AlertDialog.OnClickListener confirmListener = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mNeedUpdate = true;
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
                    int action = event.getAction();
                    if (action == KeyEvent.ACTION_UP) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            mHandler.sendEmptyMessage(MSG_FINISH_STATE);
                        }
                    }
                    return false;
                }
            };
            SomePageUtils.getInstance().checkPressedIsDrm(getActivity(),
                    photo, confirmListener, cancelListener, onKeyListener, false);
        }

        if ((mDrmFirstOpen && !mStartFromWidget) && photo.getName() != null && photo.getName().endsWith(".dcf")) {
            PhotoPageUtils.getInstance().updateDrmCurrentPhoto(photo, mHandler);
            mDrmFirstOpen = false;
        }

        if (mPhotoView.getFilmMode()) {
            requestDeferredUpdate();
        } else {
            updateUIForCurrentPhoto();
            updateTitle(false);
        }
    }

    private static Intent createSharePanoramaIntent(Uri contentUri) {
        return new Intent(Intent.ACTION_SEND)
                .setType(GalleryUtils.MIME_TYPE_PANORAMA360)
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private Intent createShareIntent(MediaObject mediaObject) {
        String msgShareTo = getActivity().getResources().getString(R.string.share);
        int type = mediaObject.getMediaType();
        Uri contentUri = GalleryUtils.transFileToContentType(mediaObject.getContentUri(), getActivity());
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(MenuExecutor.getMimeType(type))
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent = Intent.createChooser(intent, msgShareTo);
        return intent;
    }

    private void setNfcBeamPushUri(Uri uri) {
        mNfcPushUris[0] = uri;
    }

    private void updatePanoramaUI(boolean isPanorama360) {
        Menu menu = getMenu();
        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) {
            return;
        }
        MenuExecutor.updateMenuForPanorama(menu, isPanorama360, isPanorama360);
        if (isPanorama360) {
            MenuItem item = menu.findItem(R.id.action_share);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                item.setTitle(getActivity().getResources().getString(R.string.share_as_photo));
            }
        } else if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_SHARE) != 0) {
            MenuItem item = menu.findItem(R.id.action_share);
        }
    }

    private void updateProgressAndOpera() {
        if (mCurrentPhoto == null || mCurrentPhoto instanceof TrashItem) {
            return;
        }
        boolean showProgress;
        int mediaType = mCurrentPhoto.getMediaType();
        if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_THUMB) {
            showProgress = true;
            updateMenuOperations();
        } else if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY) {
            showProgress = mSupportBokeh;
            updateMenuOperations();
        } else if (mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_HDR
                || mediaType == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_FDR) {
            showProgress = false;
            refreshBottomControlsWhenReady();
            updateMenuOperations();
        } else {
            showProgress = false;
        }
        mProgressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }

    private void updateThumbFileFlag(MediaItem mediaItem) {
        if (mediaItem == null) {
            return;
        }
        Uri uri = mediaItem.getContentUri();
        int mediaType = mediaItem.getMediaType();
        if (uri == null || mediaType != MediaObject.MEDIA_TYPE_IMAGE_THUMB) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("file_flag", LocalImage.IMG_TYPE_MODE_NORMAL);
        mApplication.getContentResolver().update(uri, values, null, null);
        Toast.makeText(getActivity(), R.string.blur_save_fail, Toast.LENGTH_SHORT).show();
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
        if (getActivity() == null) {
            return false;
        }
        Configuration config = getActivity().getResources().getConfiguration();
        return config.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH;
    }

    private void showToolBar(boolean show) {
        mToolbar.clearAnimation();
        mPhotoControlBottomBar.clearAnimation();
        if (show) {
            mToolbarAnimIn.reset();
            mToolbar.startAnimation(mToolbarAnimIn);
            mToolbar.setVisibility(View.VISIBLE);
            mPhotoControlBottomBarAnimIn.reset();
            mPhotoControlBottomBar.startAnimation(mPhotoControlBottomBarAnimIn);
            mPhotoControlBottomBar.setVisibility(View.VISIBLE);
        } else {
            mToolbarAnimOut.reset();
            mToolbar.startAnimation(mToolbarAnimOut);
            mToolbar.setVisibility(View.GONE);
            mPhotoControlBottomBarAnimOut.reset();
            mPhotoControlBottomBar.startAnimation(mPhotoControlBottomBarAnimOut);
            mPhotoControlBottomBar.setVisibility(View.GONE);
        }
    }

    private void updateMenuOperations() {
        Menu menu = getMenu();
        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) {
            return;
        }
        Log.i(TAG, " updateMenuOperations mCurrentPhoto = " + mCurrentPhoto);
        MenuItem itemSetAs = menu.findItem(R.id.action_setas);
        if (mCurrentPhoto == null) {
            if (itemSetAs != null) {
                itemSetAs.setVisible(false);
            }
            return;
        }
        MenuItem item = menu.findItem(R.id.action_slideshow);
        if (item != null) {
            item.setVisible((mSecureAlbum == null) && canDoSlideShow() && !(mCurrentPhoto instanceof TrashItem));
        }
        MenuItem itemMusic = menu.findItem(R.id.action_slideshow_music);
        if (itemMusic != null) {
            itemMusic.setVisible((mSecureAlbum == null) && canDoSlideShow() && !(mCurrentPhoto instanceof TrashItem));
        }

        //播放motion photo时,按钮状态切换
        MenuItem motionPlay = menu.findItem(R.id.action_play_motion_photo);
        if (motionPlay != null) {
            motionPlay.setVisible(!(mCurrentPhoto instanceof TrashItem) && mCurrentPhoto.isMotionPhoto());
            motionPlay.setIcon(mGLRoot.isGLVideoPlaying() ?
                    R.drawable.ic_ab_motion_photo_pause : R.drawable.ic_ab_motion_photo_play);
            motionPlay.setTitle(mGLRoot.isGLVideoPlaying() ?
                    R.string.stop_motion_photo : R.string.play_motion_photo);
        }

        int mediaType = mCurrentPhoto.getMediaType();
        if (itemSetAs != null) {
            itemSetAs.setEnabled(!((mCurrentPhoto instanceof UriImage) && GalleryUtils.isMtpUri(mCurrentPhoto.getContentUri())));
        }
        int supportedOperations = mCurrentPhoto.getSupportedOperations();
        if (mReadOnlyView) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        if (mSecureAlbum != null) {
            supportedOperations &= MediaObject.SUPPORT_DELETE;
        } else {
            mCurrentPhoto.getPanoramaSupport(mUpdatePanoramaMenuItemsCallback);
            if (!mHaveImageEditor) {
                supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            }
        }
        if (mediaType == MediaObject.MEDIA_TYPE_GIF) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_WBMP) {
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
        }
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BLUR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY) {
            supportedOperations &= ~MediaObject.SUPPORT_ROTATE;
            supportedOperations &= ~MediaObject.SUPPORT_CROP;
        }
        if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY) {
            supportedOperations &= ~MediaObject.SUPPORT_SHARE;
        }

        if ((mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR
                || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR)
                && !(mCurrentPhoto instanceof TrashItem)
                && GalleryUtils.isBlendingEnable() && mSupportBokeh
                && !mSecureCamera) {
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

        if (mOriginalSetPathString != null) {
            MediaSet mediaSet = getDataManager().getMediaSet(mOriginalSetPathString);
            if (mediaSet != null && mediaSet instanceof ThingsAlbum) {
                MenuItem moveOutThingsMenu = menu.findItem(R.id.action_move_out_things);
                if (moveOutThingsMenu != null) {
                    moveOutThingsMenu.setVisible(true);
                }
            } else if (mediaSet != null && mediaSet instanceof PeopleMergeAlbum) {
                MenuItem moveOutPeopleMenu = menu.findItem(R.id.action_move_out_people);
                if (moveOutPeopleMenu != null) {
                    moveOutPeopleMenu.setVisible(true);
                }
            }
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

    private void playPhotoVoice(String path, MediaItem mediaItem) {
        if (null == mMediaPlayer) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0) {
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
                    mToast = ToastUtil.showMessage(getActivity(), mToast, R.string.play_audio_failed, Toast.LENGTH_SHORT);
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
                    SdCardPermission.showSdcardPermissionErrorDialog(getGalleryActivity2(),
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
            SdCardPermission.requestSdcardPermission(getGalleryActivity2(), storagePaths, getGalleryActivity2(), listener);
        }
    }

    private void abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(afChangeListener);
    }

    private AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mMediaPlayer != null) {
                        mMediaPlayer.reset();
                        if (mPhotoVoiceProgress != null) {
                            mPhotoVoiceProgress.stopShowTime();
                        }
                        if (mFis != null) {
                            Utils.closeSilently(mFis);
                            mFis = null;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public int getTime() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    private void requestDeferredUpdate() {
        mDeferUpdateUntil = SystemClock.uptimeMillis() + DEFERRED_UPDATE_MS;
        if (!mDeferredUpdateWaiting) {
            mDeferredUpdateWaiting = true;
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, DEFERRED_UPDATE_MS);
        }
    }

    private void updateTitle(boolean isFilmMode) {
        /*
        if (getSupportActionBar() == null) {
            return;
        }
        if (isFilmMode) {
            getSupportActionBar().setTitle(mMediaSet != null ? mMediaSet.getName() : "");
            getSupportActionBar().setDisplayOptions(((mSecureAlbum == null) && (mSetPathString != null)), false);
        } else {
            getSupportActionBar().setTitle(mCurrentPhoto != null ? mCurrentPhoto.getName() : "");
            getSupportActionBar().setDisplayOptions(((mSecureAlbum == null) && (mSetPathString != null)), true);
        }
        */
    }

    private boolean canDoSlideShow() {
        if (mMediaSet == null || mCurrentPhoto == null) {
            return false;
        }
        return mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_UNKNOWN;
    }

    private final MediaObject.PanoramaSupportCallback mUpdatePanoramaMenuItemsCallback = new MediaObject.PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_PANORAMA_UI, isPanorama360 ? 1 : 0, 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final MediaObject.PanoramaSupportCallback mRefreshBottomControlsCallback = new MediaObject.PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, isPanorama ? 1 : 0, isPanorama360 ? 1 : 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final MediaObject.PanoramaSupportCallback mUpdateShareURICallback = new MediaObject.PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_SHARE_URI, isPanorama360 ? 1 : 0, 0, mediaObject)
                        .sendToTarget();
            }
        }
    };

    private class MyDetailsSource implements DetailsHelper.DetailsSource {

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
            case R.id.icon_button:
                return setupViewIcon(view);
            case R.id.photopage_bottom_control_tiny_planet:
                return mHaveImageEditor && mShowBars
                        && mIsPanorama360 && !mPhotoView.getFilmMode();
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
                if (mCurrentPhoto instanceof TrashItem) {
                    return false;
                }
                if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_hdr_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                } else if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_FDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_fdr_gallery_sprd);
                    view.setFocusable(false);
                    view.setClickable(false);
                    return true;
                }
                return false;
            case R.id.photopage_bottom_control_image1:
                if (mCurrentPhoto instanceof TrashItem) {
                    return false;
                }
                if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_FDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO) {
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
                } else if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_FDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_VFDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR
                        || mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO) {
                    ((ImageView) view).setImageResource(R.drawable.ic_fdr_gallery_sprd);
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
        if (mCurrentPhoto == null
                || mCurrentPhoto instanceof TrashItem) {
            return false;
        }

        MediaItem mediaItem = mCurrentPhoto;
        int mediaType = mCurrentPhoto.getMediaType();

        Resources resources = getActivity().getResources();
        switch (mediaType) {
            case MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER:
                int burstCount = mediaItem.getBurstCount();
                if (burstCount == 0) {
                    Log.d(TAG, "setupViewIcon: query burst count is 0");
                    return false;
                }
                String name = resources.getString(R.string.continuous_shooting);
                button.setBackground(resources.getDrawable(R.drawable.ic_burst_gallery_sprd));
                button.setText(name + " (" + String.valueOf(burstCount) + ")");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE:
            case MediaObject.MEDIA_TYPE_IMAGE_VHDR:
            case MediaObject.MEDIA_TYPE_IMAGE_VFDR:
                long jpegSize = mediaItem.getJpegSize();
                Log.d(TAG, "updateCurrentPhoto jpegSize= " + jpegSize);
                String value = resources.getString(R.string.voice_photo);
                button.setBackground(resources.getDrawable(R.drawable.ic_voice));
                button.setText(value);
                return jpegSize > 0;

            case MediaObject.MEDIA_TYPE_IMAGE_BOKEH:
            case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR:
            case MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR:
                if (!mSupportBokeh) {
                    return false;
                }
                String BokehString = resources.getString(R.string.refocus_image);
                button.setBackground(resources.getDrawable(R.drawable.ic_aperture_gallery_sprd));
                button.setText(BokehString);
                Log.d(TAG, "updateCurrentPhoto bokeh button.");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_BLUR:
                if (!mSupportBlur) {
                    return false;
                }
                String blurString = resources.getString(R.string.refocus_image);
                button.setBackground(resources.getDrawable(R.drawable.ic_aperture_gallery_sprd));
                button.setText(blurString);
                Log.d(TAG, "updateCurrentPhoto blur button.");
                return true;
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO:
            case MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO:
                button.setBackground(resources.getDrawable(R.drawable.ic_motion_photo_open_album_sprd));
                button.setText(R.string.prefered_motion_photo);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onBottomControlClicked(int control) {
        switch (control) {
            case R.id.photopage_bottom_control_edit:
                launchPhotoEditor();
                return;
            case R.id.photopage_bottom_control_panorama:
                ((GalleryActivity2) getActivity()).getPanoramaViewHelper()
                        .showPanorama(mCurrentPhoto.getContentUri());
                return;
            case R.id.photopage_bottom_control_tiny_planet:
                launchTinyPlanet();
                return;
            case R.id.icon_button:
                MediaItem mediaItem = mCurrentPhoto;
                int mediaType = mediaItem.getMediaType();
                if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE ||
                        mediaType == MediaObject.MEDIA_TYPE_IMAGE_VHDR ||
                        mediaType == MediaObject.MEDIA_TYPE_IMAGE_VFDR) {
                    String photoVoice = mediaItem.getFilePath();
                    Log.d(TAG, "updateCurrentPhoto   photoVoice = " + photoVoice);
                    playPhotoVoiceEx(photoVoice, mediaItem);
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BLUR
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR) {
                    launchRefocusActivity();
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
                    launchBurstActivity();
                } else if (mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO
                        || mediaType == MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO) {
                    Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
                    bundle.putBoolean(Constants.KEY_SECURE_CAMERA, mSecureCamera);
                    this.setArguments(bundle);
                    GalleryUtils.launchMotionActivity(this, mCurrentPhoto);
                }
                return;
            default:
                return;
        }
    }

    private void launchPhotoEditor() {
        MediaItem current = mModel.getMediaItem(0);
        GalleryUtils.launchEditor(this, current, REQUEST_EDIT);
    }

    private void launchTinyPlanet() {
        MediaItem current = mModel.getMediaItem(0);
        Intent intent = new Intent(FilterShowActivity.TINY_PLANET_ACTION);
        intent.setClass(getActivity(), FilterShowActivity.class);
        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                ((GalleryActivity2) getActivity()).isFullscreen());
        startActivityForResult(intent, REQUEST_EDIT);
    }

    private void launchRefocusActivity() {
        Log.d(TAG, "launchRefocusActivity");
        if (mCurrentPhoto == null) {
            return;
        }
        int refocusPhotoWidth = mCurrentPhoto.getWidth();
        int refocusPhotoHeight = mCurrentPhoto.getHeight();
        String path = mCurrentPhoto.getFilePath();
        Intent intent = new Intent(getActivity(), RefocusEditActivity.class)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setDataAndType(mCurrentPhoto.getContentUri(), mCurrentPhoto.getMimeType())
                .putExtra(RefocusEditActivity.SRC_PATH, path)
                .putExtra(RefocusEditActivity.SRC_WIDTH, refocusPhotoWidth)
                .putExtra(RefocusEditActivity.SRC_HEIGHT, refocusPhotoHeight)
                .putExtra(Constants.KEY_SECURE_CAMERA, mSecureCamera);
        try {
            if (mSecureCamera) {
                startActivityForResult(intent, REQUEST_EDIT_REFOCUS);
            } else {
                getActivity().startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "launchRefocusActivity failed", e);
        }
    }

    private void launchBurstActivity() {
        Intent intent = new Intent(getActivity(), BurstActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setDataAndType(mCurrentPhoto.getContentUri(), mCurrentPhoto.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra("media_type", mCurrentPhoto.getMediaType());
        getActivity().startActivity(intent);
    }

    @Override
    public void onTopControlClicked(int id) {
        switch (id) {
            case R.id.photopage_top_control_edit:
                return;
            case R.id.photopage_top_control_share:
                return;
            case R.id.photopage_top_control_quit:
                return;
            case R.id.photopage_top_control_more:
                return;
            default:
                return;
        }
    }

    private void launchShareIntent() {
        Log.i(TAG, " onTopControlClicked mCurrentPhoto = " + mCurrentPhoto);
        Uri contentUri = mCurrentPhoto.getContentUri();
        setupNfcBeamPush();
        setNfcBeamPushUri(contentUri);
        MenuExecutor.launchShareIntent(getActivity(), mModel.getMediaItem(0));
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) {
            return;
        }
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (adapter != null) {
            adapter.setBeamPushUris(mNfcPushUris, getActivity());
            adapter.setBeamPushUrisCallback(null /*new CreateBeamUrisCallback() {
                @Override
                public Uri[] createBeamUris(NfcEvent event) {
                    return mNfcPushUris;
                }
            }*/, getActivity());
        }
    }

    private void overrideTransitionToEditor() {
        getActivity().overridePendingTransition(android.R.anim.fade_in,
                android.R.anim.fade_out);
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
        if (getActivity().getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                ((GalleryActivity2) getActivity()).isFullscreen());
        startActivityForResult(Intent.createChooser(intent, null),
                REQUEST_EDIT);
    }

    private void showTopControls() {
        if (!mShowBars) {
            mTopControls.showTopControls();
        } else {
            mTopControls.hideTopControls();
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

    private boolean isValidUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            AssetFileDescriptor f = getActivity().getContentResolver().openAssetFileDescriptor(uri, "r");
            f.close();
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "cannot open uri: " + uri, e);
            return false;
        }
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
    }

    private void showDetails() {
        if (getActivity().isDestroyed() || getActivity().isFinishing()) {
            Log.d(TAG, "<showDetails> PhotoPage has been destroyed.");
            return;
        }
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(getActivity(), mRootPane, new MyDetailsSource());
            mDetailsHelper.setCloseListener(new DetailsHelper.CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    private void showSelectMusicDialog() {
        if (mConfirmDialogListener != null) {
            mConfirmDialogListener.onConfirmDialogShown();
        }

        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        final String musicUri = GalleryUtils.getSlideMusicUri(getActivity());
        int pos = 0;
        if (musicUri != null && !musicUri.isEmpty()) {
            pos = mUserSelected;
        }
        CharSequence[] items = {
                getActivity().getText(R.string.none), getActivity().getText(R.string.select_music)
        };
        dialog.setTitle(R.string.slideshow_music);
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Activity activity = getActivity();
                if (activity == null) {
                    Log.d(TAG, "showSelectMusicDialog DialogInterface.OnClickListener: activity is null");
                    dialog.dismiss();
                    if (mConfirmDialogListener != null) {
                        mConfirmDialogListener.onConfirmDialogDismissed(true);
                    }
                    return;
                }
                Intent intent = new Intent();
                mPos = mUserSelected;
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        break;
                    case NONE_MUSIC:
                        mPos = NONE_MUSIC;
                        GalleryUtils.setSlideMusicUri(activity, null);
                        GalleryUtils.saveSelected(activity, NONE_MUSIC);
                        mUserSelected = NONE_MUSIC;
                        break;
                    case SELECT_MUSIC:
                        mPos = SELECT_MUSIC;
                        intent.setAction(RingtoneManager.ACTION_RINGTONE_PICKER);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_RINGTONE);
                        if (musicUri != null) {
                            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(musicUri));
                        }
                        startActivityForResult(intent, REQUEST_SLIDESHOW_RINGTONE);
                        break;
                    case USER_DEFINED_MUSIC:
                        mPos = USER_DEFINED_MUSIC;
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("audio/*,application/ogg,application/x-ogg");
                        intent.putExtra("applyForSlideMusic", true);
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/ogg", "application/x-ogg"});
                        startActivityForResult(intent, REQUEST_SLIDESHOW_MUSIC);
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
        mDialog = dialog.create();
        mDialog.show();
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

    public void showDrmDetails(int index) {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(getActivity(), mRootPane, new MyDetailsSource());
            mDetailsHelper.setCloseListener(new DetailsHelper.CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.reloadDrmDetails(true);
        mDetailsHelper.show();
    }

    @Override
    public void onSingleTapUp(int x, int y) {
        if (mModel == null) {
            return;
        }
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
            final MediaItem itemDrm = item;
            AlertDialog.OnClickListener onClickListener = new AlertDialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    playVideo(getActivity(), itemDrm);
                }
            };
            if (!SomePageUtils.getInstance().checkPressedIsDrm(getActivity(), itemDrm, onClickListener,
                    null, null, false)) {
                if (mSecureAlbum == null) {
                    playVideo(getActivity(), item);
                } else {
                    onBackPressed();
                }
            }
        } else if (goBack) {
            onBackPressed();
        } else if (unlock) {
            Log.d(TAG, "onSingleTapUp unlock");
            /*
            Intent intent = new Intent(getActivity(), GalleryActivity2.class);
            intent.putExtra(GalleryActivity2.KEY_DISMISS_KEYGUARD, true);
            getActivity().startActivity(intent);
            */
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

    public void playVideo(Activity activity, MediaItem item) {
        try {
            String type = item.getMimeType();
            if (type == null) {
                type = "video/*";
            }
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(item.getPlayUri(), type)
                    .putExtra(Intent.EXTRA_TITLE, item.getName())
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Constants.KEY_IS_SECURE_CAMERA, mSecureCamera)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true)
                    .setClass(activity, MovieActivity.class);
            /* if (mSecureCamera || (item.mIsDrmFile && !item.mIsDrmSupportTransfer)) {
                //不支持转发的DRM就使用自带视频播放器
                intent.setClass(activity, MovieActivity.class);
            } */
            intent.putExtra(FLAG_GALLERY, true);
            startActivityForResult(intent, REQUEST_PLAY_VIDEO);
        } catch (ActivityNotFoundException e) {
            mToast = ToastUtil.showMessage(activity, mToast, R.string.video_err, Toast.LENGTH_SHORT);
        }
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

    @Override
    public void onDeleteImage(Path path, int offset) {
        onCommitDeleteImage();
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

    public void startImageBlendingActivity(Activity activity, Uri uri, MediaItem item) {
        if (activity == null || uri == null || item == null) {
            return;
        }
        Intent intent = new Intent(getActivity(), ReplaceActivity.class);
        intent.putExtra("path", item.getFilePath());
        intent.putExtra(Constants.KEY_SECURE_CAMERA, mSecureCamera);
        intent.setDataAndType(uri, "blendingImage/jpeg");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            Log.d(TAG, "imageblendingActivity");
            startActivityForResult(intent, REQUEST_BLENDING);
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
            getActivity().startActivity(intent);
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
            Path albumPath = mApplication.getDataManager().getDefaultSetOf(true, path, intent.getAction());
            if (albumPath == null) {
                return;
            }
//            if (!albumPath.equalsIgnoreCase(mOriginalSetPathString)) {
//                // If the edited image is stored in a different album, we need
//                // to start a new activity state to show the new image
//                Bundle data = new Bundle(getArguments());
//                data.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, albumPath.toString());
//                data.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, path.toString());
//                //mActivity.getStateManager().startState(SinglePhotoPage.class, data);
//                Log.d(TAG, "setCurrentPhotoByIntent -- ");
//                return;
//            }
            if (mModel == null)  return;
            mModel.setCurrentPhoto(path, mCurrentIndex);
        }
    }

    @Override
    public void onCurrentImageUpdated() {
        mGLRoot.unfreeze();
    }

    @Override
    public void onFilmModeChanged(boolean enabled) {
        refreshBottomControlsWhenReady();
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
        updateTitle(enabled);
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

    private void transitionFromAlbumPageIfNeeded() {
        TransitionStore transitions = ((GalleryActivity2) getActivity()).getTransitionStore();

        int albumPageTransition = transitions.get(
                KEY_ALBUMPAGE_TRANSITION, MSG_ALBUMPAGE_NONE);

        if (albumPageTransition == MSG_ALBUMPAGE_NONE && mAppBridge != null
                && mRecenterCameraOnResume) {
            // Generally, resuming the PhotoPage when in Camera should
            // reset to the capture mode to allow quick photo taking
            mCurrentIndex = 0;
            mPhotoView.resetToFirstPicture();
        } else {
            int resumeIndex = transitions.get(Constants.KEY_BUNDLE_MEDIA_ITEM_INDEX, -1);
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

    private void releasePlayer() {
        if (mMediaPlayer != null) {
            Log.d(TAG, "releasePlayer");
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
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

    private Intent createWallpagerIntet(MediaObject mediaObject) {
        int mimeType = mediaObject.getMediaType();
        Path path = mediaObject.getPath();
        Intent intent = new Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(mediaObject.getContentUri(),
                MenuExecutor.getMimeType(mimeType));
        return intent;
    }

    private void showMenuPop() {
        View contentView = View.inflate(getActivity(),
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
                        GLRoot root = mGLRoot;
                        root.lockRenderThread();
                        try {
                            MediaItem current = mModel.getMediaItem(0);

                            if (current == null) {
                                // item is not ready, ignore
                                break;
                            }
                            int currentIndex = mModel.getCurrentIndex();
                            Path path = current.getPath();
                            Bundle data = new Bundle(getArguments());
                            data.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, mMediaSet.getPath().toString());
                            data.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, path.toString());
                            data.putInt(Constants.KEY_BUNDLE_PHOTO_INDEX, currentIndex);
                            data.putBoolean(Constants.KEY_BUNDLE_PHOTO_REPEAT, true);
                            //mActivity.getStateManager().startStateForResult(SlideshowPage.class, REQUEST_SLIDESHOW, data);
                            Log.d(TAG, "showMenuPop popup_menu_slideshow");
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
                            getActivity().startActivity(Intent.createChooser(
                                    intent, getActivity().getString(R.string.set_as)));
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


        mPopupWindow = new PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
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
        View contentView = View.inflate(getActivity(), R.layout.popup_slidemusic_menu, null);
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

        mSlideMusicWindow = new PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
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
                getActivity().getString(R.string.slidemusic));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_RINGTONE);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                GalleryUtils.getSlideMusicUri(getActivity()));
        startActivityForResult(intent, REQUEST_SLIDESHOW_RINGTONE);
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
                GalleryUtils.getSlideMusicUri(getActivity()));
        startActivityForResult(intent, REQUEST_SLIDESHOW_MUSIC);
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

        Path path = current.getPath();
        String confirmMsg;
        if (current.mIsDrmFile) {
            confirmMsg = getActivity().getResources().getString(R.string.delete_drm_file);
        } else if((current.getSize() >= MediaItem.DELTE_FILE_LARGE_SIZE)){
            confirmMsg = getActivity().getResources().getString(R.string.delete_large_size_file);
        } else {
            confirmMsg = getActivity().getResources().getQuantityString(
                    R.plurals.delete_selection, 1);
        }
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
            //Launch details
            Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
            int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
            FragmentManager fm = getActivity().getSupportFragmentManager();
            Fragment fragment = fm.findFragmentById(containerId);
            Log.d(TAG, "onDetailsClick fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
            if (fragment != null) {
                DetailsPageFragment detailsPageFragment = new DetailsPageFragment();
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
                bundle.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, current.getPath().toString());
                detailsPageFragment.setArguments(bundle);
                fm.beginTransaction()
                        .hide(fragment)
                        .add(containerId, detailsPageFragment)
                        .addToBackStack(null)
                        .commit();
            }
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
        final MediaItem current = mModel.getMediaItem(0);
        if (current instanceof SnailItem) {
            return;
        }
        if (current == null) {
            return;
        }

        if (!GalleryStorageUtil.isInInternalStorage(current.getFilePath()) &&
                !SdCardPermission.hasStoragePermission(current.getFilePath())) {
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(current.getFilePath());
            SdCardPermissionListener listener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    startImageBlendingActivity(getActivity(), current.getContentUri(), current);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(getGalleryActivity2(),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.i(TAG, " access permission failed");
                                }
                            });
                }
            };
            SdCardPermission.requestSdcardPermission(getGalleryActivity2(), storagePaths, getGalleryActivity2(), listener);
        } else {
            startImageBlendingActivity(getActivity(), current.getContentUri(), current);
        }
    }

    @Override
    public void onTrashRestoreClick(View view) {
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

        Path path = current.getPath();
        String confirmMsg = getActivity().getResources().getQuantityString(
                R.plurals.restore_selection, 1);
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(path);
        mMenuExecutor.onMenuClicked(R.id.action_trash_restore, confirmMsg, mConfirmDialogListener);
    }

    @Override
    public void onTrashDeleteClick(View view) {
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

        Path path = current.getPath();
        String confirmMsg = getActivity().getResources().getQuantityString(
                R.plurals.delete_selection, 1);
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(path);
        mMenuExecutor.onMenuClicked(R.id.action_trash_delete, confirmMsg, mConfirmDialogListener);
    }

    private void onMoveOutThingsClicked() {
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

        Path path = current.getPath();
        String confirmMsg = getActivity().getString(R.string.move_out_classification_dialog_title);
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(path);
        mMenuExecutor.onMenuClicked(R.id.action_move_out_things, confirmMsg, mConfirmDialogListener);
    }

    private void onMoveOutPeopleClicked() {
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

        Path path = current.getPath();
        String confirmMsg = getActivity().getString(R.string.move_out_people_dialog_title);
        mSelectionManager.deSelectAll();
        mSelectionManager.toggle(path);
        mMenuExecutor.onMenuClicked(R.id.action_move_out_people, confirmMsg, mConfirmDialogListener);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void handleMimeTypeMsg(MimeTypeMsg msg) {
        refreshBottomControlsWhenReady();
    }

    @Override
    public MediaSet getMediaSet() {
        return mMediaSet;
    }

    @Override
    public void reloadPicture() {
        MediaItem mediaItem = mModel.getMediaItem(0);
        if (mediaItem == null) {
            return;
        }
        boolean bokehThumb = (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_THUMB);
        if (mHandler.hasMessages(MSG_UPDATE_FILE_FLAG)) {
            mHandler.removeMessages(MSG_UPDATE_FILE_FLAG);
        }
        if (bokehThumb) {
            Message message = mHandler.obtainMessage();
            message.what = MSG_UPDATE_FILE_FLAG;
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
        if (getActivity().isFinishing() || mediaItem == null || mediaItem instanceof TrashItem) {
            return;
        }
        try {
            if (mSupportBokeh) {
                Log.i(TAG, "reloadNoBokehRefocusPicture type =  " + mediaItem.getMediaType());
                if (mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY
                    || mediaItem.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY) {
                    RefocusUtils.setBokehType(mCurrentPhoto.getMediaType());
                }
                mBokehSaveManager.startBokehSave(getActivity(), mediaItem, this);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
                    String message = getString(R.string.crop_saved,
                            getString(R.string.folder_edited_online_photos));
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case REQUEST_SLIDESHOW: {
                if (data == null) {
                    break;
                }
                String path = data.getStringExtra(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH);
                int index = data.getIntExtra(Constants.KEY_BUNDLE_PHOTO_INDEX, 0);
                if (path != null) {
                    mModel.setCurrentPhoto(Path.fromString(path), index);
                }
                break;
            }
            case REQUEST_SLIDESHOW_RINGTONE: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getExtras().getParcelable(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri == null) {
                        mPos = NONE_MUSIC;
                        GalleryUtils.setSlideMusicUri(getContext(), null);
                        GalleryUtils.saveSelected(getContext(), NONE_MUSIC);
                        mUserSelected = NONE_MUSIC;
                        return;
                    }
                    GalleryUtils.setSlideMusicUri(getContext(), uri.toString());
                    GalleryUtils.saveSelected(getContext(), SELECT_MUSIC);
                    mUserSelected = SELECT_MUSIC;
                }
                break;
            }
            case REQUEST_SLIDESHOW_MUSIC: {
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    GalleryUtils.setSlideMusicUri(getContext(), GalleryUtils.transformUri(uri, getContext()).toString());
                    GalleryUtils.saveSelected(getContext(), USER_DEFINED_MUSIC);
                    mUserSelected = USER_DEFINED_MUSIC;
                }
                break;
            }
            case REQUEST_PLAY_VIDEO: {
                if (data == null) {
                    break;
                }
                setCurrentPhotoByIntent(data);
                break;
            }
            case REQUEST_EDIT_REFOCUS: {
                Uri uri = data == null ? null : data.getData();
                if (uri != null && mSecureItemIds != null) {
                    Log.d(TAG, "onActivityResult add saved edited picture -> " + uri);
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
            default:
                break;
        }
    }

    private void setAs(Path path) {
        DataManager manager = getDataManager();
        MediaItem mediaItem = (MediaItem) manager.getMediaObject(path);
        if (mediaItem == null) {
            return;
        }
        lockScreen();
        String mimeType = MenuExecutor.getMimeType(manager.getMediaType(mediaItem.getPath()));
        Uri uri = GalleryUtils.transFileToContentType(manager.getContentUri(mediaItem.getPath()), getContext());
        Intent intent = new Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("mimeType", intent.getType());
        startActivity(Intent.createChooser(
                intent, getString(R.string.set_as)));
    }

    private void slideShow(int currentIndex, Path path) {
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Log.d(TAG, "slideShow fragment(" + this + "), fm(" + fm + "), containerId(" + containerId + ").");

        SlideShowPageFragment slideShowPageFragment = new SlideShowPageFragment();
        slideShowPageFragment.setDataBackListener(this);
        bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, mMediaSet.getPath().toString());
        bundle.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, path.toString());
        bundle.putInt(Constants.KEY_BUNDLE_PHOTO_INDEX, currentIndex);
        bundle.putBoolean(Constants.KEY_BUNDLE_PHOTO_REPEAT, true);
        bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
        slideShowPageFragment.setArguments(bundle);
        fm.beginTransaction()
                .replace(containerId, slideShowPageFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDataBack(Bundle data) {
        switch (data.getInt(Constants.KEY_DATA_BACK, -1)) {
            case Constants.DATA_BACK_FROM_SLIDE_SHOW:
                String path = data.getString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH);
                int index = data.getInt(Constants.KEY_BUNDLE_PHOTO_INDEX, 0);
                getArguments().putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, path);
                getArguments().putInt(Constants.KEY_BUNDLE_MEDIA_ITEM_INDEX, index);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean isBackConsumed() {
        Bundle data = new Bundle();
        data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_PHOTO_VIEW);
        data.putInt(Constants.KEY_RETURN_INDEX_HINT, mCurrentIndex);
        data.putBoolean(Constants.KEY_IS_NEED_UPDATE, mNeedUpdate);
        setDataBack(data);
        return false;
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
        Toast.makeText(getContext(), R.string.blur_save_fail, Toast.LENGTH_SHORT).show();
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
        mGLRoot.playGLVideo(item);
    }

    //当播放状态切换后, 会收到消息
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGLVideoStateChanged(GLVideoStateMsg msg) {
        Log.d(TAG, "onGLVideoStateChanged");
        updateMenuOperations();
    }
}

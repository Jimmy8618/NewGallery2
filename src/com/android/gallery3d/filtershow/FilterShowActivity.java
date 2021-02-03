/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.print.PrintHelper;

import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.IStorageUtil.StorageChangedListener;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.category.Action;
import com.android.gallery3d.filtershow.category.AdjustPanel;
import com.android.gallery3d.filtershow.category.CategoryAdapter;
import com.android.gallery3d.filtershow.category.CategorySelected;
import com.android.gallery3d.filtershow.category.CategoryView;
import com.android.gallery3d.filtershow.category.MainPanel;
import com.android.gallery3d.filtershow.category.SwipableView;
import com.android.gallery3d.filtershow.data.UserPresetsManager;
import com.android.gallery3d.filtershow.editors.BasicEditor;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.editors.EditorChanSat;
import com.android.gallery3d.filtershow.editors.EditorColorBorder;
import com.android.gallery3d.filtershow.editors.EditorCrop;
import com.android.gallery3d.filtershow.editors.EditorDraw;
import com.android.gallery3d.filtershow.editors.EditorGrad;
import com.android.gallery3d.filtershow.editors.EditorManager;
import com.android.gallery3d.filtershow.editors.EditorMirror;
import com.android.gallery3d.filtershow.editors.EditorPanel;
import com.android.gallery3d.filtershow.editors.EditorRedEye;
import com.android.gallery3d.filtershow.editors.EditorRotate;
import com.android.gallery3d.filtershow.editors.EditorStraighten;
import com.android.gallery3d.filtershow.editors.EditorTinyPlanet;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;
import com.android.gallery3d.filtershow.filters.FilterCropRepresentation;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;
import com.android.gallery3d.filtershow.filters.FilterFxRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.filters.FilterUserPresetRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.history.HistoryItem;
import com.android.gallery3d.filtershow.history.HistoryManager;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.ImageStraighten;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.imageshow.Spline;
import com.android.gallery3d.filtershow.info.InfoPanel;
import com.android.gallery3d.filtershow.pipeline.CachingPipeline;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.filtershow.presets.PresetManagementDialog;
import com.android.gallery3d.filtershow.presets.UserPresetsAdapter;
import com.android.gallery3d.filtershow.provider.SharedImageProvider;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.filtershow.state.StateView;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.filtershow.tools.XmpPresets;
import com.android.gallery3d.filtershow.tools.XmpPresets.XMresults;
import com.android.gallery3d.filtershow.ui.ExportDialog;
import com.android.gallery3d.filtershow.ui.FramedTextButton;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.android.photos.data.GalleryBitmapPool;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.GalleryPermissionsActivity;
import com.sprd.gallery3d.app.PermissionsActivity;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Vector;

public class FilterShowActivity extends FragmentActivity implements OnItemClickListener,
        OnShareTargetSelectedListener, DialogInterface.OnShowListener,
        DialogInterface.OnDismissListener, PopupMenu.OnDismissListener,
        SdCardPermissionAccessor {

    private static final String TAG = "FilterShowActivity";

    private String mAction = "";
    MasterImage mMasterImage = null;

    private static final long LIMIT_SUPPORTS_HIGHRES = 134217728; // 128Mb

    public static final String TINY_PLANET_ACTION = "com.android.camera.action.TINY_PLANET";
    public static final String LAUNCH_FULLSCREEN = "launch-fullscreen";
    public static final String VOICE_ID = "voice_id";
    public static final boolean RESET_TO_LOADED = false;
    private ImageShow mImageShow = null;

    private View mSaveButton = null;
    private View mCancelButton = null;

    private EditorPlaceHolder mEditorPlaceHolder = new EditorPlaceHolder(this);
    private Editor mCurrentEditor = null;

    private static final int SELECT_PICTURE = 1;
    private static final String LOGTAG = "FilterShowActivity";

    private boolean mShowingTinyPlanet = false;
    private boolean mShowingImageStatePanel = false;
    private boolean mShowingVersionsPanel = false;

    private final Vector<ImageShow> mImageViews = new Vector<ImageShow>();

    private ShareActionProvider mShareActionProvider;
    private File mSharedOutputFile = null;

    private boolean mSharingImage = false;

    private WeakReference<ProgressDialog> mSavingProgressDialog;

    private LoadBitmapTask mLoadBitmapTask;

    private Uri mOriginalImageUri = null;
    private ImagePreset mOriginalPreset = null;

    private Uri mSelectedImageUri = null;

    private ArrayList<Action> mActions = new ArrayList<Action>();
    private UserPresetsManager mUserPresetsManager = null;
    private UserPresetsAdapter mUserPresetsAdapter = null;
    private CategoryAdapter mCategoryLooksAdapter = null;
    private CategoryAdapter mCategoryBordersAdapter = null;
    private CategoryAdapter mCategoryGeometryAdapter = null;
    private CategoryAdapter mCategoryFiltersAdapter = null;
    private CategoryAdapter mCategoryVersionsAdapter = null;
    private int mCurrentPanel = MainPanel.BORDERS;
    private Vector<FilterUserPresetRepresentation> mVersions =
            new Vector<FilterUserPresetRepresentation>();
    private int mVersionsCounter = 0;

    private boolean mHandlingSwipeButton = false;
    private View mHandledSwipeView = null;
    private float mHandledSwipeViewLastDelta = 0;
    private float mSwipeStartX = 0;
    private float mSwipeStartY = 0;

    private ProcessingService mBoundService;
    private boolean mIsBound = false;
    private Menu mMenu;
    private DialogInterface mCurrentDialog = null;
    private PopupMenu mCurrentMenu = null;
    private boolean mLoadingVisible = true;
    private FilterRepresentation mFilterDrawRepresentation;
    public boolean mNeedCommit = false;
    private int mPhotoVoiceId = 0;
    private MenuItem mSaveItem;
    private DialogFragment mDialog;
    private SdCardPermissionListener mSdCardPermissionListener;

    public ProcessingService getProcessingService() {
        return mBoundService;
    }

    public boolean isSimpleEditAction() {
        return !PhotoPage.ACTION_NEXTGEN_EDIT.equalsIgnoreCase(mAction);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            /*
             * This is called when the connection with the service has been
             * established, giving us the service object we can use to
             * interact with the service.  Because we have bound to a explicit
             * service that we know is running in our own process, we can
             * cast its IBinder to a concrete class and directly access it.
             */
            mBoundService = ((ProcessingService.LocalBinder) service).getService();
            mBoundService.setFiltershowActivity(FilterShowActivity.this);
            mBoundService.onStart();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            /*
             * This is called when the connection with the service has been
             * unexpectedly disconnected -- that is, its process crashed.
             * Because it is running in our same process, we should never
             * see this happen.
             */
            mBoundService = null;
        }
    };

    void doBindService() {
        /*
         * Establish a connection with the service.  We use an explicit
         * class name because we want a specific service implementation that
         * we know will be running in our own process (and thus won't be
         * supporting component replacement by other applications).
         */
        bindService(new Intent(FilterShowActivity.this, ProcessingService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void updateUIAfterServiceStarted() {
        /* SPRD:for bug 508111 gallery premisson error*/
        try {
            MasterImage.setMaster(mMasterImage);
            ImageFilter.setActivityForMemoryToasts(this);
            mUserPresetsManager = new UserPresetsManager(this);
            mUserPresetsAdapter = new UserPresetsAdapter(this);

            setupMasterImage();
            setupMenu();
            setDefaultValues();
            fillEditors();
            getWindow().setBackgroundDrawable(new ColorDrawable(0));
            loadXML();

            fillCategories();
            loadMainPanel();
            extractXMPData();
            processIntent();
        } catch (SecurityException e) {
            Toast.makeText(FilterShowActivity.this, R.string.gallery_premission_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        /* @} */
    }

    /* receive storage changed msg */
    private final StorageChangedListener mStorageChangedListener = new StorageChangedListener() {

        @Override
        public void onStorageChanged(final String path, final String action) {
            Log.d(LOGTAG, "StorageChanged: path = " + path + " action = " + action);
            new Handler(getMainLooper()).post(new Runnable() {

                @Override
                public void run() {
                    FilterShowActivity.this.onStorageChanged(path, action);
                }
            });
        }
        /* receive storage changed msg */
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        ImageStraighten.mSavedAngle = 0;
        /*boolean onlyUsePortrait = getResources().getBoolean(R.bool.only_use_portrait);
        if (onlyUsePortrait) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }*/
        if (!checkPermissions()) {
            Log.v(TAG, "onCreate: Missing critical permissions.");
            finish();
            return;
        }
        clearGalleryBitmapPool();
        doBindService();
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.GRAY));
        setContentView(R.layout.filtershow_splashscreen);


        /* add storage changed monitor */
        GalleryStorageUtil.addStorageChangeListener(mStorageChangedListener);
    }

    public boolean isShowingImageStatePanel() {
        return mShowingImageStatePanel;
    }

    public void loadMainPanel() {
        // SPRD : bug 613822, IllegalStateException: Activity has been destroyed
        if (findViewById(R.id.main_panel_container) == null || isFinishing()) {
            return;
        }
        MainPanel panel = new MainPanel();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    private boolean isAdjustPanel(final FilterRepresentation representation) {
        String filterClassName = representation.getFilterClass().getSimpleName();
        Log.d(TAG, "isAdjustPanel filterClassName = " + filterClassName);
        return ("ImageFilterExposure".equals(filterClassName)
                || "ImageFilterChanSat".equals(filterClassName)
                || "ImageFilterContrast".equals(filterClassName)
                || "ImageFilterVignette".equals(filterClassName)
                || "ImageFilterSharpen".equals(filterClassName))
                && findViewById(R.id.adjust_panel_container) != null;
    }

    public void cancelCurrentFilter() {
        if (mMasterImage == null) {
            return;
        }
        HistoryManager adapter = mMasterImage.getHistory();
        int position = adapter.undo();
        mMasterImage.onHistoryItemClick(position);
        // bug 606530,undo not add FilterDrawRepresentation
        mNeedCommit = false;
        backToMain();
        invalidateViews();
    }

    private void resetGeometryFilter() {
        HistoryManager adapter = mMasterImage.getHistory();
        boolean resetSucess = false;
        int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            resetSucess = mMasterImage.resetGeometryFilter(i);
            if (!resetSucess) {
                break;
            }
        }
        // bug 606530,undo not add FilterDrawRepresentation
        mNeedCommit = false;
        backToMain();
        invalidateViews();
    }

    private void leaveAdjustPanel() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment adjustPanel = getSupportFragmentManager().findFragmentByTag(AdjustPanel.FRAGMENT_TAG);
        if (null != adjustPanel) {
            transaction.remove(adjustPanel);
            transaction.commitAllowingStateLoss();

            setViewVisibility(R.id.adjust_panel_container, View.GONE);
            setViewVisibility(R.id.bottom_panel_buttons, View.VISIBLE);

            setViewBackgroundColor(R.id.filtershow_effect_exposure, Color.TRANSPARENT);
            setViewBackgroundColor(R.id.filtershow_effect_sharpen, Color.TRANSPARENT);
            setViewBackgroundColor(R.id.filtershow_effect_contrast, Color.TRANSPARENT);
            setViewBackgroundColor(R.id.filtershow_effect_vignetter, Color.TRANSPARENT);
            setViewBackgroundColor(R.id.filtershow_effect_saturation, Color.TRANSPARENT);
        }
    }

    private void setViewVisibility(int viewId, int visibility) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void setViewBackgroundColor(int viewId, int color) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setBackgroundColor(color);
        }
    }

    private boolean hasAdjustPanel() {
        Fragment adjustPanel = getSupportFragmentManager().findFragmentByTag(AdjustPanel.FRAGMENT_TAG);
        return adjustPanel != null;
    }

    public void loadEditorPanel(final FilterRepresentation representation,
                                final Editor currentEditor) {
        if (representation.getEditorId() == ImageOnlyEditor.ID) {
            currentEditor.reflectCurrentFilter();
            return;
        }
        final int currentId = currentEditor.getID();
        Runnable showEditor = new Runnable() {
            @Override
            public void run() {
                EditorPanel panel = new EditorPanel();
                panel.setEditor(currentId);
                if (representation instanceof FilterCropRepresentation) {
                    panel.setCropRatio(((FilterCropRepresentation) representation).getCropRatio());
                }
                if (currentEditor instanceof EditorStraighten) {
                    Log.d(LOGTAG, "loadEditorPanel EditorStraighten");
                    return;
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                if (isAdjustPanel(representation)) {
                    Fragment adjustPanel = getSupportFragmentManager().findFragmentByTag(AdjustPanel.FRAGMENT_TAG);
                    if (null != adjustPanel) {
                        transaction.remove(adjustPanel);
                    }
                    transaction.replace(R.id.adjust_panel_container,
                            new AdjustPanel(new AdjustPanel.OnAdjustPanelListener() {
                                @Override
                                public void onCancelClicked() {
                                    leaveAdjustPanel();
                                }

                                @Override
                                public void onSaveClicked() {
                                    leaveAdjustPanel();
                                }
                            }, currentId),
                            AdjustPanel.FRAGMENT_TAG);
                    findViewById(R.id.adjust_panel_container).setVisibility(View.VISIBLE);
                    findViewById(R.id.bottom_panel_buttons).setVisibility(View.GONE);
                } else {
                    transaction.remove(getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG));
                    transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
                }
                /*
                 * SPRD Bug:507260,IllegalStateException in monkey test@{
                 * Original Android code:
                transaction.commit();
                 */
                transaction.commitAllowingStateLoss();
                /* @} */
            }
        };
        Fragment main = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        boolean doAnimation = false;
        if (mShowingImageStatePanel
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            doAnimation = true;
        }
        if (doAnimation && main != null && main instanceof MainPanel) {
            //MainPanel mainPanel = (MainPanel) main;
            //View container = mainPanel.getView().findViewById(R.id.category_panel_container);
            //View bottom = mainPanel.getView().findViewById(R.id.bottom_panel);
            //int panelHeight = container.getHeight() + bottom.getHeight();
            //ViewPropertyAnimator anim = mainPanel.getView().animate();
            //anim.translationY(panelHeight).start();
            final Handler handler = new Handler();
            handler.postDelayed(showEditor, 0);
        } else {
            showEditor.run();
        }
    }

    public void toggleInformationPanel() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);

        InfoPanel panel = new InfoPanel();
        panel.show(transaction, InfoPanel.FRAGMENT_TAG);
    }

    private void loadXML() {
        setContentView(R.layout.filtershow_activity);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.filtershow_actionbar_cancel_button);
        /*actionBar.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(R.color.background_screen)));*/
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.filtershow_toolbar_background_dream));

        /* SPRD: Modify for bug535592, remove them to last to avoid NPE @{
        mSaveButton = actionBar.getCustomView();
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImage();
            }
        });
        @} */

        mCancelButton = actionBar.getCustomView();
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                onBackPressed();
            }
        });

        mImageShow = findViewById(R.id.imageShow);
        mImageViews.add(mImageShow);

        setupEditors();

        mEditorPlaceHolder.hide();
        mImageShow.attach();

        setupStatePanel();

        // SPRD: Modify for bug535592, initialize mSaveButton after mImageShow is initialized to avoid NPE @{
        /*mSaveButton = actionBar.getCustomView();
        mSaveButton.setEnabled(false);
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImage();
            }
        });*/
        // @}
    }

    public void fillCategories() {
        fillLooks();
        loadUserPresets();
        fillBorders();
        fillTools();
        fillEffects();
        fillVersions();
    }

    public void setupStatePanel() {
        MasterImage.getImage().setHistoryManager(mMasterImage.getHistory());
    }

    private void fillVersions() {
        if (mCategoryVersionsAdapter != null) {
            mCategoryVersionsAdapter.clear();
        }
        mCategoryVersionsAdapter = new CategoryAdapter(this);
        mCategoryVersionsAdapter.setShowAddButton(true);
    }

    public void registerAction(Action action) {
        if (mActions.contains(action)) {
            return;
        }
        mActions.add(action);
    }

    private void loadActions() {
        for (int i = 0; i < mActions.size(); i++) {
            Action action = mActions.get(i);
            action.setImageFrame(new Rect(0, 0, 96, 96), 0);
        }
    }

    public void updateVersions() {
        // SPRD: bug 537435,Initialization not complete mCategoryVersionsAdapter is null,return @{
        if (mCategoryVersionsAdapter == null) {
            return;
        }
        // @}
        mCategoryVersionsAdapter.clear();
        FilterUserPresetRepresentation originalRep = new FilterUserPresetRepresentation(
                getString(R.string.filtershow_version_original), new ImagePreset(), -1);
        mCategoryVersionsAdapter.add(
                new Action(this, originalRep, Action.FULL_VIEW));
        ImagePreset current = new ImagePreset(MasterImage.getImage().getPreset());
        FilterUserPresetRepresentation currentRep = new FilterUserPresetRepresentation(
                getString(R.string.filtershow_version_current), current, -1);
        mCategoryVersionsAdapter.add(
                new Action(this, currentRep, Action.FULL_VIEW));
        if (mVersions.size() > 0) {
            mCategoryVersionsAdapter.add(new Action(this, Action.SPACER));
        }
        for (FilterUserPresetRepresentation rep : mVersions) {
            mCategoryVersionsAdapter.add(
                    new Action(this, rep, Action.FULL_VIEW, true));
        }
        mCategoryVersionsAdapter.notifyDataSetInvalidated();
    }

    public void addCurrentVersion() {
        ImagePreset current = new ImagePreset(MasterImage.getImage().getPreset());
        // SPRD: Modify bug 514964,the same effect, can not save
        if (isPresetExist(current)) {
            showSameEffectToast(Toast.LENGTH_SHORT);
        } else {
            mVersionsCounter++;
            FilterUserPresetRepresentation rep = new FilterUserPresetRepresentation(
                    "" + mVersionsCounter, current, -1);
            mVersions.add(rep);
            updateVersions();
        }
    }

    /* SPRD: Modify bug 514964,the same effect, can not save @{ */
    private boolean isPresetExist(ImagePreset preset) {
        if (mVersions != null && mVersions.size() > 0) {
            for (FilterUserPresetRepresentation rep : mVersions) {
                if (rep.getImagePreset().same(preset)) {
                    return true;
                }
            }
        }
        return false;
    }

    WeakReference<Toast> mSameEffectToast = null;

    private void showSameEffectToast(int toastLength) {
        Toast toast;
        if (mSameEffectToast != null) {
            toast = mSameEffectToast.get();
            if (toast != null) {
                toast.show();
                return;
            }
        }
        toast = Toast.makeText(this, R.string.already_exist, toastLength);
        mSameEffectToast = new WeakReference<Toast>(toast);
        toast.show();
    }
    /* @} */

    public void removeVersion(Action action) {
        mVersions.remove(action.getRepresentation());
        updateVersions();
    }

    public void removeLook(Action action) {
        FilterUserPresetRepresentation rep =
                (FilterUserPresetRepresentation) action.getRepresentation();
        if (rep == null) {
            return;
        }
        mUserPresetsManager.delete(rep.getId());
        updateUserPresetsFromManager();
    }

    private void fillEffects() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getEffects();
        if (mCategoryFiltersAdapter != null) {
            mCategoryFiltersAdapter.clear();
        }
        mCategoryFiltersAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : filtersRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryFiltersAdapter.add(new Action(this, representation));
        }
    }

    private void fillTools() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getTools();
        if (mCategoryGeometryAdapter != null) {
            mCategoryGeometryAdapter.clear();
        }
        mCategoryGeometryAdapter = new CategoryAdapter(this);
        boolean found = false;
        for (FilterRepresentation representation : filtersRepresentations) {
            // SPRD: Modify 20151226 for bug516977, text of some effects do not
            // changed after locale change @{
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            // @}
            mCategoryGeometryAdapter.add(new Action(this, representation));
            if (representation instanceof FilterDrawRepresentation) {
                found = true;
            }
        }
        if (!found) {
            FilterRepresentation representation = new FilterDrawRepresentation();
            Action action = new Action(this, representation);
            action.setIsDoubleAction(true);
            mCategoryGeometryAdapter.add(action);
        }
    }

    private void processIntent() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra(LAUNCH_FULLSCREEN, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // SPRD: bug 635695, After edit, voice image cant play voice
        mPhotoVoiceId = intent.getIntExtra(VOICE_ID, 0);
        mAction = intent.getAction();
        mSelectedImageUri = intent.getData();
        Uri loadUri = mSelectedImageUri;
        if (mOriginalImageUri != null) {
            loadUri = mOriginalImageUri;
        }
        if (loadUri != null) {
            startLoadBitmap(loadUri);
        } else {
            pickImage();
        }
    }

    private void setupEditors() {
        mEditorPlaceHolder.setContainer((FrameLayout) findViewById(R.id.editorContainer));
        EditorManager.addEditors(mEditorPlaceHolder);
        mEditorPlaceHolder.setOldViews(mImageViews);
    }

    private void fillEditors() {
        mEditorPlaceHolder.addEditor(new EditorChanSat());
        mEditorPlaceHolder.addEditor(new EditorGrad());
        mEditorPlaceHolder.addEditor(new EditorDraw());
        mEditorPlaceHolder.addEditor(new EditorColorBorder());
        mEditorPlaceHolder.addEditor(new BasicEditor());
        mEditorPlaceHolder.addEditor(new ImageOnlyEditor());
        mEditorPlaceHolder.addEditor(new EditorTinyPlanet());
        mEditorPlaceHolder.addEditor(new EditorRedEye());
        mEditorPlaceHolder.addEditor(new EditorCrop());
        mEditorPlaceHolder.addEditor(new EditorMirror());
        mEditorPlaceHolder.addEditor(new EditorRotate());
        mEditorPlaceHolder.addEditor(new EditorStraighten());
    }

    private void setDefaultValues() {
        Resources res = getResources();

        // TODO: get those values from XML.
        FramedTextButton.setTextSize((int) getPixelsFromDip(14));
        FramedTextButton.setTrianglePadding((int) getPixelsFromDip(4));
        FramedTextButton.setTriangleSize((int) getPixelsFromDip(10));

        Drawable curveHandle = res.getDrawable(R.drawable.camera_crop);
        int curveHandleSize = (int) res.getDimension(R.dimen.crop_indicator_size);
        Spline.setCurveHandle(curveHandle, curveHandleSize);
        Spline.setCurveWidth((int) getPixelsFromDip(3));

        mOriginalImageUri = null;
    }

    private void startLoadBitmap(Uri uri) {
        final View imageShow = findViewById(R.id.imageShow);
        imageShow.setVisibility(View.INVISIBLE);
        startLoadingIndicator();
        mShowingTinyPlanet = false;
        mLoadBitmapTask = new LoadBitmapTask();
        mLoadBitmapTask.execute(uri);
    }

    private void fillBorders() {
        FiltersManager filtersManager = FiltersManager.getManager();
        ArrayList<FilterRepresentation> borders = filtersManager.getBorders();

        for (int i = 0; i < borders.size(); i++) {
            FilterRepresentation filter = borders.get(i);
            filter.setName(getString(R.string.borders));
            if (i == 0) {
                filter.setName(getString(R.string.none));
            }
        }

        if (mCategoryBordersAdapter != null) {
            mCategoryBordersAdapter.clear();
        }
        mCategoryBordersAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : borders) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryBordersAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.loadCategoryBorderPanel(true, true);
            }
        }
    }

    public UserPresetsAdapter getUserPresetsAdapter() {
        return mUserPresetsAdapter;
    }

    public CategoryAdapter getCategoryLooksAdapter() {
        return mCategoryLooksAdapter;
    }

    public CategoryAdapter getCategoryBordersAdapter() {
        return mCategoryBordersAdapter;
    }

    public CategoryAdapter getCategoryGeometryAdapter() {
        return mCategoryGeometryAdapter;
    }

    public CategoryAdapter getCategoryFiltersAdapter() {
        return mCategoryFiltersAdapter;
    }

    public CategoryAdapter getCategoryVersionsAdapter() {
        return mCategoryVersionsAdapter;
    }

    public void removeFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        copy.removeFilter(filterRepresentation);
        MasterImage.getImage().setPreset(copy, copy.getLastRepresentation(), true);
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            FilterRepresentation lastRepresentation = copy.getLastRepresentation();
            MasterImage.getImage().setCurrentFilterRepresentation(lastRepresentation);
        }
    }

    public void useFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        if (!(filterRepresentation instanceof FilterRotateRepresentation)
                && !(filterRepresentation instanceof FilterMirrorRepresentation)
                && MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            return;
        }
        if (filterRepresentation instanceof FilterUserPresetRepresentation
                || filterRepresentation instanceof FilterRotateRepresentation
                || filterRepresentation instanceof FilterMirrorRepresentation) {
            MasterImage.getImage().onNewLook(filterRepresentation);
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        FilterRepresentation representation = copy.getRepresentation(filterRepresentation);
        if (representation == null) {
            filterRepresentation = filterRepresentation.copy();
            copy.addFilter(filterRepresentation);
        } else {
            if (filterRepresentation.allowsSingleInstanceOnly()) {
                // Don't just update the filter representation. Centralize the
                // logic in the addFilter(), such that we can keep "None" as
                // null.
                if (!representation.equals(filterRepresentation)) {
                    // Only do this if the filter isn't the same
                    // (state panel clicks can lead us here)
                    copy.removeFilter(representation);
                    copy.addFilter(filterRepresentation);
                }
            }
        }
        MasterImage.getImage().setPreset(copy, filterRepresentation, true);
        MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
    }

    public void showRepresentation(FilterRepresentation representation) {
        if (representation == null) {
            return;
        }

        if (representation instanceof FilterRotateRepresentation) {
            FilterRotateRepresentation r = (FilterRotateRepresentation) representation;
            r.rotateACW();
            // SPRD: fix bug 598521, when crop image, click the rotate may cause some issue
            backToMainFromCrop();
        }
        if (representation instanceof FilterMirrorRepresentation) {
            FilterMirrorRepresentation r = (FilterMirrorRepresentation) representation;
            r.cycle();
        }
        if (representation.isBooleanFilter()) {
            ImagePreset preset = MasterImage.getImage().getPreset();
            if (preset.getRepresentation(representation) != null) {
                // remove
                ImagePreset copy = new ImagePreset(preset);
                copy.removeFilter(representation);
                FilterRepresentation filterRepresentation = representation.copy();
                MasterImage.getImage().setPreset(copy, filterRepresentation, true);
                MasterImage.getImage().setCurrentFilterRepresentation(null);
                return;
            }
        }
        useFilterRepresentation(representation);
        /* SPRD:bug 590378, after draw image,apply mirrorFilter error @{ */
        if (representation instanceof FilterDrawRepresentation) {
            mNeedCommit = true;
            mFilterDrawRepresentation = representation;
        }
        /* @} */

        // show representation
        if (mCurrentEditor != null) {
            mCurrentEditor.detach();
        }
        mCurrentEditor = mEditorPlaceHolder.showEditor(representation.getEditorId());
        loadEditorPanel(representation, mCurrentEditor);
    }

    /* SPRD: fix bug 598521, when crop image, click the rotate may cause some issue @{ */
    private void backToMainFromCrop() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG
        );
        if (currentPanel instanceof EditorPanel) {
            EditorPanel panel = (EditorPanel) currentPanel;
            Editor e = panel.getEditor();
            if (e instanceof EditorCrop || e instanceof EditorStraighten) {
                loadMainPanel();
                showDefaultImageView();
            }
        }
    }

    /* @} */
    public Editor getEditor(int editorID) {
        return mEditorPlaceHolder.getEditor(editorID);
    }

    public void setCurrentPanel(int currentPanel) {
        mCurrentPanel = currentPanel;
    }

    public int getCurrentPanel() {
        return mCurrentPanel;
    }

    public Editor getCurrentEditor() {
        return mCurrentEditor;
    }

    public void updateCategories() {
        if (mMasterImage == null) {
            return;
        }
        ImagePreset preset = mMasterImage.getPreset();
        mCategoryLooksAdapter.reflectImagePreset(preset);
        mCategoryBordersAdapter.reflectImagePreset(preset);
    }

    public View getMainStatePanelContainer(int id) {
        return findViewById(id);
    }

    public void onShowMenu(PopupMenu menu) {
        mCurrentMenu = menu;
        menu.setOnDismissListener(this);
    }

    @Override
    public void onDismiss(PopupMenu popupMenu) {
        if (mCurrentMenu == null) {
            return;
        }
        mCurrentMenu.setOnDismissListener(null);
        mCurrentMenu = null;
    }

    @Override
    public void onShow(DialogInterface dialog) {
        mCurrentDialog = dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        mCurrentDialog = null;
    }

    private class LoadHighresBitmapTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            MasterImage master = MasterImage.getImage();
            Rect originalBounds = master.getOriginalBounds();
            if (master.supportsHighRes() && master.getOriginalBitmapLarge() != null) {
                Bitmap largeBitmap = master.getOriginalBitmapLarge();
                int highresPreviewSize = Math.max(largeBitmap.getWidth(), largeBitmap.getHeight()) * 2;
                int originalBoundsSize = Math.max(originalBounds.width(), originalBounds.height());
                if (highresPreviewSize > originalBoundsSize) {
                    highresPreviewSize = originalBoundsSize;
                }
                Rect bounds = new Rect();
                Bitmap originalHires = ImageLoader.loadOrientedConstrainedBitmap(master.getUri(),
                        master.getActivity(), highresPreviewSize,
                        master.getOrientation(), bounds);
                master.setOriginalBounds(bounds);
                master.setOriginalBitmapHighres(originalHires);
                mBoundService.setOriginalBitmapHighres(originalHires);
                master.warnListeners();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Bitmap highresBitmap = MasterImage.getImage().getOriginalBitmapHighres();
            if (highresBitmap != null) {
                float highResPreviewScale = (float) highresBitmap.getWidth()
                        / (float) MasterImage.getImage().getOriginalBounds().width();
                mBoundService.setHighresPreviewScaleFactor(highResPreviewScale);
            }
            MasterImage.getImage().warnListeners();
        }
    }

    public boolean isLoadingVisible() {
        return mLoadingVisible;
    }

    public void startLoadingIndicator() {
        final View loading = findViewById(R.id.loading);
        mLoadingVisible = true;
        loading.setVisibility(View.VISIBLE);
    }

    public void stopLoadingIndicator() {
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        mLoadingVisible = false;
    }

    private class LoadBitmapTask extends AsyncTask<Uri, Boolean, Boolean> {
        int mBitmapSize;

        public LoadBitmapTask() {
            mBitmapSize = getScreenImageSize();
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            if (!MasterImage.getImage().loadBitmap(params[0], mBitmapSize)) {
                return false;
            }
            publishProgress(ImageLoader.queryLightCycle360(MasterImage.getImage().getActivity()));
            return true;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            super.onProgressUpdate(values);
            if (isCancelled()) {
                return;
            }
            if (values[0]) {
                mShowingTinyPlanet = true;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MasterImage.setMaster(mMasterImage);
            if (isCancelled()) {
                return;
            }

            if (!result) {
                if (mOriginalImageUri != null
                        && !mOriginalImageUri.equals(mSelectedImageUri)) {
                    mOriginalImageUri = mSelectedImageUri;
                    mOriginalPreset = null;
                    Toast.makeText(FilterShowActivity.this,
                            R.string.cannot_edit_original, Toast.LENGTH_SHORT).show();
                    startLoadBitmap(mOriginalImageUri);
                } else {
                    cannotLoadImage();
                }
                return;
            }

            if (null == CachingPipeline.getRenderScriptContext()) {
                Log.v(LOGTAG, "RenderScript context destroyed during load");
                return;
            }
            final View imageShow = findViewById(R.id.imageShow);
            imageShow.setVisibility(View.VISIBLE);

            Bitmap largeBitmap = MasterImage.getImage().getOriginalBitmapLarge();
            if (largeBitmap == null) {
                return;
            }
            mBoundService.setOriginalBitmap(largeBitmap);

            float previewScale = (float) largeBitmap.getWidth()
                    / (float) MasterImage.getImage().getOriginalBounds().width();
            mBoundService.setPreviewScaleFactor(previewScale);
            if (!mShowingTinyPlanet) {
                mCategoryFiltersAdapter.removeTinyPlanet();
            }
            mCategoryLooksAdapter.imageLoaded();
            mCategoryBordersAdapter.imageLoaded();
            mCategoryGeometryAdapter.imageLoaded();
            mCategoryFiltersAdapter.imageLoaded();
            mLoadBitmapTask = null;

            MasterImage.getImage().warnListeners();
            loadActions();

            if (mOriginalPreset != null) {
                MasterImage.getImage().setLoadedPreset(mOriginalPreset);
                MasterImage.getImage().setPreset(mOriginalPreset,
                        mOriginalPreset.getLastRepresentation(), true);
                mOriginalPreset = null;
            } else {
                setDefaultPreset();
            }

            MasterImage.getImage().resetGeometryImages(true);

            if (mAction == TINY_PLANET_ACTION) {
                showRepresentation(mCategoryFiltersAdapter.getTinyPlanet());
            }
            LoadHighresBitmapTask highresLoad = new LoadHighresBitmapTask();
            highresLoad.execute();
            MasterImage.getImage().warnListeners();
            super.onPostExecute(result);
        }

    }

    private void clearGalleryBitmapPool() {
        (new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Free memory held in Gallery's Bitmap pool.  May be O(n) for n bitmaps.
                GalleryBitmapPool.getInstance().clear();
                return null;
            }
        }).execute();
    }

    @Override
    protected void onDestroy() {
        // SPRD: Modify for bug556956, cancel ProgressDialog while this is
        // destroyed
        hideSavingProgress();

        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        if (mUserPresetsManager != null) {
            mUserPresetsManager.close();
        }
        doUnbindService();
        /* remove storage changed monitor */
        GalleryStorageUtil.removeStorageChangeListener(mStorageChangedListener);
        super.onDestroy();
    }

    // TODO: find a more robust way of handling image size selection
    // for high screen densities.
    private int getScreenImageSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        return Math.max(outMetrics.heightPixels, outMetrics.widthPixels);
    }

    private void showSavingProgress(String albumName) {
        ProgressDialog progress;
        if (mSavingProgressDialog != null) {
            progress = mSavingProgressDialog.get();
            if (progress != null) {
                progress.show();
                return;
            }
        }
        // TODO: Allow cancellation of the saving process
        String progressText;
        if (albumName == null) {
            progressText = getString(R.string.saving_image);
        } else {
            progressText = getString(R.string.filtershow_saving_image, albumName);
        }
        progress = ProgressDialog.show(this, "", progressText, true, false);
        mSavingProgressDialog = new WeakReference<ProgressDialog>(progress);
    }

    private void hideSavingProgress() {
        if (mSavingProgressDialog != null) {
            ProgressDialog progress = mSavingProgressDialog.get();
            if (progress != null && progress.isShowing()) // Modify for bug556956, make sure progress is still showing before dismiss
            {
                progress.dismiss();
            }
        }
    }

    public void completeSaveImage(Uri saveUri) {
        if (mSharingImage && mSharedOutputFile != null) {
            // Image saved, we unblock the content provider
            Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                    Uri.encode(mSharedOutputFile.getAbsolutePath()));
            ContentValues values = new ContentValues();
            values.put(SharedImageProvider.PREPARE, false);
            getContentResolver().insert(uri, values);
        }
        /* SPRD: bug 635695, After edit, voice image cant play voice @{ */
        if (mPhotoVoiceId != 0) {
            ContentValues values = new ContentValues();
            values.put("photo_voice_id", mPhotoVoiceId);
            values.put("file_flag", LocalImage.IMG_TYPE_MODE_AUDIO_CAPTURE);
            int update = getContentResolver().update(saveUri, values, null, null);
            Log.d(LOGTAG, "CompleteSave Voice Image, update = " + update);
        }
        /* @} */
        setResult(RESULT_OK, new Intent().setData(saveUri));
//        hideSavingProgress();
//        finish();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider arg0, Intent arg1) {
        // First, let's tell the SharedImageProvider that it will need to wait
        // for the image
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        ContentValues values = new ContentValues();
        values.put(SharedImageProvider.PREPARE, true);
        getContentResolver().insert(uri, values);
        mSharingImage = true;

        // Process and save the image in the background.
        showSavingProgress(null);
        mImageShow.saveImage(this, mSharedOutputFile);
        return true;
    }

    private Intent getDefaultShareIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(SharedImageProvider.MIME_TYPE);
        mSharedOutputFile = SaveImage.getNewFile(this, MasterImage.getImage().getUri());
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filtershow_activity_menu, menu);
        MenuItem showState = menu.findItem(R.id.showImageStateButton);
        if (mShowingImageStatePanel) {
            showState.setTitle(R.string.hide_imagestate_panel);
        } else {
            showState.setTitle(R.string.show_imagestate_panel);
        }
        mShareActionProvider = (ShareActionProvider) menu.findItem(R.id.menu_share)
                .getActionProvider();
        mShareActionProvider.setShareIntent(getDefaultShareIntent());
        mShareActionProvider.setOnShareTargetSelectedListener(this);
        mMenu = menu;
        setupMenu();
        return true;
    }

    private void setupMenu() {
        if (mMenu == null || mMasterImage == null) {
            return;
        }
        MenuItem undoItem = mMenu.findItem(R.id.undoButton);
        MenuItem redoItem = mMenu.findItem(R.id.redoButton);
        MenuItem resetItem = mMenu.findItem(R.id.resetHistoryButton);
        MenuItem printItem = mMenu.findItem(R.id.printButton);
        mSaveItem = mMenu.findItem(R.id.saveButton);

        if (!PrintHelper.systemSupportsPrint()) {
            printItem.setVisible(false);
        }
        mMasterImage.getHistory().setOnMenuUpdateListener(new MyMenuUpdateListener(mSaveItem));
        mMasterImage.getHistory().setMenuItems(undoItem, redoItem, resetItem);
    }

    public void hideMenuItems(boolean hideMenu) {
        if (mMenu == null || mMasterImage == null) {
            return;
        }
        MenuItem undoItem = mMenu.findItem(R.id.undoButton);
        MenuItem redoItem = mMenu.findItem(R.id.redoButton);
        MenuItem resetItem = mMenu.findItem(R.id.resetHistoryButton);
        MenuItem printItem = mMenu.findItem(R.id.printButton);
        MenuItem showInfoPanel = mMenu.findItem(R.id.showInfoPanel);
        MenuItem showImageStateButton = mMenu.findItem(R.id.showImageStateButton);
        MenuItem exportFlattenButton = mMenu.findItem(R.id.exportFlattenButton);
        if (hideMenu) {
            mSaveItem.setTitle("");
            mSaveItem.setIcon(R.drawable.photo_control_save);
        } else {
            mSaveItem.setTitle(R.string.save);
            mSaveItem.setIcon(null);
        }
        printItem.setVisible(!hideMenu);
        if (!PrintHelper.systemSupportsPrint()) {
            printItem.setVisible(false);
        }
        undoItem.setVisible(!hideMenu);
        redoItem.setVisible(!hideMenu);
        resetItem.setVisible(!hideMenu);
        showInfoPanel.setVisible(!hideMenu);
        showImageStateButton.setVisible(!hideMenu);
        exportFlattenButton.setVisible(!hideMenu);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(null);
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment infoPanel = getSupportFragmentManager().findFragmentByTag(InfoPanel.FRAGMENT_TAG);
        if (infoPanel != null && infoPanel instanceof InfoPanel) {
            transaction.remove(infoPanel);
            transaction.commitAllowingStateLoss();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (isInMultiWindowMode) {
            android.util.Log.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            Toast.makeText(this, R.string.exit_multiwindow_tips, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!checkPermissions()) {
            Log.v(TAG, "onCreate: Missing critical permissions.");
            finish();
            return;
        }
        GalleryUtils.killActivityInMultiWindow(this, GalleryUtils.DONT_SUPPORT_VIEW_PHOTOS);
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getSupportFragmentManager().isStateSaved()) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.undoButton: {
                if (hasAdjustPanel()) {
                    leaveAdjustPanel();
                }
                HistoryManager adapter = mMasterImage.getHistory();
                int position = adapter.undo();
                mMasterImage.onHistoryItemClick(position);
                // bug 606530,undo not add FilterDrawRepresentation
                mNeedCommit = false;
                backToMain();
                invalidateViews();
                return true;
            }
            case R.id.redoButton: {
                HistoryManager adapter = mMasterImage.getHistory();
                int position = adapter.redo();
                mMasterImage.onHistoryItemClick(position);
                invalidateViews();
                return true;
            }
            case R.id.resetHistoryButton: {
                resetHistory();
                leaveAdjustPanel();
                return true;
            }
            case R.id.showImageStateButton: {
                toggleImageStatePanel();
                return true;
            }
            case R.id.exportFlattenButton: {
                /*  229
                 * SPRD Bug:505740  Add  judgment for bitmap loading@{
                 * Original Android code:
                showExportOptionsDialog();
                 */
                if (!mLoadingVisible) {
                    String ramsize = StandardFrameworks.getInstances().getStringSystemProperties("ro.ramsize", null);
                    Log.d(TAG, "onOptionsItemSelected: ramsize = " + ramsize);
                    int ram = 0;
                    if (ramsize != null) {
                        ram = Integer.valueOf(ramsize.substring(0, ramsize.length() - 1));
                    }
                    Rect bounds = MasterImage.getImage().getOriginalBounds();
                    if (ram >= 4096 || bounds.width() * bounds.height() <= SaveImage.MAX_IMAGE_PIXEL_COUNT) {
                        showExportOptionsDialog();
                    } else {
                        Toast.makeText(this, R.string.large_image_cannot_export, Toast.LENGTH_SHORT).show();
                    }
                }
                /* @} */
                return true;
            }
            case android.R.id.home: {
                saveImage();
                return true;
            }
            case R.id.manageUserPresets: {
                manageUserPresets();
                return true;
            }
            case R.id.showInfoPanel: {
                /*  229
                 * SPRD Bug:505740  Add  judgment for bitmap loading@{
                 * Original Android code:
                toggleInformationPanel();
                 */
                if (!mLoadingVisible) {
                    toggleInformationPanel();
                }
                /* @} */
                return true;
            }
            case R.id.printButton: {
                print();
                return true;
            }
            case R.id.saveButton: {
                //saveImage();
                requestSaveImage();
                break;
            }
            default:
                break;
        }
        return false;
    }

    public void print() {
        Bitmap bitmap = MasterImage.getImage().getHighresImage();
        PrintHelper printer = new PrintHelper(this);
        printer.printBitmap("ImagePrint", bitmap);
    }

    public void addNewPreset() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = new PresetManagementDialog();
        mDialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }

    private void manageUserPresets() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = new PresetManagementDialog();
        mDialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }

    private void showExportOptionsDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = new ExportDialog();
        mDialog.show(getSupportFragmentManager(), "ExportDialogFragment");
    }

    public void updateUserPresetsFromAdapter(UserPresetsAdapter adapter) {
        ArrayList<FilterUserPresetRepresentation> representations =
                adapter.getDeletedRepresentations();
        for (FilterUserPresetRepresentation representation : representations) {
            deletePreset(representation.getId());
        }
        ArrayList<FilterUserPresetRepresentation> changedRepresentations =
                adapter.getChangedRepresentations();
        for (FilterUserPresetRepresentation representation : changedRepresentations) {
            updatePreset(representation);
        }
        adapter.clearDeletedRepresentations();
        adapter.clearChangedRepresentations();
        loadUserPresets();
    }

    public void loadUserPresets() {
        mUserPresetsManager.load();
        updateUserPresetsFromManager();
    }

    public void updateUserPresetsFromManager() {
        ArrayList<FilterUserPresetRepresentation> presets = mUserPresetsManager.getRepresentations();
        if (presets == null) {
            return;
        }
        if (mCategoryLooksAdapter != null) {
            fillLooks();
        }
        if (presets.size() > 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.SPACER));
        }
        mUserPresetsAdapter.clear();
        for (int i = 0; i < presets.size(); i++) {
            FilterUserPresetRepresentation representation = presets.get(i);
            mCategoryLooksAdapter.add(
                    new Action(this, representation, Action.FULL_VIEW, true));
            mUserPresetsAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
        if (presets.size() > 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.ADD_ACTION));
        }
        mCategoryLooksAdapter.notifyDataSetChanged();
        mCategoryLooksAdapter.notifyDataSetInvalidated();
    }

    public void saveCurrentImagePreset(String name) {
        mUserPresetsManager.save(MasterImage.getImage().getPreset(), name);
    }

    private void deletePreset(int id) {
        mUserPresetsManager.delete(id);
    }

    private void updatePreset(FilterUserPresetRepresentation representation) {
        mUserPresetsManager.update(representation);
    }

    public void enableSave(boolean enable) {
        if (mSaveButton != null) {
            mSaveButton.setEnabled(enable);
        }
    }

    private void fillLooks() {
        FiltersManager filtersManager = FiltersManager.getManager();
        // SPRD: Modify 20151226 for bug516977, text of some effects do not
        // changed after locale change @{
        // ArrayList<FilterRepresentation> filtersRepresentations = filtersManager.getLooks();
        ArrayList<FilterFxRepresentation> filtersRepresentations = filtersManager.getLooks();
        // @}

        if (mCategoryLooksAdapter != null) {
            mCategoryLooksAdapter.clear();
        } else {
            mCategoryLooksAdapter = new CategoryAdapter(this);
        }
        int verticalItemHeight = (int) getResources().getDimension(R.dimen.action_item_height);
        mCategoryLooksAdapter.setItemHeight(verticalItemHeight);
        // SPRD: Modify 20151226 for bug516977, text of some effects do not
        // changed after locale change @{
        // for (FilterRepresentation representation : filtersRepresentations) {
        for (FilterFxRepresentation representation : filtersRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            // @}
            mCategoryLooksAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
        if (mUserPresetsManager.getRepresentations() == null
                || mUserPresetsManager.getRepresentations().size() == 0) {
            mCategoryLooksAdapter.add(new Action(this, Action.ADD_ACTION));
        }

        /*Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.loadCategoryLookPanel(true);
            }
        }*/
    }

    public void setDefaultPreset() {
        // Default preset (original)
        ImagePreset preset = new ImagePreset(); // empty
        mMasterImage.setPreset(preset, preset.getLastRepresentation(), true);
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Some utility functions
    // TODO: finish the cleanup.

    public void invalidateViews() {
        for (ImageShow views : mImageViews) {
            views.updateImage();
        }
    }

    public void hideImageViews() {
        for (View view : mImageViews) {
            view.setVisibility(View.GONE);
        }
        mEditorPlaceHolder.hide();
    }

    // //////////////////////////////////////////////////////////////////////////////
    // imageState panel...

    public void toggleImageStatePanel() {
        invalidateOptionsMenu();
        mShowingImageStatePanel = !mShowingImageStatePanel;
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof EditorPanel) {
                EditorPanel editorPanel = (EditorPanel) panel;
                editorPanel.showImageStatePanel(mShowingImageStatePanel);
            } else if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.showImageStatePanel(mShowingImageStatePanel);
            }
        }
    }

    public void toggleVersionsPanel() {
        mShowingVersionsPanel = !mShowingVersionsPanel;
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null && panel instanceof MainPanel) {
            MainPanel mainPanel = (MainPanel) panel;
            mainPanel.loadCategoryVersionsPanel();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setDefaultValues();
        if (mMasterImage == null) {
            return;
        }
        loadXML();
        fillCategories();
        loadMainPanel();

        if (mCurrentMenu != null) {
            mCurrentMenu.dismiss();
            mCurrentMenu = null;
        }
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
        // mLoadBitmapTask==null implies you have looked at the intent
        if (!mShowingTinyPlanet && (mLoadBitmapTask == null)) {
            mCategoryFiltersAdapter.removeTinyPlanet();
        }
        stopLoadingIndicator();
    }

    public void setupMasterImage() {

        HistoryManager historyManager = new HistoryManager();
        StateAdapter imageStateAdapter = new StateAdapter(this, 0);
        MasterImage.reset();
        mMasterImage = MasterImage.getImage();
        mMasterImage.setHistoryManager(historyManager);
        mMasterImage.setStateAdapter(imageStateAdapter);
        mMasterImage.setActivity(this);

        /* SPRD: bug 529848 Drawing function has some exception @{
        if (Runtime.getRuntime().maxMemory() > LIMIT_SUPPORTS_HIGHRES) {
            mMasterImage.setSupportsHighRes(true);
        } else {
            mMasterImage.setSupportsHighRes(false);
        }
        */
        mMasterImage.setSupportsHighRes(true);
        /* @} */

    }

    void resetHistory() {
        // SPRD: bug502310, if mMasterImage has not been initialized, do nothing.
        if (mMasterImage == null) {
            return;
        }
        HistoryManager adapter = mMasterImage.getHistory();
        adapter.reset();
        HistoryItem historyItem = adapter.getItem(0);
        ImagePreset original = null;
        if (RESET_TO_LOADED) {
            original = new ImagePreset(historyItem.getImagePreset());
        } else {
            original = new ImagePreset();
        }
        FilterRepresentation rep = null;
        if (historyItem != null) {
            rep = historyItem.getFilterRepresentation();
        }
        mMasterImage.setPreset(original, rep, true);
        invalidateViews();
        // bug 606530,resetHistory not add FilterDrawRepresentation
        mNeedCommit = false;
        backToMain();
    }

    public void showDefaultImageView() {
        mEditorPlaceHolder.hide();
        // SPRD:fix bug 514704,mImageShow maybe null if view  is not loaded
        if (mImageShow != null) {
            mImageShow.setVisibility(View.VISIBLE);
        }
        MasterImage.getImage().setCurrentFilter(null);
        MasterImage.getImage().setCurrentFilterRepresentation(null);
    }

    public void backToMain() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            showDefaultImageView();
            return;
        }
        // SPRD:bug 590378, after draw image,apply mirrorFilter error
        checkDrawFilter();
        loadMainPanel();
        showDefaultImageView();
    }

    @Override
    public void onBackPressed() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        /* SPRD: bug514168 java.lang.IllegalStateException @{ */
        try {
            if (hasAdjustPanel()) {
                leaveAdjustPanel();
                cancelCurrentFilter();
            } else if (currentPanel instanceof MainPanel) {
                //add for bug525820:CRASH: com.android.gallery3d ( java.lang.NullPointerException)
                hideMenuItems(false);
                MainPanel panel = (MainPanel) currentPanel;
                if (mCurrentPanel == MainPanel.GEOMETRY && mCurrentPanel != panel.getPrevSelected()) {
                    if (mCurrentEditor != null /*&& mCurrentEditor.getID() == R.id.editorStraighten*/) {
                        resetGeometryFilter();
                        mCurrentEditor.detach();
                        mCurrentEditor = null;
                        showDefaultImageView();
                    }
                    switch (panel.getPrevSelected()) {
                        case MainPanel.BORDERS:
                            panel.loadCategoryBorderPanel(true, false);
                            break;
                        case MainPanel.LOOKS:
                            panel.loadCategoryLookPanel(true, false);
                            break;
                        case MainPanel.FILTERS:
                            panel.loadCategoryFiltersPanel(true, false);
                            break;
                        default:
                            panel.loadCategoryBorderPanel(true, false);
                            break;
                    }
                } else if (mImageShow == null || !mImageShow.hasModifications()) {
                    done();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.unsaved);
                    builder.setPositiveButton(R.string.save_and_exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            //saveImage();
                            requestSaveImage();
                        }
                    });
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            done();
                        }
                    });
                    builder.show();
                }
            } else if (currentPanel instanceof EditorPanel) {
                cancelCurrentFilter();
                mNeedCommit = false;
                backToMain();
            } else {
                mNeedCommit = true;
                backToMain();
            }
        } catch (IllegalStateException e) {
            Log.d(LOGTAG, "Perform this action after onSaveInstanceState when onBackPressed !");
            finish();
        }
        /* @} */
    }

    public void cannotLoadImage() {
        Toast.makeText(this, R.string.cannot_load_image, Toast.LENGTH_SHORT).show();
        finish();
    }

    // //////////////////////////////////////////////////////////////////////////////

    public float getPixelsFromDip(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                r.getDisplayMetrics());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        mMasterImage.onHistoryItemClick(position);
        invalidateViews();
    }

    public void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_PICTURE:
                if (resultCode == RESULT_OK) {
                    Uri selectedImageUri = data.getData();
                    startLoadBitmap(selectedImageUri);
                }
                break;
            case SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE:
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
                break;
            default:
                break;
        }
    }

    private boolean willSaveImage() {
        Fragment currentPanel = getSupportFragmentManager()
                .findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            MainPanel panel = (MainPanel) currentPanel;
            if (mCurrentPanel == MainPanel.GEOMETRY && mCurrentPanel != panel.getPrevSelected()) {
                return false;
            }
        }
        return true;
    }

    public void saveImage() {
        Fragment currentPanel = getSupportFragmentManager()
                .findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            MainPanel panel = (MainPanel) currentPanel;
            if (mCurrentPanel == MainPanel.GEOMETRY && mCurrentPanel != panel.getPrevSelected()) {
                hideMenuItems(false);
                if (mCurrentEditor != null /* && mCurrentEditor.getID() == R.id.editorStraighten*/) {
                    mCurrentEditor.detach();
                    mCurrentEditor = null;
                    showDefaultImageView();
                }
                switch (panel.getPrevSelected()) {
                    case MainPanel.BORDERS:
                        panel.loadCategoryBorderPanel(true, false);
                        break;
                    case MainPanel.LOOKS:
                        panel.loadCategoryLookPanel(true, false);
                        break;
                    case MainPanel.FILTERS:
                        panel.loadCategoryFiltersPanel(true, false);
                        break;
                    default:
                        panel.loadCategoryBorderPanel(true, false);
                        break;
                }
                return;
            }
        }
        if (mImageShow.hasModifications()) {
            // Get the name of the album, to which the image will be saved
            File saveDir = SaveImage.getFinalSaveDirectory(this, mSelectedImageUri);
            int bucketId = GalleryUtils.getBucketId(saveDir.getPath());
            String albumName = LocalAlbum.getLocalizedName(getResources(), bucketId, null);
            showSavingProgress(albumName);
            /* SPRD: for bug 473964,489089 edit on the original picture@{ */
            // mImageShow.saveImage(this, null);
            File dest = SaveImage.getNewFile(this, mSelectedImageUri);
            // SPRD: Modify 20151231 for bug519394, TransactionTooLargeException thrown if size of parcel too large
            // especially do much change when do ImageDraw. So consider to save ImagePreset instead of transfer it
            // by intent. @{
            /*
            Intent processIntent = ProcessingService.getSaveIntent(this, MasterImage
                    .getImage().getPreset(), dest, mSelectedImageUri, mSelectedImageUri, true, 90,
                    1f, true);
            */
            MasterImage.getImage().setSavedPreset(MasterImage.getImage().getPreset());
            Intent processIntent = ProcessingService.getSaveIntent(this, dest, mSelectedImageUri,
                    mSelectedImageUri, true, 90, 1f, true);
            // @}
            startService(processIntent);
            /* @} */
        } else {
            done();
        }
    }


    public void done() {
        hideSavingProgress();
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        finish();
    }

    private void extractXMPData() {
        // SPRD: Modify 20151222 for bug492889, NPE thrown when do intentfuzzer test.
        if (getIntent().getData() == null) {
            Log.e(LOGTAG, "extractXMPData() -> Empty intent, do nothing.");
            finish();
            return;
        }

        XMresults res = XmpPresets.extractXMPData(
                getBaseContext(), mMasterImage, getIntent().getData());
        if (res == null) {
            return;
        }

        mOriginalImageUri = res.originalimage;
        mOriginalPreset = res.preset;
    }

    public Uri getSelectedImageUri() {
        return mSelectedImageUri;
    }

    public void setHandlesSwipeForView(View view, float startX, float startY) {
        mHandlingSwipeButton = view != null;
        mHandledSwipeView = view;
        int[] location = new int[2];
        view.getLocationInWindow(location);
        mSwipeStartX = location[0] + startX;
        mSwipeStartY = location[1] + startY;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        /* SPRD: for bug 510487 Interface without initialization, throw NullPointerException when click @{ */
        if (isLoadingVisible()) {
            return true;
        }
        /* @} */
        if (mHandlingSwipeButton) {
            int direction = CategoryView.HORIZONTAL;
            if (mHandledSwipeView instanceof CategoryView) {
                direction = ((CategoryView) mHandledSwipeView).getOrientation();
            }

            // SPRD:ADD for bug 398897 do not remove operate when edit mode. @{
            if (mHandledSwipeView instanceof StateView) {
                Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(
                        MainPanel.FRAGMENT_TAG);
                if (currentPanel instanceof EditorPanel) {
                    return super.dispatchTouchEvent(ev);
                }
            }
            // @}

            if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float delta = ev.getY() - mSwipeStartY;
                float distance = mHandledSwipeView.getHeight();
                if (direction == CategoryView.VERTICAL) {
                    delta = ev.getX() - mSwipeStartX;
                    mHandledSwipeView.setTranslationX(delta);
                    distance = mHandledSwipeView.getWidth();
                } else {
                    mHandledSwipeView.setTranslationY(delta);
                }
                delta = Math.abs(delta);
                float transparency = Math.min(1, delta / distance);
                mHandledSwipeView.setAlpha(1.f - transparency);
                mHandledSwipeViewLastDelta = delta;
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || ev.getActionMasked() == MotionEvent.ACTION_UP) {
                mHandledSwipeView.setTranslationX(0);
                mHandledSwipeView.setTranslationY(0);
                mHandledSwipeView.setAlpha(1.f);
                mHandlingSwipeButton = false;
                float distance = mHandledSwipeView.getHeight();
                if (direction == CategoryView.VERTICAL) {
                    distance = mHandledSwipeView.getWidth();
                }
                if (mHandledSwipeViewLastDelta > distance) {
                    ((SwipableView) mHandledSwipeView).delete();
                }
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public Point mHintTouchPoint = new Point();

    public Point hintTouchPoint(View view) {
        int location[] = new int[2];
        view.getLocationOnScreen(location);
        int x = mHintTouchPoint.x - location[0];
        int y = mHintTouchPoint.y - location[1];
        return new Point(x, y);
    }

    public void startTouchAnimation(View target, float x, float y) {
        final CategorySelected hint =
                findViewById(R.id.categorySelectedIndicator);
        int location[] = new int[2];
        target.getLocationOnScreen(location);
        mHintTouchPoint.x = (int) (location[0] + x);
        mHintTouchPoint.y = (int) (location[1] + y);
        int locationHint[] = new int[2];
        ((View) hint.getParent()).getLocationOnScreen(locationHint);
        int dx = (int) (x - (hint.getWidth()) / 2);
        int dy = (int) (y - (hint.getHeight()) / 2);
        hint.setTranslationX(location[0] - locationHint[0] + dx);
        hint.setTranslationY(location[1] - locationHint[1] + dy);
        hint.setVisibility(View.VISIBLE);
        hint.animate().scaleX(2).scaleY(2).alpha(0).withEndAction(new Runnable() {
            @Override
            public void run() {
                hint.setVisibility(View.INVISIBLE);
                hint.setScaleX(1);
                hint.setScaleY(1);
                hint.setAlpha(1);
            }
        });
    }

    /*
     * SPRD:bug 590378, after draw image,apply mirrorFilter error @{
     */
    private void checkDrawFilter() {
        if (mNeedCommit && mFilterDrawRepresentation != null) {
            ImagePreset oldPreset = MasterImage.getImage().getPreset();
            ImagePreset copy = new ImagePreset(oldPreset);
            FilterRepresentation filter = mFilterDrawRepresentation.copy();
            filter.setIgnoreStatus(true);
            copy.addFilter(filter);
            MasterImage.getImage().commitDrawFilter(copy);
            mFilterDrawRepresentation = null;
            mNeedCommit = false;
        }
    }

    /* @} */
    /* usb storage changed */
    private void onStorageChanged(String path, String action) {
        if (mSelectedImageUri != null && path != null) {
            FilterShowActivity.this.finish();
        }

    }

    /* usb storage changed */
    public void saveMenuItem(boolean isShow) {
        if (mSaveItem != null) {
            mSaveItem.setVisible(isShow || (mImageShow != null && mImageShow.hasModifications()));
        }
    }

    public int getHistoryCount() {
        HistoryManager adapter = mMasterImage.getHistory();
        int count = adapter.getCount();
        return count;
    }

    private class MyMenuUpdateListener implements HistoryManager.OnMenuUpdateListener {
        private MenuItem mSaveMenuItem;

        public MyMenuUpdateListener(MenuItem saveItem) {
            mSaveMenuItem = saveItem;
        }

        @Override
        public void onMenuUpdated() {
            if (mImageShow != null && mSaveMenuItem != null) {
                mSaveMenuItem.setVisible(mImageShow.hasModifications());
            }
        }
    }

    private boolean checkPermissions() {
        boolean hasCriticalPermissions;

        hasCriticalPermissions = GalleryUtils.checkStoragePermissions(this);
        if (!hasCriticalPermissions) {
            try {
                GalleryUtils.requestPermission(this, GalleryPermissionsActivity.class, PermissionsActivity
                        .START_FROM_FILTER_SHOW);
            } catch (Exception e) {
                Toast.makeText(FilterShowActivity.this, R.string.gallery_premission_error, Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return hasCriticalPermissions;
    }

    private void requestSaveImage() {
        File saveDir = SaveImage.getSaveDirectory(this, mSelectedImageUri);
        if (saveDir == null) {
            Toast.makeText(GalleryAppImpl.getApplication(), R.string.refocus_save_fail, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String filePath = saveDir.getAbsolutePath();
        if (willSaveImage()
                && !GalleryStorageUtil.isInInternalStorage(filePath)
                && !SdCardPermission.hasStoragePermission(filePath)) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    saveImage();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(FilterShowActivity.this, null);
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            SdCardPermission.requestSdcardPermission(this, storagePaths, this, sdCardPermissionListener);
        } else {
            saveImage();
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }
}

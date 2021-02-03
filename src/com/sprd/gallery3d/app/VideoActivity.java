/**
 * Created by Spreadst
 */

package com.sprd.gallery3d.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.GalleryActionBar;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.app.SinglePhotoPage;
import com.android.gallery3d.app.SlideshowPage;
import com.android.gallery3d.app.SprdAlbumPage;
import com.android.gallery3d.app.SprdAlbumSetPage;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.util.GalleryUtils;

public final class VideoActivity extends AbstractGalleryActivity implements OnCancelListener {
    public static final String EXTRA_SLIDESHOW = "slideshow";
    public static final String EXTRA_CROP = "crop";

    public static final String ACTION_REVIEW = "com.android.camera.action.REVIEW";
    public static final String KEY_GET_CONTENT = "get-content";
    public static final String KEY_GET_ALBUM = "get-album";
    public static final String KEY_TYPE_BITS = "type-bits";
    public static final String KEY_MEDIA_TYPES = "mediaTypes";

    private static final String TAG = "VideoActivity";
    private GalleryActionBar mActionBar;
    private Dialog mVersionCheckDialog;
    private static Activity sLastActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        /* SPRD: Modify 20150609 Spreadst of bug444059, avoid ANR in monkey test cause by too many threads run @{ */
        if (GalleryUtils.isMonkey()) {
            if (sLastActivity != null) {
                Log.e(TAG, "VideoActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
            }
            sLastActivity = this;
        }
        /* @} */
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.main);
        /**SPRD:Bug510007  check storage permission  @{*/
        if (!GalleryUtils.checkStoragePermissions(this)) {
            GalleryUtils.requestPermission(this, PickVideosPermissionsActivity.class, PermissionsActivity.START_FROM_VIDEO);
            finish();
            return;
        }

        /**@}*/
        mActionBar = new GalleryActionBar(this);

        if (savedInstanceState != null) {
            getStateManager().restoreFromState(savedInstanceState);
        } else {
            initializeByIntent();
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
                //if (type.endsWith("/image")) intent.setType("image/*");
                if (type.endsWith("/video")) {
                    intent.setType("video/*");
                }
            }
            startGetContent(intent);
        } else if (Intent.ACTION_VIEW.equalsIgnoreCase(action)
                || ACTION_REVIEW.equalsIgnoreCase(action)) {
            startViewAction(intent);
        } else {
            startDefaultPage();
        }
    }

    public void startDefaultPage() {
        PicasaSource.showSignInReminder(this);
        Bundle data = new Bundle();
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_VIDEO));
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
        data.putBoolean(KEY_GET_CONTENT, true);
        int typeBits = DataManager.INCLUDE_VIDEO;
        if (ApiHelper.HAS_INTENT_EXTRA_LOCAL_ONLY) {
            if (intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)) {
                typeBits |= DataManager.INCLUDE_LOCAL_ONLY;
            }
        }
        data.putInt(KEY_TYPE_BITS, typeBits);
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, getDataManager().getTopSetPath(typeBits));
        getStateManager().startState(SprdAlbumSetPage.class, data);
    }

    private String getContentType(Intent intent) {
        String type = intent.getType();
        if (type != null) {
            return type;
        }

        Uri uri = intent.getData();
        try {
            return getContentResolver().getType(uri);
        } catch (Throwable t) {
            Log.w(TAG, "get type fail", t);
            return null;
        }
    }

    private void startViewAction(Intent intent) {
        Boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);
        if (slideshow) {
            getActionBar().hide();
            DataManager manager = getDataManager();
            Path path = manager.findPathByUri(intent.getData(), intent.getType());
            if (path == null || manager.getMediaObject(path)
                    instanceof MediaItem) {
                path = Path.fromString(
                        manager.getTopSetPath(DataManager.INCLUDE_IMAGE));
            }
            Bundle data = new Bundle();
            data.putString(SlideshowPage.KEY_SET_PATH, path.toString());
            data.putBoolean(SlideshowPage.KEY_RANDOM_ORDER, true);
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
                        getStateManager().startState(SprdAlbumPage.class, data);
                    } else {
                        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH, setPath.toString());
                        getStateManager().startState(SprdAlbumSetPage.class, data);
                    }
                } else {
                    startDefaultPage();
                }
            } else {
                Path itemPath = dm.findPathByUri(uri, contentType);
                if (itemPath == null) {
                    Log.w(TAG, "itemPath is null");
                    return;
                }
                /**SPRD:473267 M porting add video entrance & related bug-fix
                 Modify 20150106 of bug 390428,video miss after crop @{ */
                Path albumPath = dm.getDefaultSetOf(false, itemPath, intent.getAction());
                /**@}*/
                // TODO: Make this parameter public so other activities can reference it.
                boolean singleItemOnly = intent.getBooleanExtra("SingleItemOnly", false);
                if (!singleItemOnly && albumPath != null) {
                    data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                            albumPath.toString());
                }
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, itemPath.toString());
                getStateManager().startState(SinglePhotoPage.class, data);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // send the back event to the top sub-state
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            getStateManager().onBackPressed();
        } finally {
            root.unlockRenderThread();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GLRoot root = getGLRoot();
        if (root != null) {
            root.lockRenderThread();
        }
        try {
            getStateManager().destroy();
        } finally {
            if (root != null) {
                root.unlockRenderThread();
            }
        }
    }

    // SPRD:bug 627975 for video, toast error
    @Override
    protected int getToastFlag() {
        return GalleryUtils.DONT_SUPPORT_VIEW_VIDEO;
    }

    @Override
    protected void onResume() {
        //Utils.assertTrue(getStateManager().getStateCount() > 0);
        super.onResume();
        /* SPRD:bug 543815 if no critical permissions,ActivityState count is 0,when fixed screen @{ */
        if (getStateManager().getStateCount() <= 0) {
            finish();
            return;
        }
        if (mActionBar != null) {
            mActionBar.updateGalleryActionBar(this);
        }
        /* @} */
        if (mVersionCheckDialog != null) {
            mVersionCheckDialog.show();
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
    public GalleryActionBar getGalleryActionBar() {
        return mActionBar;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mVersionCheckDialog) {
            mVersionCheckDialog = null;
        }
    }
}

package com.android.gallery3d.v2.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.UriImage;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.cust.MotionImageView;
import com.android.gallery3d.v2.cust.MotionThumbView;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.MotionThumbItem;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.android.gallery3d.v2.util.MotionFrameDecoder;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MotionActivity extends AppCompatActivity implements MotionFrameDecoder.OnFrameAvailableListener,
        MotionThumbView.OnThumbSelectListener, SdCardPermissionAccessor {
    private static final String TAG = MotionActivity.class.getSimpleName();
    private static final String DATA_PATH = "data-media-path";
    private static final int REQUEST_MOTION = 4; //REQUEST_EDIT

    private AlertDialog mPermissionDeniedDialog;

    private MotionImageView mMotionImageView;
    private MotionThumbView mMotionThumbView;

    private MotionFrameDecoder mMotionFrameDecoder;

    private MenuItem mSaveMenu;
    private MotionThumbItem mCurrentThumbItem;

    private WeakReference<ProgressDialog> mSavingProgressDialog;
    private WeakReference<ProgressDialog> mLoadingProgressDialog;

    private Path mPath;
    private Uri mSavedUri;

    private SdCardPermissionListener mSdCardPermissionListener;

    private static boolean mSecureCamera;

    public static void launch(Activity activity, MediaItem item) {
        if (item instanceof UriImage) {
            try {
                long _id = ContentUris.parseId(item.getContentUri());
                if (_id > 0) {
                    item = (MediaItem) GalleryAppImpl.getApplication().getDataManager()
                            .getMediaObject(LocalImage.ITEM_PATH.getChild(_id));
                    Log.d(TAG, "launch is UriImage : " + item.getPath());
                }
            } catch (Exception ignored) {
            }
        }
        Intent intent = new Intent(activity, MotionActivity.class);
        intent.setData(item.getContentUri());
        intent.putExtra(DATA_PATH, item.getPath().toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivityForResult(intent, REQUEST_MOTION);
    }

    public static void launch(Fragment fragment, MediaItem item) {
        if (item instanceof UriImage) {
            try {
                long _id = ContentUris.parseId(item.getContentUri());
                if (_id > 0) {
                    item = (MediaItem) GalleryAppImpl.getApplication().getDataManager()
                            .getMediaObject(LocalImage.ITEM_PATH.getChild(_id));
                    Log.d(TAG, "launch is UriImage : " + item.getPath());
                }
            } catch (Exception ignored) {
            }
        }
        mSecureCamera = fragment.getArguments().getBoolean(Constants.KEY_SECURE_CAMERA);
        Intent intent = new Intent(fragment.getContext(), MotionActivity.class);
        intent.setData(item.getContentUri());
        intent.putExtra(DATA_PATH, item.getPath().toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fragment.startActivityForResult(intent, REQUEST_MOTION);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_motion);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mMotionImageView = findViewById(R.id.image);
        mMotionThumbView = findViewById(R.id.thumb);
        mMotionThumbView.setOnThumbSelectListener(this);

        //check permission
        if (PermissionUtil.hasPermissions(this)) {
            //load data if has permission
            loadData();
        } else {
            PermissionUtil.requestPermissions(this);
        }
    }

    private void loadData() {
        mPath = Path.fromString(getIntent().getStringExtra(DATA_PATH));

        mMotionFrameDecoder = new MotionFrameDecoder(mPath, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        // UNISOC added for bug 1206598, display activity in secure camera mode.
        if (mSecureCamera) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        if (mMotionFrameDecoder != null) {
            mMotionFrameDecoder.resume();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.motion_menu, menu);
        mSaveMenu = menu.findItem(R.id.action_save);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                if (ClickInterval.ignore()) {
                    return true;
                }
                saveSelectImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (mMotionFrameDecoder != null) {
            mMotionFrameDecoder.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mMotionFrameDecoder != null) {
            mMotionFrameDecoder.destroy();
        }
        hideSavingProgress();
        hideLoadingProgress();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult");
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

    @Override
    public void onScreenNail(Bitmap bitmap) {
        if (mMotionImageView.getBitmap() == null) {
            mMotionImageView.setBitmap(bitmap);
        }
    }

    @Override
    public void onFrameDecodeStart(boolean forSave) {
        if (forSave) {
            //保存图片开始
            int bucketId = ((LocalMediaItem) GalleryAppImpl.getApplication()
                    .getDataManager().getMediaObject(mPath)).getBucketId();
            String albumName = LocalAlbum.getLocalizedName(getResources(), bucketId, null);
            showSavingProgress(albumName);
        } else {
            showLoadingProgress();
            mMotionThumbView.onFrameDecodeStart(forSave);
        }
    }

    @Override
    public void onFrameDecodeEnd(boolean forSave) {
        if (forSave) {
            //保存图片结束
            hideSavingProgress();
            Log.d(TAG, "save done, mSavedUri = " + mSavedUri);
            if (mSavedUri == null) {
                mSavedUri = GalleryAppImpl.getApplication().getDataManager()
                        .getMediaObject(mPath).getContentUri();
            }
            setResult(RESULT_OK, new Intent().setData(mSavedUri));
            finish();
        } else {
            hideLoadingProgress();
            mMotionThumbView.onFrameDecodeEnd(forSave);
        }
    }

    @Override
    public void onFrameAvailable(MotionThumbItem item) {
        mMotionThumbView.onFrameAvailable(item);
    }

    @Override
    public void onThumbSelected(MotionThumbItem item) {
        mCurrentThumbItem = item;
        if (mSaveMenu != null) {
            mSaveMenu.setVisible(true);
        }
        mMotionImageView.setBitmap(item.getBitmap());
    }

    @Override
    public void onSaveFrame(String path, Bitmap bitmap, long saveTime) {
        //保存图片到文件并插入数据库
        Log.d(TAG, "onSaveFrame path = " + path);
        FileOutputStream fos = null;
        try {
            if (!GalleryStorageUtil.isInInternalStorage(path)) {
                SdCardPermission.mkFile(path);
                fos = new FileOutputStream(SdCardPermission
                        .createExternalFileDescriptor(path, "rw").getFileDescriptor());
            } else {
                fos = new FileOutputStream(path);
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            MediaItem item = (MediaItem) GalleryAppImpl.getApplication().getDataManager().getMediaObject(mPath);
            ContentValues values = SaveImage.getContentValues(this, item.getContentUri(),
                    new File(path), saveTime);
            mSavedUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            GalleryUtils.copyExifInfo(item.filePath, path);
        } catch (Exception e) {
            Log.e(TAG, "save image error", e);
        } finally {
            Utils.closeSilently(fos);
        }
    }

    @Override
    public void onSaveFrame(Uri uri, Bitmap bitmap, long saveTime) {
        Log.d(TAG, "onSaveFrame uri = " + uri);
        FileOutputStream fos = null;
        try {
            fos = (FileOutputStream) getContentResolver().openOutputStream(uri);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            SaveImage.updateFilePending(this, uri, bitmap.getWidth(), bitmap.getHeight());
            mSavedUri = uri;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(fos);
        }
    }

    private void saveSelectImage() {
        if (mCurrentThumbItem == null) {
            Log.d(TAG, "no item selected!!");
            return;
        }
        MediaItem item = (MediaItem) GalleryAppImpl.getApplication().getDataManager().getMediaObject(mPath);
        ArrayList<String> filePaths = new ArrayList<>();

        if (!GalleryStorageUtil.isInInternalStorage(item.getFilePath())
                && !SdCardPermission.hasStoragePermission(item.getFilePath())) {
            String storageName = SdCardPermission.getStorageName(item.getFilePath());
            filePaths.add(storageName);
        }

        if (filePaths.size() > 0) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    Log.d(TAG, "permission allowed startSaveTask");
                    mMotionFrameDecoder.startSaveTask(mCurrentThumbItem);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(MotionActivity.this, null);
                }
            };
            SdCardPermission.requestSdcardPermission(this, filePaths, this, sdCardPermissionListener);
        } else {
            Log.d(TAG, "has permission startSaveTask");
            mMotionFrameDecoder.startSaveTask(mCurrentThumbItem);
        }
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
        String progressText;
        if (albumName == null) {
            progressText = getString(R.string.saving_image);
        } else {
            progressText = getString(R.string.filtershow_saving_image, albumName);
        }
        progress = ProgressDialog.show(this, "", progressText, true, false);
        mSavingProgressDialog = new WeakReference<>(progress);
    }

    private void hideSavingProgress() {
        if (mSavingProgressDialog != null) {
            ProgressDialog progress = mSavingProgressDialog.get();
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
            }
        }
    }

    private void showLoadingProgress() {
        ProgressDialog progress;
        if (mLoadingProgressDialog != null) {
            progress = mLoadingProgressDialog.get();
            if (progress != null) {
                progress.show();
                return;
            }
        }
        String progressText = getString(R.string.loading);
        progress = ProgressDialog.show(this, "", progressText, true, false);
        mLoadingProgressDialog = new WeakReference<>(progress);
    }

    private void hideLoadingProgress() {
        if (mLoadingProgressDialog != null) {
            ProgressDialog progress = mLoadingProgressDialog.get();
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
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
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }
}

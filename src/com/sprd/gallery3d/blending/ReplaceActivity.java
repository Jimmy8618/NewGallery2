package com.sprd.gallery3d.blending;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.filters.FilterFxRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterFx;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.util.Constants;
import com.sprd.blending.ImageBlending;
import com.sprd.blending.bean.ExtractResult;
import com.sprd.blending.bean.TagPoint;
import com.sprd.blending.bean.UpdateInfo;
import com.sprd.gallery3d.blending.bean.BlendingRequest;
import com.sprd.gallery3d.blending.view.BlendingView;
import com.sprd.gallery3d.blending.view.VerticalSeekBar;
import com.sprd.refocus.RefocusUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ReplaceActivity extends Activity implements View.OnClickListener, BlendingView.StateChangeCallback, BlendIngManager.ExtractCallBack {

    private static final String TAG = ReplaceActivity.class.getSimpleName();

    private static final String GOOGLE_PHOTOS_CONTENT_PROVIDER = "content://com.google.android.apps.photos.contentprovider";
    private final static String TARGET_BACK_BLUR_VERSION = "persist.sys.cam.ba.blur.version";
    private final static String TARGET_FOR_BLUR_VERSION = "persist.sys.cam.fr.blur.version";
    //public int refocusbacVersion = SprdFramewoks.getInstances().getIntFromSystemProperties(TARGET_BACK_BLUR_VERSION, -1);
    //public int refocusforVersion = SprdFramewoks.getInstances().getIntFromSystemProperties(TARGET_FOR_BLUR_VERSION, -1);
    private static final int SET_ORIG_IMAGE = -1;
    private static final int ADD_NEW_BACKGROUND = -2;
    private static final int SELECT_IMAGE = 3;
    private static final int CROP_IMAGE = 4;

    private LinearLayout mBottomLayout;
    private LinearLayout mFixSubject;
    private TextView mFixSubjectText;
    private ImageButton mFixSubjectButton;
    private LinearLayout mFixBackground;
    private TextView mFixBackgroundText;
    private ImageButton mFixBackgroundButton;
    private BlendingView mBlendingView;
    private VerticalSeekBar mRotate;
    private VerticalSeekBar mZoom;
    private View mZoomLayout;
    private View mRotateLayout;
    private RecyclerView mRecycleView;
    private ProgressBar mProgressbar;
    private RecycleAdapter mRecycleAdapter;
    private Uri mUri;

    private ImageFilter mImageFilter;
    private MenuItem mUndoItem;
    private MenuItem mHelpItem;
    private MenuItem mConfirmItem;
    private MenuItem mSaveItem;
    private ProgressDialog mProgressDialog;
    private boolean mHasChanged = false;

    private String path;
    private boolean mIsOrigImage = true;
    private List<UpdateInfo> mOperations = new ArrayList<>();
    private BlendIngManager manager;
    private ExtractResult extractresult;
    private List<ImageFilter> mFilters = new ArrayList();
    private boolean mQuit = false;
    private boolean mSecureCamera = false;

    private Future mTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (setupIntent()) {
            return;
        }
        setContentView(R.layout.activity_replace);
        initActionbar();
        initView();
        switchState(BlendingView.INVALID);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
        verifyFirst("imageblending", "first_load");
        mSecureCamera = getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false);
        if (mSecureCamera) {
            Window win = getWindow();
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(winParams);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }
        hideDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: called");
        if (manager != null) {
            manager.destory();
        }
        if (mFilters != null && mFilters.size() > 0) {
            for (ImageFilter filter : mFilters) {
                filter.freeResources();
            }
        }
    }

    private void initActionbar() {
        android.app.ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowTitleEnabled(true);
        } else {
            Log.d(TAG, "initActionbar: actionBar is null");
        }
        if (mBlendingView != null) {
            mBlendingView.setStateChangeCallback(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        getMenuInflater().inflate(R.menu.imgaeblending, menu);
        mUndoItem = menu.findItem(R.id.undo);
        mHelpItem = menu.findItem(R.id.help);
        mConfirmItem = menu.findItem(R.id.ok);
        mSaveItem = menu.findItem(R.id.save);
        actionbarStateChange();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undo:
                if (mBlendingView.getState() == BlendingView.EDIT_BACKGROUND) {
                    if (mIsOrigImage) {
                        mSaveItem.setEnabled(false);
                        mBlendingView.setDisplayBitmap(manager.getSrcInScreenbitmap());
                    } else {
                        mBlendingView.setDisplayBitmap(manager.getmDstInScreenBitmap());
                    }
                    /* @Bug 1226248 */
                    if(mRecycleAdapter != null){
                        mRecycleAdapter.setSelectnNum(0);
                    }
                    if(mRecycleView != null){
                        mRecycleView.smoothScrollToPosition(0);
                    }
                    /* @ */
                    mUndoItem.setEnabled(false);
                } else if (mBlendingView.getState() == BlendingView.UPDATE_SUBJECT_OUTLINE) {
                    Log.d(TAG, "onOptionsItemSelected: ");
                    if (mBlendingView.hasStroke()) {
                        Log.d(TAG, "onOptionsItemSelected: 8");
                        mBlendingView.undo();
                        if (!mBlendingView.hasStroke()) {
                            enableCorrect(false);
                            mUndoItem.setEnabled(false);
                        }
                        break;
                    }
                    manager.unDo(mBlendingView);
                }
                break;
            case R.id.help:
                showHelpDialog();
                break;
            case R.id.ok:
                if (mBlendingView.getState() == BlendingView.UPDATE_SUBJECT_OUTLINE) {
                    mRecycleAdapter.notifyDataSetChanged();
                    mBlendingView.setNotMoveTarget(true);
                    mBlendingView.setState(BlendingView.EDIT_BACKGROUND);
                } else if (mBlendingView.getState() == BlendingView.ADJUST_FOREGROUND_SUBJECT_RECT) {
                    showProgressDialog();
                    manager.extractBitmap(mBlendingView);
                }
                mUndoItem.setEnabled(false);
                break;
            case R.id.save:
                if (!mHasChanged) {
                    break;
                }

                beginSave();
                break;
            case android.R.id.home:
                if (mBlendingView.getState() == BlendingView.EDIT_BACKGROUND) {
                    doSave();
                } else {
                    super.onBackPressed();
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_IMAGE || requestCode == CROP_IMAGE) {
                final Uri uri = data.getData();
                Log.d(TAG, "onActivityResult: " + uri + " requestCode : " + requestCode);
                try {
                    initNewBackground(uri, requestCode);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.init_bitmap_faild, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mBlendingView.getState() == BlendingView.EDIT_BACKGROUND) {
            mQuit = true;
            doSave();
        } else {
            super.onBackPressed();
        }
    }

    private void hideDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
    }

    private void initNewBackground(final Uri uri, int requestCode) {
        Bitmap bitmap;
        if (manager == null) {
            return;
        }
        final Bitmap targetBitmap = manager.getSrcBitmap();
        if (targetBitmap == null) {
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        RefocusUtils.loadBitmap(ReplaceActivity.this, uri, options);

        float bitmapRatio = (float) options.outWidth / (float) options.outHeight;
        bitmapRatio = (float) Math.round(bitmapRatio * 100) / 100f;
        float targetRatio = (float) targetBitmap.getWidth() / (float) targetBitmap.getHeight();
        targetRatio = (float) Math.round(targetRatio * 100) / 100f;
        Log.d(TAG, "initNewBackground: suit the bitmap to screen : " + options.outWidth + "- " + options.outHeight);
        Log.d(TAG, "initNewBackground: bitmapRatio : " + bitmapRatio + " ratio : " + targetRatio);
        if (Math.abs(bitmapRatio - targetRatio) < 0.02) {
            options.inJustDecodeBounds = false;
            bitmap = RefocusUtils.loadBitmap(ReplaceActivity.this, uri, options);
            if (bitmap.getWidth() != targetBitmap.getWidth()) {
                //go to scale
                Log.d(TAG, "initNewBackground: scale");
                bitmap = Bitmap.createScaledBitmap(bitmap, targetBitmap.getWidth(), targetBitmap.getHeight(), true);
            } else {
                //normal, do nothing
            }
            if (requestCode == CROP_IMAGE) {
                //delete the crop image, we have load it as a bitmap
                getContentResolver().delete(uri, null, null);
                Log.d(TAG, "initNewBackground: delete " + uri);
            }
            mHasChanged = true;
            mSaveItem.setEnabled(true);
            mUndoItem.setEnabled(false);
            mZoomLayout.setVisibility(View.GONE);
            mRotateLayout.setVisibility(View.GONE);
            manager.setmDstBitmap(bitmap);
            mBlendingView.setNotMoveTarget(false);
            mBlendingView.setDisplayBitmap(manager.getmDstInScreenBitmap());
            mImageFilter = null;
            mIsOrigImage = false;
            new GetAdapterData().start();
        } else {
            //go to crop
            if (requestCode == CROP_IMAGE) {
                //delete the crop image, we have load it as a bitmap
                getContentResolver().delete(uri, null, null);
                Log.d(TAG, "initNewBackground: delete " + uri);
                Toast.makeText(this, R.string.init_bitmap_faild, Toast.LENGTH_SHORT).show();
                return;
            }
            if (uri.toString().startsWith(GOOGLE_PHOTOS_CONTENT_PROVIDER)) {
                if (mTask != null) {
                    mTask.cancel();
                    mTask = null;
                }
                mProgressbar.setVisibility(View.VISIBLE);
                mTask = GalleryAppImpl.getApplication().getThreadPool().submit(new ThreadPool.Job<Uri>() {
                    @Override
                    public Uri run(ThreadPool.JobContext jc) {
                        try {
                            return getUriForFile(uri, jc);
                        } catch (Exception e) {
                            Log.e(TAG, "initNewBackground error, " + e.toString());
                            return null;
                        }
                    }
                }, new FutureListener<Uri>() {
                    @Override
                    public void onFutureDone(Future<Uri> future) {
                        if (future.isCancelled()) {
                            return;
                        }
                        final Uri cropUri = future.get();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressbar.setVisibility(View.GONE);
                                startCropActivity(cropUri == null ? uri : cropUri,
                                        targetBitmap.getWidth(), targetBitmap.getHeight());
                            }
                        });
                    }
                });
            } else {
                startCropActivity(uri, targetBitmap.getWidth(), targetBitmap.getHeight());
            }
        }
    }

    private void startCropActivity(Uri uri, int width, int height) {
        Log.d(TAG, "startCropActivity uri = " + uri + ", width = " + width + ", height = " + height);
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.putExtra("aspectX", width);
        intent.putExtra("aspectY", height);
        intent.putExtra("crop_in_gallery", true);
        intent.putExtra(CropExtras.KEY_SHOW_WHEN_LOCKED, mSecureCamera);
        intent.setDataAndType(uri, "image/*");
        startActivityForResult(intent, CROP_IMAGE);
    }

    private Uri getUriForFile(Uri uri, ThreadPool.JobContext jc) {
        if (jc.isCancelled()) {
            return null;
        }
        Uri retUri = null;
        String path = findPathByUri(uri);
        if (jc.isCancelled()) {
            return null;
        }
        if (path != null) {
            Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Images.ImageColumns._ID
            }, MediaStore.Images.ImageColumns.DATA + "=?", new String[]{path}, null);
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    retUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getInt(0));
                }
            }
            Utils.closeSilently(cursor);
        }
        return retUri;
    }

    private String findPathByUri(Uri uri) {
        String _data = null;
        Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToNext()) {
                _data = cursor.getString(0);
            }
        }
        Utils.closeSilently(cursor);
        return _data;
    }

    private boolean setupIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return true;
        }
        path = intent.getStringExtra("path");
        mUri = intent.getData();
        if (mUri == null) {
            finish();
            Log.d(TAG, "onCreate: mUri is null");

            return true;
        }
        Log.d(TAG, "onCreate: mUri :" + mUri + "--path :" + path);
        return false;
    }

    private void initView() {
        mBlendingView = findViewById(R.id.srcview);
        mBlendingView.setStateChangeCallback(this);
        mBottomLayout = findViewById(R.id.bottom_content);
        mFixSubject = findViewById(R.id.fix_subject);
        mFixSubjectText = findViewById(R.id.fix_subject_text);
        mFixSubjectButton = findViewById(R.id.fix_subject_button);
        mFixBackground = findViewById(R.id.fix_background);
        mFixBackgroundText = findViewById(R.id.fix_background_text);
        mFixBackgroundButton = findViewById(R.id.fix_background_button);
        mProgressbar = findViewById(R.id.init_progressbar);

        mFixSubject.setOnClickListener(this);
        mFixSubjectText.setOnClickListener(this);
        mFixSubjectButton.setOnClickListener(this);
        mFixBackgroundText.setOnClickListener(this);
        mFixBackgroundButton.setOnClickListener(this);
        mFixBackground.setOnClickListener(this);

        mZoomLayout = findViewById(R.id.zoom);
        mRotateLayout = findViewById(R.id.rotate);
        mRotate = findViewById(R.id.rotate_seekbar);
        mZoom = findViewById(R.id.zoom_seekbar);

        //if use seekbar need remove this
        mZoomLayout.setVisibility(View.GONE);
        mZoomLayout.setVisibility(View.GONE);

        mZoom.setListenr(new VerticalSeekBar.onCheckProgressListener() {
            @Override
            public boolean onCheck(int progress) {
                Log.d(TAG, "onCheck() called with: progress = [" + progress + "]");
                float v = progress + (mBlendingView.getminFactor() * 100);
                Log.d(TAG, "onCheck: " + v + " v * 0.01f :" + (v * 0.01f));
                return mBlendingView.checkZoom(v * 0.01f);
            }
        });
        mZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "onProgressChanged() called with progress = [" + progress + "], fromUser = [" + fromUser + "]");
                float v = progress + (mBlendingView.getminFactor() * 100);
                Log.d(TAG, "onProgressChanged: " + v + " v * 0.01f :" + (v * 0.01f));
                mBlendingView.setScaleFactor(v * 0.01f);
                if (mSaveItem != null) {
                    mSaveItem.setEnabled(true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onStartTrackingTouch: 1");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onStopTrackingTouch: 2");
            }
        });
        mRotate.setListenr(new VerticalSeekBar.onCheckProgressListener() {
            @Override
            public boolean onCheck(int progress) {
                return mBlendingView.checkRotate(180 - progress);
            }
        });
        mRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "onProgressChanged() seekBar progress= [" + progress + "]");
                if (mRotate.getVisibility() == View.VISIBLE) {
                    mBlendingView.setRotate(180 - progress);
                    mSaveItem.setEnabled(true);
                    mHasChanged = true;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        mRecycleView = findViewById(R.id.recycleview);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mRecycleView.setLayoutManager(linearLayoutManager);
        mRecycleAdapter = new RecycleAdapter(this);
        mRecycleView.setAdapter(mRecycleAdapter);
        mRecycleAdapter.setSelectedCallback(new RecycleAdapter.SelectedCallback() {
            @Override
            public void OnSelected(RecycleAdapter.Thumbnail thumbnail) {
                Log.d(TAG, "OnSelected: " + thumbnail.id);
                Bitmap srcInScreenbitmap = manager.getSrcInScreenbitmap();
                Bitmap dstInScreenBitmap = manager.getmDstInScreenBitmap();

                if (thumbnail.id == SET_ORIG_IMAGE) {
                    mUndoItem.setEnabled(false);
                    mImageFilter = null;
                    mHasChanged = false;
                    if (mIsOrigImage) {
                        mSaveItem.setEnabled(false);
                        mBlendingView.setDisplayBitmap(srcInScreenbitmap);
                    } else {
                        mSaveItem.setEnabled(true);
                        mBlendingView.setDisplayBitmap(dstInScreenBitmap);
                    }
                } else if (thumbnail.id == ADD_NEW_BACKGROUND) {
                    selectImage(ReplaceActivity.this);
                } else {
                    Bitmap bitmap;
                    mHasChanged = true;
                    mSaveItem.setEnabled(true);
                    mUndoItem.setEnabled(true);
                    mImageFilter = mFilters.get(thumbnail.id);
                    if (mIsOrigImage) {
                        bitmap = srcInScreenbitmap.copy(Bitmap.Config.ARGB_8888, true);
                    } else {
                        bitmap = dstInScreenBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    }
                    Bitmap apply = mImageFilter.apply(bitmap, 1.0f, 2);
                    mBlendingView.setDisplayBitmap(apply);
                }
            }
        });
    }

    private void actionbarStateChange() {
        int state = mBlendingView.getState();
        ActionBar actionBar = getActionBar();
        Log.d(TAG, "actionbarStateChange: " + state);
        switch (state) {
            case BlendingView.SELECT_FOREGROUND_SUBJECT:
                actionBar.setTitle(R.string.select_foreground_subject);
                mHelpItem.setVisible(true);
                mUndoItem.setVisible(false);
                mConfirmItem.setVisible(false);
                mSaveItem.setVisible(false);
                break;
            case BlendingView.ADJUST_FOREGROUND_SUBJECT_RECT:
                actionBar.setTitle(R.string.adjust_foreground_subject);
                mHelpItem.setVisible(false);
                mUndoItem.setVisible(false);
                mConfirmItem.setVisible(true);
                mSaveItem.setVisible(false);
                break;
            case BlendingView.UPDATE_SUBJECT_OUTLINE:
                actionBar.setTitle(R.string.correct_subject_outline);
                mHelpItem.setVisible(true);
                mUndoItem.setVisible(true);
                mUndoItem.setEnabled(false);
                mConfirmItem.setVisible(true);
                mSaveItem.setVisible(false);
                break;
            case BlendingView.EDIT_BACKGROUND:
                actionBar.setTitle(R.string.edit_background);
                mHelpItem.setVisible(false);
                mUndoItem.setVisible(true);
                mConfirmItem.setVisible(false);
                mSaveItem.setVisible(true);
                mSaveItem.setEnabled(false);
                break;
            default:
                mHelpItem.setVisible(false);
                mUndoItem.setVisible(false);
                mConfirmItem.setVisible(false);
                mSaveItem.setVisible(false);
                mSaveItem.setEnabled(false);
        }
    }

    private void beginSave() {
        final Point targetInSrcPosition = mBlendingView.getOriginalPoint();
        if (targetInSrcPosition.x < 0) {
            targetInSrcPosition.x = 0;
        }
        if (targetInSrcPosition.y < 0) {
            targetInSrcPosition.y = 0;
        }
        final Point targetInSrcCenterPosition = mBlendingView.getTargetInSrcCenterPosition();

        int result = manager.verifyPosition(targetInSrcPosition, targetInSrcCenterPosition, mBlendingView.getScaleFactor(), mBlendingView.getRotate());
        // map to src
        //final Point screenPointInSrcLoc = mBlendingView.getScreenPointInSrcLoc(extractresult.getCoordinateX(),
        //        extractresult.getCoordinateY() + (int) mBlendingView.getSrcInScreenRectF().top);
        final Point screenPointInSrcLoc = new Point(extractresult.getCoordinateX(),
                extractresult.getCoordinateY() + (int) mBlendingView.getSrcInScreenRectF().top);
        if (result == 0) {
            showProgressDialog();
            manager.blendingBitmap(new BlendingRequest(mImageFilter, targetInSrcPosition, targetInSrcCenterPosition, path,
                    mIsOrigImage, screenPointInSrcLoc, mBlendingView.getScaleFactor(), mBlendingView.getRotate()));
        } else if (0 < result && result <= 3) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.verify_prompt);
            builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    showProgressDialog();
                    manager.blendingBitmap(new BlendingRequest(mImageFilter, targetInSrcPosition,
                            targetInSrcCenterPosition, path, mIsOrigImage, screenPointInSrcLoc,
                            mBlendingView.getScaleFactor(), mBlendingView.getRotate()));

                }
            });
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });
            builder.show();
        } else if (result == -1) {
            Toast.makeText(ReplaceActivity.this, R.string.fg_too_large, Toast.LENGTH_SHORT).show();
        }
    }

    private void doSave() {
        if (!mHasChanged) {
            finish();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved);
        builder.setPositiveButton(R.string.save_and_exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                beginSave();
            }
        });
        builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                finish();
            }
        });
        builder.show();
    }

    private void showHelpDialog() {
        int id = R.string.def_object_tips;
        int state = mBlendingView.getState();
        if (state == BlendingView.SELECT_FOREGROUND_SUBJECT) {
            id = R.string.def_object_tips;
        } else if (state == BlendingView.UPDATE_SUBJECT_OUTLINE) {
            id = R.string.correct_object_tips;
        }
        new AlertDialog.Builder(ReplaceActivity.this)
                .setMessage(id)
                .setNegativeButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    private void selectImage(Activity context) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Constants.KEY_SECURE_CAMERA, mSecureCamera);
        context.startActivityForResult(intent, SELECT_IMAGE);
    }

    private void switchState(int state) {
        Log.d(TAG, "switchState: " + state);
        switch (state) {
            case BlendingView.SELECT_FOREGROUND_SUBJECT:
                mBottomLayout.setVisibility(View.GONE);
                mFixSubject.setVisibility(View.GONE);
                mFixSubjectText.setVisibility(View.GONE);
                mFixBackgroundText.setVisibility(View.GONE);
                mFixBackground.setVisibility(View.GONE);
                mRecycleView.setVisibility(View.GONE);
                mZoomLayout.setVisibility(View.GONE);
                mRotateLayout.setVisibility(View.GONE);
                break;
            case BlendingView.ADJUST_FOREGROUND_SUBJECT_RECT:
                mBottomLayout.setVisibility(View.GONE);
                mFixSubject.setVisibility(View.GONE);
                mFixBackground.setVisibility(View.GONE);
                mFixSubjectText.setVisibility(View.GONE);
                mFixBackgroundText.setVisibility(View.GONE);
                mRecycleView.setVisibility(View.GONE);
                mZoomLayout.setVisibility(View.GONE);
                mRotateLayout.setVisibility(View.GONE);
                break;
            case BlendingView.UPDATE_SUBJECT_OUTLINE:
                mBottomLayout.setVisibility(View.VISIBLE);
                mFixSubject.setVisibility(View.VISIBLE);
                mFixBackground.setVisibility(View.VISIBLE);
                mFixSubjectButton.setEnabled(false);
                mFixSubject.setClickable(false);
                mFixSubjectText.setEnabled(false);
                mFixSubjectText.setTextColor(Color.GRAY);
                mFixBackgroundButton.setEnabled(false);
                mFixBackground.setClickable(false);
                mFixBackgroundText.setEnabled(false);
                mFixBackgroundText.setTextColor(Color.GRAY);
                mFixSubjectText.setVisibility(View.VISIBLE);
                mFixBackgroundText.setVisibility(View.VISIBLE);
                mRecycleView.setVisibility(View.GONE);
                mZoomLayout.setVisibility(View.GONE);
                mRotateLayout.setVisibility(View.GONE);
                verifyFirst("imageblending", "first_update");
                break;
            case BlendingView.EDIT_BACKGROUND:
                mBottomLayout.setVisibility(View.GONE);
                mFixSubject.setVisibility(View.GONE);
                mFixSubjectText.setVisibility(View.GONE);
                mFixBackgroundText.setVisibility(View.GONE);
                mFixBackground.setVisibility(View.GONE);
                mRecycleView.setVisibility(View.VISIBLE);
                mZoomLayout.setVisibility(View.GONE);
                mRotateLayout.setVisibility(View.GONE);
                break;
            default:
                mBottomLayout.setVisibility(View.GONE);
                mFixSubject.setVisibility(View.GONE);
                mFixSubjectText.setVisibility(View.GONE);
                mFixBackgroundText.setVisibility(View.GONE);
                mFixBackground.setVisibility(View.GONE);
                mRecycleView.setVisibility(View.GONE);
        }
    }

    private void verifyFirst(String preferencesName, String key) {
        SharedPreferences blending = getSharedPreferences(preferencesName, MODE_PRIVATE);
        boolean firstload = blending.getBoolean(key, true);
        if (firstload) {
            showHelpDialog();
            blending.edit().putBoolean(key, false).commit();
        }
    }

    private ArrayList<RecycleAdapter.Thumbnail> initAdapterData(Bitmap bitmap, List<ImageFilter> filters) {
        ArrayList<RecycleAdapter.Thumbnail> mThumbnails = new ArrayList<>();
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.effect_replace_background);
        if (bitmap.isRecycled()) {
            Log.d(TAG, "initAdapterData: bitmap is Recycled");
            return null;
        }
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bmp.getWidth(), bmp.getHeight(), true);
        RecycleAdapter.Thumbnail original = new RecycleAdapter.Thumbnail(scaledBitmap, getResources().getString(R.string.original), SET_ORIG_IMAGE);
        mThumbnails.add(original);

        for (int i = 0; i < filters.size(); i++) {
            ImageFilter filter = filters.get(i);
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, bmp.getWidth(), bmp.getHeight(), true);
            Bitmap apply = filter.apply(scaledBitmap, 1.0f, 0);
            RecycleAdapter.Thumbnail filterThumbnail = new RecycleAdapter.Thumbnail(apply, filter.getName(), i);
            mThumbnails.add(i + 1, filterThumbnail);
        }

        RecycleAdapter.Thumbnail addBackground = new RecycleAdapter.Thumbnail(null, getResources().getString(R.string.add), ADD_NEW_BACKGROUND);
        addBackground.setType(1);
        mThumbnails.add(addBackground);
        bmp.recycle();
        return mThumbnails;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        boolean mIsForground = false;
        switch (id) {
            case R.id.fix_subject:
            case R.id.fix_subject_text:
            case R.id.fix_subject_button:
                mIsForground = true;
                break;
            case R.id.fix_background:
            case R.id.fix_background_text:
            case R.id.fix_background_button:
                mIsForground = false;
                break;
        }
        for (int j = 0; j < mOperations.size(); j++) {
            mOperations.get(j).setIsforground(mIsForground);
            mOperations.get(j).setIsupdate(true);
        }
        showProgressDialog();
        boolean b = manager.updateBitmap(mBlendingView, mOperations, mIsForground);
        mBlendingView.clearStroke();
        if (!b) {
            hideDialog();
        }
    }

    @Override
    public void onStateChange(int state) {
        switchState(state);
        actionbarStateChange();
    }

    @Override
    public void enableCorrect(boolean enable) {
        if (mBlendingView.getState() == BlendingView.EDIT_BACKGROUND) {
            mHasChanged = true;
            if (!mSaveItem.isEnabled()) {
                mSaveItem.setEnabled(true);
            }
        } else {
            mFixSubjectButton.setEnabled(enable);
            mFixSubject.setClickable(enable);
            mFixSubjectText.setEnabled(enable);
            mFixBackgroundButton.setEnabled(enable);
            mFixBackground.setClickable(enable);
            mFixBackgroundText.setEnabled(enable);
            if (enable) {
                mFixSubjectText.setTextColor(Color.WHITE);
                mFixBackgroundText.setTextColor(Color.WHITE);
            } else {
                mFixSubjectText.setTextColor(Color.GRAY);
                mFixBackgroundText.setTextColor(Color.GRAY);
            }
            mUndoItem.setEnabled(true);
        }
    }

    @Override
    public void addOperate(List<TagPoint> points) {
        int size = points.size();
        if (size < 3) {
            Log.d(TAG, "addOperate: touch point si too few");
            return;
        }
        TagPoint firstpoint = points.get(0);
        TagPoint lastpoint = points.get(size - 1);
        if (!firstpoint.ismIsFirst()) {
            Log.d(TAG, "addOperate: update points first is not first, and last is not last");
            return;
        }
        if (!lastpoint.isLast()) {
            Log.d(TAG, "addOperate: update points last is not last");
            return;
        }
        points.remove(0);
        points.remove(points.size() - 1);
        int size1 = points.size();
        int movex[] = new int[size1];
        int movey[] = new int[size1];
        for (int j = 0; j < points.size(); j++) {
            movex[j] = points.get(j).x;
            movey[j] = points.get(j).y;
        }
        UpdateInfo updateInfo = new UpdateInfo();
        updateInfo.setMovex(movex);
        updateInfo.setMovey(movey);
        mOperations.add(updateInfo);
        points.clear();
    }

    @Override
    public boolean shouldrecycle(Bitmap bitmap) {
        return !bitmap.equals(manager.getSrcInScreenbitmap()) && !bitmap.equals(manager.getmDstInScreenBitmap());
    }

    /**
     * init BlendIngManager after BlendingView's onlayout(), make sure canvas rect has init.
     */
    @Override
    public void startInit() {
        if (manager == null) {
            manager = new BlendIngManager(mUri, this, this);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void initScaleFactor(float mMinScaleFactor, float mMaxScaleFactor) {
        Log.d(TAG, "initScaleFactor() called with: mMinScaleFactor = [" + mMinScaleFactor + "], mMaxScaleFactor = [" + mMaxScaleFactor + "]");
        int min = (int) (mMinScaleFactor * 100);
        int max = (int) (mMaxScaleFactor * 100);
        Log.d(TAG, "initScaleFactor: min" + min + " max:" + max);
        mRotate.setMax(max - min);
        mRotate.setProgress((int) (100 - (mMinScaleFactor * 100)), true);
    }


    @Override
    public void initPreviewBitmapFinish(final Bitmap mSrcBitmap) {
        mBlendingView.initBitmap(mSrcBitmap);
        mBlendingView.setUseMatirx(true);
        manager.setBitmapInScreenRect(mBlendingView.getSrcInScreenRectF());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mConfirmItem != null) {
                    mConfirmItem.setEnabled(false);
                }
                mBlendingView.setDisplayBitmap(mSrcBitmap);
                mProgressbar.setVisibility(View.VISIBLE);
            }
        });
    }

    public void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(ReplaceActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
        }
        mProgressDialog.setMessage(getContent());
        mProgressDialog.show();
    }

    public CharSequence getContent() {
        int state = mBlendingView.getState();
        switch (state) {
            case BlendingView.ADJUST_FOREGROUND_SUBJECT_RECT:
                return getResources().getText(R.string.extract_forground);
            case BlendingView.UPDATE_SUBJECT_OUTLINE:
                return getResources().getText(R.string.update_forground);
            case BlendingView.EDIT_BACKGROUND:
                return getResources().getText(R.string.blending_image);
        }
        return getResources().getText(R.string.loading);
    }

    @Override
    public void extractBitmapFinish(ExtractResult result) {
        Log.d(TAG, "extractBitmapFinish() called with: result = [" + result.toString() + "]");
        hideDialog();
        this.extractresult = result;
        int resultcode = result.getResultCode();
        if (resultcode == ImageBlending.SUCCESS && result.getBitmap() != null) {
            mBlendingView.setState(BlendingView.UPDATE_SUBJECT_OUTLINE);
            mBlendingView.setTargetBitmap(result.getBitmap());
            mBlendingView.setTargetBitmapPosition(result.getCoordinateX(), result.getCoordinateY());
            new GetAdapterData().start();
        } else {
            showToast(resultcode, true);
        }
    }

    @Override
    public void updateBitmapFinish(ExtractResult result) {
        Log.d(TAG, "updateBitmapFinish() called with: result = [" + result + "]");
        hideDialog();
        this.extractresult = result;
        int resultcode = result.getResultCode();
        if (resultcode == ImageBlending.SUCCESS) {
            mBlendingView.setTargetBitmap(result.getBitmap());
            mBlendingView.setTargetBitmapPosition(result.getCoordinateX(), result.getCoordinateY());
            mUndoItem.setEnabled(true);
        } else {
            showToast(resultcode, false);
        }
        mOperations.clear();
        mBlendingView.clearPoints();
    }

    private void showToast(int resultcode, boolean finish) {
        int resid = R.string.extract_failed;
        if (resultcode == ImageBlending.NOT_FOREGROUND_OBJECT) {
            resid = R.string.extract_failed_notforground;
        } else if (resultcode == ImageBlending.MEMORY_ALLOCATION_FAILED_OR_OTHER_REASON) {
            resid = R.string.extract_failed_nomemory;
        } else if (resultcode == ImageBlending.TIME_OUT) {
            resid = R.string.extract_timeout;
        }

        Toast.makeText(ReplaceActivity.this, resid, Toast.LENGTH_SHORT).show();
        if (finish) {
            finish();
        }
    }

    @Override
    public void initYuvBitmapFinish(Bitmap bitmap) {
        if (isDestroyed()) {
            return;
        }
        if (mConfirmItem != null) {
            mConfirmItem.setEnabled(true);
        }
        if (bitmap == null) {
            Toast.makeText(this, R.string.refocus_init_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        mBlendingView.setUseMatirx(false);
        mBlendingView.setDisplayBitmap(bitmap);
        manager.initBlending();
    }

    @Override
    public void blendingFinish() {
        if (isDestroyed()) {
            return;
        }
        mHasChanged = false;
        hideDialog();
        mSaveItem.setEnabled(false);
        Toast.makeText(ReplaceActivity.this, R.string.blending_success, Toast.LENGTH_SHORT).show();
        if (mQuit) {
            finish();
        }
    }

    @Override
    public void saveMaskResult(int obj) {
        if (obj == -1) {
            mUndoItem.setEnabled(false);
        } else {
            mUndoItem.setEnabled(true);
        }
    }

    @Override
    public void initDepthFinish() {
        if (isDestroyed()) {
            return;
        }
        mBlendingView.setState(BlendingView.SELECT_FOREGROUND_SUBJECT);
        mProgressbar.setVisibility(View.GONE);
    }

    class GetAdapterData extends Thread {

        @Override
        public void run() {
            final ArrayList<RecycleAdapter.Thumbnail> thumbnails = initAdapterData(manager.getSrcBitmap(), getFilter());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRecycleAdapter.setData(thumbnails);
                }
            });
        }

        public List<ImageFilter> getFilter() {
            if (mFilters.size() > 0) {
                return mFilters;
            }
            int[] drawid = {R.drawable.filtershow_fx_0005_punch, R.drawable.filtershow_fx_0000_vintage, R.drawable.filtershow_fx_0004_bw_contrast, R.drawable.filtershow_fx_0002_bleach, R.drawable.filtershow_fx_0001_instant, R.drawable.filtershow_fx_0007_washout, R.drawable.filtershow_fx_0003_blue_crush, R.drawable.filtershow_fx_0008_washout_color, R.drawable.filtershow_fx_0006_x_process};

            int[] fxNameid = {R.string.ffx_punch, R.string.ffx_vintage, R.string.ffx_bw_contrast, R.string.ffx_bleach, R.string.ffx_instant, R.string.ffx_washout, R.string.ffx_blue_crush, R.string.ffx_washout_color, R.string.ffx_x_process};

            for (int i = 0; i < drawid.length; i++) {
                ImageFilterFx ImageFilterFx = new ImageFilterFx();
                FilterRepresentation defaultRepresentation2 = new FilterFxRepresentation(GalleryAppImpl.getApplication().getResources().getString(fxNameid[i]), drawid[i], fxNameid[i]);
                ImageFilterFx.setName(GalleryAppImpl.getApplication().getResources().getString(fxNameid[i]));
                ImageFilterFx.setResources(GalleryAppImpl.getApplication().getResources());
                FilterEnvironment f = new FilterEnvironment();
                ImageFilterFx.useRepresentation(defaultRepresentation2);
                ImageFilterFx.setEnvironment(f);
                mFilters.add(ImageFilterFx);
            }
            return mFilters;
        }
    }
}


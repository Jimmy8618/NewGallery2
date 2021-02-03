package com.sprd.gallery3d.refocusimage;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.refocusimage.RefocusImageView.RefocusViewCallback;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusData;
import com.sprd.refocus.RefocusUtils;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class RefocusEditActivity extends Activity implements Handler.Callback, RefocusViewCallback,
        SdCardPermissionAccessor {
    private static final String TAG = "RefocusEditActivity";

    private int mType;
    public static final String SRC_PATH = "refocus_path";
    public static final String SRC_WIDTH = "refocus_width";
    public static final String SRC_HEIGHT = "refocus_height";

    private Handler mUiHandler, mThreadHandler;
    private HandlerThread mHandlerThread = null;
    private MenuItem mSaveItem;
    private MenuItem mCompareItem;
    private View mCompareView;
    private Uri mUri;
    private String mFilePath;
    private String mfNum;
    private String[] fNumList = new String[]{"F0.95", "F1.4", "F2.0", "F2.8", "F4.0", "F5.6", "F8.0", "F11.0", "F13.0", "F16.0"};
    private static final String REF_START = "F8.0";
    private static final String REF_END = "F2.0";
    private RefocusImageView mRefocusView;
    private ProgressBar mProgressBar;
    private static final int MSG_DECODE_SRC = 1 << 0;
    private static final int MSG_DISPLAY_SRC = 1 << 1;
    private static final int MSG_INIT_REFOCUS = 1 << 2;
    private static final int MSG_INIT_UI = 1 << 3;
    private static final int MSG_INIT_FAIL = 1 << 4;
    private static final int MSG_HIDE_CIRCLE = 1 << 5;
    private static final int DELAY_HIDE_TIME = 1000;
    private ContentResolver mResolver;
    private int mSrcWidth, mSrcHeight;
    private int mScale;
    private SeekBar mSeekBar;
    private TextView mStartValue, mEndValue, mCurValue;
    private byte[] mEditYuv;
    private int mYuvW, mYuvH;
    private int mRotate;
    private Point mYuvP = new Point();
    private CommonRefocus mComRefocus;
    private boolean mPaused = true;

    public static boolean DEBUG = false;
    private int mOrgProgress, mCurProgress, mOldProgress;
    private RefocusTask mRefocusTask = null;
    private volatile boolean mInitLib = false;
    private Object mObject = new Object();
    private SdCardPermissionListener mSdCardPermissionListener;
    private boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
    private boolean mSecureCamera;
    private boolean mDoRefocus = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate.");
        super.onCreate(savedInstanceState);
        initWidgets();
        initActionBar();
        initBase();
        String filePath = getIntent().getStringExtra(SRC_PATH);
        if (filePath == null) {
            return;
        }
        if (!GalleryStorageUtil.isInInternalStorage(filePath)
                && !SdCardPermission.hasStoragePermission(filePath)) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    processIntent();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(RefocusEditActivity.this,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            });
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            SdCardPermission.requestSdcardPermission(this, storagePaths, this, sdCardPermissionListener);
        } else {
            processIntent();
        }
        mSecureCamera = getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume.");
        mPaused = false;
        super.onResume();
        if (!mSecureCamera) {
            setShowWhenLocked(false);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause.");
        mPaused = true;
        if (mRefocusTask != null && !mRefocusTask.isCancelled()
                && mRefocusTask.getStatus() == AsyncTask.Status.RUNNING) {
            Log.d(TAG, "onPause, RefocusTask running,and cancel mRefocusTask!");
            mRefocusTask.cancel(true);
            mRefocusTask = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy.");
        super.onDestroy();
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        if (mThreadHandler != null) {
            mThreadHandler.removeCallbacksAndMessages(null);
        }
        if (mUiHandler != null) {
            mUiHandler.removeCallbacksAndMessages(null);
        }
        synchronized (mObject) {
            if (mComRefocus != null && mInitLib) {
                if(!mDoRefocus) {
                    mComRefocus.unInitLib();
                    mInitLib = false;
                }
            }
        }
        mEditYuv = null;
        System.gc();
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
                break;
        }
    }

    private void initBase() {
        DEBUG = RefocusUtils.isRefocusTestMode();
        mHandlerThread = new HandlerThread("RefocusEditActivity-Handler");
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper(), this); // this is "Handler.Callback"
        mUiHandler = new MainHandler(this);
    }

    private static class MainHandler extends Handler {
        private final WeakReference<RefocusEditActivity> mRefocusWeak;

        public MainHandler(RefocusEditActivity refocusEditActivity) {
            mRefocusWeak = new WeakReference<>(refocusEditActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            RefocusEditActivity refocusEditActivity = mRefocusWeak.get();
            if (refocusEditActivity != null) {
                if (refocusEditActivity.isFinishing()) {
                    return;
                }
                refocusEditActivity.handleMainMsg(msg);
            }
        }
    }

    /**
     * UiHandler's handleMessage. excute in main thread
     */
    private void handleMainMsg(Message msg) {
        switch (msg.what) {
            case MSG_DISPLAY_SRC:
                Log.d(TAG, " setSrcBitmap sucess");
                mRefocusView.setSrcBitmap((Bitmap) msg.obj);
                break;
            case MSG_INIT_UI:
                Log.d(TAG, "init finished, update ui.");
                addCompareListener();
                mProgressBar.setVisibility(View.GONE);
                mSeekBar.setEnabled(true);
                switch (mType) {
                    case RefocusData.TYPE_REFOCUS:
                        // refocus F8-F2  progress is 0-255
                        mSeekBar.setMax(255);
                        mSeekBar.setProgress(mOrgProgress);
                        mStartValue.setText(REF_START);
                        mEndValue.setText(REF_END);
                        String curValue = RefocusUtils.getRefCurValue(mOrgProgress);
                        mCurValue.setText(curValue);
                        break;
                    case RefocusData.TYPE_BOKEH_ARC:
                    case RefocusData.TYPE_BOKEH_SPRD:
                    case RefocusData.TYPE_BOKEH_SBS:
                    case RefocusData.TYPE_BLUR_GAUSS:
                    case RefocusData.TYPE_BLUR_FACE:
                    case RefocusData.TYPE_BLUR_TF:
                        // refocus F0.95-F16.0  progress is 0-9
                        mSeekBar.setMax(9);
                        mSeekBar.setProgress(mOrgProgress);
                        mStartValue.setText(fNumList[0]);
                        mEndValue.setText(fNumList[9]);
                        mCurValue.setText(fNumList[mOrgProgress]);
                        break;
                    default:
                        break;
                }
                Point srcPoint = RefocusUtils.yuvToSrcPoint(mYuvP, mSrcWidth, mSrcHeight, mRotate);
                mRefocusView.initCircle(srcPoint, RefocusImageView.CIRCLE_DEF);
                mRefocusView.setCanTouch(true);
                mUiHandler.sendEmptyMessageDelayed(MSG_HIDE_CIRCLE, DELAY_HIDE_TIME);
                break;
            case MSG_INIT_FAIL:
                mProgressBar.setVisibility(View.GONE);
                mSeekBar.setEnabled(false);
                mRefocusView.setCanTouch(false);
                Toast.makeText(this, R.string.refocus_init_fail, Toast.LENGTH_SHORT).show();
                break;
            case MSG_HIDE_CIRCLE:
                mRefocusView.hideCircle();
            default:
                break;
        }
    }

    /**
     * ThreadHandler's handleMessage, excute in child thread
     */
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_DECODE_SRC:
                decodeBitmap();
                break;
            case MSG_INIT_REFOCUS:
                boolean success = initRefocusData();
                if (!success) {
                    mUiHandler.sendEmptyMessage(MSG_INIT_FAIL);
                    break;
                }
                if (RefocusEditActivity.this.isFinishing()) {
                    return true;
                }
                switch (mType) {
                    case RefocusData.TYPE_BOKEH_ARC:
                    case RefocusData.TYPE_BOKEH_SPRD:
                    case RefocusData.TYPE_BOKEH_SBS:
                    case RefocusData.TYPE_BLUR_FACE:
                    case RefocusData.TYPE_BLUR_GAUSS:
                        synchronized (mObject) {
                            mComRefocus.initLib();
                            mInitLib = true;
                        }
                        break;
                    case RefocusData.TYPE_REFOCUS:
                    case RefocusData.TYPE_BLUR_TF:
                        // need calculate depth
                        synchronized (mObject) {
                            mComRefocus.initLib();
                            mComRefocus.calDepth();
                            mInitLib = true;
                        }
                        break;
                    default:
                        break;
                }
                if (DEBUG) {
                    mComRefocus.dumpData(mFilePath);
                }
                mUiHandler.sendEmptyMessage(MSG_INIT_UI);
                break;
            default:
                break;

        }
        return true;
    }

    private void decodeBitmap() {
        Log.d(TAG, "decodeBitmap start.");
        Options options = new Options();
        options.inSampleSize = getScaleRatio();
        options.inPreferredConfig = isLowRam ? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
        Bitmap srcBitmap = RefocusUtils.loadBitmap(RefocusEditActivity.this, mUri, options);
        if (srcBitmap == null) {
            return;
        }
        Log.d(TAG, "decodeBitmap end. scale bitmap w " + srcBitmap.getWidth() + " h " + srcBitmap.getHeight());
        Message msg = mUiHandler.obtainMessage();
        msg.what = MSG_DISPLAY_SRC;
        msg.obj = srcBitmap;
        mUiHandler.sendMessage(msg);
    }

    private boolean initRefocusData() {
        Log.d(TAG, "initRefocusData start");
        InputStream inStream = null;
        try {
            if (GalleryStorageUtil.isInInternalStorage(mFilePath)) {
                inStream = mResolver.openInputStream(mUri);
            } else {
                inStream = SdCardPermission.createExternalInputStream(mFilePath);
            }
            byte[] content = RefocusUtils.streamToByte(inStream);
            if (content == null) {
                Log.i(TAG, "read stream by uri fail!");
                return false;
            }
            mComRefocus = CommonRefocus.getInstance(content);
            if (mComRefocus == null) {
                return false;
            }
            mType = mComRefocus.getType();
            RefocusData data = mComRefocus.getRefocusData();
            if (data == null || data.getMainYuv() == null) {
                return false;
            }
            mEditYuv = data.getMainYuv();
            mRotate = data.getRotation();
            mYuvW = data.getYuvWidth();
            mYuvH = data.getYuvHeight();
            mYuvP = new Point(data.getSel_x(), data.getSel_y());
            mOrgProgress = mComRefocus.getProgress();
            mCurProgress = mOrgProgress;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } catch (Error e) {
            e.printStackTrace();
            return false;
        } finally {
            Utils.closeSilently(inStream);
        }
        Log.d(TAG, "initRefocusData end");
        return true;
    }

    private void initWidgets() {
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_refocus_edit);
        mRefocusView = findViewById(R.id.refocus_view);
        mProgressBar = findViewById(R.id.progress_bar);
        mSeekBar = findViewById(R.id.refocus_seekbar);
        mStartValue = findViewById(R.id.start_value);
        mEndValue = findViewById(R.id.end_value);
        mCurValue = findViewById(R.id.current_value);
        mRefocusView.setCanTouch(false);
        mRefocusView.setRefocusViewCallback(this);
        mProgressBar.setVisibility(View.VISIBLE);
        mSeekBar.setOnSeekBarChangeListener(new ChangeListenerImp());
        mSeekBar.setEnabled(false);
    }

    private void processIntent() {
        Intent intent = getIntent();
        mResolver = this.getContentResolver();
        mUri = intent.getData();
        if (mUri == null) {
            return;
        }
        mFilePath = intent.getStringExtra(SRC_PATH);
        Log.d(TAG, "processIntent mFilePath = " + mFilePath);
        int width = intent.getIntExtra(SRC_WIDTH, 0);
        int height = intent.getIntExtra(SRC_HEIGHT, 0);
        Log.d(TAG, "processIntent w = " + width + " h =" + height);
        if (width == 0 || height == 0) {
            InputStream is = null;
            try {
                Options options = new Options();
                options.inJustDecodeBounds = true;
                is = mResolver.openInputStream(mUri);
                BitmapFactory.decodeStream(is, null, options);
                width = options.outWidth;
                height = options.outHeight;
                Log.d(TAG, "processIntent decode w = " + width + "  h =" + height);
            } catch (Exception e) {
                Log.e(TAG, "processIntent Exception ", e);
            } finally {
                Utils.closeSilently(is);
            }
        }
        mSrcWidth = width;
        mSrcHeight = height;
        mRefocusView.setSrcRectF(mSrcWidth, mSrcHeight);
        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        Log.d(TAG, "Screen w: " + screenWidth + ", Screen h: " + screenHeight);
        mRefocusView.setScreenRectF(screenWidth, screenHeight);
        float wScale = (float) mSrcWidth / (float) screenWidth;
        float hScale = (float) mSrcHeight / (float) screenHeight;
        float scale = Math.max(wScale, hScale);
        mScale = (int) ((scale < 1.0f) ? 1 : scale);
        Log.d(TAG, "processIntent scale = " + scale + ", mScale = " + mScale);
        showSrcBitmap();
        initRefocus();
    }

    private void showSrcBitmap() {
        mThreadHandler.sendEmptyMessage(MSG_DECODE_SRC);
    }

    private void initRefocus() {
        mThreadHandler.sendEmptyMessage(MSG_INIT_REFOCUS);
    }

    private int getScaleRatio() {
        return mScale;
    }

    private void initActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.photo_toolbar_background));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_undo_redo_save, menu);
        mCompareItem = menu.findItem(R.id.refocus_edit_compare);
        mCompareItem.setIcon(R.drawable.ic_refocus_compare);
        mSaveItem = menu.findItem(R.id.refocus_edit_save);
        mSaveItem.setIcon(R.drawable.ic_refocus_storage);
        mCompareItem.setEnabled(false);
        mSaveItem.setEnabled(false);
        return true;
    }

    private void addCompareListener() {
        mCompareView = findCompareView();
        if (mCompareView == null) {
            return;
        }
        mCompareView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        doReset();
                        break;
                    case MotionEvent.ACTION_UP:
                        doRedo();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    private View findCompareView() {
        ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        if (decorView == null) {
            return null;
        }
        View view = decorView.findViewById(R.id.refocus_edit_compare);
        Log.d(TAG, "compareView = " + view);
        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.refocus_edit_save:
                requestSave();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mSaveItem != null && mSaveItem.isEnabled()) {
            showSaveDialog();
        } else {
            super.onBackPressed();
        }
    }

    private void doSave() {
        SaveBitmapTask saveBitmapTask = new SaveBitmapTask();
        saveBitmapTask.execute(mEditYuv);
    }

    private void doReset() {
        mRefocusView.reset();
        mOldProgress = mCurProgress;
        mSeekBar.setProgress(mOrgProgress);
    }

    private void doRedo() {
        mRefocusView.redo();
        mSeekBar.setProgress(mOldProgress);
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.refocus_confirm_save);
        builder.setNegativeButton(R.string.refocus_quit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setPositiveButton(R.string.save_and_exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                requestSave();
            }
        });
        builder.show();
    }

    private class SaveBitmapTask extends AsyncTask<byte[], Void, Uri> {
        @Override
        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Uri doInBackground(byte[]... params) {
            String filePath = getIntent().getStringExtra(SRC_PATH);
            // UNISOC added for bug 1203904, avoid gallery crash.
            if (TextUtils.isEmpty(mfNum)) {
                mfNum = fNumList[mCurProgress];
            }
            return GalleryUtils.saveJpegByYuv(RefocusEditActivity.this,
                    params[0], mYuvW, mYuvH, mRotate, mUri, filePath, mfNum.substring(1));
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (uri == null) {
                Toast.makeText(RefocusEditActivity.this, R.string.refocus_save_fail, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(RefocusEditActivity.this, R.string.refocus_save_success, Toast.LENGTH_SHORT).show();
            }
            mProgressBar.setVisibility(View.GONE);
            if (mSecureCamera) {
                setResult(RESULT_OK, new Intent().setData(uri));
            }
            finish();
        }
    }

    private class ChangeListenerImp implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mSeekBar.setProgress(progress);
            mCurProgress = progress;
            switch (mType) {
                case RefocusData.TYPE_BOKEH_ARC:
                case RefocusData.TYPE_BOKEH_SPRD:
                case RefocusData.TYPE_BOKEH_SBS:
                case RefocusData.TYPE_BLUR_TF:
                case RefocusData.TYPE_BLUR_FACE:
                case RefocusData.TYPE_BLUR_GAUSS:
                    // [F0.95 - F16], seekBar progress [0,9]
                    mCurValue.setText(fNumList[progress]);
                    mfNum = fNumList[progress];
                    break;
                case RefocusData.TYPE_REFOCUS:
                    // [F8 - F2], seekBar progress [0,255]
                    String curValue = RefocusUtils.getRefCurValue(progress);
                    mCurValue.setText(curValue);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            //do nothing!
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "SeekBar on Stop Touch ");
            doRefocus();
            /* Bug 1230905 */
            showRefocusingDialog();
            /* @ */
        }
    }

    @Override
    public void onUpdatePoint(Point srcPoint) {
        mYuvP = RefocusUtils.srcToYuvPoint(srcPoint, mSrcWidth, mSrcHeight, mRotate);
        Log.d(TAG, "onUpdatePoint srcPoint = " + srcPoint + ", mYuvP" + mYuvP);
    }

    @Override
    public void touchValid() {
        if (mUiHandler.hasMessages(MSG_HIDE_CIRCLE)) {
            mUiHandler.removeMessages(MSG_HIDE_CIRCLE);
        }
    }

    @Override
    public void doRefocus() {
        Log.d(TAG, "doRefocus.");
        mCompareItem.setEnabled(false);
        if (RefocusEditActivity.this.isFinishing()) {
            return;
        }
        if (mRefocusTask != null && !mRefocusTask.isCancelled()
                && mRefocusTask.getStatus() == AsyncTask.Status.RUNNING) {
            Log.d(TAG, "AsyncTask running ,and cancel AsyncTask! ");
            mRefocusTask.cancel(true);
            mRefocusTask = null;
        }
        mRefocusTask = new RefocusTask();
        mRefocusTask.execute();
    }

    private class RefocusTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... params) {
            int blurIntensity = mComRefocus.calBlurIntensity(mCurProgress);
            Log.d(TAG, "RefocusTask do refocus start. blurIntensity = " + blurIntensity + ", mYuvP" + mYuvP);
            if (isCancelled() || mEditYuv == null) return null;
            synchronized (mObject) {
                mDoRefocus = true;
            }
            mEditYuv = mComRefocus.doRefocus(mEditYuv, mYuvP, blurIntensity);
            synchronized (mObject) {
                mDoRefocus = false;
                if(RefocusEditActivity.this.isDestroyed() && mInitLib){
                    mComRefocus.unInitLib();
                    mInitLib = false;
                    return null;
                }
            }

            if (DEBUG) {
                String fileName = mYuvP.x + "x" + mYuvP.y + "_" + blurIntensity + ".yuv";
                RefocusUtils.dumpEditYuv(mEditYuv, mFilePath, fileName);
            }
            Log.d(TAG, "RefocusTask do refocus end.");
            Options options = new Options();
            options.inSampleSize = getScaleRatio();
            options.inPreferredConfig = isLowRam ? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
            Bitmap editBitmap;
            if (GalleryUtils.isJpegHwCodecAvailable()) {
                editBitmap = RefocusUtils.hwYuv2bitmap(mEditYuv, mYuvW, mYuvH, mRotate, options);
                Log.d(TAG, "refocus hwYuv2bitmap finish.");
            } else {
                editBitmap = RefocusUtils.yuv2bitmap(mEditYuv, mYuvW, mYuvH, mRotate, options);
                Log.d(TAG, "refocus yuv2bitmap finish.");
            }
            return editBitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                Log.d(TAG, "do refocus fail.");
                return;
            }
            /* Bug 1230905 */
            if(mRefocusingDialog != null && !isFinishing() && mRefocusingDialog.isShowing()){
                mRefocusingDialog.dismiss();
            }
            /* @ */
            mRefocusView.setBitmap(bitmap);
            mSaveItem.setEnabled(true);
            mCompareItem.setEnabled(true);
        }
    }
    /* Bug 1230905 */
    private ProgressDialog mRefocusingDialog;
    private void showRefocusingDialog(){
        if(mRefocusingDialog == null){
            mRefocusingDialog = new ProgressDialog(this);
        }
        mRefocusingDialog.setMessage(getResources().getString(R.string.processing_please_wait));
        mRefocusingDialog.setCancelable(false);
        mRefocusingDialog.show();
    }
    /* @ */
    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }

    private void requestSave() {
        String filePath = getIntent().getStringExtra(SRC_PATH);
        if (!GalleryStorageUtil.isInInternalStorage(filePath)
                && !SdCardPermission.hasStoragePermission(filePath)) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    doSave();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(RefocusEditActivity.this, null);
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            SdCardPermission.requestSdcardPermission(this, storagePaths, this, sdCardPermissionListener);
        } else {
            doSave();
        }
    }
}



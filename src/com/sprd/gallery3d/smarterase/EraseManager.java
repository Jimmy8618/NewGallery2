package com.sprd.gallery3d.smarterase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import com.android.gallery3d.app.GalleryStorageUtil;

import java.io.File;

public class EraseManager {

    private static final String TAG = "EraseManager";

    private static final int MESSAGE_LOAD_BITMAP = 1;
    private static final int MESSAGE_GENERATE_BITMAP = 2;
    private static final int MESSAGE_SAVE_BITMAP = 3;

    private Context mContext;
    private UIControl mControl;
    private Uri mUri;
    private Handler mMainUIHandler;
    private BackgroundHandler mBackgroundHandler;
    private HandlerThread mThread;
    private Bitmap mOriginalBitmap;
    private Size mOriginalSize;

    private SmartEraseNative mSmartEraseNative;

    public enum State {
        STATE_INIT,
        STATE_PAINT,
        STATE_ERASE_FINISH,
    }

    public State mState = State.STATE_INIT;
    private Bitmap mErasedBitmap;
    private Bitmap mInScreenErasedBitmap;
    private int mScale;

    class BackgroundHandler extends Handler {
        BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: msg.what=" + msg.what);
            int what = msg.what;
            try {
                switch (what) {
                    case MESSAGE_LOAD_BITMAP:
                        loadBitmap();
                        break;
                    case MESSAGE_GENERATE_BITMAP:
                        generateInternal();
                        break;
                    case MESSAGE_SAVE_BITMAP:
                        saveBitmap(mErasedBitmap);
                        mMainUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "run: save complete!");
                                mControl.dismissProgressDialog();
                                mControl.quit();
                            }
                        });
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public EraseManager(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
        Log.d(TAG, "EraseManager: mUri=" + mUri);

        mMainUIHandler = new Handler(mContext.getMainLooper());
        mThread = new HandlerThread("EraseHandlerThread");
        mThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "uncaughtException: " + t.getName() + "has a " + e.getMessage());
                e.printStackTrace();
            }
        });
        mThread.start();
        mBackgroundHandler = new BackgroundHandler(mThread.getLooper());
        mSmartEraseNative = new SmartEraseNative();
    }

    public void release() {
        recycleBitmap(mErasedBitmap);
        recycleBitmap(mInScreenErasedBitmap);
        mThread.quitSafely();
    }

    public void startLoadBitmap() {
        mBackgroundHandler.sendEmptyMessage(MESSAGE_LOAD_BITMAP);
    }

    private void loadBitmap() {
        mState = State.STATE_INIT;
        mOriginalBitmap = SmartEraseUtils.getOriginalBitmap(mContext.getContentResolver(), mUri);
        mOriginalSize = SmartEraseUtils.getBitmapSize(mContext.getContentResolver(), mUri);
        RectF displayRectF = mControl.getDisplayViewRectF();
        mScale = SmartEraseUtils.getScale(displayRectF.width(), displayRectF.height(), mOriginalSize.getWidth(), mOriginalSize.getHeight());
        Log.d(TAG, "loadBitmap: mScale = " + mScale);
        Bitmap screenBitmap = SmartEraseUtils.getScaledBitmap(mContext.getContentResolver(), mUri, mScale);
        Log.d(TAG, "loadBitmap: real bitmap size: " + mOriginalSize + ", displayRectF=" + displayRectF + ", screen bitmap size: " + screenBitmap.getWidth() + " x " + screenBitmap.getHeight());
        mControl.setDisplayBitmap(screenBitmap);
    }

    public Size getOriginalBitmapSize() {
        return mOriginalSize;
    }

    private void generateInternal() {
        Log.d(TAG, "generateInternal: B");
        Bitmap maskedBitmap = mOriginalBitmap;
        //testSaveMaskedBitmap(maskedBitmap);

        Bitmap maskBitmap = mControl.getMaskBitmap();
        //testSaveMaskBitmap(maskBitmap);

        int width = maskedBitmap.getWidth();
        int height = maskedBitmap.getHeight();

        // TODO call jni
        mSmartEraseNative.init();
        byte[] result = mSmartEraseNative.process(SmartEraseUtils.toByteArray(maskedBitmap), SmartEraseUtils.toByteArray(maskBitmap),
                width, height);
        Log.d(TAG, "generateInternal: result.length=" + result.length);
        mSmartEraseNative.deinit();
        mErasedBitmap = SmartEraseUtils.toBitmap(result, width, height);
        Log.d(TAG, "generateInternal: save result bitmap, size: " + mErasedBitmap.getWidth() + " x " + mErasedBitmap.getHeight());
        //saveBitmap(resultBitmap);
        mState = State.STATE_ERASE_FINISH;
        Log.d(TAG, "generateInternal: erased bitmap size: " + mErasedBitmap.getWidth() + " x " + mErasedBitmap.getHeight());

        recycleBitmap(maskedBitmap);
        recycleBitmap(maskBitmap);

        int dstWidth = mErasedBitmap.getWidth() / mScale;
        int dstHeight = mErasedBitmap.getHeight() / mScale;
        mInScreenErasedBitmap = Bitmap.createScaledBitmap(mErasedBitmap, dstWidth, dstHeight, false);
        Log.d(TAG, "generateInternal: in screen erased bitmap size: " + mInScreenErasedBitmap.getWidth() + " x " + mInScreenErasedBitmap.getHeight());

        mMainUIHandler.post(new Runnable() {
            @Override
            public void run() {
                mControl.showErasedBitmap();
                mControl.dismissProgressDialog();
            }
        });

        Log.d(TAG, "generateInternal: E");
    }

    public void save() {
        mControl.showSaveProgressDialog();
        mBackgroundHandler.sendEmptyMessage(MESSAGE_SAVE_BITMAP);

    }

    private void saveBitmap(Bitmap bitmap) {
        File newFile = SmartEraseUtils.getNewFile(mContext, mUri);
        Log.d(TAG, "saveBitmap: " + newFile);
        SmartEraseUtils.saveBitmap(bitmap, newFile.getAbsolutePath());
        String[] scanArray = new String[]{newFile.getAbsolutePath()};
        // UNISOC added for bug 1177042, scan files in the phone's internal storage
        if (GalleryStorageUtil.isInInternalStorage(scanArray[0])) {
            MediaScannerConnection.scanFile(mContext, scanArray, null, null);
        }
    }

    //BEGIN: for test
    private void testSaveMaskedBitmap(Bitmap bitmap) {
        File saveDirectory = SmartEraseUtils.getFinalSaveDirectory(mContext, mUri);
        File new_file = new File(saveDirectory, "masked_bitmap.jpg");
        if (new_file.exists()) {
            new_file.delete();
        }
        Log.d(TAG, "saveMaskedBitmap: " + new_file + ", size: " + bitmap.getWidth() + " X " + bitmap.getHeight());
        SmartEraseUtils.saveBitmap(bitmap, new_file.getAbsolutePath());
        String[] scanArray = new String[]{new_file.getAbsolutePath()};
        MediaScannerConnection.scanFile(mContext, scanArray, null, null);
    }

    private void testSaveMaskBitmap(Bitmap bitmap) {
        File saveDirectory = SmartEraseUtils.getFinalSaveDirectory(mContext, mUri);
        File new_file = new File(saveDirectory, "mask_bitmap.jpg");
        if (new_file.exists()) {
            new_file.delete();
        }
        Log.d(TAG, "saveMaskBitmap: " + new_file + ", size: " + bitmap.getWidth() + " X " + bitmap.getHeight());
        SmartEraseUtils.saveBitmap(bitmap, new_file.getAbsolutePath());
        String[] scanArray = new String[]{new_file.getAbsolutePath()};
        MediaScannerConnection.scanFile(mContext, scanArray, null, null);
    }
    //END: for test

    public void generate() {
        mControl.showProgressDialog();
        mBackgroundHandler.sendEmptyMessage(MESSAGE_GENERATE_BITMAP);
    }

    public void setUIControl(UIControl control) {
        mControl = control;
    }

    public Bitmap getOriginalBitmap() {
        return mOriginalBitmap;
    }

    public Bitmap getInScreenErasedBitmap() {
        return mInScreenErasedBitmap;
    }

    public void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}

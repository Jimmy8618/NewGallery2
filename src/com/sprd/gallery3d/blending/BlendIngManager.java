package com.sprd.gallery3d.blending;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.blending.Blending;
import com.sprd.blending.ImageBlending;
import com.sprd.blending.bean.Depth;
import com.sprd.blending.bean.ExtractResult;
import com.sprd.blending.bean.Mask;
import com.sprd.blending.bean.TagPoint;
import com.sprd.blending.bean.UpdateInfo;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.blending.bean.BlendingRequest;
import com.sprd.gallery3d.blending.view.BlendingView;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusData;
import com.sprd.refocus.RefocusUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Stack;

/**
 * the Operation sequence:
 * 1. init : MSG_INIT_PREVIEW_BITMAP -> MSG_INIT_YUV_BITMAP -> initBlending() -> MSG_INIT_DEPTH
 * 2. extract bitmap: create mask(MSG_GET_MASK) -> get forground bitmap
 * 3. update bitmap: update mask(MSG_UPDATE_MASK) -> get forground bitmap(MSG_GET_TARGET_BITMAP)
 * 4. BLENDING_BITMAP
 */
public final class BlendIngManager {

    private static final String TAG = BlendIngManager.class.getSimpleName();
    private static final String IS_DEBUG_OPEN = "imageblending.debug";
    public static boolean DEBUG = false;

    private static String ROOT_PATH = "sdcard/imageblending";
    private static String EXTRACT_PATH = ROOT_PATH + "/extract";
    private static String UPDATE_PATH = ROOT_PATH + "/update";
    public int i = 0;

    private static final int MSG_INIT_PREVIEW_BITMAP = 106;
    private static final int MSG_INIT_PREVIEW_BITMAP_FINISH = 107;

    private static final int MSG_INIT_YUV_BITMAP = 108;
    private static final int MSG_INIT_YUV_BITMAP_FINISH = 109;

    private static final int MSG_INIT_DEPTH = 110;
    private static final int MSG_INIT_DEPTH_FINISH = 111;

    private static final int MSG_GET_MASK = 112;
    private static final int MSG_GET_MASK_FINISH = 113;

    private static final int MSG_UPDATE_MASK = 114;
    private static final int MSG_UPDATE_MASK_FINISH = 115;

    private static final int BLENDING_BITMAP = 116;
    private static final int BLENDING_BITMAP_FINISH = 117;

    private static final int UNDO_MASK = 119;
    private static final int UNDO_MASK_FINISH = 120;

    private static final int MSG_GET_TARGET_BITMAP = 121;
    private static final int MSG_GET_TARGET_BITMAP_FINISH = 122;


    private static final int SAVE_MASK = 118;

    private Bitmap mSrcBitmap;
    private Bitmap mSrcInScreenbitmap;
    private Bitmap mScaleDownBitmap;
    private ExtractCallBack mCallBack;
    private Mask mMask;
    private BlendingHandler mBlendingHandler;
    private Handler mMainHandler;
    private Context mContext;
    private Uri mUri;
    private RefocusData mRefocusData;
    private Blending mBlending;
    private RectF mSrcInScreenRect;
    private Depth mDepth;
    private HandlerThread imageBlending;
    private CommonRefocus mCommonRefocus;
    private Bitmap mDstBitmap;

    private Stack<byte[]> mUpdates = new Stack<>();

    private Bitmap mDstInScreenBitmap;


    public Bitmap getmDstInScreenBitmap() {
        return mDstInScreenBitmap;
    }

    void initBlending() {
        mBlending = ImageBlending.getInstances(mCommonRefocus);
        mBlendingHandler.sendEmptyMessage(MSG_INIT_DEPTH);
    }

    private int computeSize(int width, int height) {
        float pixels = width * height;
        float v = pixels / 1000000;
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.00");
        String format = df.format(v);
        float v1 = Float.parseFloat(format);
        return (int) (v1 / 0.04);
    }

    void setmDstBitmap(Bitmap mDstBitmap) {
        this.mDstBitmap = mDstBitmap;
        mDstInScreenBitmap = Bitmap.createScaledBitmap(mDstBitmap, mSrcInScreenbitmap.getWidth(), mSrcInScreenbitmap.getHeight(), false);
    }

    Bitmap getmDstBitmap() {
        return mDstBitmap;
    }

    void unDo(BlendingView view) {
        Log.d(TAG, "unDo: ");
        int size = mUpdates.size();
        if (size <= 0) {
            Log.d(TAG, "onOptionsItemSelected: no update");
            return;
        }

        Log.d(TAG, "onOptionsItemSelected: mUpdates.size()" + size);
        byte[] mask = mUpdates.pop();

        mMask.setMask(mask);
        mMask.setmMaskWidth(mDepth.getmDepthWidth());
        mMask.setmMaskHeight(mDepth.getmDepthHeight());
        RectF box = view.getBox();
        mBlendingHandler.obtainMessage(UNDO_MASK, box).sendToTarget();
    }

    static class BlendingHandler extends Handler {
        BlendIngManager blendIngManager;

        BlendingHandler(Looper looper, BlendIngManager blendIngManager) {
            super(looper);
            WeakReference<BlendIngManager> blendIngManagerWeakReference = new WeakReference<>(blendIngManager);
            this.blendIngManager = blendIngManagerWeakReference.get();
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "BlendingHandler->handleMessage: >>>" + msg.what + "--" + blendIngManager.getMessage(msg));
            blendIngManager.blendingHandMessage(msg);
        }
    }

    private void blendingHandMessage(Message msg) {
        switch (msg.what) {
            case MSG_INIT_PREVIEW_BITMAP:
                Bitmap previewBitmap;
                try {
                    previewBitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), mUri);
                    mCallBack.initPreviewBitmapFinish(previewBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mBlendingHandler.sendEmptyMessage(MSG_INIT_YUV_BITMAP);
                break;
            case MSG_INIT_YUV_BITMAP:
                if (mSrcInScreenRect == null || mSrcInScreenRect.width() <= 0 || mSrcInScreenRect.height() <= 0) {
                    Log.d(TAG, "blendingHandMessage: mSrcInScreenRect error");
                    mMainHandler.sendEmptyMessage(MSG_INIT_YUV_BITMAP_FINISH);
                    return;
                }
                try {
                    byte[] content = BlendingUtil.readStream(mContext.getContentResolver().openInputStream(mUri));
                    mCommonRefocus = CommonRefocus.getInstance(content);
                    if (mCommonRefocus == null) {
                        mMainHandler.sendEmptyMessage(MSG_INIT_YUV_BITMAP_FINISH);
                        return;
                    }
                    mRefocusData = mCommonRefocus.getRefocusData();
                    if (mRefocusData == null || mRefocusData.getMainYuv() == null) {
                        Log.i(TAG, "CommonRefocus yuv is null!");
                        mMainHandler.sendEmptyMessage(MSG_INIT_YUV_BITMAP_FINISH);
                        return;
                    }
                    byte[] mainYuvByte = mRefocusData.getMainYuv();
                    int rotation = mRefocusData.getRotation();
                    int mYuvWidth = mRefocusData.getYuvWidth();
                    int mYuvHeight = mRefocusData.getYuvHeight();
                    byte[] bytes = GalleryUtils.isJpegHwCodecAvailable()
                            ? RefocusUtils.hwYuv2jpeg(mainYuvByte, mYuvWidth, mYuvHeight, rotation)
                            : RefocusUtils.yuv2jpeg(mainYuvByte, mYuvWidth, mYuvHeight, rotation);
                    mSrcBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    mSrcInScreenbitmap = Bitmap.createScaledBitmap(mSrcBitmap, (int) mSrcInScreenRect.width(), (int) mSrcInScreenRect.height(), false);
                    if (DEBUG) {
                        BlendingUtil.writeToLocalFile(mSrcBitmap, "original.jpg", ROOT_PATH);
                        BlendingUtil.dumpBitmapAsBMPForDebug(mSrcBitmap, "original.bmp", ROOT_PATH);
                    }
                } catch (FileNotFoundException | Error e) {
                    e.printStackTrace();
                }
                mMainHandler.sendEmptyMessage(MSG_INIT_YUV_BITMAP_FINISH);
                break;
            case MSG_INIT_DEPTH:
                mDepth = new Depth(mRefocusData.getDepthData(),
                        mRefocusData.getDepthWidth(), mRefocusData.getDepthHeight());
                Log.d(TAG, "handleMessage: " + mRefocusData.getDepthSize());
                if (DEBUG) {
                    BlendingUtil.writeByteData(mDepth.getDepth(), "original_depth_" + mDepth.getmDepthWidth() + "_" + mDepth.getmDepthHeight(), ROOT_PATH);
                }
                mBlending.initdepth(mDepth, mRefocusData.getRotation(), mCommonRefocus);
                if (DEBUG) {
                    BlendingUtil.writeByteData(mDepth.getDepth(), "depth_" + mDepth.getmDepthWidth() + "_" + mDepth.getmDepthHeight(), ROOT_PATH);
                }

                mScaleDownBitmap = Bitmap.createScaledBitmap(mSrcBitmap,
                        mDepth.getmDepthWidth(), mDepth.getmDepthHeight(), true);
                if (DEBUG) {
                    BlendingUtil.dumpBitmapAsBMPForDebug(mScaleDownBitmap, "scale_original.bmp", ROOT_PATH);
                }

                mMainHandler.sendEmptyMessage(MSG_INIT_DEPTH_FINISH);
                break;
            case MSG_GET_MASK:
                Bitmap bitmap = mScaleDownBitmap;
                SparseArray<Object> rectHashMap = (SparseArray<Object>) msg.obj;
                int[] rect = (int[]) rectHashMap.get(0);
                RectF box = (RectF) rectHashMap.get(1);
                if (DEBUG) {
                    String builder = "center_x : " + rect[0]
                            + "\r\ncenter_y      : " + rect[1]
                            + "\r\nright_bottom_x : " + rect[2]
                            + "\r\nright_bpttom_y : " + rect[3];
                    BlendingUtil.writeByteData(builder.getBytes(), "extract_points", EXTRACT_PATH);
                }
                byte[] maskbyte = mBlending.createmask(bitmap, mDepth, rect);
                ExtractResult createmaskResult = mBlending.getCreatemaskResult();
                int resultCode = createmaskResult.getResultCode();
                if (maskbyte == null || resultCode != ImageBlending.SUCCESS) {
                    mMainHandler.obtainMessage(MSG_GET_MASK_FINISH, createmaskResult).sendToTarget();
                    return;
                }
                mMask = new Mask(maskbyte, mDepth.getmDepthWidth(), mDepth.getmDepthHeight());
                mMask = mBlending.scaleMask(mMask, (int) mSrcInScreenRect.width(), (int) mSrcInScreenRect.height());

                bitmap = mBlending.getForgroundBitmap(mSrcInScreenbitmap, mMask.getMask(), (int) box.left,
                        (int) box.top, (int) box.width(), (int) box.height());
                if (DEBUG) {
                    BlendingUtil.writeToLocalFile(bitmap, "extract_Result.jpg", EXTRACT_PATH);
                }
                createmaskResult = mBlending.getCreatemaskResult();
                createmaskResult.setBitmap(bitmap);

                mMainHandler.obtainMessage(MSG_GET_MASK_FINISH, createmaskResult).sendToTarget();
                break;
            case MSG_UPDATE_MASK:
                SparseArray<Object> array = (SparseArray<Object>) msg.obj;
                List<UpdateInfo> mOperations = (List<UpdateInfo>) array.get(0);
                RectF updatebox = (RectF) array.get(1);
                byte[] createmask;
                if (DEBUG) {
                    BlendingUtil.dumpPoints(mOperations.get(0).getMovex(), mOperations.get(0).getMovey(),
                            UPDATE_PATH + BlendIngManager.this.i, "path");
                }
                byte[] backupMask = new byte[324 * 243 * 2 + 65 * 2 * 8];
                int maskLength = mBlending.saveMask(backupMask);
                mUpdates.push(backupMask);
                mMainHandler.obtainMessage(SAVE_MASK, maskLength).sendToTarget();
                createmask = mBlending.updatemask(mScaleDownBitmap, mMask.getMask(), mOperations);
                mMask.setMask(createmask);
                mMask.setmMaskWidth(mDepth.getmDepthWidth());
                mMask.setmMaskHeight(mDepth.getmDepthHeight());
                mBlendingHandler.obtainMessage(MSG_GET_TARGET_BITMAP, updatebox).sendToTarget();
                break;
            case MSG_GET_TARGET_BITMAP:
                RectF boxrect = (RectF) msg.obj;
                mMask = mBlending.scaleMask(mMask, (int) mSrcInScreenRect.width(), (int) mSrcInScreenRect.height());//放大mask
                Bitmap forgroundBitmap = mBlending.getForgroundBitmap(mSrcInScreenbitmap, mMask.getMask(),
                        (int) boxrect.left, (int) boxrect.top, (int) boxrect.width(), (int) boxrect.height());
                if (forgroundBitmap == null) {
                    ExtractResult result = mBlending.getCreatemaskResult();
                    if (result.getWidth() < 0 || result.getHeight() < 0) {
                        ExtractResult updatemaskResult = mBlending.getCreatemaskResult();
                        updatemaskResult.setBitmap(null);
                        updatemaskResult.setResultCode(ImageBlending.FOREGROUND_WIDTH_BELOW_ZERO);
                        mMainHandler.obtainMessage(MSG_UPDATE_MASK_FINISH, updatemaskResult).sendToTarget();
                    }
                    return;
                }
                if (DEBUG) {
                    BlendingUtil.writeToLocalFile(forgroundBitmap, "extract_Result.jpg", UPDATE_PATH + BlendIngManager.this.i);
                    i++;
                }
                ExtractResult updatemaskResult = mBlending.getCreatemaskResult();
                updatemaskResult.setBitmap(forgroundBitmap);
                mMainHandler.obtainMessage(MSG_UPDATE_MASK_FINISH, updatemaskResult).sendToTarget();
                break;
            case BLENDING_BITMAP:
                BlendingRequest request = (BlendingRequest) msg.obj;
                Bitmap resultBitmap;
                ImageFilter imageFilter = request.getmImageFilter();
                Point screenPointInSrcLoc = request.getScreenPointInSrcLoc();
                Point targetInSrcPosition = request.getmTargetInSrcPosition();
                Point targetInSrcCenterPosition = request.getTargetInSrcCenterPosition();
                float scaleFactor = request.getScaleFactor();
                float angle = request.getAngle();
                //new background
                Bitmap newBackground = request.ismIsOrigImage() ? mSrcBitmap : mDstBitmap;
                if (imageFilter != null) {
                    Bitmap copy = newBackground.copy(newBackground.getConfig(), false);
                    newBackground = imageFilter.apply(copy, 1.0f, 0);
                }

                resultBitmap = mBlending.doBlending(mSrcBitmap, mMask, newBackground,
                        targetInSrcPosition.x, targetInSrcPosition.y, targetInSrcCenterPosition.x,
                        targetInSrcCenterPosition.y, scaleFactor, angle);
                saveBitmap(resultBitmap, request.getPath());
                mMainHandler.sendEmptyMessage(BLENDING_BITMAP_FINISH);
                break;
            case UNDO_MASK:
                if (BlendIngManager.DEBUG) {
                    BlendingUtil.writeByteData(mMask.getMask(), "mask", ROOT_PATH + "/save" + mUpdates.size());
                }
                byte[] bytes = mBlending.undoUpdate(mMask.getMask());
                mMask.setMask(bytes);
                mBlendingHandler.obtainMessage(MSG_GET_TARGET_BITMAP, msg.obj).sendToTarget();
                break;
        }
    }

    private String getMessage(Message msg) {
        switch (msg.what) {
            case MSG_INIT_PREVIEW_BITMAP:
                return "MSG_INIT_PREVIEW_BITMAP";
            case MSG_INIT_YUV_BITMAP:
                return "MSG_INIT_YUV_BITMAP";
            case MSG_INIT_DEPTH:
                return "MSG_INIT_DEPTH";
            case MSG_GET_MASK:
                return "MSG_GET_MASK";
            case MSG_UPDATE_MASK:
                return "MSG_UPDATE_MASK";
            case BLENDING_BITMAP:
                return "BLENDING_BITMAP";
            case MSG_INIT_PREVIEW_BITMAP_FINISH:
                return "MSG_INIT_PREVIEW_BITMAP_FINISH";
            case MSG_INIT_YUV_BITMAP_FINISH:
                return "MSG_INIT_YUV_BITMAP_FINISH";
            case MSG_GET_MASK_FINISH:
                return "MSG_GET_MASK_FINISH";
            case MSG_UPDATE_MASK_FINISH:
                return "MSG_UPDATE_MASK_FINISH";
            case BLENDING_BITMAP_FINISH:
                return "BLENDING_BITMAP_FINISH";
            case MSG_INIT_DEPTH_FINISH:
                return "MSG_INIT_DEPTH_FINISH";
            case SAVE_MASK:
                return "SAVE_MASK";
            case UNDO_MASK:
                return "UNDO_MASK";
            case UNDO_MASK_FINISH:
                return "UNDO_MASK_FINISH";
            case MSG_GET_TARGET_BITMAP:
                return "MSG_GET_TARGET_BITMAP";
            case MSG_GET_TARGET_BITMAP_FINISH:
                return "MSG_GET_TARGET_BITMAP_FINISH";
            default:
                return "NOT MATCH";
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, "MainHandler -> handleMessage: >>>" + msg.what + "--" + getMessage(msg));
            switch (msg.what) {
                case MSG_INIT_PREVIEW_BITMAP_FINISH:
                    mCallBack.initPreviewBitmapFinish((Bitmap) msg.obj);
                    break;
                case MSG_INIT_YUV_BITMAP_FINISH:
                    mCallBack.initYuvBitmapFinish(mSrcInScreenbitmap);
                    break;
                case MSG_GET_MASK_FINISH:
                    ExtractResult result = (ExtractResult) msg.obj;
                    mCallBack.extractBitmapFinish(result);
                    break;
                case MSG_UPDATE_MASK_FINISH:
                    ExtractResult updsateresult = (ExtractResult) msg.obj;
                    mCallBack.updateBitmapFinish(updsateresult);
                    break;
                case BLENDING_BITMAP_FINISH:
                    mCallBack.blendingFinish();
                    break;
                case SAVE_MASK:
                    mCallBack.saveMaskResult((int) msg.obj);
                    break;
                case MSG_INIT_DEPTH_FINISH:
                    mCallBack.initDepthFinish();
                    break;
            }
        }
    }

    public BlendIngManager(Uri uri, Context context, ExtractCallBack callBack) {
        this.mContext = context;
        this.mUri = uri;
        this.mCallBack = callBack;
        DEBUG = StandardFrameworks.getInstances().getBooleanFromSystemProperties(IS_DEBUG_OPEN, false);
        Log.d(TAG, "BlendIngManager: --DEBUG : " + DEBUG);
        if (DEBUG) {
            File file = new File(ROOT_PATH);
            if (file.exists()) {
                BlendingUtil.deleteDir(file);
            }
        }
        mMainHandler = new MainHandler();
        imageBlending = new HandlerThread("ImageBlending-HandlerThread");
        imageBlending.start();
        imageBlending.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "uncaughtException: " + t.getName() + "has a " + e.getMessage());
                e.printStackTrace();
            }
        });
        mBlendingHandler = new BlendingHandler(imageBlending.getLooper(), this);
        mBlendingHandler.sendEmptyMessage(MSG_INIT_PREVIEW_BITMAP);
    }

    public void destory() {
        if (imageBlending != null) {
            imageBlending.interrupt();
            imageBlending.quit();
            try {
                imageBlending.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "destory: InterruptedException");
            }
            imageBlending = null;
        }
        if (mBlending != null) {
            mBlending.destory();
        }
        BitmapUtils.recycleBitmap(mSrcBitmap);
        BitmapUtils.recycleBitmap(mSrcInScreenbitmap);
        BitmapUtils.recycleBitmap(mDstBitmap);
        BitmapUtils.recycleBitmap(mDstInScreenBitmap);
        BitmapUtils.recycleBitmap(mScaleDownBitmap);
    }

    public void extractBitmap(BlendingView mBlendingView) {
        int[] rect = new int[4];
        float[] floats = mBlendingView.getDefinitionPoint(mDepth.getmDepthWidth(), mDepth.getmDepthHeight());
        floats[0] = (floats[0] + floats[2]) / 2;
        floats[1] = (floats[1] + floats[3]) / 2;
        rect[0] = (int) floats[0];
        rect[1] = (int) floats[1];
        rect[2] = (int) floats[2];
        rect[3] = (int) floats[3];
        int maxwidth = mSrcInScreenbitmap.getWidth();
        int maxheight = mSrcInScreenbitmap.getHeight();
        if (rect[0] < 0) {
            rect[0] = 0;
        }
        if (rect[1] < 0) {
            rect[1] = 0;
        }
        if (rect[2] > maxwidth) {
            rect[2] = maxwidth;
        }
        if (rect[3] > maxheight) {
            rect[3] = maxheight;
        }

        if (rect.length < 4) {
            return;
        }
        Log.d(TAG, "extractBitmap: " + rect[0] + "-" + rect[1] + "-" + rect[2] + "-" + rect[3]);
        RectF box = mBlendingView.getBox();
        SparseArray<Object> rectHashMap = new SparseArray<>(2);
        rectHashMap.put(0, rect);
        rectHashMap.put(1, box);
        mBlendingHandler.obtainMessage(MSG_GET_MASK, rectHashMap).sendToTarget();
    }

    public boolean updateBitmap(BlendingView view, List<UpdateInfo> mOperations, boolean mIsForground) {
        if (mOperations == null) {
            return false;
        }
        mOperations.clear();
        blendXandYcorrdinate(view, mOperations, mIsForground);
        if (mOperations.size() <= 0) {
            return false;
        }
        RectF box = view.getBox();
        SparseArray<Object> rectHashMap = new SparseArray<>(2);
        rectHashMap.put(0, mOperations);
        rectHashMap.put(1, box);
        mBlendingHandler.obtainMessage(MSG_UPDATE_MASK, rectHashMap).sendToTarget();
        return true;
    }

    public int verifyPosition(Point targetInSrcPosition, Point centerPosition, float scaleFactor, float angle) {
        mBlending.scaleMask(mMask, mSrcBitmap.getWidth(), mSrcBitmap.getHeight());
        return mBlending.verify_position(mMask.getMask(), mSrcBitmap.getWidth(), mSrcBitmap.getHeight(),
                targetInSrcPosition.x, targetInSrcPosition.y, centerPosition.x, centerPosition.y, scaleFactor, angle);
    }

    public void blendingBitmap(BlendingRequest request) {
        if (request.ismIsOrigImage() && request.getmImageFilter() == null) {
            return;
        }
        mBlendingHandler.obtainMessage(BLENDING_BITMAP, request).sendToTarget();
    }

    private void blendXandYcorrdinate(BlendingView mBlendingView, List<UpdateInfo> mOperations, boolean mIsForground) {
        List<TagPoint> points = mBlendingView.getUpdatePoint(mDepth.getmDepthWidth(), mDepth.getmDepthHeight());
        Log.d(TAG, "blendXandYcorrdinate: " + points.size());
        if (points.size() <= 1) {
            return;
        }
        int size = points.size();
        int[] xloc = new int[size];
        int[] yloc = new int[size];
        for (int i = 0; i < size; i++) {
            Point point = points.get(i);
            xloc[i] = point.x;
            yloc[i] = point.y;
        }
        UpdateInfo updateInfo = new UpdateInfo();
        updateInfo.setMovex(xloc);
        updateInfo.setMovey(yloc);
        updateInfo.setIsforground(mIsForground);
        mOperations.add(updateInfo);
    }

    private void saveBitmap(Bitmap result, String path) {
        Uri uri = mUri;
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        String name = df.format(new Date());
        File savePath = new File(path.substring(0, path.lastIndexOf("/")));
        File file = BlendingUtil.writeToLocalFile(result, name + ".jpg", savePath.toString());
        //android.media.MediaScannerConnection.scanFile(ReplaceActivity.this, new String[]{file.toString()}, null, null);
        long time = System.currentTimeMillis() / 1000;
        Uri uri1 = SaveImage.linkNewFileToUri(GalleryAppImpl.getApplication(), uri, file, time, false);
        Log.d(TAG, "blendingBitmap: " + uri1);
    }

    Bitmap getSrcBitmap() {
        return mSrcBitmap;
    }

    public Bitmap getSrcInScreenbitmap() {
        return mSrcInScreenbitmap;
    }

    void setBitmapInScreenRect(RectF rect) {
        this.mSrcInScreenRect = rect;
    }

    interface ExtractCallBack {
        void initPreviewBitmapFinish(Bitmap mSrcBitmap);

        void initYuvBitmapFinish(Bitmap bitmap);

        void initDepthFinish();

        void extractBitmapFinish(ExtractResult result);

        void updateBitmapFinish(ExtractResult updsateresult);

        void saveMaskResult(int obj);

        void blendingFinish();
    }

}

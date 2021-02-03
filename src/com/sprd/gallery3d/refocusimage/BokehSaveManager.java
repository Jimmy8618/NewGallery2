package com.sprd.gallery3d.refocusimage;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.PriorityThreadFactory;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.refocus.RefocusUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by SPRD
 */

public class BokehSaveManager {
    public static final String TAG = "BokehSaveManager";
    private ThreadPoolExecutor mExecutor;
    private LinkedBlockingQueue mWorkQueue;
    private ConcurrentHashMap<String, MediaItem> mConcurrentHashMap;
    private static BokehSaveManager sInstance = null;
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 4;
    private static final int KEEP_ALIVE_TIME = 5;
    private boolean mSdCardPermissionDenied = false;

    private BokehSaveManager() {
        this.mWorkQueue = new LinkedBlockingQueue<Runnable>();
        this.mConcurrentHashMap = new ConcurrentHashMap<String, MediaItem>();
        this.mExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, mWorkQueue, new PriorityThreadFactory("bokeh-save-thread-pool",
                android.os.Process.THREAD_PRIORITY_BACKGROUND));
    }

    public static synchronized BokehSaveManager getInstance() {
        if (sInstance == null) {
            sInstance = new BokehSaveManager();
        }
        return sInstance;
    }

    public interface BokehSaveCallBack {
        void bokehSaveDone(String filePath);

        void pictureBokehDone(String filePath, byte[] bokehPicture);

        void bokehSaveError(String filePath);
    }

    public MediaItem getMediaItemByPath(String path) {
        return mConcurrentHashMap.get(path);
    }

    private boolean isSaveing(String path) {
        return mConcurrentHashMap.get(path) != null;
    }

    public void startBokehSave(final Activity activity, final MediaItem mediaItem, final BokehSaveCallBack callBack) {
        if (activity == null || mediaItem == null) {
            Log.d(TAG, "startBokehSave, mediaItem or context is null, return!");
            return;
        }
        String path = mediaItem.getFilePath();
        if (isSaveing(path) || mSdCardPermissionDenied) {
            Log.d(TAG, "task is running, path = " + path + " mSdCardPermissionDenied = " + mSdCardPermissionDenied);
            return;
        }
        mConcurrentHashMap.put(path, mediaItem);

        boolean hasSDCardPermission = SdCardPermission.hasStoragePermission(path);
        Log.d(TAG, "startBokehSave: path = " + path + ", hasSDCardPermission = " + hasSDCardPermission);
        if (!GalleryStorageUtil.isInInternalStorage(path)
                && !hasSDCardPermission) {
            final SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    mSdCardPermissionDenied = false;
                    saveBokehInternal(activity, mediaItem, callBack);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    mSdCardPermissionDenied = true;
                    mConcurrentHashMap.remove(mediaItem.getFilePath());
                    SdCardPermission.showSdcardPermissionErrorDialog(activity,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSdCardPermissionDenied = false;
                                    activity.onBackPressed();
                                }
                            });
                }
            };
            final ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(path);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SdCardPermission.requestSdcardPermission(activity, storagePaths, (SdCardPermissionAccessor) activity, sdCardPermissionListener);
                }
            });
        } else {
            saveBokehInternal(activity, mediaItem, callBack);
        }
    }

    private void saveBokehInternal(Context context, MediaItem mediaItem, final BokehSaveCallBack callBack) {
        String path = mediaItem.getFilePath();
        Log.d(TAG, "ready save mediaItem = " + mediaItem + "path = " + path);
        SaveWorker saveWorker = new SaveWorker(context, mediaItem.getContentUri(), path, new WorkListener() {
            @Override
            public void onSaveStart(String path) {
                Log.d(TAG, "bokeh save start! path = " + path);
            }

            @Override
            public void onSaveError(String path) {
                Log.d(TAG, "bokeh save error! path = " + path);
                callBack.bokehSaveError(path);
            }

            @Override
            public void bokehDone(String filePath, byte[] bokehPicture) {
                callBack.pictureBokehDone(filePath, bokehPicture);
            }

            @Override
            public void onSaveDone(String path) {
                Log.d(TAG, "bokeh save done! path = " + path);
                callBack.bokehSaveDone(path);
                mConcurrentHashMap.remove(path);
            }
        });

        if (mWorkQueue.size() >= 2) {
            SaveWorker removeWorker = (SaveWorker) mWorkQueue.poll();
            if (removeWorker != null) {
                String removeWorkerPath = removeWorker.getPath();
                Log.d(TAG, "remove Worker Path = " + removeWorkerPath);
                mConcurrentHashMap.remove(removeWorkerPath);
            }
        }
        mExecutor.execute(saveWorker);
    }

    public interface WorkListener {
        void onSaveStart(String path);

        void onSaveError(String path);

        void bokehDone(String filePath, byte[] bokehPicture);

        void onSaveDone(String path);
    }

    private class SaveWorker implements Runnable {

        private Uri mUri;
        private String mPath;
        private Context mContext;
        private WorkListener mListener;

        public SaveWorker(Context context, Uri uri, String path, WorkListener listener) {
            this.mUri = uri;
            this.mPath = path;
            this.mContext = context;
            this.mListener = listener;
        }

        @Override
        public void run() {
            // now, just realtime bokeh image need do bokeh save
            mListener.onSaveStart(mPath);
            InputStream inStream = null;
            try {
                Log.d(TAG, "run: mPath = " + mPath);
                if (GalleryStorageUtil.isInInternalStorage(mPath)) {
                    inStream = mContext.getContentResolver().openInputStream(mUri);
                } else {
                    inStream = SdCardPermission.createExternalInputStream(mPath);
                }
                byte[] newdata = RefocusUtils.doUpdateBokeh(inStream, mPath);
                if (newdata != null) {
                    if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                        GalleryUtils.saveBokehJpeg(mContext, newdata, mUri, mPath);
                    } else {
                        GalleryUtils.saveNewJpeg(mContext, newdata, mUri, mPath);
                    }
                    mListener.onSaveDone(mPath);
                } else {
                    mListener.onSaveError(mPath);
                }
            } catch (Exception e) {
                mListener.onSaveError(mPath);
                e.printStackTrace();
            } catch (Error e) {
                mListener.onSaveError(mPath);
                e.printStackTrace();
            } finally {
                Utils.closeSilently(inStream);
            }

        }

        public synchronized String getPath() {
            return mPath;
        }
    }

    public void quit() {
        mWorkQueue.clear();
        mConcurrentHashMap.clear();
    }

    public boolean isBokehSaveing() {
        if (mConcurrentHashMap == null) {
            return false;
        }
        int size = mConcurrentHashMap.size();
        return size > 0;
    }

}

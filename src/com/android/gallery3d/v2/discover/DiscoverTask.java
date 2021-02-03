package com.android.gallery3d.v2.discover;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import android.util.Log;
import android.util.SparseIntArray;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.IStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.discover.people.FaceDetector;
import com.android.gallery3d.v2.discover.people.PeopleTask;
import com.android.gallery3d.v2.discover.things.AbstractClassifier;
import com.android.gallery3d.v2.discover.things.Recognition;
import com.android.gallery3d.v2.location.LocationResult;
import com.android.gallery3d.v2.location.LocationTask;
import com.android.gallery3d.v2.util.UIHandler;
import com.android.gallery3d.v2.util.UIMessageHandler;
import com.sprd.frameworks.StandardFrameworks;

import java.util.ArrayList;
import java.util.List;

public class DiscoverTask extends ContentObserver implements DiscoverManager.OnThingsResultListener,
        DiscoverManager.OnPeopleResultListener, DiscoverManager.OnLocationResultListener, UIMessageHandler {
    private static final String TAG = DiscoverTask.class.getSimpleName();

    private static final int START_DISCOVER_TASK = 1;
    private static final int DELAY_TIME = 5 * 1000;

    //过滤掉小图
    private static final int MIN_THINGS_PIC_SIZE = 200;
    private static final int MIN_PEOPLE_PIC_SIZE = 100;

    private LoadTask mLoadTask;

    private PeopleTask mPeopleTask;
    private LocationTask mLocationTask;

    private Handler mMainHandler;
    private boolean mResumed = false;

    private final IStorageUtil.StorageChangedListener mStorageChangedListener =
            new IStorageUtil.StorageChangedListener() {
                @Override
                public void onStorageChanged(String path, String action) {
                    Log.d(TAG, "onStorageChanged action = " + action + ", path = " + path + ", mResumed = " + mResumed);
                    if (action == null || path == null || !mResumed) {
                        return;
                    }
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        //收到U盘拔出消息, 先停止task, 之后再开启
                        mMainHandler.removeMessages(START_DISCOVER_TASK);
                        pauseInternal();
                        mMainHandler.sendEmptyMessageDelayed(START_DISCOVER_TASK, DELAY_TIME);
                    }
                }
            };

    public DiscoverTask(Handler handler) {
        super(handler);
        mMainHandler = new UIHandler<>(this);
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this
        );
        mPeopleTask = new PeopleTask(handler);
        if (StandardFrameworks.getInstances().isSupportLocation()) {
            mLocationTask = new LocationTask(handler);
        }
    }

    @Override
    public void handleUIMessage(Message msg) {
        if (msg.what == START_DISCOVER_TASK) {
            resumeInternal();
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        if (mLoadTask != null) {
            mLoadTask.notifyDirty();
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        mResumed = true;
        mMainHandler.removeMessages(START_DISCOVER_TASK);
        mMainHandler.sendEmptyMessageDelayed(START_DISCOVER_TASK, DELAY_TIME);
        GalleryStorageUtil.addStorageChangeListener(mStorageChangedListener);
    }

    private void resumeInternal() {
        Log.d(TAG, "resumeInternal mLoadTask = " + mLoadTask + ", mResumed = " + mResumed);
        if (mLoadTask != null) {
            return;
        }
        DiscoverManager.getDefault().register(DiscoverManager.SCENE_THINGS, this);
        DiscoverManager.getDefault().register(DiscoverManager.SCENE_PEOPLE, this);
        DiscoverManager.getDefault().register(DiscoverManager.SCENE_LOCATION, this);
        if (mLoadTask == null) {
            mLoadTask = new LoadTask();
            mLoadTask.start();
        }
        mPeopleTask.onResume();
        if (StandardFrameworks.getInstances().isSupportLocation()) {
            mLocationTask.onResume();
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        mResumed = false;
        mMainHandler.removeMessages(START_DISCOVER_TASK);
        pauseInternal();
        GalleryStorageUtil.removeStorageChangeListener(mStorageChangedListener);
    }

    private void pauseInternal() {
        Log.d(TAG, "pauseInternal mLoadTask = " + mLoadTask + ", mResumed = " + mResumed);
        if (mLoadTask == null) {
            return;
        }
        DiscoverManager.getDefault().unregister(DiscoverManager.SCENE_THINGS, this);
        DiscoverManager.getDefault().unregister(DiscoverManager.SCENE_PEOPLE, this);
        DiscoverManager.getDefault().unregister(DiscoverManager.SCENE_LOCATION, this);
        if (mLoadTask != null) {
            mLoadTask.terminate();
            mLoadTask = null;
        }
        mPeopleTask.onPause();
        if (StandardFrameworks.getInstances().isSupportLocation()) {
            mLocationTask.onPause();
        }
    }

    private List<ImageBean> getUnClassifiedImages() {
        List<ImageBean> imageBeans = new ArrayList<>();
        String imageWhere = MediaStore.Images.ImageColumns.WIDTH + ">" + MIN_THINGS_PIC_SIZE
                + " AND " + MediaStore.Images.ImageColumns.HEIGHT + ">" + MIN_THINGS_PIC_SIZE;
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            imageWhere += ") AND (" + "(file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null)";
        }
        Cursor cursor = null;
        try {
            cursor = GalleryAppImpl.getApplication().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Images.ImageColumns._ID,
                    MediaStore.Images.ImageColumns.DATA,
                    MediaStore.Images.ImageColumns.ORIENTATION,
                    MediaStore.Images.ImageColumns.MIME_TYPE
            }, imageWhere, null, null);

            if (cursor != null) {
                int imageId;
                String path;
                int orientation;
                String mimeType;
                //已做过分类的事务
                SparseIntArray things = DiscoverStore.getAllThings();
                while (cursor.moveToNext()) {
                    imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                    path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                    orientation = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION));
                    mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE));
                    if (null != mimeType && mimeType.equalsIgnoreCase("image/gif")
                            || (things.get(imageId, -1) != -1)) {
                        continue;
                    }
                    imageBeans.add(new ImageBean(imageId, path, orientation, 0f, 0f, 0));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(cursor);
        }
        return imageBeans;
    }

    private List<ImageBean> getUnDetectedImages() {
        List<ImageBean> imageBeans = new ArrayList<>();
        String imageWhere = MediaStore.Images.ImageColumns.WIDTH + ">" + MIN_PEOPLE_PIC_SIZE
                + " AND " + MediaStore.Images.ImageColumns.HEIGHT + ">" + MIN_PEOPLE_PIC_SIZE;
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            imageWhere += ") AND (" + "(file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null)";
        }
        Cursor cursor = null;
        try {
            cursor = GalleryAppImpl.getApplication().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Images.ImageColumns._ID,
                    MediaStore.Images.ImageColumns.DATA,
                    MediaStore.Images.ImageColumns.ORIENTATION,
                    MediaStore.Images.ImageColumns.MIME_TYPE
            }, imageWhere, null, null);

            if (cursor != null) {
                int imageId;
                String path;
                int orientation;
                String mimeType;
                //已做过检测的人脸
                SparseIntArray vectors = DiscoverStore.getAllVectors();
                while (cursor.moveToNext()) {
                    imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                    path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                    orientation = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION));
                    mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE));
                    if (null != mimeType && mimeType.equalsIgnoreCase("image/gif")
                            || (vectors.get(imageId, -1) != -1)) {
                        continue;
                    }
                    imageBeans.add(new ImageBean(imageId, path, orientation, 0f, 0f, 0));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(cursor);
        }
        return imageBeans;
    }

    private List<ImageBean> getUnLocationedImages() {
        List<ImageBean> imageBeans = new ArrayList<>();
        String imageWhere = MediaStore.Images.ImageColumns.LATITUDE + " is not null and "
                + MediaStore.Images.ImageColumns.LONGITUDE + " is not null and "
                + MediaStore.Images.ImageColumns.LATITUDE + " > 0 and "
                + MediaStore.Images.ImageColumns.LONGITUDE + " > 0";
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            imageWhere += ") AND (" + "(file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null)";
        }
        String orderClause = MediaStore.Images.ImageColumns.DATE_MODIFIED + " DESC, "
                + MediaStore.Images.ImageColumns._ID + " DESC";

        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.LATITUDE,
                MediaStore.Images.ImageColumns.LONGITUDE,
                MediaStore.Images.ImageColumns.DATE_MODIFIED
        }, imageWhere, null, orderClause);

        if (cursor != null) {
            int imageId;
            double lat;
            double logt;
            long date;
            SparseIntArray locations = DiscoverStore.getAllLocations();
            while (cursor.moveToNext()) {
                imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                if (locations.get(imageId, -1) != -1) {
                    continue;
                }
                lat = cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LATITUDE));
                logt = cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LONGITUDE));
                date = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED));
                imageBeans.add(new ImageBean(imageId, null, 0, lat, logt, date));
            }
        }
        Utils.closeSilently(cursor);
        return imageBeans;
    }

    @Override
    public void onResult(int imageId, @NonNull List<Recognition> recognitionList) {
        Log.d(TAG, "onResult (" + imageId + ")->" + AbstractClassifier.recognizedThings(recognitionList));
        if (DiscoverStore.isThingExist(imageId)) {
            return;
        }
        int classification = AbstractClassifier.getResult(recognitionList);
        //添加到数据库
        DiscoverStore.insertThing(imageId, classification);
    }

    @Override
    public void onResult(int imageId, @NonNull FaceDetector.Face[] faces) {
        Log.d(TAG, "onResult (" + imageId + ")->" + FaceDetector.detectedFaces(faces));
        if (DiscoverStore.isVectorExist(imageId)) {
            return;
        }
        DiscoverStore.insertVector(imageId, faces);
    }

    @Override
    public void onResult(int imageId, @NonNull LocationResult location) {
        Log.d(TAG, "onResult (" + imageId + ")->" + location.featureName);
        if (DiscoverStore.isLocationExist(imageId)) {
            return;
        }
        DiscoverStore.insertLocationInfo(imageId, location);
    }

    private class LoadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Log.d(TAG, "run start.");
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty) {
                        Log.d(TAG, "run wait.");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
                //////////////////////////begin/////////////////////////////
                //事务分类
                List<ImageBean> unClassified = getUnClassifiedImages();
                for (ImageBean imageBean : unClassified) {
                    if (!mActive || mDirty) {
                        break;
                    }
                    DiscoverManager.getDefault().process(DiscoverManager.SCENE_THINGS,
                            imageBean.imageId, imageBean.path, imageBean.orientation, imageBean.latitude, imageBean.longitude, imageBean.date);
                }
                unClassified.clear();
                //检测人脸信息
                List<ImageBean> unDetected = getUnDetectedImages();
                for (ImageBean imageBean : unDetected) {
                    if (!mActive || mDirty) {
                        break;
                    }
                    DiscoverManager.getDefault().process(DiscoverManager.SCENE_PEOPLE,
                            imageBean.imageId, imageBean.path, imageBean.orientation, imageBean.latitude, imageBean.longitude, imageBean.date);
                }
                unDetected.clear();
                //位置信息
                if (StandardFrameworks.getInstances().isSupportLocation()) {
                    List<ImageBean> unLocationed = getUnLocationedImages();
                    for (ImageBean imageBean : unLocationed) {
                        if (!mActive || mDirty) {
                            break;
                        }
                        DiscoverManager.getDefault().process(DiscoverManager.SCENE_LOCATION,
                                imageBean.imageId, imageBean.path, imageBean.orientation, imageBean.latitude, imageBean.longitude, imageBean.date);
                    }
                    unLocationed.clear();
                }
                ///////////////////////////end//////////////////////////////
            }
            Log.d(TAG, "run end.");
        }

        synchronized void notifyDirty() {
            mDirty = true;
            DiscoverManager.getDefault().clearProcess();
            notifyAll();
        }

        synchronized void terminate() {
            mActive = false;
            DiscoverManager.getDefault().clearProcess();
            notifyAll();
        }
    }

    private class ImageBean {
        int imageId;
        String path;
        int orientation;
        double latitude;
        double longitude;
        long date;

        public ImageBean(int imageId, String path, int orientation, double latitude, double longitude, long date) {
            this.imageId = imageId;
            this.path = path;
            this.orientation = orientation;
            this.latitude = latitude;
            this.longitude = longitude;
            this.date = date;
        }
    }
}

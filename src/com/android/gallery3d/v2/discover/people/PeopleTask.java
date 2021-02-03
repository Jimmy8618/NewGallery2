package com.android.gallery3d.v2.discover.people;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.discover.db.DiscoverStore;

import java.util.ArrayList;
import java.util.List;

public class PeopleTask extends ContentObserver {
    private static final String TAG = PeopleTask.class.getSimpleName();

    private static final int MAX_SAMPLE_COUNT = 12;

    private static final int MIN_SAMPLE_COUNT = MAX_SAMPLE_COUNT / 3;

    private static final int SAMPLE_MIN_WIDTH = 60;
    private static final int SAMPLE_MIN_HEIGHT = 60;

    private static final int SAMPLE_MAX_YAW_ANGLE = 60;
    private static final int SAMPLE_MAX_ROLL_ANGLE = 60;

    private static final int SAMPLE_MIN_FD_SCORE = 450;
    //private static final int SAMPLE_MIN_FA_SCORE = 300;

    private static final float MIN_SIMILAR = 0.55f;
    private static final float AVE_SIMILAR = 0.45f;

    private static final float SAMPLE_MIN_SIMILAR = 0.6f;
    private static final float SAMPLE_AVE_SIMILAR = 0.55f;

    private LoadTask mLoadTask;

    public PeopleTask(Handler handler) {
        super(handler);
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                DiscoverStore.VectorInfo.Media.CONTENT_URI,
                true,
                this
        );
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (mLoadTask != null) {
            mLoadTask.notifyDirty();
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        if (mLoadTask == null) {
            mLoadTask = new LoadTask();
            mLoadTask.start();
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        if (mLoadTask != null) {
            mLoadTask.terminate();
            mLoadTask = null;
        }
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
                //Step1. 先从数据库中读出所有Vector信息和Face信息数据
                List<VectorBean> vectorBeanList = getUnMatchedVectorInfo();
                if (!mActive || mDirty || vectorBeanList.size() <= 0) {
                    continue;
                }

                SparseArray<VectorBean> allVectorBeans = getAllVectorInfo();
                if (!mActive || mDirty) {
                    continue;
                }

                List<FaceBean> faceBeanList = getFacesInfo();
                if (!mActive || mDirty) {
                    continue;
                }

                //Step2. 有了数据后准备遍历Vector信息数据
                for (VectorBean vectorInfo : vectorBeanList) {
                    if (!mActive || mDirty) {
                        break;
                    }
                    //与Face信息依次对比
                    //如果Face表是空的
                    if (faceBeanList.size() <= 0) {
                        if (setVectorAsNewFace(vectorInfo)) {
                            break;
                        }
                    } else {
                        //该条Vector记录是否被处理
                        boolean vecDealed = false;
                        //遍历Face,获取最相似的FaceInfo
                        float maxAve = 0;
                        FaceBean maxSimilarFace = null;
                        for (FaceBean face : faceBeanList) {
                            if (!mActive || mDirty) {
                                break;
                            }
                            face.compare(vectorInfo, allVectorBeans);
                            if (maxAve < face.aveSimilar) {
                                maxAve = face.aveSimilar;
                                maxSimilarFace = face;
                            }
                        }
                        if (!mActive || mDirty) {
                            break;
                        }
                        //
                        if (maxSimilarFace != null) {
                            //判断当前Vector记录是否和maxSimilarFace记录相似
                            if (maxSimilarFace.maxSimilar >= MIN_SIMILAR
                                    && maxSimilarFace.aveSimilar >= AVE_SIMILAR) {
                                //判断Face样本够不够,样本不够添加样本
                                if (maxSimilarFace.sampleCount < MIN_SAMPLE_COUNT) {
                                    //是否需要添加到当前Face记录样本中去
                                    if (!setVectorAsThisFaceSample(vectorInfo, maxSimilarFace)) {
                                        //不满足样本条件,作为新的Face
                                        if (setVectorAsNewFace(vectorInfo)) {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                } else {
                                    //设置当前Vector属于此Face
                                    setVectorAsThisFace(vectorInfo, maxSimilarFace);
                                    //是否需要添加到当前Face记录样本中去
                                    if (setVectorAsThisFaceSample(vectorInfo, maxSimilarFace)) {
                                        break;
                                    }
                                }
                                //设置为已处理
                                vecDealed = true;
                            }
                        }

                        //如果在Face表中没有找到相似的,看是否需要添加新的人脸
                        if (!vecDealed) {
                            if (setVectorAsNewFace(vectorInfo)) {
                                break;
                            }
                        }
                    }
                }
                ///////////////////////////end//////////////////////////////
            }
            Log.d(TAG, "run end.");
        }

        synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }

    private ImageInfo getImageInfo(int imageId) {
        ImageInfo imageInfo = null;
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                        MediaStore.Images.ImageColumns.DATA,
                        MediaStore.Images.ImageColumns.ORIENTATION
                }, MediaStore.Images.ImageColumns._ID + "=" + imageId, null, null
        );
        if (cursor != null && cursor.moveToFirst()) {
            imageInfo = new ImageInfo(
                    imageId,
                    cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)),
                    cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION))
            );
        }
        Utils.closeSilently(cursor);
        return imageInfo;
    }

    private boolean setVectorAsNewFace(VectorBean vectorInfo) {
        //是否设置当前Vector记录为人脸并添加为样本
        if (vectorInfo.width >= SAMPLE_MIN_WIDTH
                && vectorInfo.height >= SAMPLE_MIN_HEIGHT
                && Math.abs(vectorInfo.yawAngle) <= SAMPLE_MAX_YAW_ANGLE
                && Math.abs(vectorInfo.rollAngle) <= SAMPLE_MAX_ROLL_ANGLE
                && vectorInfo.fdScore >= SAMPLE_MIN_FD_SCORE
            /*&& vectorInfo.faScore >= SAMPLE_MIN_FA_SCORE*/) {
            //人脸头像
            ImageInfo imageInfo = getImageInfo(vectorInfo.image_id);
            String head = "";
            if (imageInfo != null) {
                if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                    head = FaceDetector.saveHead(GalleryAppImpl.getApplication().getContentResolver(), imageInfo.uri, imageInfo.orientation, vectorInfo.x,
                            vectorInfo.y, vectorInfo.width, vectorInfo.height, vectorInfo.id);
                } else {
                    head = FaceDetector.saveHead(imageInfo.path, imageInfo.orientation, vectorInfo.x,
                            vectorInfo.y, vectorInfo.width, vectorInfo.height, vectorInfo.id);
                }
            }
            //添加Face记录
            ContentValues faceValues = new ContentValues();
            faceValues.put(DiscoverStore.FaceInfo.Columns.HEAD, head);
            faceValues.put(DiscoverStore.FaceInfo.Columns.NAME, "");
            faceValues.put(DiscoverStore.FaceInfo.Columns.SAMPLES, vectorInfo.id);
            Uri uri = GalleryAppImpl.getApplication().getContentResolver().insert(DiscoverStore.FaceInfo.Media.CONTENT_URI, faceValues);
            //更新当前Vector记录为已配对
            ContentValues vectorValues = new ContentValues();
            vectorValues.put(DiscoverStore.VectorInfo.Columns.IS_MATCHED, 1);
            vectorValues.put(DiscoverStore.VectorInfo.Columns.FACE_ID, ContentUris.parseId(uri));
            GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.VectorInfo.Media.CONTENT_URI, vectorValues,
                    DiscoverStore.VectorInfo.Columns._ID + "=" + vectorInfo.id, null);
            return true;
        }
        return false;
    }

    private boolean setVectorAsThisFaceSample(VectorBean vectorInfo, FaceBean faceInfo) {
        if (faceInfo.sampleCount < MAX_SAMPLE_COUNT
                && faceInfo.maxSimilar >= SAMPLE_MIN_SIMILAR
                && faceInfo.aveSimilar >= SAMPLE_AVE_SIMILAR
                && vectorInfo.width >= SAMPLE_MIN_WIDTH
                && vectorInfo.height >= SAMPLE_MIN_HEIGHT
                && Math.abs(vectorInfo.yawAngle) <= SAMPLE_MAX_YAW_ANGLE
                && Math.abs(vectorInfo.rollAngle) <= SAMPLE_MAX_ROLL_ANGLE
                && vectorInfo.fdScore >= SAMPLE_MIN_FD_SCORE
            /*&& vectorInfo.faScore >= SAMPLE_MIN_FA_SCORE*/) {
            //添加当前Face样本
            ContentValues sampleAdd = new ContentValues();
            sampleAdd.put(DiscoverStore.FaceInfo.Columns.SAMPLES, faceInfo.samples + "," + vectorInfo.id);
            GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.FaceInfo.Media.CONTENT_URI, sampleAdd,
                    DiscoverStore.FaceInfo.Columns._ID + "=" + faceInfo.id, null);
            //更新当前Vector记录为已配对
            ContentValues vectorValues = new ContentValues();
            vectorValues.put(DiscoverStore.VectorInfo.Columns.IS_MATCHED, 1);
            vectorValues.put(DiscoverStore.VectorInfo.Columns.FACE_ID, faceInfo.id);
            GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.VectorInfo.Media.CONTENT_URI, vectorValues,
                    DiscoverStore.VectorInfo.Columns._ID + "=" + vectorInfo.id, null);
            return true;
        }
        return false;
    }

    private void setVectorAsThisFace(VectorBean vectorInfo, FaceBean faceInfo) {
        //更新当前Vector记录为已配对
        ContentValues vectorValues = new ContentValues();
        vectorValues.put(DiscoverStore.VectorInfo.Columns.IS_MATCHED, 1);
        vectorValues.put(DiscoverStore.VectorInfo.Columns.FACE_ID, faceInfo.id);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.VectorInfo.Media.CONTENT_URI, vectorValues,
                DiscoverStore.VectorInfo.Columns._ID + "=" + vectorInfo.id, null);
    }

    private List<VectorBean> getUnMatchedVectorInfo() {
        SparseIntArray existImages = new SparseIntArray();
        Cursor imageCursor = null;
        try {
            imageCursor = GalleryAppImpl.getApplication().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{
                            MediaStore.Images.ImageColumns._ID
                    }, null, null, null);

            if (imageCursor != null) {
                int key;
                while (imageCursor.moveToNext()) {
                    key = imageCursor.getInt(imageCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                    existImages.put(key, key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(imageCursor);
        }

        List<VectorBean> unMatched = new ArrayList<>();
        Cursor vectorCursor = null;
        try {
            vectorCursor = GalleryAppImpl.getApplication().getContentResolver().query(
                    DiscoverStore.VectorInfo.Media.CONTENT_URI, null,
                    DiscoverStore.VectorInfo.Columns.IS_MATCHED + "=-1", null, null);

            if (vectorCursor != null) {
                int imageId;
                while (vectorCursor.moveToNext()) {
                    imageId = vectorCursor.getInt(vectorCursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
                    if (existImages.get(imageId, -1) == -1) {
                        continue;
                    }
                    unMatched.add(new VectorBean(vectorCursor));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(vectorCursor);
        }

        return unMatched;

    }

    private SparseArray<VectorBean> getAllVectorInfo() {
        SparseArray<VectorBean> vectorBeanSparseArray = new SparseArray<>();
        Cursor vectorCursor = GalleryAppImpl.getApplication().getContentResolver().query(
                DiscoverStore.VectorInfo.Media.CONTENT_URI, null,
                null, null, null);
        if (vectorCursor != null) {
            int vecId;
            while (vectorCursor.moveToNext()) {
                vecId = vectorCursor.getInt(vectorCursor.getColumnIndex(DiscoverStore.VectorInfo.Columns._ID));
                vectorBeanSparseArray.put(vecId, new VectorBean(vectorCursor));
            }
        }
        Utils.closeSilently(vectorCursor);
        return vectorBeanSparseArray;
    }

    private List<FaceBean> getFacesInfo() {
        List<FaceBean> faceBeanList = new ArrayList<>();
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(
                DiscoverStore.FaceInfo.Media.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                faceBeanList.add(new FaceBean(cursor));
            }
        }
        Utils.closeSilently(cursor);
        return faceBeanList;
    }

    private class VectorBean {
        int id;
        int image_id;
        int x;
        int y;
        int width;
        int height;
        int yawAngle;
        int rollAngle;
        int fdScore;
        int faScore;
        byte[] feature;

        VectorBean(Cursor cursor) {
            id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns._ID));
            image_id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.IMAGE_ID));
            x = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.X));
            y = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.Y));
            width = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.WIDTH));
            height = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.HEIGHT));
            yawAngle = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.YAW_ANGLE));
            rollAngle = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.ROLL_ANGLE));
            fdScore = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.FDSCORE));
            faScore = cursor.getInt(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.FASCORE));
            feature = cursor.getBlob(cursor.getColumnIndex(DiscoverStore.VectorInfo.Columns.VECTOR));
        }
    }

    private class FaceBean {
        int id;
        String samples;

        private int sampleCount = 0;
        private float maxSimilar = 0;
        private float aveSimilar = 0;

        FaceBean(Cursor cursor) {
            id = cursor.getInt(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns._ID));
            samples = cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.SAMPLES));
        }

        void compare(VectorBean currentVec, SparseArray<VectorBean> vectorBeanSparseArray) {
            if (TextUtils.isEmpty(samples)) {
                return;
            }
            VectorBean sampleVec;
            float similar;
            float sumSimilar = 0;
            String[] vecs = samples.split(",");
            //
            sampleCount = 0;
            maxSimilar = 0;
            aveSimilar = 0;
            //
            //当前Vector与Face样本中的Vector比较相似度
            for (String vec : vecs) {
                //样本Vector
                sampleVec = vectorBeanSparseArray.get(Integer.valueOf(vec));
                if (sampleVec == null) {
                    continue;
                }
                similar = FaceDetector.getDefault().match(currentVec.feature, sampleVec.feature);
                if (maxSimilar < similar) {
                    maxSimilar = similar;
                }
                sumSimilar += similar;
                sampleCount++;
            }
            if (sampleCount > 0) {
                aveSimilar = sumSimilar / sampleCount;
            }
        }
    }

    private class ImageInfo {
        String path;
        Uri uri;
        int orientation;

        ImageInfo(int imageId, String path, int orientation) {
            this.path = path;
            this.orientation = orientation;
            this.uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(String.valueOf(imageId)).build();
        }
    }
}

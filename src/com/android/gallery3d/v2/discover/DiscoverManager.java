package com.android.gallery3d.v2.discover;

import android.net.Uri;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.discover.people.FaceDetector;
import com.android.gallery3d.v2.discover.things.AbstractClassifier;
import com.android.gallery3d.v2.discover.things.Recognition;
import com.android.gallery3d.v2.location.LocationAchieverThread;
import com.android.gallery3d.v2.location.LocationRequest;
import com.android.gallery3d.v2.location.LocationResult;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author baolin.li
 */
public class DiscoverManager implements LocationAchieverThread.OnLocationParseListener {
    private static final String TAG = DiscoverManager.class.getSimpleName();

    static final int SCENE_THINGS = 0;
    static final int SCENE_PEOPLE = 1;
    static final int SCENE_LOCATION = 2;

    private static final List<Integer> SCENES = new ArrayList<>();

    static {
        SCENES.add(SCENE_THINGS);
        SCENES.add(SCENE_PEOPLE);
        SCENES.add(SCENE_LOCATION);
    }

    private static DiscoverManager sDiscoverManager;

    private final LinkedList<ImageBean> mLinkedList = new LinkedList<>();

    List<OnThingsResultListener> mThingsListener = new ArrayList<>();
    private List<OnPeopleResultListener> mPeopleListener = new ArrayList<>();
    private List<OnLocationResultListener> mLocationListener = new ArrayList<>();

    private Task mTask;

    private AbstractClassifier mClassifier;

    boolean mEngineConnected = true;

    public interface Listener {
    }

    public interface OnThingsResultListener extends Listener {
        void onResult(int imageId, @NonNull List<Recognition> recognitionList);
    }

    public interface OnPeopleResultListener extends Listener {
        void onResult(int imageId, @NonNull FaceDetector.Face[] faces);
    }

    public interface OnLocationResultListener extends Listener {
        void onResult(int imageId, @NonNull LocationResult location);
    }

    DiscoverManager() {
        mTask = new Task();
    }

    public static DiscoverManager getDefault() {
        if (sDiscoverManager == null) {
            synchronized (DiscoverManager.class) {
                if (sDiscoverManager == null) {
                    if (GalleryUtils.isSprdPlatform()) {
                        sDiscoverManager = new SprdDiscoverManager();
                    } else {
                        sDiscoverManager = new DiscoverManager();
                    }
                    Log.e(TAG, "use " + sDiscoverManager);
                    LocationAchieverThread.getDefault().registLocationParseListener(sDiscoverManager);
                    /*开启线程*/
                    sDiscoverManager.startTask();
                }
            }
        }
        return sDiscoverManager;
    }

    public void register(int scene, @NonNull Listener listener) {
        if (!SCENES.contains(scene)) {
            throw new IllegalArgumentException("register error, no such scene " + scene);
        }
        switch (scene) {
            case SCENE_THINGS:
                mThingsListener.add((OnThingsResultListener) listener);
                break;
            case SCENE_PEOPLE:
                mPeopleListener.add((OnPeopleResultListener) listener);
                break;
            case SCENE_LOCATION:
                mLocationListener.add((OnLocationResultListener) listener);
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public void unregister(int scene, @NonNull Listener listener) {
        if (!SCENES.contains(scene)) {
            throw new IllegalArgumentException("unregister error, no such scene " + scene);
        }
        switch (scene) {
            case SCENE_THINGS:
                mThingsListener.remove(listener);
                break;
            case SCENE_PEOPLE:
                mPeopleListener.remove(listener);
                break;
            case SCENE_LOCATION:
                mLocationListener.remove(listener);
                break;
            default:
                break;
        }
    }

    public void process(int scene, int imageId, String path, int orientation, double latitude, double longitude, long date) {
        boolean needNotify = false;
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                needNotify = true;
            }
            mLinkedList.add(new ImageBean(scene, imageId, path, orientation, latitude, longitude, date));
        }
        if (needNotify) {
            mTask.onDirty();
        }
    }

    void clearProcess() {
        synchronized (mLinkedList) {
            mLinkedList.clear();
        }
        LocationAchieverThread.getDefault().clearRequest();
    }

    private void startTask() {
        mTask.start();
    }

    private ImageBean post() {
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                return null;
            } else {
                return mLinkedList.remove();
            }
        }
    }

    class ImageBean {
        int scene;
        int imageId;
        String path;
        int orientation;
        double latitude;
        double longitude;
        long date;
        Uri uri;

        ImageBean(int scene, int imageId, String path, int orientation, double latitude, double longitude, long date) {
            this.scene = scene;
            this.imageId = imageId;
            this.path = path;
            this.orientation = orientation;
            this.latitude = latitude;
            this.longitude = longitude;
            this.date = date;
            this.uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(String.valueOf(imageId)).build();
        }
    }

    void onDirty() {
        mTask.onDirty();
    }

    private AbstractClassifier getClassifier() {
        if (mClassifier == null) {
            try {
                Class c = Class.forName("com.android.gallery3d.v2.discover.things.TFLiteMnasNet");
                mClassifier = (AbstractClassifier) c.newInstance();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                Log.e(TAG, "getClassifier error", e);
            }
        }
        return mClassifier;
    }

    protected void classifyImage(ImageBean image) {
        if (getClassifier() == null) {
            return;
        }
        List<Recognition> recognitionList;
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            recognitionList = getClassifier().open().recognize(image.uri, image.orientation);
        } else {
            recognitionList = getClassifier().open()
                    .recognize(image.path, image.orientation);
        }
        for (OnThingsResultListener l : mThingsListener) {
            l.onResult(image.imageId, recognitionList);
        }
    }

    protected void closeClassifier() {
        if (mClassifier != null) {
            mClassifier.close();
        }
    }

    private class Task extends Thread {
        private ImageBean mImageBean;

        synchronized void onDirty() {
            notifyAll();
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                if (!mEngineConnected) {
                    synchronized (this) {
                        Log.d(TAG, "Task wait, wait AIEngine B, mEngineConnected = " + mEngineConnected);
                        Utils.waitWithoutInterrupt(this);
                        Log.d(TAG, "Task wait, wait AIEngine E, mEngineConnected = " + mEngineConnected);
                        continue;
                    }
                }
                mImageBean = post();
                synchronized (this) {
                    if (mImageBean == null) {
                        closeClassifier();
                        Log.d(TAG, "Task wait.");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                switch (mImageBean.scene) {
                    case SCENE_THINGS:
                        if (mThingsListener.size() > 0) {
                            classifyImage(mImageBean);
                        }
                        break;
                    case SCENE_PEOPLE:
                        if (mPeopleListener.size() > 0) {
                            FaceDetector.Face[] faces;
                            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                                faces = FaceDetector.getDefault().detectFaces(GalleryAppImpl.getApplication().getContentResolver(), mImageBean.uri, mImageBean.orientation);
                            } else {
                                faces = FaceDetector.getDefault().detectFaces(mImageBean.path, mImageBean.orientation);
                            }
                            for (OnPeopleResultListener l : mPeopleListener) {
                                l.onResult(mImageBean.imageId, faces);
                            }
                        }
                        break;
                    case SCENE_LOCATION:
                        if (mLocationListener.size() > 0) {
                            LocationAchieverThread.getDefault().addRequest(
                                    LocationRequest.genRequest(mImageBean.imageId, mImageBean.latitude, mImageBean.longitude, mImageBean.date));
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void onLocationParsed(@NonNull LocationResult result) {
        for (OnLocationResultListener l : mLocationListener) {
            l.onResult((int) result.imageId, result);
        }
    }
}

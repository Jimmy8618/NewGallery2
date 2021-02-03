package com.android.gallery3d.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.ui.PhotoView;
import com.sprd.frameworks.StandardFrameworks;

import java.util.Arrays;

public class SprdCameraUtil {

    private static final String TAG = "CameraUtils";
    private static final long INTERVAL_MAX_MS = 1100;
    private static final long INTERVAL_MIN_MS = 900;

    private static final int BLUR_SELFSHOT_NO_CONVERED = 1;
    private static final int BLUR_SELFSHOT_CONVERED = 2;
    private boolean mIsFlip = false;
    private boolean mSelectVolueUp = false;
    private boolean mSelectVolueDown = false;

    private long mPrevTime;
    private long mCurrentTime;
    private int mPrevBlurValue = 0;
    private int mCurrentBlurValue = 0;
    private boolean mFlipSucess = true;
    private android.util.Size mPreviewSize;
    private CaptureRequest.Builder mPreviewBuilder2;
    private CameraDevice mCameraDevice2;
    private SurfaceHolder mSurfaceHolder2;
    private CameraCaptureSession mSession2;
    private Context mContext;
    private Activity mActivity;
    private SurfaceView mSurfaceView;
    private Handler mHandler;
    private PhotoView mPhotoView;

    public SprdCameraUtil(Activity activity, Handler handler, PhotoView photoView) {
        mActivity = activity;
        mHandler = handler;
        mPhotoView = photoView;
    }

    private abstract class CameraResultStateCallback extends CameraCaptureSession.CaptureCallback {
        public abstract void monitorControlStates(CaptureResult result);
    }

    private CameraResultStateCallback mCameraResultStateCallback = new CameraResultStateCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            monitorControlStates(result);
        }

        @Override
        public void monitorControlStates(CaptureResult result) {
            Integer blurCoveredValue = result.get(StandardFrameworks.getInstances().getSprdCaptureResult());
            if (blurCoveredValue != null) {
                int value = blurCoveredValue;
                if (value == BLUR_SELFSHOT_NO_CONVERED) {
                    mFlipSucess = true;
                    mPrevBlurValue = value;
                    mPrevTime = SystemClock.uptimeMillis();
                } else {
                    mCurrentBlurValue = value;
                    mCurrentTime = SystemClock.uptimeMillis();
                }
                startFlipImage();
            }
        }
    };

    private CameraDevice.StateCallback mCameraDeviceStateCallback2 = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            try {
                mCameraDevice2 = camera;
                startPreviewAPI2_2(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    };

    private CameraCaptureSession.StateCallback mSessionStateCallback2 = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mSession2 = session;
            try {
                updatePreview2(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    public void setVolueKey(boolean volueUp, boolean volueDown) {
        mSelectVolueDown = volueDown;
        mSelectVolueUp = volueUp;
    }

    public void initialSurfaceView() {
        mSurfaceView = mActivity.findViewById(R.id.sv2camera);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceHolder2 = mSurfaceView.getHolder();
        mSurfaceHolder2.addCallback(new Callback() {

            @Override
            public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
                Log.d(TAG, "surfaceChanged 2");
            }

            @Override
            public void surfaceCreated(SurfaceHolder arg0) {
                Log.d(TAG, "surfaceCreated 2");
                try {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder arg0) {
            }

        });
    }

    public void startFlipImage() {
        long intervalTime = mCurrentTime - mPrevTime;
        if (intervalTime > INTERVAL_MIN_MS && intervalTime < INTERVAL_MAX_MS
                && mCurrentBlurValue == BLUR_SELFSHOT_CONVERED
                && mPrevBlurValue == BLUR_SELFSHOT_NO_CONVERED && mFlipSucess) {
            mFlipSucess = false;
            Log.i(TAG, " startFlipImage");
            if (mSelectVolueUp) {
                mPhotoView.prevImage();
            } else if (mSelectVolueDown) {
                mPhotoView.nextImage();
            } else {
                mPhotoView.nextImage();
            }
        }
    }

    public void openCamera() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    openCamera2andrStartPreviewAPI2();
                } catch (Exception e) {
                    Log.e(TAG, "open camera 0 failed");
                }
            }
        };
        t.start();
    }

    private void openCamera2andrStartPreviewAPI2() {
        CameraManager cameraManager = (CameraManager) mActivity
                .getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("1");
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            cameraManager.openCamera("14", mCameraDeviceStateCallback2, mHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startPreviewAPI2_2(CameraDevice camera) throws CameraAccessException {
        Surface surface = mSurfaceHolder2.getSurface();
        try {
            mPreviewBuilder2 = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewBuilder2.addTarget(surface);
        camera.createCaptureSession(Arrays.asList(surface), mSessionStateCallback2, mHandler);
    }

    private void updatePreview2(CameraCaptureSession session) throws CameraAccessException {
        session.setRepeatingRequest(mPreviewBuilder2.build(), mCameraResultStateCallback, mHandler);
    }

    public void closeCameraAPI2_2() {
        if (mSurfaceView != null) {
            mSurfaceView.setVisibility(View.GONE);
        }
        if (mCameraDevice2 == null) {
            Log.e(TAG, "mCameraDevice2 = null");
            return;
        }
        if (mSession2 == null) {
            Log.e(TAG, "mSession2 = null");
            return;
        }
        try {
            mSession2.abortCaptures();
            mSession2 = null;
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Failed to close existing camera capture session", ex);
        } catch (Exception ex) {
            Log.e(TAG, "close exception", ex);
        }
        mCameraDevice2.close();
        mCameraDevice2 = null;
    }

    public void showSurfaceView() {
        if (mSurfaceView != null) {
            mSurfaceView.setVisibility(View.VISIBLE);
        }
    }
}

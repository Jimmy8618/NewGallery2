/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.CanvasAnimation;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.GLES11Canvas;
import com.android.gallery3d.glrenderer.GLES20Canvas;
import com.android.gallery3d.glrenderer.UploadedTexture;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MotionEventHelper;
import com.android.gallery3d.util.Profile;
import com.android.gallery3d.v2.data.GLVideoStateMsg;
import com.android.gallery3d.v2.media.SimplePlayer;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

// The root component of all <code>GLView</code>s. The rendering is done in GL
// thread while the event handling is done in the main thread.  To synchronize
// the two threads, the entry points of this package need to synchronize on the
// <code>GLRootView</code> instance unless it can be proved that the rendering
// thread won't access the same thing as the method. The entry points include:
// (1) The public methods of HeadUpDisplay
// (2) The public methods of CameraHeadUpDisplay
// (3) The overridden methods in GLRootView.
public class GLRootView extends GLSurfaceView
        implements GLSurfaceView.Renderer, GLRoot,
        SurfaceTextureScreenNail.OnGLVideoFrameAvailableListener {
    private static final String TAG = "GLRootView";

    //播放停止后, 延时一段时间再刷一次界面
    private static final long GL_VIDEO_RENDER_DELAY = 300;

    private static final boolean DEBUG_FPS = false;
    private int mFrameCount = 0;
    private long mFrameCountingStart = 0;

    private static final boolean DEBUG_INVALIDATE = false;
    private int mInvalidateColor = 0;

    private static final boolean DEBUG_DRAWING_STAT = false;

    private static final boolean DEBUG_PROFILE = false;
    private static final boolean DEBUG_PROFILE_SLOW_ONLY = false;

    private static final int FLAG_INITIALIZED = 1;
    private static final int FLAG_NEED_LAYOUT = 2;

    private GL11 mGL;
    private GLCanvas mCanvas;
    private GLView mContentView;

    private OrientationSource mOrientationSource;
    // mCompensation is the difference between the UI orientation on GLCanvas
    // and the framework orientation. See OrientationManager for details.
    private int mCompensation;
    // mCompensationMatrix maps the coordinates of touch events. It is kept sync
    // with mCompensation.
    private Matrix mCompensationMatrix = new Matrix();
    private int mDisplayRotation;

    private int mFlags = FLAG_NEED_LAYOUT;
    private volatile boolean mRenderRequested = false;

    private final ArrayList<CanvasAnimation> mAnimations =
            new ArrayList<CanvasAnimation>();

    private final ArrayDeque<OnGLIdleListener> mIdleListeners =
            new ArrayDeque<OnGLIdleListener>();

    private final IdleRunner mIdleRunner = new IdleRunner();

    private final ReentrantLock mRenderLock = new ReentrantLock();
    private final Condition mFreezeCondition =
            mRenderLock.newCondition();
    private boolean mFreeze;

    private long mLastDrawFinishTime;
    private boolean mInDownState = false;
    private boolean mFirstDraw = true;

    private static final long DRAW_FRAME_TRY_LOCK_TIME_OUT = 600L;
    private static final long TRY_LOCK_TIME_OUT = 100L;

    //New feature add for Bug 1033745, add for playing video in GLSurfaceView @{
    //渲染视频的ScreenNail
    private SurfaceTextureScreenNail mGLVideoScreenNail;
    //播放器
    private SimplePlayer mGLVideoPlayer;

    //视频播放状态 GL_VIDEO_STATE_*
    //视频没有播放
    private static final int GL_VIDEO_STATE_IDLE = 0;
    //视频正在播放
    private static final int GL_VIDEO_STATE_PLAYING = 1;
    //视频播放状态
    private int mGLVideoState = GL_VIDEO_STATE_IDLE;
    //当前播放的文件Path
    private Path mGLVideoPath;
    // @}

    public GLRootView(Context context) {
        this(context, null);
    }

    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFlags |= FLAG_INITIALIZED;
        setBackgroundDrawable(null);
        setEGLContextClientVersion(ApiHelper.HAS_GLES20_REQUIRED ? 2 : 1);
        if (ApiHelper.USE_888_PIXEL_FORMAT) {
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        } else {
            setEGLConfigChooser(5, 6, 5, 0, 0, 0);
        }
        setRenderer(this);
        if (ApiHelper.USE_888_PIXEL_FORMAT) {
            getHolder().setFormat(PixelFormat.RGB_888);
        } else {
            getHolder().setFormat(PixelFormat.RGB_565);
        }

        // Uncomment this to enable gl error check.
        // setDebugFlags(DEBUG_CHECK_GL_ERROR);
        //创建渲染视频的ScreenNail
        mGLVideoScreenNail = new SurfaceTextureScreenNail(this);
    }

    @Override
    public void registerLaunchedAnimation(CanvasAnimation animation) {
        // Register the newly launched animation so that we can set the start
        // time more precisely. (Usually, it takes much longer for first
        // rendering, so we set the animation start time as the time we
        // complete rendering)
        mAnimations.add(animation);
    }

    @Override
    public void addOnGLIdleListener(OnGLIdleListener listener) {
        synchronized (mIdleListeners) {
            mIdleListeners.addLast(listener);
            mIdleRunner.enable();
        }
    }

    @Override
    public void setContentPane(GLView content) {
        if (mContentView == content) {
            return;
        }
        if (mContentView != null) {
            if (mInDownState) {
                long now = SystemClock.uptimeMillis();
                MotionEvent cancelEvent = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                mContentView.dispatchTouchEvent(cancelEvent);
                cancelEvent.recycle();
                mInDownState = false;
            }
            mContentView.detachFromRoot();
            BasicTexture.yieldAllTextures();
        }
        mContentView = content;
        if (content != null) {
            content.attachToRoot(this);
            requestLayoutContentPane();
        }
    }

    @Override
    public void requestRenderForced() {
        superRequestRender();
    }

    @Override
    public void requestRender() {
        if (DEBUG_INVALIDATE) {
            StackTraceElement e = Thread.currentThread().getStackTrace()[4];
            String caller = e.getFileName() + ":" + e.getLineNumber() + " ";
            Log.d(TAG, "invalidate: " + caller);
        }
        if (mRenderRequested) {
            return;
        }
        mRenderRequested = true;
        if (ApiHelper.HAS_POST_ON_ANIMATION) {
            postOnAnimation(mRequestRenderOnAnimationFrame);
        } else {
            super.requestRender();
        }
    }

    private Runnable mRequestRenderOnAnimationFrame = new Runnable() {
        @Override
        public void run() {
            superRequestRender();
        }
    };

    private void superRequestRender() {
        super.requestRender();
    }

    @Override
    public void requestLayoutContentPane() {
        mRenderLock.lock();
        try {
            if (mContentView == null || (mFlags & FLAG_NEED_LAYOUT) != 0) {
                return;
            }

            // "View" system will invoke onLayout() for initialization(bug ?), we
            // have to ignore it since the GLThread is not ready yet.
            if ((mFlags & FLAG_INITIALIZED) == 0) {
                return;
            }

            mFlags |= FLAG_NEED_LAYOUT;
            requestRender();
        } finally {
            mRenderLock.unlock();
        }
    }

    private void layoutContentPane() {
        mFlags &= ~FLAG_NEED_LAYOUT;

        int w = getWidth();
        int h = getHeight();
        int displayRotation = 0;
        int compensation = 0;

        // Get the new orientation values
        if (mOrientationSource != null) {
            displayRotation = mOrientationSource.getDisplayRotation();
            compensation = mOrientationSource.getCompensation();
        } else {
            displayRotation = 0;
            compensation = 0;
        }

        if (mCompensation != compensation) {
            mCompensation = compensation;
            if (mCompensation % 180 != 0) {
                mCompensationMatrix.setRotate(mCompensation);
                // move center to origin before rotation
                mCompensationMatrix.preTranslate(-w / 2, -h / 2);
                // align with the new origin after rotation
                mCompensationMatrix.postTranslate(h / 2, w / 2);
            } else {
                mCompensationMatrix.setRotate(mCompensation, w / 2, h / 2);
            }
        }
        mDisplayRotation = displayRotation;

        // Do the actual layout.
        if (mCompensation % 180 != 0) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        Log.i(TAG, "layout content pane " + w + "x" + h
                + " (compensation " + mCompensation + ")");
        if (mContentView != null && w != 0 && h != 0) {
            mContentView.layout(0, 0, w, h);
        }
        // Uncomment this to dump the view hierarchy.
        //mContentView.dumpTree("");
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            requestLayoutContentPane();
        }
    }

    /**
     * Called when the context is created, possibly after automatic destruction.
     */
    // This is a GLSurfaceView.Renderer callback
    @Override
    public void onSurfaceCreated(GL10 gl1, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");
        //当Surface创建,或重新创建时, gl 上下文会发生变化, 因此,需要停止播放视频
        stopGLVideo();
        //释放原先的Texture
        mGLVideoScreenNail.releaseSurfaceTexture();

        GL11 gl = (GL11) gl1;
        if (mGL != null) {
            // The GL Object has changed
            Log.i(TAG, "GLObject has changed from " + mGL + " to " + gl);
        }
        mRenderLock.lock();
        try {
            mGL = gl;
            mCanvas = ApiHelper.HAS_GLES20_REQUIRED ? new GLES20Canvas() : new GLES11Canvas(gl);
            //重新创建Texture
            mGLVideoScreenNail.acquireSurfaceTexture(mCanvas);
            BasicTexture.invalidateAllTextures();
        } finally {
            mRenderLock.unlock();
        }

        if (DEBUG_FPS || DEBUG_PROFILE) {
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        } else {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    /**
     * Called when the OpenGL surface is recreated without destroying the
     * context.
     */
    // This is a GLSurfaceView.Renderer callback
    @Override
    public void onSurfaceChanged(GL10 gl1, int width, int height) {
        Log.i(TAG, "onSurfaceChanged: " + width + "x" + height
                + ", gl10: " + gl1.toString());
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        GalleryUtils.setRenderThread();
        if (DEBUG_PROFILE) {
            Log.d(TAG, "Start profiling");
            Profile.enable(20);  // take a sample every 20ms
        }
        GL11 gl = (GL11) gl1;
        Utils.assertTrue(mGL == gl);

        mCanvas.setSize(width, height);
    }

    private void outputFps() {
        long now = System.nanoTime();
        if (mFrameCountingStart == 0) {
            mFrameCountingStart = now;
        } else if ((now - mFrameCountingStart) > 1000000000) {
            Log.d(TAG, "fps: " + (double) mFrameCount
                    * 1000000000 / (now - mFrameCountingStart));
            mFrameCountingStart = now;
            mFrameCount = 0;
        }
        ++mFrameCount;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        AnimationTime.update();
        long t0;
        if (DEBUG_PROFILE_SLOW_ONLY) {
            Profile.hold();
            t0 = System.nanoTime();
        }

        //避免ANR, 尝试lock
        boolean acquired = tryLock(DRAW_FRAME_TRY_LOCK_TIME_OUT);
        if (!acquired) {
            Log.w(TAG, "onDrawFrame: lock is held by another thread");
            return;
        }

        while (mFreeze) {
            try {
                mFreezeCondition.awaitUninterruptibly();
            } catch (Exception e) {
                Log.w(TAG, "onDrawFrame awaitUninterruptibly exception: ", e);
            }
        }

        try {
            onDrawFrameLocked(gl);
        } finally {
            if (acquired) {
                mRenderLock.unlock();
            }
        }

        // We put a black cover View in front of the SurfaceView and hide it
        // after the first draw. This prevents the SurfaceView being transparent
        // before the first draw.
        if (mFirstDraw) {
            mFirstDraw = false;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    View root = getRootView();
                    View cover = root.findViewById(R.id.gl_root_cover);
                    cover.setVisibility(GONE);
                }
            }, getResources().getInteger(R.integer.gl_cover_hide_duration));
        }

        if (DEBUG_PROFILE_SLOW_ONLY) {
            long t = System.nanoTime();
            long durationInMs = (t - mLastDrawFinishTime) / 1000000;
            long durationDrawInMs = (t - t0) / 1000000;
            mLastDrawFinishTime = t;

            if (durationInMs > 34) {  // 34ms -> we skipped at least 2 frames
                Log.v(TAG, "----- SLOW (" + durationDrawInMs + "/" +
                        durationInMs + ") -----");
                Profile.commit();
            } else {
                Profile.drop();
            }
        }
    }

    private void onDrawFrameLocked(GL10 gl) {
        if (DEBUG_FPS) {
            outputFps();
        }

        // release the unbound textures and deleted buffers.
        mCanvas.deleteRecycledResources();

        // reset texture upload limit
        UploadedTexture.resetUploadLimit();

        mRenderRequested = false;

        synchronized (this) {
            if ((mOrientationSource != null
                    && mDisplayRotation != mOrientationSource.getDisplayRotation())
                    || (mFlags & FLAG_NEED_LAYOUT) != 0) {
                layoutContentPane();
            }
        }

        mCanvas.save(GLCanvas.SAVE_FLAG_ALL);
        rotateCanvas(-mCompensation);
        if (mContentView != null) {
            mContentView.render(mCanvas);
        } else {
            // Make sure we always draw something to prevent displaying garbage
            mCanvas.clearBuffer();
        }
        mCanvas.restore();

        if (!mAnimations.isEmpty()) {
            long now = AnimationTime.get();
            for (int i = 0, n = mAnimations.size(); i < n; i++) {
                mAnimations.get(i).setStartTime(now);
            }
            mAnimations.clear();
        }

        if (UploadedTexture.uploadLimitReached()) {
            requestRender();
        }

        synchronized (mIdleListeners) {
            if (!mIdleListeners.isEmpty()) {
                mIdleRunner.enable();
            }
        }

        if (DEBUG_INVALIDATE) {
            mCanvas.fillRect(10, 10, 5, 5, mInvalidateColor);
            mInvalidateColor = ~mInvalidateColor;
        }

        if (DEBUG_DRAWING_STAT) {
            mCanvas.dumpStatisticsAndClear();
        }
    }

    private void rotateCanvas(int degrees) {
        if (degrees == 0) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        mCanvas.translate(cx, cy);
        mCanvas.rotate(degrees, 0, 0, 1);
        if (degrees % 180 != 0) {
            mCanvas.translate(-cy, -cx);
        } else {
            mCanvas.translate(-cx, -cy);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_UP) {
            mInDownState = false;
        } else if (!mInDownState && action != MotionEvent.ACTION_DOWN) {
            return false;
        }

        if (mCompensation != 0) {
            event = MotionEventHelper.transformEvent(event, mCompensationMatrix);
        }

        /* SPRD: Modify for bug555555, ANR seldom happen if mRenderLock is locked by another thread @{ */
        if (GalleryUtils.isMonkey()) {
            Log.d(TAG, "<dispatchTouchEvent> mRenderLock isLocked:" + mRenderLock.isLocked() + " isHeldByCurrentThread:"
                    + mRenderLock.isHeldByCurrentThread());
        }
        boolean acquired = tryLock(TRY_LOCK_TIME_OUT);

        if (!acquired) {
            Log.w(TAG, "<dispatchTouchEvent> lock is held by another thread");
            return false;
        }
        /* @} */
        try {
            // If this has been detached from root, we don't need to handle event
            boolean handled = mContentView != null
                    && mContentView.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_DOWN && handled) {
                mInDownState = true;
            }
            return handled;
        } finally {
            mRenderLock.unlock();
        }
    }

    private class IdleRunner implements Runnable {
        // true if the idle runner is in the queue
        private boolean mActive = false;

        @Override
        public void run() {
            OnGLIdleListener listener;
            synchronized (mIdleListeners) {
                mActive = false;
                if (mIdleListeners.isEmpty()) {
                    return;
                }
                listener = mIdleListeners.removeFirst();
            }

            //避免ANR, 尝试lock
            boolean acquired = tryLock(TRY_LOCK_TIME_OUT);

            boolean keepInQueue = false;
            try {
                if (mCanvas != null) {
                    keepInQueue = listener.onGLIdle(mCanvas, mRenderRequested);
                }
            } finally {
                if (acquired) {
                    mRenderLock.unlock();
                }
            }

            synchronized (mIdleListeners) {
                if (keepInQueue) {
                    mIdleListeners.addLast(listener);
                }
                if (!mRenderRequested && !mIdleListeners.isEmpty()) {
                    enable();
                }
            }
        }

        public void enable() {
            // Who gets the flag can add it to the queue
            if (mActive) {
                return;
            }
            mActive = true;
            queueEvent(this);
        }
    }

    @Override
    public void lockRenderThread() {
        mRenderLock.lock();
    }

    @Override
    public void unlockRenderThread() {
        mRenderLock.unlock();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        unfreeze();
        super.onPause();
        if (DEBUG_PROFILE) {
            Log.d(TAG, "Stop profiling");
            Profile.disableAll();
            Profile.dumpToFile("/sdcard/gallery.prof");
            Profile.reset();
        }
    }

    @Override
    public void setOrientationSource(OrientationSource source) {
        synchronized (this) {
            mOrientationSource = source;
        }
    }

    @Override
    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    @Override
    public int getCompensation() {
        return mCompensation;
    }

    @Override
    public Matrix getCompensationMatrix() {
        return mCompensationMatrix;
    }

    @Override
    public void freeze() {
        mRenderLock.lock();
        mFreeze = true;
        mRenderLock.unlock();
    }

    @Override
    public void unfreeze() {
        mRenderLock.lock();
        mFreeze = false;
        mFreezeCondition.signalAll();
        mRenderLock.unlock();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void setLightsOutMode(boolean enabled) {
        if (!ApiHelper.HAS_SET_SYSTEM_UI_VISIBILITY) {
            return;
        }

        int flags = 0;
        if (enabled) {
            flags = STATUS_BAR_HIDDEN;
            if (ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) {
                flags |= (SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
        setSystemUiVisibility(flags);
    }

    // We need to unfreeze in the following methods and in onPause().
    // These methods will wait on GLThread. If we have freezed the GLRootView,
    // the GLThread will wait on main thread to call unfreeze and cause dead
    // lock.
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        unfreeze();
        super.surfaceChanged(holder, format, w, h);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        unfreeze();
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        unfreeze();
        super.surfaceDestroyed(holder);
    }

    /* SPRD: Add for bug606797, need call unfreeze() when call surfaceRedrawNeeded() @{ */
    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        unfreeze();
        super.surfaceRedrawNeeded(holder);
    }
    /* @} */

    @Override
    protected void onDetachedFromWindow() {
        unfreeze();
        super.onDetachedFromWindow();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            unfreeze();
        } finally {
            super.finalize();
        }
    }

    // SPRD: Modify 20160114 for bug522691, add to debug for GLThread @{
    @Override
    public boolean isFreezed() {
        Log.d(TAG, "<isFreezed> thread name:" + Thread.currentThread().getName() + ", mFreeze = " + mFreeze);
        return mFreeze;
    }
    // @}

    @Override
    public void setGLVisibility(int visibility) {
        setVisibility(visibility);
    }

    private boolean tryLock(long timeout) {
        boolean acquired;
        //long t = System.currentTimeMillis();
        if (mRenderLock.isLocked()) {
            try {
                acquired = mRenderLock.tryLock(timeout, TimeUnit.MILLISECONDS);
                //if (timeout == DRAW_FRAME_TRY_LOCK_TIME_OUT) {
                //    Log.d(TAG, "tryLock cost " + (System.currentTimeMillis() - t) + " ms, acquired = " + acquired);
                //}
            } catch (InterruptedException e) {
                Log.e(TAG, "tryLock failed, " + e.toString());
                acquired = false;
            }
        } else {
            mRenderLock.lock();
            acquired = true;
        }
        return acquired;
    }

    //当视频播放时, 有视频帧时, 回调此方法
    @Override
    public void onGLVideoFrameAvailable(ScreenNail s) {
        //Log.d(TAG, "onGLVideoFrameAvailable");
        //刷新界面
        requestRender();
    }

    //在图片浏览界面, 调用此方法绘制视频帧
    @Override
    public synchronized void renderGLVideo(GLCanvas canvas, int x, int y, int width, int height) {
        //Log.d(TAG, "renderGLVideo (" + x + ", " + y + ", " + width + ", " + height + ")");
        //有视频帧数据时, 绘制界面
        mGLVideoScreenNail.draw(canvas, x, y, width, height);
    }

    //播放视频
    @Override
    public synchronized void playGLVideo(MediaItem item) {
        //参数为空时, 若正在播放视频, 停止播放
        if (item == null) {
            if (mGLVideoState != GL_VIDEO_STATE_IDLE && mGLVideoPlayer != null) {
                //mGLVideoPlayer.reset();
                mGLVideoPlayer.stop();
                setGLVideoState(GL_VIDEO_STATE_IDLE);
            }
            mGLVideoPath = null;
            return;
        }

        //初始化播放器,若初始化失败,则返回
        if (!initGLVideoPlayer()) {
            return;
        }

        //如果正在播放, 而且将要播放的是同一个视频, 则返回
        if (mGLVideoState == GL_VIDEO_STATE_PLAYING
                && isSameGLVideo(item.getPath())) {
            return;
        }

        //以下, 开始播放视频
        try {
            //FileInputStream fis = new FileInputStream(item.getFilePath());
            //long offset = fis.available() - item.getMotionVideoLength();
            //long length = item.getMotionVideoLength();

            //mGLVideoPlayer.reset();
            mGLVideoPlayer.stop();
            //mGLVideoPlayer.setDataSource(fis.getFD(), offset, length);
            mGLVideoPlayer.setDataSource(item);
            mGLVideoPlayer.prepare();
            mGLVideoPlayer.start();
            mGLVideoPath = item.getPath();
            Log.d(TAG, "playGLVideo " + mGLVideoPath);
            setGLVideoState(GL_VIDEO_STATE_PLAYING);
        } catch (Exception e) {
            setGLVideoState(GL_VIDEO_STATE_IDLE);
            Log.e(TAG, "playGLVideo error!", e);
        }
    }

    //停止播放视频
    @Override
    public synchronized void stopGLVideo() {
        if (mGLVideoPlayer != null) {
            mGLVideoPlayer.stop();
            //mGLVideoPlayer.reset();
            //mGLVideoPlayer.release();
            mGLVideoPlayer = null;
            mGLVideoPath = null;
            Log.d(TAG, "stopGLVideo");
        }
        setGLVideoState(GL_VIDEO_STATE_IDLE);
    }

    //判断是否在播放视频
    @Override
    public synchronized boolean isGLVideoPlaying() {
        return mGLVideoState == GL_VIDEO_STATE_PLAYING;
    }

    //判断是否是同一个视频
    private boolean isSameGLVideo(Path path) {
        return mGLVideoPath != null && mGLVideoPath.equals(path);
    }

    //初始化播放器
    private boolean initGLVideoPlayer() {
        if (mGLVideoPlayer != null) {
            return true;
        }
        //获取需要绑定的Surface,若返回空, 则初始化失败
        Surface surface = mGLVideoScreenNail.getSurface();
        if (surface == null) {
            Log.e(TAG, "initGLVideoPlayer failed, surface is null.");
            return false;
        }
        //创建播放器
        mGLVideoPlayer = new SimplePlayer();
        mGLVideoPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //设置Surface
        mGLVideoPlayer.setSurface(surface);
        mGLVideoPlayer.setOnErrorListener(new SimplePlayer.OnErrorListener() {
            @Override
            public boolean onError(SimplePlayer mp, int what, int extra) {
                Log.e(TAG, "play GLVideo error, what = " + what + ", extra = " + extra);
                setGLVideoState(GL_VIDEO_STATE_IDLE);
                requestRender();
                return false;
            }
        });
        mGLVideoPlayer.setOnCompletionListener(new SimplePlayer.OnCompletionListener() {
            @Override
            public void onCompletion(SimplePlayer mp) {
                Log.d(TAG, "play GLVideo complete.");
                setGLVideoState(GL_VIDEO_STATE_IDLE);
                requestRender();
            }
        });
        mGLVideoPlayer.setOnVideoSizeChangedListener(new SimplePlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(SimplePlayer mp, int width, int height) {
                Log.d(TAG, "play GLVideo size changed, " + width + "x" + height);
            }
        });
        setGLVideoState(GL_VIDEO_STATE_IDLE);
        return true;
    }

    //设置播放器状态
    private void setGLVideoState(int state) {
        if (mGLVideoState == state) {
            return;
        }
        Log.d(TAG, "GLVideo state changed from " + stateString(mGLVideoState) + " to " + stateString(state));
        mGLVideoState = state;

        // 播放停止后, 再刷一次, 显示图片缩略图, 否则界面会显示视频最后一帧
        if (mGLVideoState == GL_VIDEO_STATE_IDLE) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestRender();
                }
            }, GL_VIDEO_RENDER_DELAY);
        }
        EventBus.getDefault().post(new GLVideoStateMsg());
    }

    private String stateString(int state) {
        return state == GL_VIDEO_STATE_PLAYING ? "STATE_PLAYING" : "STATE_IDLE";
    }
}

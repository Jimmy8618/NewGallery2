/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.util.Log;
import android.view.Surface;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.glrenderer.ExtTexture;
import com.android.gallery3d.glrenderer.GLCanvas;

//Modify for Bug 1033745, playing video in GLSurfaceView feature
public class SurfaceTextureScreenNail implements ScreenNail, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = SurfaceTextureScreenNail.class.getSimpleName();

    //0x8D65
    private static final int GL_TEXTURE_EXTERNAL_OES = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

    private ExtTexture mExtTexture;
    private SurfaceTexture mSurfaceTexture;
    private int mWidth;
    private int mHeight;
    private float[] mTransform = new float[16];
    private boolean mHasTexture = false;
    private boolean mAcquired;
    private OnGLVideoFrameAvailableListener mOnGLVideoFrameAvailableListener;

    public interface OnGLVideoFrameAvailableListener {
        void onGLVideoFrameAvailable(ScreenNail s);
    }

    public SurfaceTextureScreenNail(OnGLVideoFrameAvailableListener l) {
        mAcquired = false;
        mOnGLVideoFrameAvailableListener = l;
    }

    //创建SurfaceTexture, 与opengl绑定
    public void acquireSurfaceTexture(GLCanvas canvas) {
        if (mAcquired) {
            return;
        }
        Log.d(TAG, "acquireSurfaceTexture canvas = " + canvas);
        mExtTexture = new ExtTexture(canvas, GL_TEXTURE_EXTERNAL_OES);
        mExtTexture.setSize(mWidth, mHeight);
        mSurfaceTexture = new SurfaceTexture(mExtTexture.getId());
        setDefaultBufferSize(mSurfaceTexture, mWidth, mHeight);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mAcquired = true;
    }

    //释放SurfaceTexture
    public void releaseSurfaceTexture() {
        if (!mAcquired) {
            return;
        }
        Log.d(TAG, "releaseSurfaceTexture");
        synchronized (this) {
            mHasTexture = false;
        }
        mExtTexture.recycle();
        mExtTexture = null;
        releaseSurfaceTexture(mSurfaceTexture);
        mSurfaceTexture = null;
        mAcquired = false;
    }

    public Surface getSurface() {
        if (mSurfaceTexture == null) {
            return null;
        }
        return new Surface(mSurfaceTexture);
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
        resizeTexture();
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        synchronized (this) {
            //若没有可渲染的帧数据, 则直接返回
            if (!mHasTexture) {
                return;
            }
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTransform);
            mHasTexture = false;

            // Flip vertically.
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            int cx = x + width / 2;
            int cy = y + height / 2;
            canvas.translate(cx, cy);
            canvas.scale(1, -1, 1);
            canvas.translate(-cx, -cy);
            updateTransformMatrix(mTransform);
            canvas.drawTexture(mExtTexture, mTransform, x, y, width, height);
            canvas.restore();
        }
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF dest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void noDraw() {
    }

    @Override
    public void recycle() {
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            mHasTexture = true;
        }
        if (mOnGLVideoFrameAvailableListener != null) {
            mOnGLVideoFrameAvailableListener.onGLVideoFrameAvailable(this);
        }
    }

    private void setDefaultBufferSize(SurfaceTexture st, int width, int height) {
        if (ApiHelper.HAS_SET_DEFALT_BUFFER_SIZE) {
            st.setDefaultBufferSize(width, height);
        }
    }

    private void releaseSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(null);
        if (ApiHelper.HAS_RELEASE_SURFACE_TEXTURE) {
            st.release();
        }
    }

    private void resizeTexture() {
        if (mExtTexture != null) {
            mExtTexture.setSize(mWidth, mHeight);
            setDefaultBufferSize(mSurfaceTexture, mWidth, mHeight);
        }
    }

    private void updateTransformMatrix(float[] matrix) {
        //Log.d(TAG, "updateTransformMatrix " + Arrays.toString(matrix));
    }
}
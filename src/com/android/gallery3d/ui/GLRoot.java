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

import android.content.Context;
import android.graphics.Matrix;

import com.android.gallery3d.anim.CanvasAnimation;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.glrenderer.GLCanvas;

public interface GLRoot {

    // Listener will be called when GL is idle AND before each frame.
    // Mainly used for uploading textures.
    interface OnGLIdleListener {
        boolean onGLIdle(
                GLCanvas canvas, boolean renderRequested);
    }

    void addOnGLIdleListener(OnGLIdleListener listener);

    void registerLaunchedAnimation(CanvasAnimation animation);

    void requestRenderForced();

    void requestRender();

    void requestLayoutContentPane();

    void lockRenderThread();

    void unlockRenderThread();

    void setContentPane(GLView content);

    void setOrientationSource(OrientationSource source);

    int getDisplayRotation();

    int getCompensation();

    Matrix getCompensationMatrix();

    void freeze();

    void unfreeze();

    void setLightsOutMode(boolean enabled);

    Context getContext();

    // SPRD: Modify 20160114 for bug522691, add to debug for GLThread @{
    boolean isFreezed();

    // @}
    void setGLVisibility(int visibility);

    // SPRD: New feature add for Bug 1033745, add for playing video in GLSurfaceView @{
    // 渲染视频
    void renderGLVideo(GLCanvas canvas, int x, int y, int width, int height);

    //播放视频, 若参数为null, 则停止播放
    void playGLVideo(MediaItem item);

    //停止播放视频
    void stopGLVideo();

    //判断是否在播放视频
    boolean isGLVideoPlaying();
    // @}
}

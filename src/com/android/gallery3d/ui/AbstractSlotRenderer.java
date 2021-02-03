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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;

import com.android.gallery3d.R;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeOutTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.StringTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.sprd.gallery3d.drm.SlotRendererUtils;

public abstract class AbstractSlotRenderer implements SlotView.SlotRenderer {

    private final ResourceTexture mVideoOverlay;
    private final ResourceTexture mVideoPlayIcon;
    private final ResourceTexture mPanoramaIcon;
    private final NinePatchTexture mFramePressed;
    private final NinePatchTexture mFrameSelected;
    private FadeOutTexture mFramePressedUp;

    /* SPRD: Drm feature start @{ */
    public final ResourceTexture mDRMLockedIcon;
    public final ResourceTexture mDRMUnlockedIcon;
    /* SPRD: Drm feature end @} */

    private final ResourceTexture mVideoIndicatorTexture;
    private final ResourceTexture mSelectedTexture;
    private final ResourceTexture mUnSelectedTexture;
    private final ResourceTexture mUnSelectedAlbumTexture;
    private final ResourceTexture mGifTexture;
    private final ResourceTexture mRefocusTexture;
    private final ResourceTexture mBurstOverlay;
    private final ColorTexture mSelectedBackgroundTexture;

    protected AbstractSlotRenderer(Context context) {
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_gallery_play);
        mPanoramaIcon = new ResourceTexture(context, R.drawable.ic_360pano_holo_light);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFrameSelected = new NinePatchTexture(context, R.drawable.grid_selected);
        /* SPRD: Drm feature start @{ */
        mDRMLockedIcon = SlotRendererUtils.getInstance().createDrmStatusOverlay(true, context);
        mDRMUnlockedIcon = SlotRendererUtils.getInstance().createDrmStatusOverlay(false, context);
        /* SPRD: Drm feature end @} */

        mVideoIndicatorTexture = new ResourceTexture(context, R.drawable.ic_newui_indicator_video);
        mUnSelectedTexture = new ResourceTexture(context, R.drawable.ic_newui_unselected);
        mSelectedTexture = new ResourceTexture(context, R.drawable.ic_newui_selected);
        mUnSelectedAlbumTexture = new ResourceTexture(context, R.drawable.ic_newui_unselected_album);
        mGifTexture = new ResourceTexture(context, R.drawable.ic_newui_indicator_gif);
        mRefocusTexture = new ResourceTexture(context, R.drawable.ic_newui_indicator_refocus);
        mBurstOverlay = new ResourceTexture(context, R.drawable.ic_newui_indicator_burst);
        int placeHolderColor = context.getResources().getColor(R.color.albumset_placeholder);
        mSelectedBackgroundTexture = new ColorTexture(placeHolderColor);
    }

    protected void drawContent(GLCanvas canvas,
                               Texture content, int width, int height, int rotation) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);

        // The content is always rendered in to the largest square that fits
        // inside the slot, aligned to the top of the slot.
        width = height = Math.min(width, height);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }

        // Fit the content into the box
        float scale = Math.min(
                (float) width / content.getWidth(),
                (float) height / content.getHeight());
        canvas.scale(scale, scale, 1);
        content.draw(canvas, 0, 0);

        canvas.restore();
    }

    protected void drawVideoOverlay(GLCanvas canvas, int width, int height) {
        // Scale the video overlay to the height of the thumbnail and put it
        // on the left side.
        ResourceTexture v = mVideoOverlay;
        float scale = (float) height / v.getHeight();
        int w = Math.round(scale * v.getWidth());
        int h = Math.round(scale * v.getHeight());
        v.draw(canvas, 0, 0, w, h);

        int s = Math.min(width, height) / 6;
        mVideoPlayIcon.draw(canvas, (width - s) / 2, (height - s) / 2, s, s);
    }

    protected void drawPanoramaIcon(GLCanvas canvas, int width, int height) {
        int iconSize = Math.min(width, height) / 6;
        mPanoramaIcon.draw(canvas, (width - iconSize) / 2, (height - iconSize) / 2,
                iconSize, iconSize);
    }

    protected boolean isPressedUpFrameFinished() {
        if (mFramePressedUp != null) {
            if (mFramePressedUp.isAnimating()) {
                return false;
            } else {
                mFramePressedUp = null;
            }
        }
        return true;
    }

    protected void drawPressedUpFrame(GLCanvas canvas, int width, int height) {
        if (mFramePressedUp == null) {
            mFramePressedUp = new FadeOutTexture(mFramePressed);
        }
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressedUp, 0, 0, width, height);
    }

    protected void drawPressedFrame(GLCanvas canvas, int width, int height) {
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressed, 0, 0, width, height);
    }

    protected void drawSelectedFrame(GLCanvas canvas, int width, int height) {
        drawFrame(canvas, mFrameSelected.getPaddings(), mFrameSelected, 0, 0, width, height);
    }

    protected static void drawFrame(GLCanvas canvas, Rect padding, Texture frame,
                                    int x, int y, int width, int height) {
        frame.draw(canvas, x - padding.left, y - padding.top, width + padding.left + padding.right,
                height + padding.top + padding.bottom);
    }

    protected void drawSelectionState(GLCanvas canvas, boolean selected, int width, int height) {
        if (selected) {
            mSelectedTexture.draw(canvas, 0, 0);
        } else {
            mUnSelectedTexture.draw(canvas, 0, 0);
        }
    }

    protected void drawAlbumSelectionState(GLCanvas canvas, boolean selected, int canvasWidth, int
            canvasHeight) {
        if (selected) {
            mSelectedTexture.draw(canvas, canvasWidth - mSelectedTexture.getHeight(), 0);
        } else {
            mUnSelectedAlbumTexture.draw(canvas, canvasWidth - mUnSelectedAlbumTexture.getHeight(), 0);
        }
    }

    protected void drawGifIndicator(GLCanvas canvas, int canvasWidth, int canvasHeight) {
        mGifTexture.draw(canvas, canvasWidth - mGifTexture.getHeight(), 0);
    }

    protected void drawVideoIndicator(GLCanvas canvas, int canvasWidth, int canvasHeight, String
            duration) {
        mVideoIndicatorTexture.draw(canvas, canvasWidth - mVideoIndicatorTexture.getWidth(), 0);
        if (duration != null) {
            StringTexture durationTexture = StringTexture.newInstance(duration, 20, Color.WHITE);
            int durationX = canvasWidth - durationTexture.getWidth() - mVideoIndicatorTexture
                    .getWidth();
            int durationY = (mVideoIndicatorTexture.getHeight() - durationTexture.getHeight()) / 2;
            durationTexture.draw(canvas, durationX, durationY);
        }
    }

    protected void drawRefocusIndicator(GLCanvas canvas, int canvasWidth, int canvasHeight) {
        mRefocusTexture.draw(canvas, canvasWidth - mGifTexture.getHeight(), 0);
    }

    protected void drawBurstIndicator(GLCanvas canvas, int canvasWidth, int canvasHeight) {
        mBurstOverlay.draw(canvas, canvasWidth - mGifTexture.getHeight(), 0);
    }

    private void drawScaleContent(GLCanvas canvas,
                                  Texture content, int width, int height, int rotation,
                                  float scale) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);

        // The content is always rendered in to the largest square that fits
        // inside the slot, aligned to the top of the slot.
        width = height = Math.min(width, height);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }

        int px = (int) (width - (content.getWidth() * scale)) / 2;
        int py = (int) (height - (content.getHeight() * scale)) / 2;
        int w = (int) (content.getWidth() * scale);
        int h = (int) (content.getHeight() * scale);
        content.draw(canvas, px, py, w, h);

        canvas.restore();
    }

    protected void drawSelectedContent(GLCanvas canvas,
                                       Texture content, int width, int height, int rotation) {
        drawContent(canvas, mSelectedBackgroundTexture, width, height, rotation);

        float scale = 0.8f * Math.min(
                (float) width / content.getWidth(),
                (float) height / content.getHeight());
        drawScaleContent(canvas, content, width, height, rotation, scale);
    }
}

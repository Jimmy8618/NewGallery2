/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.category;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.ui.SelectionRenderer;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ToastUtil;

public class CategoryView extends IconView
        implements View.OnClickListener, SwipableView {

    private static final String LOGTAG = "CategoryView";
    public static final int VERTICAL = 0;
    public static final int HORIZONTAL = 1;
    private Paint mPaint = new Paint();
    private Action mAction;
    private Paint mSelectPaint;
    CategoryAdapter mAdapter;
    private int mSelectionStroke;
    private Paint mBorderPaint;
    private int mBorderStroke;
    private float mStartTouchX = 0;
    private float mStartTouchY = 0;
    private float mDeleteSlope = 20;
    private int mSelectionColor = Color.WHITE;
    private int mSpacerColor = Color.WHITE;
    private boolean mCanBeRemoved = false;
    private long mDoubleActionLast = 0;
    private long mDoubleTapDelay = 250;
    private Toast mToast = null;
    private boolean isDehaze = false;

    public CategoryView(Context context) {
        super(context);
        setOnClickListener(this);
        Resources res = getResources();
        mSelectionStroke = res.getDimensionPixelSize(R.dimen.thumbnail_margin);
        mSelectPaint = new Paint();
        mSelectPaint.setStyle(Paint.Style.FILL);
        mSelectionColor = res.getColor(R.color.filtershow_category_selection);
        mSpacerColor = res.getColor(R.color.filtershow_categoryview_text);

        mSelectPaint.setColor(mSelectionColor);
        mBorderPaint = new Paint(mSelectPaint);
        mBorderPaint.setColor(Color.BLACK);
        mBorderStroke = mSelectionStroke / 3;
    }

    @Override
    public boolean isHalfImage() {
        if (mAction == null) {
            return false;
        }
        if (mAction.getType() == Action.CROP_VIEW) {
            return true;
        }
        return mAction.getType() == Action.ADD_ACTION;
    }

    private boolean canBeRemoved() {
        return mCanBeRemoved;
    }

    private void drawSpacer(Canvas canvas) {
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mSpacerColor);
        if (getOrientation() == CategoryView.VERTICAL) {
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, getHeight() / 5, mPaint);
        } else {
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, getWidth() / 5, mPaint);
        }
    }

    @Override
    public boolean needsCenterText() {
        if (mAction != null && mAction.getType() == Action.ADD_ACTION) {
            return true;
        }
        return super.needsCenterText();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mAction != null) {
            if (mAction.getType() == Action.SPACER) {
                drawSpacer(canvas);
                return;
            }
            if (mAction.isDoubleAction()) {
                return;
            }
            mAction.setImageFrame(new Rect(0, 0, getWidth(), getHeight()), getOrientation());
            if (mAction.getImage() != null) {
                setBitmap(mAction.getImage());
            }
        }
        super.onDraw(canvas);
        if (mAdapter.isSelected(this)) {
            SelectionRenderer.drawSelection(canvas, 0, 0,
                    getWidth(), getHeight(),
                    mSelectionStroke, mSelectPaint, mBorderStroke, mBorderPaint);
        }
    }

    public void setAction(Action action, CategoryAdapter adapter) {
        mAction = action;
        setText(mAction.getName());
        mAdapter = adapter;
        mCanBeRemoved = action.canBeRemoved();
        setUseOnlyDrawable(false);
        if (mAction.getType() == Action.ADD_ACTION) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.filtershow_add);
            setBitmap(bitmap);
            setUseOnlyDrawable(true);
            setText(getResources().getString(R.string.filtershow_add_button_looks));
        } else {
            setBitmap(mAction.getImage());
        }
        if (getResources().getString(R.string.ffx_dehaze).equals(mAction.getName())) {
            isDehaze = true;
        }
        invalidate();
    }

    @Override
    public void onClick(View view) {
        FilterShowActivity activity = (FilterShowActivity) getContext();
        // SPRD：Add for bug#616421, Do not respond click events if the category view is not available
        if (!MasterImage.getImage().mIsAvailable) {
            mToast = ToastUtil.showMessage(getContext(), mToast, R.string.loading, Toast.LENGTH_SHORT);
            return;
        }

        Rect originalBounds = MasterImage.getImage().getOriginalBounds();
        if (originalBounds != null) {
            int w = originalBounds.width();
            int h = originalBounds.height();
            if (isDehaze && w * h < GalleryUtils.DEHAZE_MAX_SIZE) {
                mToast = ToastUtil.showMessage(getContext(), mToast, R.string
                        .ffx_dont_support_dehaze, Toast.LENGTH_SHORT);
                return;
            }
        }

        if (mAction.getType() == Action.ADD_ACTION) {
            /* SPRD: fix bug 500338,crash when loading @{ */
            if (MasterImage.getImage().getPreset() != null) {
                activity.addNewPreset();
            }
            /* @}*/
        } else if (mAction.getType() != Action.SPACER) {
            if (mAction.isDoubleAction()) {
                long current = System.currentTimeMillis() - mDoubleActionLast;
                if (current < mDoubleTapDelay) {
                    activity.showRepresentation(mAction.getRepresentation());
                }
                mDoubleActionLast = System.currentTimeMillis();
            } else {
                /* SPRD: fix bug 500338,crash when loading @{ */
                if (MasterImage.getImage().getPreset() != null) {
                    /*
                     * SPRD: Add 20150316 Spreadst of bug410659, display abnormal when rotate
                     * and undo and rotate image again. @{
                     */
                    // activity.showRepresentation(mAction.getRepresentation());
                    FilterRepresentation actionRep = mAction.getRepresentation();
                    if (actionRep instanceof FilterRotateRepresentation
                            || actionRep instanceof FilterMirrorRepresentation) {
                        ImagePreset copy = new ImagePreset(MasterImage.getImage().getPreset());
                        FilterRepresentation rep = copy.getRepresentation(actionRep);
                        if (rep != null) {
                            activity.showRepresentation(rep);
                        } else {
                            actionRep.resetRepresentation();
                            activity.showRepresentation(actionRep);
                        }
                    } else {
                        activity.showRepresentation(actionRep);
                    }
                }
                /* @} */
            }
            mAdapter.setSelected(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = super.onTouchEvent(event);
        FilterShowActivity activity = (FilterShowActivity) getContext();

        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            activity.startTouchAnimation(this, event.getX(), event.getY());
        }
        if (!canBeRemoved()) {
            return ret;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mStartTouchY = event.getY();
            mStartTouchX = event.getX();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            setTranslationX(0);
            setTranslationY(0);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float delta = event.getY() - mStartTouchY;
            if (getOrientation() == CategoryView.VERTICAL) {
                delta = event.getX() - mStartTouchX;
            }
            if (Math.abs(delta) > mDeleteSlope) {
                activity.setHandlesSwipeForView(this, mStartTouchX, mStartTouchY);
            }
        }
        return true;
    }

    @Override
    public void delete() {
        mAdapter.remove(mAction);
    }
}

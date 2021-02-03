package com.sprd.gallery3d.refocusimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.refocus.RefocusUtils;

public class RefocusImageView extends View {

    private Bitmap mSrcBitmap, mRefocusBitmap;
    private RectF mSrcInScreenRectF = null;
    private RectF mCanDrawRectF = null;
    private RectF mSrcRectF = null;
    private RectF mScreenRectF = null;
    private RectF mSrcBmpRectF = null;
    private RectF mCanvasRectF = null;
    private Matrix mDisplaySrcMatrix = new Matrix();
    private Matrix mRatioSrcMatrix = new Matrix();
    private Paint mPaint;
    private Point mDrawPoint = new Point(); // draw circle center point
    private Point mPoint = new Point(); //click View point
    private RefocusViewCallback mCallBack;
    private static final String TAG = "RefocusImageView";
    private int mCircleRadius;
    private boolean mCanTouch;
    private boolean mReset;
    private boolean mHideCircle;
    private Paint mBitmapPaint = new Paint();

    public RefocusImageView(Context context) {
        super(context);
        init();
    }

    public RefocusImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RefocusImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setSrcBitmap(Bitmap srcBitmap) {
        this.mSrcBitmap = srcBitmap;
        mReset = true;
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setFilterBitmap(true);
        mSrcInScreenRectF = new RectF();
        mDisplaySrcMatrix.reset();
        mSrcBmpRectF = new RectF(0, 0, srcBitmap.getWidth(), srcBitmap.getHeight());
        Log.d(TAG, "mSrcBmpRectF = " + mSrcBmpRectF + " mCanvasRectF = " + mCanvasRectF);
        checkCanvasRectF();
        mDisplaySrcMatrix.setRectToRect(mSrcBmpRectF, mCanvasRectF, Matrix.ScaleToFit.CENTER);
        mDisplaySrcMatrix.mapRect(mSrcInScreenRectF, mSrcBmpRectF);
        mRatioSrcMatrix.setRectToRect(mSrcRectF, mCanvasRectF, Matrix.ScaleToFit.CENTER);
        Log.d(TAG, "intSrcRectF mSrcInScreenRectF : " + mSrcInScreenRectF);
        Log.d(TAG, "intSrcRectF mSrcRectF: " + mSrcRectF);
        postInvalidate();
    }

    // set after refocus bitmap
    public void setBitmap(Bitmap bitmap) {
        this.mRefocusBitmap = bitmap;
        mReset = false;
        mDisplaySrcMatrix.reset();
        RectF refocusBmpRectF = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        mDisplaySrcMatrix.setRectToRect(refocusBmpRectF, mCanvasRectF, Matrix.ScaleToFit.CENTER);
        mHideCircle = true;
        postInvalidate();
    }

    public void hideCircle() {
        mHideCircle = true;
        postInvalidate();
    }

    public void setSrcRectF(int srcW, int srcH) {
        mSrcRectF = new RectF(0, 0, srcW, srcH);
    }

    public void setScreenRectF(int screenW, int screenH) {
        mScreenRectF = new RectF(0, 0, screenW, screenH);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mCanvasRectF = new RectF(left, top, right, bottom);
        Log.d(TAG, "onLayout mCanvasRectF: " + mCanvasRectF);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mCanTouch) {
            return true;
        }
        int action = event.getAction();
        Point point = new Point();
        point.x = (int) event.getX();
        point.y = (int) event.getY();
        if (!mSrcInScreenRectF.contains(point.x, point.y)) {
            return true;
        }
        mHideCircle = false;
        mCallBack.touchValid();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mPoint.set(point.x, point.y);
                postInvalidate();
                break;
            case MotionEvent.ACTION_UP:
                mPoint.set(point.x, point.y);
                Point srcPoint = getSrcPoint(mPoint);
                mCallBack.onUpdatePoint(srcPoint);
                mCallBack.doRefocus();
                getSrcPoint(mPoint);
                postInvalidate();
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(getResources().getColor(R.color.photo_background));
        if (mReset) {
            if (mSrcBitmap != null && mDisplaySrcMatrix != null) {
                canvas.drawBitmap(mSrcBitmap, mDisplaySrcMatrix, mBitmapPaint);
            }
        } else {
            if (mRefocusBitmap != null && mDisplaySrcMatrix != null) {
                canvas.drawBitmap(mRefocusBitmap, mDisplaySrcMatrix, mBitmapPaint);
            }
        }

        if (mCanDrawRectF != null && !mHideCircle) {
            mDrawPoint.set(mPoint.x, mPoint.y);
            if (mPoint.x > mCanDrawRectF.right) {
                mDrawPoint.x = (int) mCanDrawRectF.right;
            }
            if (mPoint.x < mCanDrawRectF.left) {
                mDrawPoint.x = (int) mCanDrawRectF.left;
            }
            if (mPoint.y > mCanDrawRectF.bottom) {
                mDrawPoint.y = (int) mCanDrawRectF.bottom;
            }
            if (mPoint.y < mCanDrawRectF.top) {
                mDrawPoint.y = (int) mCanDrawRectF.top;
            }
            canvas.drawCircle(mDrawPoint.x, mDrawPoint.y, mCircleRadius, mPaint);
        }

        if (RefocusEditActivity.DEBUG) {
            mPaint.setColor(Color.BLUE);
            canvas.drawRect(mCanvasRectF, mPaint);
            /*
            if (mSrcRectF != null) {
                mPaint.setColor(Color.RED);
                canvas.drawRect(mSrcRectF, mPaint);
            }
            if (mSrcBmpRectF != null) {
                mPaint.setColor(Color.GRAY);
                canvas.drawRect(mSrcBmpRectF, mPaint);
            }
            */
            if (mSrcInScreenRectF != null) {
                mPaint.setColor(Color.GREEN);
                canvas.drawRect(mSrcInScreenRectF, mPaint);
            }
            if (mCanDrawRectF != null) {
                mPaint.setColor(Color.YELLOW);
                canvas.drawRect(mCanDrawRectF, mPaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        RefocusUtils.recycleBitmap(mSrcBitmap);
        RefocusUtils.recycleBitmap(mRefocusBitmap);
        mSrcBitmap = null;
        mRefocusBitmap = null;
    }

    /**
     * Touch point invert to real src bitmap point
     *
     * @return src bitmap point
     */
    public Point getSrcPoint(Point viewPoint) {
        Matrix matrix = new Matrix();
        mRatioSrcMatrix.invert(matrix);
        float[] floats = new float[2];
        floats[0] = viewPoint.x;
        floats[1] = viewPoint.y;
        matrix.mapPoints(floats);
        Point srcPoint = new Point((int) floats[0], (int) floats[1]);
        Log.d(TAG, "getSrcPoint: srcPoint : " + srcPoint);
        return srcPoint;
    }

    public Point getViewPoint(Point srcPoint) {
        float[] floats = new float[2];
        floats[0] = srcPoint.x;
        floats[1] = srcPoint.y;
        mRatioSrcMatrix.mapPoints(floats);
        Point viewPoint = new Point((int) floats[0], (int) floats[1]);
        Log.d(TAG, "getViewPoint: viewPoint : " + viewPoint);
        return viewPoint;
    }

    public void reset() {
        mReset = true;
        postInvalidate();
    }

    public void redo() {
        mReset = false;
        postInvalidate();
    }

    public static final int CIRCLE_DEF = 0;
    public static final int CIRCLE_BLUR = 1;

    private void init() {
        Log.d(TAG, "init.");
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(5);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
    }

    public void initCircle(Point srcP, int type) {
        if (this.mSrcBitmap == null) {
            return;
        }
        mPoint = getViewPoint(srcP);
        mPaint.setColor(Color.WHITE);
        switch (type) {
            case CIRCLE_DEF:
                mCircleRadius = GalleryUtils.dpToPixel(30);
                break;
            case CIRCLE_BLUR:
                mCircleRadius = GalleryUtils.dpToPixel(48);
                break;
        }
        mCanDrawRectF = new RectF(
                mSrcInScreenRectF.left + mCircleRadius,
                mSrcInScreenRectF.top + mCircleRadius,
                mSrcInScreenRectF.right - mCircleRadius,
                mSrcInScreenRectF.bottom - mCircleRadius);
        Log.d(TAG, "initCircle mCanDrawRectF: " + mCanDrawRectF);
        postInvalidate();
    }

    public void setCanTouch(boolean enable) {
        mCanTouch = enable;
    }

    public interface RefocusViewCallback {
        void touchValid();

        void onUpdatePoint(Point srcPoint);

        void doRefocus();
    }

    public void setRefocusViewCallback(RefocusViewCallback callback) {
        this.mCallBack = callback;
    }

    private void checkCanvasRectF() {
        if (mCanvasRectF == null) {
            mCanvasRectF = mScreenRectF;
            Log.d(TAG, "checkCanvasRectF mCanvasRectF = " + mCanvasRectF);
        }
    }
}

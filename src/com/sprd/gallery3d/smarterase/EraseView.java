package com.sprd.gallery3d.smarterase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.Stack;

public class EraseView extends View implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "EraseView";
    public static boolean DEBUG = false;
    private UIControl mControl;

    private Bitmap mSrcInScreenBitmap;
    private Paint mStrokePaint;
    private Paint mCirclePaint;
    private int mStrokeColor;
    private float mLastX = 0;
    private float mLastY = 0;

    private RectF mSrcInScreenRectF = null;
    private Matrix mDisplaySrcMatrix;

    private RectF mViewRectF = null;
    private Canvas mLinesCanvas;
    // this bitmap used to cache the lines the user that draw to screen, in the on draw we will
    // draw this bitmap.
    private Bitmap mStrokeBitmap;
    private Paint mMaskBitmapPaint;

    private final Object mLock = new Object();
    private float mScaleFactor = 1.0f;
    private static final float MAX_SCALE_FACTOR = 2.0f;
    private static final float MIN_SCALE_FACTOR = 1.0f;
    private ScaleGestureDetector mScaleGestureDetector;
    private float centerPointX = 0;
    private float centerPointY = 0;

    private enum Mode {
        MODE_INIT,
        MODE_MOVE,
        MODE_DRAW
    }

    private Mode mMode = Mode.MODE_INIT;

    private class StrokeData {

        float mPaintWidth;
        Path path = new Path();
        boolean isLines = false;

        public void setPaintWidth(float paintWidth) {
            mPaintWidth = paintWidth;
        }

        void onDraw(Canvas canvas, Paint paint) {
            paint.setStrokeWidth(mPaintWidth);
            canvas.drawPath(path, paint);
        }
    }

    private ArrayList<StrokeData> mStrokes = new ArrayList<>();
    private Stack<StrokeData> mRemovedStrokes = new Stack<>();
    private StrokeData mStroke;

    public EraseView(Context context) {
        super(context);
    }

    public EraseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public EraseView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStrokeColor = getContext().getResources().getColor(R.color.smart_erase_paint_color);
        mStrokePaint = new Paint();
        mStrokePaint.setColor(mStrokeColor);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setAntiAlias(true);
        mStrokePaint.setDither(true);

        mMaskBitmapPaint = new Paint();
        mMaskBitmapPaint.setStyle(Paint.Style.FILL);
        mMaskBitmapPaint.setColor(Color.WHITE);
        mMaskBitmapPaint.setAntiAlias(true);

        mCirclePaint = new Paint();
        mCirclePaint.setStrokeWidth(2);
        mCirclePaint.setAntiAlias(true);

        mDisplaySrcMatrix = new Matrix();
        mSrcInScreenRectF = new RectF();
        mViewRectF = new RectF();

        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            Log.d(TAG, "onLayout() called with: changed = [" + changed + "], left = [" + left +
                    "], top = [" + top + "], right = [" + right + "], bottom = [" + bottom + "]");
            mViewRectF.set(0, 0, getWidth(), getHeight());

            Log.d(TAG, "onLayout: mViewRectF=" + mViewRectF + ", mControl=" + mControl);
            if (mControl != null) {
                mControl.onViewInitFinish();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mSrcInScreenBitmap == null || mSrcInScreenRectF == null
                || mControl.getState() == EraseManager.State.STATE_ERASE_FINISH) {
            return super.onTouchEvent(event);
        } else {
            //how to check if draw in the rect after scale the bitmap
            //if (event.getPointerCount() ==1 && !checkShowToast(point)) return  super
            // .onTouchEvent(event);
            if (!mSrcInScreenRectF.contains((int) event.getX(), (int) event.getY())
                    && ((int) event.getY() < mSrcInScreenRectF.top || mSrcInScreenRectF.bottom < (int) event.getY())) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    Log.d(TAG,
                            "onTouchEvent: touch point is not is src rect! [" + (int) event.getX() + ", " + (int) event.getY() + "]");
                    return false;
                }
            }

            // change coordinate of screen to coordinate of src bitmap
//            Point point = new Point((int) event.getX(), (int) event.getY());
//            point.x = (int) (point.x - mSrcInScreenRectF.left);
//            point.y = (int) (point.y - mSrcInScreenRectF.top);
            int[] p = mapPointToBitmap(new float[]{event.getX() - mSrcInScreenRectF.left,
                    event.getY() - mSrcInScreenRectF.top});
//            point.set(p[0], p[1]);
            Point point = new Point(p[0], p[1]);

            if (event.getPointerCount() == 2) {
                mScaleGestureDetector.onTouchEvent(event);
            }
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    Log.d(TAG, "onTouchEvent: ACTION_DOWN: " + point);
                    mMode = Mode.MODE_DRAW;
                    mStroke = new StrokeData();
                    mStroke.setPaintWidth(mControl.getStrokeWidth());
                    mStroke.path.moveTo(point.x, point.y);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    Log.d(TAG, "onTouchEvent: ACTION_POINTER_DOWN");
                    mLastX = point.x;
                    mLastY = point.y;
                    mMode = Mode.MODE_MOVE;
                    if (event.getPointerCount() == 2) {
                        if (mStroke != null && !mStroke.path.isEmpty() && mStroke.isLines) {
                            mStrokes.add(mStroke);
                            undo();
                        }
                        float[] floats = centerPointBetweenFingers(event);
                        if (centerPointX != 0 && centerPointY != 0) {
                            mLastX = floats[0];
                            mLastY = floats[1];
                        } else {
                            centerPointX = floats[0];
                            centerPointY = floats[1];
                        }
                        mStroke = null;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.d(TAG,
                            "onTouchEvent: ACTION_MOVE: pointer count:" + event.getPointerCount() +
                                    ", mMode = " + mMode + ", " + point);
                    if (event.getPointerCount() == 2) {
                        if (mMode == Mode.MODE_MOVE) {
                            float[] floats = centerPointBetweenFingers(event);
                            float x = floats[0] - mLastX;
                            float y = floats[1] - mLastY;
                            centerPointX -= x;
                            centerPointY -= y;
                            if (!checkRect()) {
                                centerPointX += x;
                                centerPointY += y;
                            }
                            mLastX = floats[0];
                            mLastY = floats[1];
                        }
                    } else {
                        if (mStroke != null && mMode == Mode.MODE_DRAW) {
                            mStroke.isLines = true;
                            mStroke.path.lineTo(point.x, point.y);
                            mStroke.onDraw(mLinesCanvas, mStrokePaint);
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    Log.d(TAG, "onTouchEvent: ACTION_POINTER_UP: mMode=" + mMode);
                    mMode = Mode.MODE_DRAW;
                    if (mScaleFactor == 1.0f) {
                        centerPointX = 0;
                        centerPointY = 0;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    Log.d(TAG, "onTouchEvent: ACTION_UP: " + (mStroke == null ? "mStroke == null" :
                            ("path is empty: " + mStroke.path.isEmpty() + ", is lines: " + mStroke.isLines)));
                    if (mStroke != null && !mStroke.path.isEmpty() && mStroke.isLines) {
                        mStrokes.add(mStroke);
                    }
                    mControl.updateButton();
                    break;
            }

            invalidate();
            return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mStrokeBitmap == null || mSrcInScreenBitmap == null || mDisplaySrcMatrix == null || mSrcInScreenBitmap.isRecycled()) {
            return;
        }

        if (mControl.getState() != EraseManager.State.STATE_ERASE_FINISH) {
            Log.d(TAG, "onDraw: draw source bitmap");
            Log.d(TAG, "onDraw: mScaleFactor=" + mScaleFactor + ", [" + centerPointX + ", " + centerPointY + "]");
            canvas.save();
            canvas.scale(mScaleFactor, mScaleFactor, centerPointX, centerPointY);
            synchronized (mLock) {
                Log.d(TAG, "onDraw: mDisplaySrcMatrix = " + mDisplaySrcMatrix);
                canvas.drawBitmap(mSrcInScreenBitmap, mDisplaySrcMatrix, null);
            }
            canvas.drawBitmap(mStrokeBitmap, mSrcInScreenRectF.left, mSrcInScreenRectF.top,
                    mMaskBitmapPaint);

            canvas.restore();
            if (mControl.isChangeStrokeWidth()) {
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = mControl.getStrokeWidth() / 2;
                Log.d(TAG, "onDraw: draw circle: ");
                mCirclePaint.setStyle(Paint.Style.FILL);
                mCirclePaint.setColor(mStrokeColor);
                canvas.drawCircle(cx, cy, r, mCirclePaint);
                mCirclePaint.setStyle(Paint.Style.STROKE);
                mCirclePaint.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, r, mCirclePaint);
            }

            if (DEBUG) {
                Paint p = new Paint();
                p.setColor(Color.BLUE);
                p.setStrokeWidth(10);
                p.setStyle(Paint.Style.STROKE);
                p.setAntiAlias(true);
                p.setDither(true);
                canvas.drawPoint(centerPointX, centerPointY, p);
            }
        } else {
            canvas.save();
            Log.d(TAG, "onDraw: draw erased bitmap");
            canvas.drawBitmap(mControl.getInScreenErasedBitmap(), mDisplaySrcMatrix, null);
            canvas.restore();
        }
    }

    public void setControl(UIControl control) {
        mControl = control;
    }

    public int[] mapPointToBitmap(float[] p) {
        float x =
                (p[0] + (mScaleFactor - 1) * (centerPointX - mSrcInScreenRectF.left)) / mScaleFactor;
        float y =
                (p[1] + (mScaleFactor - 1) * (centerPointY - mSrcInScreenRectF.top)) / mScaleFactor;
        Log.d(TAG, "mapPointToBitmap: [" + p[0] + "," + p[1] + "]" + " ==> " + "[" + x + "," + y +
                "]" + ", mScaleFactor=" + mScaleFactor);
        return new int[]{(int) x, (int) y};
    }

    private float[] centerPointBetweenFingers(MotionEvent event) {
        float xPoint0 = event.getX(0);
        float yPoint0 = event.getY(0);
        float xPoint1 = event.getX(1);
        float yPoint1 = event.getY(1);
        float v = (xPoint0 + xPoint1) / 2;
        float v1 = (yPoint0 + yPoint1) / 2;
        return new float[]{v, v1};
    }

    public void undo() {
        if (mStrokes.size() <= 0) {
            return;
        }
        mRemovedStrokes.push(mStrokes.remove(mStrokes.size() - 1));

        if (mStrokeBitmap != null) {
            mLinesCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //clear mLinesCanvas
        }
        drawStroke(mLinesCanvas, mStrokePaint);
        invalidate();
    }

    public void redo() {
        if (mRemovedStrokes.size() > 0) {
            mStrokes.add(mRemovedStrokes.pop());

            if (mStrokeBitmap != null) {
                mLinesCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //clear
                // mLinesCanvas
            }
            drawStroke(mLinesCanvas, mStrokePaint);
            invalidate();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        synchronized (mLock) {
            if (mSrcInScreenBitmap != null && !mSrcInScreenBitmap.isRecycled()) {
                Log.d(TAG, "setBitmap: recycle " + mSrcInScreenBitmap);
                mSrcInScreenBitmap.recycle();
            }
            mSrcInScreenBitmap = bitmap;
        }

        mDisplaySrcMatrix.reset();
        RectF srcRectF = new RectF(0, 0, mSrcInScreenBitmap.getWidth(),
                mSrcInScreenBitmap.getHeight());
        mDisplaySrcMatrix.setRectToRect(srcRectF, mViewRectF, Matrix.ScaleToFit.CENTER);
        mDisplaySrcMatrix.mapRect(mSrcInScreenRectF, srcRectF);
        mStrokeBitmap = Bitmap.createBitmap((int) mSrcInScreenRectF.width(),
                (int) mSrcInScreenRectF.height(), Bitmap.Config.ARGB_8888);
        Log.d(TAG, "setBitmap: \nsrcRectF: " + srcRectF +
                "\nmViewRectF: " + mViewRectF +
                "\nmSrcInScreenRectF: " + mSrcInScreenRectF +
                "\nmStrokeBitmap size: " + mStrokeBitmap.getWidth() + " x " + mStrokeBitmap.getHeight());
        mLinesCanvas = new Canvas(mStrokeBitmap);
        postInvalidate();
    }

    public void setScaleFactor(float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        Log.d(TAG, "setScaleFactor() called with: scaleFactor = [" + scaleFactor + "]");
        postInvalidate();
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float v = detector.getScaleFactor();
        if (DEBUG) {
            Log.d(TAG, "onScale: " + v);
        }
        float v1 = mScaleFactor * v;
        if (v1 > MAX_SCALE_FACTOR) {
            v1 = MAX_SCALE_FACTOR;
        }
        if (v1 < MIN_SCALE_FACTOR) {
            v1 = MIN_SCALE_FACTOR;
        }

        mScaleFactor = v1;
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    private boolean checkRect() {
        Matrix matrix = new Matrix();
        matrix.postScale(mScaleFactor, mScaleFactor, centerPointX, centerPointY);
        RectF rectF = new RectF();
        matrix.mapRect(rectF, mViewRectF);
        boolean result = rectF.left < 0 && rectF.top < 0 &&
                rectF.right > mViewRectF.right && rectF.bottom > mViewRectF.bottom;
        Log.d(TAG, "checkRect: rectF=" + rectF + ", mViewRectF=" + mViewRectF + " -->" +
                " " + result);
        return result;
    }

    public int getStrokeNum() {
        return mStrokes.size();
    }

    public int getRemovedStrokeNum() {
        return mRemovedStrokes.size();
    }

    private void drawStroke(Canvas canvas, Paint paint) {
        for (StrokeData data : mStrokes) {
            data.onDraw(canvas, paint);
        }
    }

    public Bitmap getMaskBitmap() {
        Size oriBitmapSize = mControl.getOriginalBitmapSize();
        Matrix matrix = new Matrix();
        RectF linesBitmapRect = new RectF(0, 0, mStrokeBitmap.getWidth(), mStrokeBitmap.getHeight());
        RectF oriRect = new RectF(0, 0, oriBitmapSize.getWidth(), oriBitmapSize.getHeight());
        matrix.setRectToRect(linesBitmapRect, oriRect, Matrix.ScaleToFit.CENTER);
        Bitmap maskBitmap = Bitmap.createBitmap(oriBitmapSize.getWidth(), oriBitmapSize.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas maskCanvas = new Canvas(maskBitmap);
        maskCanvas.drawColor(Color.WHITE);

        Paint maskStrokePaint = new Paint(mStrokePaint);
        maskStrokePaint.setColor(Color.BLACK);
        ArrayList<StrokeData> dstStroke = new ArrayList<>(mStrokes);
        for (StrokeData stroke : dstStroke) {
            stroke.path.transform(matrix);
            stroke.mPaintWidth = matrix.mapRadius(stroke.mPaintWidth);
            stroke.onDraw(maskCanvas, maskStrokePaint);
        }
        return maskBitmap;
    }
}

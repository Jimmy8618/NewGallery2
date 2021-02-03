package com.sprd.gallery3d.blending.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.sprd.blending.bean.TagPoint;
import com.sprd.gallery3d.blending.BlendIngManager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;


public class BlendingView extends View implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "BlendingView";

    // States for BlendingView
    public static final int INVALID = -1; // draw rect
    public static final int SELECT_FOREGROUND_SUBJECT = 0; // draw rect
    public static final int ADJUST_FOREGROUND_SUBJECT_RECT = 1;// move rect
    public static final int UPDATE_SUBJECT_OUTLINE = 2;// update mask
    public static final int EDIT_BACKGROUND = 3;//replace bacground

    private int mState = -1;//init value is -1 make the view can't be touch
    private Bitmap mTargetBitmap, mSrcInScreenbitmap;
    private Paint mUpdatePaint, mDebugpaint;
    private Paint mFirstStatePaint;
    //use this collection in SELECT_FOREGROUND_SUBJECT and UPDATE_SUBJECT_OUTLINE
    private List<TagPoint> mPoints = new ArrayList<>();
    private Matrix mTargetObjectMatrix = new Matrix();
    private float mLastX = 0;
    private float mLastY = 0;
    private boolean mNotMoveTarget;
    private RectF mDrawBounds;
    private StateChangeCallback mCallBack;
    private static final int TOUCH_TOLERANCE = 45;
    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;
    private static final int MIN_WIDTH = 50;
    private int mEdgeSelected;
    private RectF mSrcInScreenRectF = null;
    private RectF mSrcRectF = null;
    private RectF mTargetInScreenBounds = null;
    private Matrix mDisplaySrcMatrix;
    private boolean mUseMatirx;
    private int mRotate = 0;
    private int mBaseRotate = 0;
    private RectF mCanvasRectF = null;
    private Canvas mLinescanvas;
    // this bitmap used to cache the lines the user that draw to screen, in the on draw we will draw this bitmap.
    private Bitmap mLinesbitmap;

    private Bitmap mDebugBitmap;
    private List<TagPoint> mDebugPoints;
    private boolean mShowToast = true;
    private final Object mLock = new Object();
    private int mVertexRadius;
    private float mScaleFactor = 1.0f;
    private float mMaxScaleFactor = 2.0f;
    private float mMinScaleFactor = 0.2f;
    private RectF mOriginalTargetInScreenBounds;
    private ScaleGestureDetector scaleGestureDetector;
    private List<StrokeData> mStrokeDatas = new ArrayList<>();
    private StrokeData data;
    private float centerPointX = 0;
    private float centerPointY = 0;
    private float firstlines[] = new float[4];
    private float secondlines[] = new float[4];
    private State mBlendingState;

    public BlendingView(Context context) {
        super(context);
        init();
    }

    public BlendingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlendingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setNotMoveTarget(boolean mNotMoveTarget) {
        this.mNotMoveTarget = mNotMoveTarget;
    }

    public int getState() {
        return mState;
    }

    public RectF getSrcInScreenRectF() {
        return mSrcInScreenRectF;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public int getRotate() {
        return mRotate;
    }

    private void init() {
        mUpdatePaint = new Paint();
        mUpdatePaint.setColor(Color.BLUE);
        mUpdatePaint.setStrokeWidth(10);
        mUpdatePaint.setStyle(Paint.Style.STROKE);
        mUpdatePaint.setAntiAlias(true);
        mUpdatePaint.setDither(true);

        mFirstStatePaint = new Paint();
        mFirstStatePaint.setStyle(Paint.Style.STROKE);
        mFirstStatePaint.setColor(Color.WHITE);
        mFirstStatePaint.setAntiAlias(true);
        mFirstStatePaint.setStrokeWidth(3);

        mDisplaySrcMatrix = new Matrix();
        mSrcInScreenRectF = new RectF();
        mCanvasRectF = new RectF();

        mDebugpaint = new Paint();
        mTargetInScreenBounds = new RectF();
        scaleGestureDetector = new ScaleGestureDetector(getContext(), this);
    }

    public void initBitmap(Bitmap srcBitmap) {
        mDisplaySrcMatrix.reset();
        mSrcRectF = new RectF(0, 0, srcBitmap.getWidth(), srcBitmap.getHeight());
        mDisplaySrcMatrix.setRectToRect(mSrcRectF, mCanvasRectF, Matrix.ScaleToFit.CENTER);
        mDisplaySrcMatrix.mapRect(mSrcInScreenRectF, mSrcRectF);
        Log.d(TAG, "initBitmap mSrcRectF: " + mSrcRectF + "\nmCanvasRectF: " + mCanvasRectF + "\nmSrcInScreenRectF: " + mSrcInScreenRectF);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mSrcInScreenbitmap == null || mSrcInScreenRectF == null || mBlendingState == null || mNotMoveTarget) {
            return super.onTouchEvent(event);
        } else {
            //how to check if draw in the rect after scale the bitmap
            //if (event.getPointerCount() ==1 && !checkShowToast(point)) return  super.onTouchEvent(event);
            if (!mSrcInScreenRectF.contains((int) event.getX(), (int) event.getY())
                    && ((int) event.getY() < mSrcInScreenRectF.top || mSrcInScreenRectF.bottom < (int) event.getY())) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
            }
            mBlendingState.onTouchEvent(event);
            invalidate();
            return true;
        }
    }

    public float[] mapPointToBitmap(float[] p) {
        float x = (p[0] + (mScaleFactor - 1) * centerPointX) / mScaleFactor;
        float y = (p[1] + (mScaleFactor - 1) * (centerPointY - mSrcInScreenRectF.top)) / mScaleFactor;
        return new float[]{x, y};
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

    public boolean hasStroke() {
        return mStrokeDatas.size() > 0;
    }

    public void undo() {
        if (mStrokeDatas.size() <= 0) {
            return;
        }
        mStrokeDatas.remove(mStrokeDatas.size() - 1);

        if (mLinesbitmap != null) {
            mLinescanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //clear mLinescanvas
        }
        for (StrokeData data : mStrokeDatas) {
            data.onDraw(mLinescanvas, mUpdatePaint);
        }
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            Log.d(TAG, "onLayout() called with: changed = [" + changed + "], left = [" + left + "], top = [" + top + "], right = [" + right + "], bottom = [" + bottom + "]");
            int mWidthPixels = getWidth();
            int mHeightPixels = getHeight();
            mCanvasRectF.set(0, 0, mWidthPixels, mHeightPixels);
            mVertexRadius = Math.min(mWidthPixels, mHeightPixels) / 60;
            mCallBack.startInit();
        }
    }

    /**
     * bitmap size must be {@link #mSrcInScreenRectF}
     */
    public void setDisplayBitmap(Bitmap bitmap) {
        synchronized (mLock) {
            if (mSrcInScreenbitmap != null && !mSrcInScreenbitmap.isRecycled()
                    && mCallBack.shouldrecycle(mSrcInScreenbitmap)) {
                Log.d(TAG, "setDisplayBitmap: recycle " + mSrcInScreenbitmap);
                mSrcInScreenbitmap.recycle();
            }
            mSrcInScreenbitmap = bitmap;
        }
        postInvalidate();
    }


    public void setUseMatirx(boolean useMatirx) {
        mUseMatirx = useMatirx;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSrcInScreenbitmap == null || mDisplaySrcMatrix == null || mSrcInScreenbitmap.isRecycled()) {
            return;
        }
        super.onDraw(canvas);
        if (mBlendingState != null) {
            mBlendingState.onDraw(canvas);
        }

        if (BlendIngManager.DEBUG) {
            if (mDebugBitmap != null) {
                mDebugpaint.setStyle(Paint.Style.FILL);
                mDebugpaint.setColor(Color.RED);
                mDebugpaint.setStrokeWidth(1);
                canvas.drawBitmap(mDebugBitmap, 0, 0, null);
                for (int i = 0; i < mDebugPoints.size(); i++) {
                    TagPoint tagPoint = mDebugPoints.get(i);
                    canvas.drawPoint(tagPoint.x, tagPoint.y, mDebugpaint);
                }
            }

            mDebugpaint.setColor(Color.RED);
            mDebugpaint.setStrokeWidth(2);
            mDebugpaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(mCanvasRectF, mDebugpaint);
        }
    }

    public void mapRect(Matrix matrix) {
        matrix.mapRect(mTargetInScreenBounds, getTargetRectF());
        if (BlendIngManager.DEBUG) {
            Log.d(TAG, matrix.toShortString() + "\n" + mTargetInScreenBounds.toString());
        }
    }


    private void drawShadows(Canvas canvas, Paint p, RectF innerBounds, RectF outerBounds) {
        canvas.drawRect(outerBounds.left, outerBounds.top, innerBounds.right, innerBounds.top, p);
        canvas.drawRect(innerBounds.right, outerBounds.top, outerBounds.right, innerBounds.bottom, p);
        canvas.drawRect(innerBounds.left, innerBounds.bottom, outerBounds.right, outerBounds.bottom, p);
        canvas.drawRect(outerBounds.left, innerBounds.top, innerBounds.left, outerBounds.bottom, p);
    }

    private void updateRect() {
        TagPoint start = mPoints.get(0);
        TagPoint end = mPoints.get(mPoints.size() - 1);
        int right, left, top, bottom;
        if (start.x > end.x) {
            right = start.x;
            left = end.x;
        } else {
            right = end.x;
            left = start.x;
        }
        if (start.y > end.y) {
            top = end.y;
            bottom = start.y;
        } else {
            top = start.y;
            bottom = end.y;
        }
        if (mDrawBounds == null) {
            mDrawBounds = new RectF(left, top, right, bottom);
        } else {
            mDrawBounds.set(left, top, right, bottom);
        }
    }

    /**
     * draw the rect depends to user's slide
     */
    private void drawSelectRect(Canvas canvas) {
        if (!judgePointIsValid()) {
            return;
        }
        if (mDrawBounds == null) {
            return;
        }
        updateRect();
        TagPoint end = mPoints.get(mPoints.size() - 1);
        mFirstStatePaint.setStyle(Paint.Style.STROKE);
        mFirstStatePaint.setColor(Color.WHITE);
        // Draw rect
        Log.d(TAG, "drawSelectRect: " + mDrawBounds);
        canvas.drawRect(mDrawBounds, mFirstStatePaint);
        mFirstStatePaint.setStyle(Paint.Style.FILL);
        // Draw external circle
        mFirstStatePaint.setColor(Color.parseColor("#33FFFFFF"));
        canvas.drawCircle(end.x, end.y, 40, mFirstStatePaint);
        // Draw internal circle
        mFirstStatePaint.setColor(Color.parseColor("#6495ED"));
        canvas.drawCircle(end.x, end.y, 20, mFirstStatePaint);
        if (mState == SELECT_FOREGROUND_SUBJECT && end.isLast()) {
            setState(BlendingView.ADJUST_FOREGROUND_SUBJECT_RECT);
            postInvalidate();
        }
    }

    private void drawRect(Canvas canvas, boolean drawmask) {
        if (mDrawBounds == null) {
            return;
        }
        mFirstStatePaint.setColor(0xCF000000);
        if (drawmask) {
            // Draw mask without rect
            drawShadows(canvas, mFirstStatePaint, mDrawBounds, getSrcInScreenRectF());
        }

        mFirstStatePaint.setStyle(Paint.Style.STROKE);
        mFirstStatePaint.setColor(Color.WHITE);
        mFirstStatePaint.setStrokeWidth(3);
        // Draw rect
        if (drawmask) {
            canvas.drawRect(mDrawBounds, mFirstStatePaint);
        } else {
            RectF rectF = new RectF(mDrawBounds.left, mDrawBounds.top,
                    mDrawBounds.right, mDrawBounds.bottom);
            canvas.drawRect(rectF, mFirstStatePaint);
        }
        mFirstStatePaint.setStyle(Paint.Style.FILL);
        mFirstStatePaint.setColor(Color.parseColor("#6495ED"));
        if (drawmask) {
            // Draw 4 points for corners of rect
            canvas.drawCircle(mDrawBounds.left, mDrawBounds.top, mVertexRadius, mFirstStatePaint);
            canvas.drawCircle(mDrawBounds.right, mDrawBounds.top, mVertexRadius, mFirstStatePaint);
            canvas.drawCircle(mDrawBounds.right, mDrawBounds.bottom, mVertexRadius, mFirstStatePaint);
            canvas.drawCircle(mDrawBounds.left, mDrawBounds.bottom, mVertexRadius, mFirstStatePaint);
        }
    }

    private boolean checkShowToast(Point point) {
        if (mShowToast && mState == UPDATE_SUBJECT_OUTLINE && !mDrawBounds.contains(point.x, point.y)) {
            Toast.makeText(getContext(), R.string.draw_tips, Toast.LENGTH_SHORT).show();
            mShowToast = false;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mShowToast = true;
                }
            }, 2500);
            return false;
        }
        return true;
    }

    private void moveSlide(float dX, float dY) {
        int movingEdges = mEdgeSelected;
        RectF crop = new RectF(mDrawBounds);
        float dx = 0;
        float dy = 0;
        if ((movingEdges & MOVE_LEFT) != 0) {
            dx = Math.min(crop.left + dX, crop.right - MIN_WIDTH) - crop.left;
        }
        if ((movingEdges & MOVE_TOP) != 0) {
            dy = Math.min(crop.top + dY, crop.bottom - MIN_WIDTH) - crop.top;
        }
        if ((movingEdges & MOVE_RIGHT) != 0) {
            dx = Math.max(crop.right + dX, crop.left + MIN_WIDTH) - crop.right;
        }
        if ((movingEdges & MOVE_BOTTOM) != 0) {
            dy = Math.max(crop.bottom + dY, crop.top + MIN_WIDTH) - crop.bottom;
        }

        if ((movingEdges & MOVE_LEFT) != 0) {
            crop.left += dx;
        }
        if ((movingEdges & MOVE_TOP) != 0) {
            crop.top += dy;
        }
        if ((movingEdges & MOVE_RIGHT) != 0) {
            crop.right += dx;
        }
        if ((movingEdges & MOVE_BOTTOM) != 0) {
            crop.bottom += dy;
        }
        if (getSrcInScreenRectF().contains(crop)) {
            mDrawBounds = crop;
        }
    }

    private void calculateSelectedEdge(float x, float y) {
        RectF cropped = mDrawBounds;

        float left = Math.abs(x - cropped.left);
        float right = Math.abs(x - cropped.right);
        float top = Math.abs(y - cropped.top);
        float bottom = Math.abs(y - cropped.bottom);

        mEdgeSelected = MOVE_BLOCK;
        // Check left or right.
        if ((left <= TOUCH_TOLERANCE) && ((y + TOUCH_TOLERANCE) >= cropped.top) && ((y - TOUCH_TOLERANCE) <= cropped.bottom) && (left < right)) {
            mEdgeSelected |= MOVE_LEFT;
        } else if ((right <= TOUCH_TOLERANCE) && ((y + TOUCH_TOLERANCE) >= cropped.top) && ((y - TOUCH_TOLERANCE) <= cropped.bottom)) {
            mEdgeSelected |= MOVE_RIGHT;
        }

        // Check top or bottom.
        if ((top <= TOUCH_TOLERANCE) && ((x + TOUCH_TOLERANCE) >= cropped.left) && ((x - TOUCH_TOLERANCE) <= cropped.right) && (top < bottom)) {
            mEdgeSelected |= MOVE_TOP;
        } else if ((bottom <= TOUCH_TOLERANCE) && ((x + TOUCH_TOLERANCE) >= cropped.left) && ((x - TOUCH_TOLERANCE) <= cropped.right)) {
            mEdgeSelected |= MOVE_BOTTOM;
        }
    }

    private boolean judgePointIsValid() {
        int size = mPoints.size();
        if (size < 2) {
            return false;
        }
        Point start = mPoints.get(0);
        Point end = mPoints.get(size - 1);
        if (!mSrcInScreenRectF.contains(start.x, start.y) || !mSrcInScreenRectF.contains(end.x, end.y)) {
            Toast.makeText(getContext(), R.string.valid_point, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * get the position of forground subject in OriginalSrcBitmap
     */
    public Point getTargetInSrcPosition() {
        int x = (int) (mTargetInScreenBounds.left);
        int y = (int) (mTargetInScreenBounds.top);
        Point point = getScreenPointInSrcLoc(x, y);
        Log.d(TAG, "getTargetInSrcPosition: point : " + point);
        return point;
    }

    public Point getTargetInSrcCenterPosition() {
        int x = (int) (mTargetInScreenBounds.centerX());
        int y = (int) (mTargetInScreenBounds.centerY());
        return getScreenPointInSrcLoc(x, y);
    }

    public Point getOriginalPoint() {
        return getScreenPointInSrcLoc((int) mOriginalTargetInScreenBounds.left, (int) mOriginalTargetInScreenBounds.top);
    }

    public Point getScreenPointInSrcLoc(int x, int y) {
        Matrix matrix = getMatrixinvert(mDisplaySrcMatrix);
        float[] floats = {x, y};
        matrix.mapPoints(floats);
        return new Point((int) floats[0], (int) floats[1]);
    }

    public boolean checkIsInvaildRect() {
        return mDrawBounds == null || Math.abs(mDrawBounds.width()) < MIN_WIDTH || Math.abs(mDrawBounds.height()) < MIN_WIDTH;
    }

    /**
     * get the point the user have draw to mLinesBitmap.
     */
    public List<TagPoint> getUpdatePoint(int width, int height) {
        Log.d(TAG, "getUpdatePoint() called with: width = [" + width + "], height = [" + height + "]");
        List<TagPoint> updatePoints = new ArrayList<>();
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(mLinesbitmap, width, height, true);//(243* 324)
        float[] definitionPoint = getDefinitionPoint(width, height);//rect int width * height(243 * 324)
        for (int i = (int) definitionPoint[1]; i < definitionPoint[3]; i++) {
            for (int j = (int) definitionPoint[0]; j < definitionPoint[2]; j++) {
                if (scaledBitmap.getPixel(j, i) == Color.BLUE) {
                    TagPoint tagPoint = new TagPoint(j, i);
                    if (!updatePoints.contains(tagPoint)) {
                        updatePoints.add(tagPoint);
                    }
                }
            }
        }
        return updatePoints;
    }

    public Matrix getMatrixinvert(Matrix matrix) {
        Matrix m = new Matrix();
        matrix.invert(m);
        return m;
    }

    public float[] getDefinitionPoint(int width, int height) {
        Log.d(TAG, "getDefinitionPoint() called with: width = [" + width + "], height = [" + height + "]");
        Matrix matrix = getMatrixinvert(mDisplaySrcMatrix);
        Matrix targetMatrix = getSrcToTargetMatrix(width, height);
        float[] startAndEnd = getRectPoints();
        matrix.mapPoints(startAndEnd);//srceen --> oringal
        targetMatrix.mapPoints(startAndEnd);//oringal --> width*height
        return startAndEnd;
    }

    @NonNull
    private Matrix getSrcToTargetMatrix(int width, int height) {
        Matrix targetMatrix = new Matrix();
        RectF targetRectF = new RectF(0, 0, width, height);
        targetMatrix.setRectToRect(mSrcRectF, targetRectF, Matrix.ScaleToFit.CENTER);
        return targetMatrix;
    }

    public float[] getRectPoints() {
        return new float[]{mDrawBounds.left, mDrawBounds.top, mDrawBounds.right, mDrawBounds.bottom};
    }

    public void setTargetBitmap(Bitmap bitmap) {
        mTargetBitmap = bitmap;
        if (mState == EDIT_BACKGROUND) {
            float width = bitmap.getWidth();
            float height = bitmap.getHeight();
            float w = getWidth() / 10.0f;
            float h = getHeight() / 10.0f;
            float max = Math.max(w / width, h / height);
            //mMinScaleFactor = max;
            Log.d(TAG, "setTargetBitmap: " + mMinScaleFactor);
            float min = Math.min(getWidth() / width, getHeight() / height);
            mMaxScaleFactor = min;
            Log.d(TAG, "setTargetBitmap: " + mMaxScaleFactor);
            mCallBack.initScaleFactor(mMinScaleFactor, mMaxScaleFactor);
        }
    }

    public void setTargetBitmapPosition(int coordinateX, int coordinateY) {
        Log.d(TAG, "setTargetBitmapPosition: coordinateX: " + coordinateX + ",coordinateY:" + coordinateY);
        float[] coord = {coordinateX, coordinateY};
        mTargetObjectMatrix.reset();
        mTargetObjectMatrix.postTranslate(coord[0], coord[1] + mSrcInScreenRectF.top);
        RectF rectF = getTargetRectF();
        mTargetObjectMatrix.mapRect(mTargetInScreenBounds, rectF);
        Log.d(TAG, "setTargetBitmapPosition: " + rectF);
        mOriginalTargetInScreenBounds = new RectF(mTargetInScreenBounds);
    }

    public void clearPoints() {
        mPoints.clear();
        if (mLinesbitmap != null) {
            mLinescanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //clear mLinescanvas
        }
        mCallBack.enableCorrect(false);
        postInvalidate();
    }

    public void setState(int state) {
        mState = state;
        Log.d(TAG, "setState: " + state);
        switch (mState) {
            case ADJUST_FOREGROUND_SUBJECT_RECT:
                mBlendingState = new AdjustRectState();
                if (mTargetBitmap != null) {
                    mTargetBitmap.recycle();
                    mTargetBitmap = null;
                }
                break;
            case SELECT_FOREGROUND_SUBJECT:
                mBlendingState = new SelectRectState();
                mPoints.clear();
                break;
            case UPDATE_SUBJECT_OUTLINE:
                mBlendingState = new UpdateState();
                mMaxScaleFactor = 2.0f;
                mMinScaleFactor = 1.0f;
                mLinesbitmap = Bitmap.createBitmap((int) mSrcInScreenRectF.width(),
                        (int) mSrcInScreenRectF.height(), Bitmap.Config.ARGB_8888);
                mLinescanvas = new Canvas(mLinesbitmap);

                break;
            case EDIT_BACKGROUND:
                mBlendingState = new EditBackgroundState();
                mScaleFactor = 1.0f;
                mMaxScaleFactor = 2.0f;
                mMinScaleFactor = 0.2f;
                mLinesbitmap.recycle();
                break;
        }
        if (mCallBack != null) {
            mCallBack.onStateChange(mState);
        }
        invalidate();
    }

    public void setDebugBitmapPoints(Bitmap bitmap, List<TagPoint> list) {
        this.mDebugBitmap = bitmap;
        this.mDebugPoints = list;
    }

    public void showSmallRect() {
        if (mShowToast) {
            Toast.makeText(getContext(), R.string.small_rect, Toast.LENGTH_SHORT).show();
            mShowToast = false;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mShowToast = true;
                }
            }, 2500);
        }
    }

    public boolean checkCantain(RectF src, RectF target) {
        // check for empty first
        return src.left < src.right && src.top < src.bottom
                // now check for containment
                && src.left < target.left && src.top < target.top
                && src.right > target.right && src.bottom > target.bottom;
    }

    @NonNull
    private RectF getTargetRectF() {
        return new RectF(0, 0, mTargetBitmap.getWidth(), mTargetBitmap.getHeight());
    }

    public boolean checkZoom(float scaleFactor) {
        return check(mRotate, scaleFactor);
    }

    public void setScaleFactor(float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        Log.d(TAG, "setScaleFactor() called with: scaleFactor = [" + scaleFactor + "]");
        mCallBack.enableCorrect(true);
        postInvalidate();
    }

    public float getminFactor() {
        return mMinScaleFactor;
    }

    public boolean checkRotate(int rotate) {
        return check(rotate, mScaleFactor);
    }

    private boolean check(int rotate, float scaleFactor) {
        RectF rectF1 = new RectF();
        Matrix matrix = new Matrix(mTargetObjectMatrix);
        matrix.mapRect(rectF1, getTargetRectF());
        matrix.postScale(scaleFactor, scaleFactor, mTargetInScreenBounds.centerX(), mTargetInScreenBounds.centerY());
        matrix.mapRect(rectF1, getTargetRectF());
        matrix.postRotate(-1 * rotate, mTargetInScreenBounds.centerX(), mTargetInScreenBounds.centerY());
        matrix.mapRect(rectF1, getTargetRectF());
        Log.d(TAG, "check:mSrcInScreenRectF:" + mSrcInScreenRectF.toString() +
                "\n      rectF1:" + rectF1.toString() +
                "\n      rotate:" + rotate +
                "\n      scaleFactor:" + scaleFactor +
                "\n      checkCantain:" + checkCantain(mSrcInScreenRectF, rectF1));
        boolean b = checkCantain(mSrcInScreenRectF, rectF1);
        if (!b && mShowToast) {
            Toast.makeText(getContext(), R.string.blending_move_limit, Toast.LENGTH_SHORT).show();
            mShowToast = false;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mShowToast = true;
                }
            }, 2500);
        }
        return b;
    }

    public void setRotate(int rotate) {
        Log.d(TAG, "setRotate() called with: rotate = [" + rotate + "]");
        mCallBack.enableCorrect(true);
        mRotate = rotate;
        postInvalidate();
    }

    public void setRotate(float[] firstlines, float[] secondlines) {
        float k1 = (firstlines[1] - firstlines[3]) / (firstlines[0] - firstlines[2]);
        float k2 = (secondlines[1] - secondlines[3]) / (secondlines[0] - secondlines[2]);
        float tan = (k1 - k2) / (1 + k1 * k2);
        double degress = Math.toDegrees(Math.atan(tan));
        degress += mBaseRotate;
        if (checkRotate((int) degress)) {
            setRotate((int) degress);
        }
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float v = detector.getScaleFactor();
        if (BlendIngManager.DEBUG) {
            Log.d(TAG, "onScale: " + v);
        }
        float v1 = mScaleFactor * v;
        if (v1 > mMaxScaleFactor) {
            v1 = mMaxScaleFactor;
        }
        if (v1 < mMinScaleFactor) {
            v1 = mMinScaleFactor;
        }
        if (mState == EDIT_BACKGROUND) {
            if (checkZoom(v1)) {
                mScaleFactor = v1;
                return true;
            } else {
                return false;
            }
        } else if (mState == UPDATE_SUBJECT_OUTLINE) {
            mScaleFactor = v1;
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    public void clearStroke() {
        Log.d(TAG, "clearStroke: ");
        mStrokeDatas.clear();
    }

    public RectF getBox() {
        return new RectF(mDrawBounds.left, mDrawBounds.top - mSrcInScreenRectF.top, mDrawBounds.right, mDrawBounds.bottom - mSrcInScreenRectF.top);
    }

    class StrokeData {

        float mPaintWidth;
        Path path = new Path();

        public void setPaintWidth(float paintWidth) {
            mPaintWidth = paintWidth;
        }

        void onDraw(Canvas canvas, Paint paint) {
            paint.setStrokeWidth(mPaintWidth);
            canvas.drawPath(path, paint);
        }
    }

    public interface StateChangeCallback {
        void onStateChange(int state);

        void enableCorrect(boolean enable);

        void addOperate(List<TagPoint> points);

        boolean shouldrecycle(Bitmap bitmap);

        void initScaleFactor(float mMinScaleFactor, float mMaxScaleFactor);

        void startInit();
    }

    public void setStateChangeCallback(StateChangeCallback callback) {
        this.mCallBack = callback;
    }

    abstract class State {

        public abstract void onTouchEvent(MotionEvent event);

        public void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            synchronized (mLock) {
                if (mUseMatirx) {
                    canvas.drawBitmap(mSrcInScreenbitmap, mDisplaySrcMatrix, null);
                } else {
                    canvas.drawBitmap(mSrcInScreenbitmap, 0, mSrcInScreenRectF.top, null);
                }
                if (BlendIngManager.DEBUG) {
                    Log.d(TAG, "onDraw: mSrcInScreenbitmap - mState:" + mState + " mUseMatirx:" + mUseMatirx);
                }
            }
        }
    }

    class SelectRectState extends State {

        @Override
        public void onTouchEvent(MotionEvent event) {
            TagPoint point = new TagPoint((int) event.getX(), (int) event.getY());
            int action = event.getAction();
            Log.d(TAG, "onTouchEvent: " + action);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (!mPoints.isEmpty()) {
                        mPoints.clear();
                    }
                    mPoints.add(point);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mSrcInScreenRectF.contains(point.x, point.y)) {
                        return;
                    }
                    mPoints.add(point);
                    updateRect();
                    break;
                case MotionEvent.ACTION_UP:
                    if (mSrcInScreenRectF.contains(point.x, point.y)) {
                        point.setIsLast(true);
                        mPoints.add(point);
                    } else {
                        mPoints.get(mPoints.size() - 1).setIsLast(true);
                        Log.d(TAG, "onTouchEvent: " + mPoints.get(mPoints.size() - 1).isLast());
                    }
                    updateRect();
                    if (checkIsInvaildRect()) {
                        showSmallRect();
                        mPoints.clear();
                        if (mDrawBounds != null) {
                            mDrawBounds.setEmpty();
                        }
                        return;
                    }
                    setState(ADJUST_FOREGROUND_SUBJECT_RECT);
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawSelectRect(canvas);
        }
    }

    class AdjustRectState extends State {

        @Override
        public void onTouchEvent(MotionEvent event) {
            TagPoint point = new TagPoint((int) event.getX(), (int) event.getY());
            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mLastX = point.x;
                    mLastY = point.y;
                    calculateSelectedEdge(point.x, point.y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float offX = point.x - mLastX;
                    float offY = point.y - mLastY;
                    mLastX = point.x;
                    mLastY = point.y;
                    if (mEdgeSelected == MOVE_BLOCK) {
                        mDrawBounds.offset(offX, offY);
                        if (!getSrcInScreenRectF().contains(mDrawBounds)) {
                            mDrawBounds.offset(-offX, -offY);
                        }
                    } else {
                        moveSlide(offX, offY);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawRect(canvas, true);
        }
    }

    class UpdateState extends State {

        private Path mPath = new Path();
        int MODE_MOVE = 1;
        int MODE_DRAW = 2;
        int mMode;

        @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onTouchEvent(MotionEvent event) {
            TagPoint point = new TagPoint((int) event.getX(), (int) event.getY());
            point.y = (int) (point.y - mSrcInScreenRectF.top);
            float[] p = mapPointToBitmap(new float[]{point.x, point.y});
            point.set((int) p[0], (int) p[1]);

            if (event.getPointerCount() == 2) {
                scaleGestureDetector.onTouchEvent(event);
            }
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mPath.reset();
                    mMode = MODE_DRAW;
                    data = new StrokeData();
                    data.path.moveTo(point.x, point.y);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    mLastX = point.x;
                    mLastY = point.y;
                    mMode = MODE_MOVE;
                    if (event.getPointerCount() == 2) {
                        if (data != null && !data.path.isEmpty()) {
                            mStrokeDatas.add(data);
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
                        data = null;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2) {
                        if (mMode == MODE_MOVE) {
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
                        if (data != null && mMode == MODE_DRAW) {
                            data.path.lineTo(point.x, point.y);
                            //y = -5x + 15
                            data.setPaintWidth(-5 * mScaleFactor + 15);
                            data.onDraw(mLinescanvas, mUpdatePaint);
                        }
                    }
                    mCallBack.enableCorrect(true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    mMode = MODE_DRAW;
                    if (mScaleFactor == 1.0f) {
                        centerPointX = 0;
                        centerPointY = 0;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    point.setIsLast(true);
                    if (data != null) {
                        mStrokeDatas.add(data);
                    }
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (mTargetBitmap == null) {
                return;
            }
            canvas.save();
            canvas.scale(mScaleFactor, mScaleFactor, centerPointX, centerPointY);
            super.onDraw(canvas);
            Log.d(TAG, "onDraw: centerPointX:" + centerPointX + ", centerPointY:" + centerPointY);
            mFirstStatePaint.setColor(0xCF000000);
            mFirstStatePaint.setStyle(Paint.Style.FILL);
            // Draw black mask
            canvas.drawRect(0, mSrcInScreenRectF.top, mSrcInScreenRectF.right, mSrcInScreenRectF.bottom, mFirstStatePaint);
            // Draw target object
            canvas.drawBitmap(mTargetBitmap, mTargetObjectMatrix, null);
            //draw lines
            canvas.drawBitmap(mLinesbitmap, 0, mSrcInScreenRectF.top, mFirstStatePaint);
            //draw rect
            drawRect(canvas, false);
            canvas.restore();
            if (BlendIngManager.DEBUG) {
                canvas.drawPoint(centerPointX, centerPointY, mUpdatePaint);
            }
        }
    }

    private boolean checkRect() {
        Matrix matrix = new Matrix();
        matrix.postScale(mScaleFactor, mScaleFactor, centerPointX, centerPointY);
        RectF rectF = new RectF();
        matrix.mapRect(rectF, mCanvasRectF);
        Log.d(TAG, "checkRect: " + rectF);
        boolean result = rectF.left < 0 && rectF.top < 0 &&
                rectF.right > mCanvasRectF.right && rectF.bottom > mCanvasRectF.bottom;
        return result;
    }

    class EditBackgroundState extends State {

        Matrix matrix = new Matrix();

        @Override
        public void onTouchEvent(MotionEvent event) {
            // set only touch forground can move.
            //if (!mTargetInScreenBounds.contains(point.x, point.y)){
            //    return;
            //}
            TagPoint point = new TagPoint((int) event.getX(), (int) event.getY());
            int pointerCount = event.getPointerCount();
            scaleGestureDetector.onTouchEvent(event);
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mLastX = point.x;
                    mLastY = point.y;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (pointerCount == 2) {
                        mLastX = 0;
                        mLastY = 0;
                        firstlines[0] = event.getX(0);
                        firstlines[1] = event.getY(0);
                        firstlines[2] = event.getX(1);
                        firstlines[3] = event.getY(1);
                        mBaseRotate = mRotate;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pointerCount == 2) {
                        mLastX = 0;
                        mLastY = 0;
                        secondlines[0] = event.getX(0);
                        secondlines[1] = event.getY(0);
                        secondlines[2] = event.getX(1);
                        secondlines[3] = event.getY(1);
                        setRotate(firstlines, secondlines);
                    } else {
                        if (mLastX == 0) {
                            return;
                        }
                        float offX = point.x - mLastX;
                        float offY = point.y - mLastY;
                        mLastX = point.x;
                        mLastY = point.y;
                        mTargetInScreenBounds.offset(offX, offY);
                        if (mSrcInScreenRectF.contains(mTargetInScreenBounds)) {
                            mTargetObjectMatrix.postTranslate(offX, offY);
                        } else {
                            mTargetInScreenBounds.offset(-offX, -offY);
                        }
                    }
                    mCallBack.enableCorrect(true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    break;
                case MotionEvent.ACTION_UP:
                    RectF rectF = getTargetRectF();
                    mTargetObjectMatrix.mapRect(mOriginalTargetInScreenBounds, rectF);
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            matrix.set(mTargetObjectMatrix);
            mapRect(matrix);
            matrix.postScale(mScaleFactor, mScaleFactor, mTargetInScreenBounds.centerX(), mTargetInScreenBounds.centerY());
            mapRect(matrix);
            matrix.postRotate(-1 * mRotate, mTargetInScreenBounds.centerX(), mTargetInScreenBounds.centerY());
            mapRect(matrix);
            canvas.drawBitmap(mTargetBitmap, matrix, null);
            if (BlendIngManager.DEBUG) {
                Log.d(TAG, "onDraw: mTargetInScreenBounds:" + mTargetInScreenBounds);
                canvas.drawLine(secondlines[0], secondlines[1], secondlines[2], secondlines[3], mUpdatePaint);
                canvas.drawLine(firstlines[0], firstlines[1], firstlines[2], firstlines[3], mUpdatePaint);
                mUpdatePaint.setStrokeWidth(2.0f);
                mUpdatePaint.setTextSize(30);
                canvas.drawRect(mTargetInScreenBounds, mUpdatePaint);
                canvas.drawText("[" + mTargetInScreenBounds.left + "," + mTargetInScreenBounds.top + "],"
                                + "(" + mRotate + ")," + "(" + mScaleFactor + ")",
                        mTargetInScreenBounds.left, mTargetInScreenBounds.top, mUpdatePaint);
            }
        }
    }
}
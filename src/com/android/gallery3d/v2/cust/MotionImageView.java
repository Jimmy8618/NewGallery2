package com.android.gallery3d.v2.cust;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.view.View;

public class MotionImageView extends View {
    private static final String TAG = MotionImageView.class.getSimpleName();

    private RectF mCanvasRect;
    private RectF mBitmapRect;
    private Matrix mDrawMatrix;

    private Paint mPaint;
    private Bitmap mBitmap;

    public MotionImageView(Context context) {
        super(context);
        init();
    }

    public MotionImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MotionImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MotionImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mCanvasRect = new RectF();
        mBitmapRect = new RectF();
        mDrawMatrix = new Matrix();

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
    }

    public synchronized void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        mBitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        mDrawMatrix.reset();
        mDrawMatrix.setRectToRect(mBitmapRect, mCanvasRect, Matrix.ScaleToFit.CENTER);
        postInvalidate();
    }

    public synchronized Bitmap getBitmap() {
        return mBitmap;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mCanvasRect.set(left, top, right, bottom);
        mDrawMatrix.reset();
        mDrawMatrix.setRectToRect(mBitmapRect, mCanvasRect, Matrix.ScaleToFit.CENTER);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBitmap != null) {
            canvas.drawBitmap(mBitmap, mDrawMatrix, mPaint);
        }
    }
}

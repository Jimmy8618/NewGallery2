/**
 * Create by baolin.li
 */

package com.android.gallery3d.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.R;

public class RotateSeekBar extends View {
    private static final String TAG = "RotateSeekBar";
    private static final String O = "°";

    private Drawable mRulerIcon;
    private Drawable mUpperIcon;
    private Drawable mBottomIcon;
    private int mTextSize;
    private Paint mPaint;

    private Rect mTextBound;
    private FontMetrics mFontMetrics;

    private static final int MAX = 90;
    private int mProgress = MAX / 2;
    private int mAngle = getAngleFromProgress();

    private float mDownX;
    private float mUpperIconX;

    private RotateSeekBarChangeListener mRotateSeekBarChangeListener;

    public interface RotateSeekBarChangeListener {
        /**
         * called when seekBar touch down
         */
        void onSeekBarTouchDown(RotateSeekBar seekBar);

        /**
         * called when seekBar progress changed
         */
        void onProgressChanged(RotateSeekBar seekBar, int progress, int angle);

        /**
         * called when seekBar touch up
         */
        void onSeekBarTouchUp(RotateSeekBar seekBar);
    }

    public RotateSeekBar(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
    }

    public RotateSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        init(context, attrs, 0, 0);
    }

    public RotateSeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // TODO Auto-generated constructor stub
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public RotateSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // TODO Auto-generated constructor stub
        init(context, attrs, defStyleAttr, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray ar = context.getTheme().obtainStyledAttributes(attrs, R.styleable.RotateSeekBar,
                defStyleAttr, defStyleRes);
        int n = ar.getIndexCount();

        for (int i = 0; i < n; i++) {
            int attr = ar.getIndex(i);
            switch (attr) {
                case R.styleable.RotateSeekBar_rulerIcon:
                    mRulerIcon = ar.getDrawable(attr);
                    break;
                case R.styleable.RotateSeekBar_upperIcon:
                    mUpperIcon = ar.getDrawable(attr);
                    break;
                case R.styleable.RotateSeekBar_bottomIcon:
                    mBottomIcon = ar.getDrawable(attr);
                    break;
                case R.styleable.RotateSeekBar_textSize:
                    mTextSize = ar.getDimensionPixelSize(attr,
                            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                                    getResources().getDisplayMetrics()));
                    break;
            }
        }

        ar.recycle();

        mTextBound = new Rect();

        mPaint = new Paint();
        mPaint.setTextSize(mTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAntiAlias(true);
        mPaint.getTextBounds("+88°", 0, "+88°".length(), mTextBound);
        mPaint.setColor(Color.WHITE);

        mFontMetrics = mPaint.getFontMetrics();
    }

    @Override
    protected void onFinishInflate() {
        // TODO Auto-generated method stub
        super.onFinishInflate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        drawRuler(canvas);
        drawText(canvas);
        drawBottomIcon(canvas);
        drawUpperIcon(canvas);
    }

    private void drawUpperIcon(Canvas canvas) {
        int delta = (getWidth() - mUpperIcon.getIntrinsicWidth()) * mAngle / MAX;
        int left = (getWidth() - mUpperIcon.getIntrinsicWidth()) / 2 + delta;
        int right = (getWidth() + mUpperIcon.getIntrinsicWidth()) / 2 + delta;
        mUpperIcon.setBounds(left, 5, right, mUpperIcon.getIntrinsicHeight() + 5);
        mUpperIcon.draw(canvas);
    }

    private void drawBottomIcon(Canvas canvas) {
        mBottomIcon.setBounds((getWidth() - mBottomIcon.getIntrinsicWidth()) / 2,
                getHeight() - mBottomIcon.getIntrinsicHeight() - 5,
                (getWidth() + mBottomIcon.getIntrinsicWidth()) / 2,
                getHeight() - 5);
        mBottomIcon.draw(canvas);
    }

    private void drawRuler(Canvas canvas) {
        int left = (getWidth() - mRulerIcon.getIntrinsicWidth()) / 2;
        int right = (getWidth() + mRulerIcon.getIntrinsicWidth()) / 2;
        if (left < 0) {
            int d = Math.abs(left) * 2 / 5;
            left += d;
            right -= d;
        }
        mRulerIcon.setBounds(left,
                (getHeight() - mRulerIcon.getIntrinsicHeight()) / 2,
                right,
                (getHeight() + mRulerIcon.getIntrinsicHeight()) / 2);
        mRulerIcon.draw(canvas);
    }

    private void drawText(Canvas canvas) {
        float fontHeight = mFontMetrics.bottom - mFontMetrics.top;
        float textBaseY = getHeight() - (getHeight() - fontHeight) / 2 - mFontMetrics.bottom;
        canvas.drawText((mAngle > 0 ? "+" + mAngle : (mAngle < 0 ? "-" + mAngle : mAngle)) + O,
                getWidth() / 2, textBaseY, mPaint);
    }

    private int getAngleFromProgress() {
        return mProgress - MAX / 2;
    }

    private int getProgressFromAngle() {
        return mAngle + MAX / 2;
    }

    public void setProgress(int progress) {
        if (progress < 0 || progress > MAX) {
            return;
        }
        mProgress = progress;
        mAngle = getAngleFromProgress();
        invalidate();
    }

    public int getProgress() {
        return mProgress;
    }

    public void setAngle(int angle) {
        if (angle < -MAX / 2 || angle > MAX / 2) {
            return;
        }
        mAngle = angle;
        mProgress = getProgressFromAngle();
        invalidate();
    }

    public int getAngle() {
        return mAngle;
    }

    public void setRotateSeekBarChangeListener(RotateSeekBarChangeListener l) {
        mRotateSeekBarChangeListener = l;
    }

    private void touchDown(MotionEvent event) {
        mDownX = event.getX();
        mUpperIconX = mProgress * (getWidth() - mUpperIcon.getIntrinsicWidth()) / MAX
                + mUpperIcon.getIntrinsicWidth() / 2;
        if (mRotateSeekBarChangeListener != null) {
            mRotateSeekBarChangeListener.onSeekBarTouchDown(this);
        }
    }

    private void dragThumb(MotionEvent event) {
        float delta = event.getX() - mDownX;
        int progress;
        if (mUpperIconX + delta > getWidth() - mUpperIcon.getIntrinsicWidth() / 2) {
            progress = MAX;
        } else if (mUpperIconX + delta < mUpperIcon.getIntrinsicWidth() / 2) {
            progress = 0;
        } else {
            progress = (int) (MAX * (mUpperIconX + delta - mUpperIcon.getIntrinsicWidth() / 2) / (getWidth() - mUpperIcon
                    .getIntrinsicWidth()));
        }
        if (progress != mProgress) {
            mProgress = progress;
            mAngle = getAngleFromProgress();
            if (mRotateSeekBarChangeListener != null) {
                mRotateSeekBarChangeListener.onProgressChanged(this, mProgress, mAngle);
            }
            invalidate();
        }
    }

    private void touchUp(MotionEvent event) {
        if (mRotateSeekBarChangeListener != null) {
            mRotateSeekBarChangeListener.onSeekBarTouchUp(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                dragThumb(event);
                return true;
            case MotionEvent.ACTION_UP:
                touchUp(event);
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // TODO Auto-generated method stub
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int width;
        int height;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            int desired = getPaddingLeft() + getPaddingRight() + mRulerIcon
                    .getIntrinsicWidth();
            width = desired;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            float textHeight = mTextBound.height();
            int desired = (int) (getPaddingTop() + textHeight + getPaddingBottom()
                    + mUpperIcon.getIntrinsicHeight()
                    + mBottomIcon.getIntrinsicHeight() + 30);
            height = desired;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // TODO Auto-generated method stub
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setProgress(ss.progress);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        // TODO Auto-generated method stub
        Parcelable p = super.onSaveInstanceState();
        SavedState s = new SavedState(p);
        s.progress = mProgress;
        return s;
    }

    static class SavedState extends BaseSavedState {
        int progress;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            progress = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(progress);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}

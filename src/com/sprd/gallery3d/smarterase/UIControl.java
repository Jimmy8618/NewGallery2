package com.sprd.gallery3d.smarterase;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.android.gallery3d.R;

public class UIControl implements View.OnClickListener,
        SeekBar.OnSeekBarChangeListener, MenuItem.OnMenuItemClickListener {

    private static final String TAG = "UIControl";

    private SmartEraseActivity mActivity;
    private EraseView mEraseView;
    private View mUndoRedo;
    private ImageView mUndo;
    private ImageView mRedo;
    private MenuItem mGenerateMenu;
    private View mStrokeWidthView;
    private SeekBar mStrokeWidthBar;
    private ProgressDialog mProgressDialog;

    private EraseManager mManager;

    private boolean isChangeStrokeWidth;
    private int mStrokeWidth = 50;

    public UIControl(SmartEraseActivity activity, EraseManager manager) {
        mActivity = activity;
        mManager = manager;
        mEraseView = activity.findViewById(R.id.blendview);
        mEraseView.setControl(this);

        mUndoRedo = activity.findViewById(R.id.undo_redo);
        mUndoRedo.setVisibility(View.INVISIBLE);
        mUndo = activity.findViewById(R.id.undo);
        mRedo = activity.findViewById(R.id.redo);
        mUndo.setOnClickListener(this);
        mRedo.setOnClickListener(this);
        mStrokeWidthView = activity.findViewById(R.id.stroke_width);
        mStrokeWidthBar = activity.findViewById(R.id.stroke_width_seekbar);
        mStrokeWidthBar.setOnSeekBarChangeListener(this);
    }

    public void setOptionsMenu(Menu menu) {
        mGenerateMenu = menu.findItem(R.id.smart_erase_generate);
        //mGenerateMenu.setIcon(R.drawable.ic_smart_erase_generage);
        mGenerateMenu.setTitle(mActivity.getString(R.string.next));
        mGenerateMenu.setVisible(false);
        mGenerateMenu.setOnMenuItemClickListener(this);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.redo:
                mEraseView.redo();
                updateButton();
                break;
            case R.id.undo:
                mEraseView.undo();
                updateButton();
                break;
        }
    }

    public void updateButton() {
        if (mEraseView.getStrokeNum() > 0) {
            mUndo.setEnabled(true);
            mGenerateMenu.setVisible(true);
        } else {
            mUndo.setEnabled(false);
            mGenerateMenu.setVisible(false);
        }

        if (mEraseView.getRemovedStrokeNum() > 0) {
            mRedo.setEnabled(true);
        } else {
            mRedo.setEnabled(false);
        }

        if (mUndo.isEnabled() || mRedo.isEnabled()) {
            mUndoRedo.setVisibility(View.VISIBLE);
        }

    }

    public RectF getDisplayViewRectF() {
        Rect rect = new Rect();
        mEraseView.getDrawingRect(rect);
        Log.d(TAG, "getDisplayViewRectF: " + rect);
        return new RectF(rect);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.d(TAG, "onProgressChanged: progress=" + progress + ", fromUser=" + fromUser);
        mStrokeWidth = progress;
        mEraseView.postInvalidate();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStartTrackingTouch: " + seekBar.getProgress());
        isChangeStrokeWidth = true;
        mEraseView.postInvalidate();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStopTrackingTouch: " + seekBar.getProgress());
        isChangeStrokeWidth = false;
        mEraseView.postInvalidate();
    }

    public boolean isChangeStrokeWidth() {
        return isChangeStrokeWidth;
    }

    public int getStrokeWidth() {
        return mStrokeWidth;
    }


    public void onViewInitFinish() {
        mActivity.startLoadBitmap();
    }

    public void setDisplayBitmap(final Bitmap bitmap) {
        Log.d(TAG, "setBitmap: bitmap=" + bitmap);
        mEraseView.setBitmap(bitmap);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (getState() != EraseManager.State.STATE_ERASE_FINISH) {
            mUndo.setEnabled(false);
            mRedo.setEnabled(false);
            mStrokeWidthBar.setEnabled(false);
            mManager.generate();
        } else {
            mManager.save();
        }
        return false;
    }

    public Bitmap getMaskBitmap() {
        return mEraseView.getMaskBitmap();
    }

    public ProgressDialog showProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        mProgressDialog = ProgressDialog.show(mActivity, null, mActivity.getString(R.string.processing_please_wait));
        return mProgressDialog;
    }

    public void dismissProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        //Toast.makeText(mActivity, "Smart erase complete!", Toast.LENGTH_SHORT).show();
    }

    public ProgressDialog showSaveProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        mProgressDialog = ProgressDialog.show(mActivity, null, mActivity.getString(R.string.saving_image));
        return mProgressDialog;
    }

    public Bitmap getOriginalBitmap() {
        return mManager.getOriginalBitmap();
    }

    public Size getOriginalBitmapSize() {
        return mManager.getOriginalBitmapSize();
    }

    public EraseManager.State getState() {
        return mManager.mState;
    }

    public void showErasedBitmap() {
        mEraseView.postInvalidate();
        mUndoRedo.setVisibility(View.INVISIBLE);
        mStrokeWidthView.setVisibility(View.INVISIBLE);
        mGenerateMenu.setTitle(mActivity.getString(R.string.save));
    }

    public Bitmap getInScreenErasedBitmap() {
        return mManager.getInScreenErasedBitmap();
    }

    public void quit() {
        mActivity.finish();
    }
}

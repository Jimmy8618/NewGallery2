package com.android.gallery3d.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.v2.util.ClickInterval;

/**
 * Created by apuser on 2/24/17.
 */

public class PhotoControlBottomBar extends LinearLayout implements View.OnClickListener {
    private static final String TAG = "PhotoControlBottomBar";
    private ImageButton mShareButton;
    private ImageButton mDistanceButton;
    private ImageButton mEditButton;
    private ImageButton mDeleteButton;
    private ImageButton mDetailsButton;
    private ImageButton mImageBlendingButton;
    private ImageButton mTrashRestoreButton;
    private ImageButton mTrashDeleteButton;

    private View mShareContainer;
    private View mDistanceContainer;
    private View mEditContainer;
    private View mDeleteContainer;
    private View mDetailsContainer;
    private View mImageBleningContainer;
    private View mTrashRestoreContainer;
    private View mTrashDeleteContainer;


    public interface OnPhotoControlBottomBarMenuClickListener {
        void onShareClick(View view);

        void onEditClick(View view);

        void onDeleteClick(View view);

        void onDetailsClick(View view);

        void onDistanceClick(View view);

        void onImageBlendingClick(View view);

        void onTrashRestoreClick(View view);

        void onTrashDeleteClick(View view);
    }

    private OnPhotoControlBottomBarMenuClickListener mOnPhotoControlBottomBarMenuClickListener;

    public void setOnPhotoControlBottomBarMenuClickListener(OnPhotoControlBottomBarMenuClickListener l) {
        mOnPhotoControlBottomBarMenuClickListener = l;
    }

    public PhotoControlBottomBar(Context context) {
        super(context);
    }

    public PhotoControlBottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoControlBottomBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PhotoControlBottomBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShareButton = findViewById(R.id.share);
        mDistanceButton = findViewById(R.id.distance);
        mEditButton = findViewById(R.id.edit);
        mDeleteButton = findViewById(R.id.delete);
        mDetailsButton = findViewById(R.id.details);
        mImageBlendingButton = findViewById(R.id.imageblending);
        mTrashRestoreButton = findViewById(R.id.trash_restore);
        mTrashDeleteButton = findViewById(R.id.trash_delete);

        mShareButton.setOnClickListener(this);
        mDistanceButton.setOnClickListener(this);
        mEditButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        mDetailsButton.setOnClickListener(this);
        mImageBlendingButton.setOnClickListener(this);
        mTrashRestoreButton.setOnClickListener(this);
        mTrashDeleteButton.setOnClickListener(this);

        mShareContainer = findViewById(R.id.share_container);
        mDistanceContainer = findViewById(R.id.distance_container);
        mEditContainer = findViewById(R.id.edit_container);
        mDeleteContainer = findViewById(R.id.delete_container);
        mDetailsContainer = findViewById(R.id.details_container);
        mImageBleningContainer = findViewById(R.id.imageblending_container);
        mTrashRestoreContainer = findViewById(R.id.trash_restore_container);
        mTrashDeleteContainer = findViewById(R.id.trash_delete_container);
    }

    @Override
    public void onClick(View view) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onClick ignore");
            return;
        }
        if (mOnPhotoControlBottomBarMenuClickListener != null) {
            switch (view.getId()) {
                case R.id.share:
                    mOnPhotoControlBottomBarMenuClickListener.onShareClick(view);
                    break;
                case R.id.distance:
                    mOnPhotoControlBottomBarMenuClickListener.onDistanceClick(view);
                    break;
                case R.id.edit:
                    mOnPhotoControlBottomBarMenuClickListener.onEditClick(view);
                    break;
                case R.id.delete:
                    mOnPhotoControlBottomBarMenuClickListener.onDeleteClick(view);
                    break;
                case R.id.details:
                    mOnPhotoControlBottomBarMenuClickListener.onDetailsClick(view);
                    break;
                case R.id.imageblending:
                    mOnPhotoControlBottomBarMenuClickListener.onImageBlendingClick(view);
                    break;
                case R.id.trash_restore:
                    mOnPhotoControlBottomBarMenuClickListener.onTrashRestoreClick(view);
                    break;
                case R.id.trash_delete:
                    mOnPhotoControlBottomBarMenuClickListener.onTrashDeleteClick(view);
                    break;
                default:
                    break;
            }
        }
    }

    public void updateMenuOperation(int supported) {
        boolean supportShare = (supported & MediaObject.SUPPORT_SHARE) != 0;
        boolean supportEdit = (supported & MediaObject.SUPPORT_EDIT) != 0;
        boolean supportDelete = (supported & MediaObject.SUPPORT_DELETE) != 0;
        boolean supportDetails = (supported & MediaObject.SUPPORT_INFO) != 0;
        boolean supportBlending = (supported & MediaObject.SUPPORT_BLENDING) != 0;
        boolean supportTrashRestore = (supported & MediaObject.SUPPORT_TRASH_RESTORE) != 0;
        boolean supportTrashDelete = (supported & MediaObject.SUPPORT_TRASH_DELETE) != 0;

        mShareButton.setVisibility(supportShare ? VISIBLE : GONE);
        mShareContainer.setVisibility(supportShare ? VISIBLE : GONE);
        //mDistanceButton.setVisibility(supportBlending ? VISIBLE : GONE);
        //mDistanceContainer.setVisibility(supportBlending ? VISIBLE : GONE);
        mDistanceButton.setVisibility(GONE);
        mDistanceContainer.setVisibility(GONE);
        mEditButton.setVisibility(supportEdit ? VISIBLE : GONE);
        mEditContainer.setVisibility(supportEdit ? VISIBLE : GONE);
        mDeleteButton.setVisibility(supportDelete ? VISIBLE : GONE);
        mDeleteContainer.setVisibility(supportDelete ? VISIBLE : GONE);
        mDetailsButton.setVisibility(supportDetails ? VISIBLE : GONE);
        mDetailsContainer.setVisibility(supportDetails ? VISIBLE : GONE);
        mImageBleningContainer.setVisibility(supportBlending ? VISIBLE : GONE);
        mImageBlendingButton.setVisibility(supportBlending ? VISIBLE : GONE);
        mTrashRestoreContainer.setVisibility(supportTrashRestore ? VISIBLE : GONE);
        mTrashRestoreButton.setVisibility(supportTrashRestore ? VISIBLE : GONE);
        mTrashDeleteContainer.setVisibility(supportTrashDelete ? VISIBLE : GONE);
        mTrashDeleteButton.setVisibility(supportTrashDelete ? VISIBLE : GONE);
    }
}


package com.android.gallery3d.filtershow.category;

import android.app.Activity;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.editors.EditorStraighten;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.imageshow.ImageStraighten;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.ui.RotateSeekBar;

public class CategoryGeometryPanel extends Fragment implements OnClickListener,
        RotateSeekBar.RotateSeekBarChangeListener {
    public static final String FRAGMENT_TAG = "CategoryGeometryPanel";
    private static final String PARAMETER_TAG = "currentPanel";

    private int mCurrentAdapter = MainPanel.GEOMETRY;
    private CategoryAdapter mAdapter;

    private RotateSeekBar mRotateSeekBar;

    private View mInvertButton;
    private View mRotateButton;

    private View mCropFreeButton;
    private View mCrop_4_3_Button;
    private View mCrop_16_9_Button;
    private View mCropSquareButton;
    private View mCropOriginalButton;

    public void setAdapter(int value) {
        mCurrentAdapter = value;
    }

    @Override
    public void onAttach(Activity activity) {
        // TODO Auto-generated method stub
        super.onAttach(activity);
        FilterShowActivity filterShowActivity = (FilterShowActivity) activity;
        mAdapter = filterShowActivity.getCategoryGeometryAdapter();
        filterShowActivity.hideMenuItems(true);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(PARAMETER_TAG, mCurrentAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        View main = inflater.inflate(R.layout.geometry_bottom_panel, container, false);

        int selectedPanel = mCurrentAdapter;
        if (savedInstanceState != null) {
            selectedPanel = savedInstanceState.getInt(PARAMETER_TAG);
            FilterShowActivity filterShowActivity = (FilterShowActivity) getActivity();
            mAdapter = filterShowActivity.getCategoryGeometryAdapter();
            mCurrentAdapter = selectedPanel;
        }

        mRotateSeekBar = main.findViewById(R.id.edit_rotateSeekBar);
        mRotateSeekBar.setRotateSeekBarChangeListener(this);
        mRotateSeekBar.setAngle(ImageStraighten.mSavedAngle);

        mInvertButton = main.findViewById(R.id.edit_invert);
        mRotateButton = main.findViewById(R.id.edit_rotate);

        mCropFreeButton = main.findViewById(R.id.edit_crop_free);
        mCrop_4_3_Button = main.findViewById(R.id.edit_crop_4_3);
        mCrop_16_9_Button = main.findViewById(R.id.edit_crop_16_9);
        mCropSquareButton = main.findViewById(R.id.edit_crop_square);
        mCropOriginalButton = main.findViewById(R.id.edit_crop_origin);

        mInvertButton.setOnClickListener(this);
        mRotateButton.setOnClickListener(this);

        mCropFreeButton.setOnClickListener(this);
        mCrop_4_3_Button.setOnClickListener(this);
        mCrop_16_9_Button.setOnClickListener(this);
        mCropSquareButton.setOnClickListener(this);
        mCropOriginalButton.setOnClickListener(this);

        return main;
    }

    @Override
    public void onClick(View view) {
        // TODO Auto-generated method stub
        switch (view.getId()) {
            case R.id.edit_invert:
                onClickEffectWithAction(mAdapter.getItem(3)); // 镜像
                break;
            case R.id.edit_rotate:
                onClickEffectWithAction(mAdapter.getItem(2)); // 旋转
                break;
            case R.id.edit_crop_free:
                onClickEffectWithAction(mAdapter.getItem(0)); // 裁剪
                break;
            case R.id.edit_crop_4_3:
                onClickEffectWithAction(mAdapter.getItem(4)); // 裁剪
                break;
            case R.id.edit_crop_16_9:
                onClickEffectWithAction(mAdapter.getItem(5)); // 裁剪
                break;
            case R.id.edit_crop_square:
                onClickEffectWithAction(mAdapter.getItem(6)); // 裁剪
                break;
            case R.id.edit_crop_origin:
                onClickEffectWithAction(mAdapter.getItem(7)); // 裁剪
                break;
        }
    }

    @Override
    public void onSeekBarTouchDown(RotateSeekBar seekBar) {
        // TODO Auto-generated method stub
        if (!((((FilterShowActivity) getActivity()).getCurrentEditor()) instanceof EditorStraighten)) {
            onClickEffectWithAction(mAdapter.getItem(1)); // 调节旋转
        }
        Editor editor = ((FilterShowActivity) getActivity()).getCurrentEditor();
        if (null != editor) {
            editor.getImageShow().onSeekBarTouchDown();
        }
    }

    @Override
    public void onProgressChanged(RotateSeekBar seekBar, int progress, int angle) {
        // TODO Auto-generated method stub
        Editor editor = ((FilterShowActivity) getActivity()).getCurrentEditor();
        if (null != editor) {
            editor.getImageShow().onSeekBarProgressChanged(progress, angle);
        }
    }

    @Override
    public void onSeekBarTouchUp(RotateSeekBar seekBar) {
        // TODO Auto-generated method stub
        Editor editor = ((FilterShowActivity) getActivity()).getCurrentEditor();
        if (null != editor) {
            editor.getImageShow().onSeekBarTouchUp();
        }
    }

    private void onClickEffectWithAction(Action action) {
        if (MasterImage.getImage().getPreset() != null) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            FilterRepresentation actionRep = action.getRepresentation();
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
    }

}

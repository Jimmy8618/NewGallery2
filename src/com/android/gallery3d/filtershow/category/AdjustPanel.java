package com.android.gallery3d.filtershow.category;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.history.HistoryManager;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

/**
 * Created by apuser on 3/2/17.
 */

public class AdjustPanel extends Fragment implements View.OnClickListener {
    public static final String FRAGMENT_TAG = "AdjustPanel";
    public static final boolean HIDE_IMAGE_VIGNETTE = true;

    private Editor mEditor;
    private int mEditorID;
    private SeekBar mSeekBar;

    private OnAdjustPanelListener mOnAdjustPanelListener;

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cancel:
                cancelCurrentFilter();
                FilterShowActivity activity = (FilterShowActivity) getActivity();
                activity.mNeedCommit = false;
                if (null != mOnAdjustPanelListener) {
                    mOnAdjustPanelListener.onCancelClicked();
                }
                MasterImage masterImage = MasterImage.getImage();
                masterImage.setEditorPanel(mEditor);
                break;
            case R.id.complete:
                if (mEditor != null) {
                    mEditor.finalApplyCalled();
                    mEditor.detach();
                }
                if (null != mOnAdjustPanelListener) {
                    mOnAdjustPanelListener.onSaveClicked();
                }
                break;
        }
    }

    public interface OnAdjustPanelListener {
        void onCancelClicked();

        void onSaveClicked();
    }

    public AdjustPanel() {
        super();
    }

    @SuppressLint("ValidFragment")
    public AdjustPanel(OnAdjustPanelListener l, int editorID) {
        super();
        mOnAdjustPanelListener = l;
        mEditorID = editorID;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        FilterShowActivity filterShowActivity = (FilterShowActivity) activity;
        mEditor = filterShowActivity.getEditor(mEditorID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View main = inflater.inflate(R.layout.layout_adjust_panel, container, false);
        main.findViewById(R.id.cancel).setOnClickListener(this);
        main.findViewById(R.id.complete).setOnClickListener(this);
        mSeekBar = main.findViewById(R.id.seekBar);

        FilterShowActivity filterShowActivity = (FilterShowActivity) getActivity();
        mEditor = filterShowActivity.getEditor(mEditorID);
        if (mEditor != null) {
            mEditor.setUpEditorUI(mSeekBar);
            mEditor.reflectCurrentFilter();
            mEditor.openUtilityPanel(null);
        }

        return main;
    }

    private void cancelCurrentFilter() {
        MasterImage masterImage = MasterImage.getImage();
        HistoryManager adapter = masterImage.getHistory();
        int position = adapter.undo();
        masterImage.onHistoryItemClick(position);
        ((FilterShowActivity) getActivity()).invalidateViews();
    }
}

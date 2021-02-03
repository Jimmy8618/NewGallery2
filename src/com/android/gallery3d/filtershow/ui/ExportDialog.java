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

package com.android.gallery3d.filtershow.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.fragment.app.DialogFragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

public class ExportDialog extends DialogFragment implements View.OnClickListener,
        SeekBar.OnSeekBarChangeListener {
    SeekBar mSeekBar;
    TextView mSeekVal;
    EditText mWidthText;
    EditText mHeightText;
    TextView mEstimatedSize;
    int mQuality = 95;
    int mExportWidth = 0;
    int mExportHeight = 0;
    Rect mOriginalBounds;
    int mCompressedSize;
    Rect mCompressedBounds;
    float mExportCompressionMargin = 1.1f;
    float mRatio;
    String mSliderLabel;
    boolean mEditing = false;
    Handler mHandler;
    int mUpdateDelay = 1000;
    Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateCompressionFactor();
            updateSize();
        }
    };

    private class Watcher implements TextWatcher {
        private EditText mEditText;

        Watcher(EditText text) {
            mEditText = text;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            textChanged(mEditText);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mHandler = new Handler(getActivity().getMainLooper());

        View view = inflater.inflate(R.layout.filtershow_export_dialog, container);
        mSeekBar = view.findViewById(R.id.qualitySeekBar);
        mSeekVal = view.findViewById(R.id.qualityTextView);
        mSliderLabel = getString(R.string.quality) + ": ";
        mSeekBar.setProgress(mQuality);
        mSeekVal.setText(mSliderLabel + mSeekBar.getProgress());
        mSeekBar.setOnSeekBarChangeListener(this);
        mWidthText = view.findViewById(R.id.editableWidth);
        mHeightText = view.findViewById(R.id.editableHeight);
        mEstimatedSize = view.findViewById(R.id.estimadedSize);

        mOriginalBounds = MasterImage.getImage().getOriginalBounds();
        ImagePreset preset = MasterImage.getImage().getPreset();
        // SPRD: fix bug 494387, mOriginalBounds and preset may be null
        if (mOriginalBounds == null || preset == null) {
            return null;
        }
        mOriginalBounds = preset.finalGeometryRect(mOriginalBounds.width(),
                mOriginalBounds.height());
        mRatio = mOriginalBounds.width() / (float) mOriginalBounds.height();
        mWidthText.setText("" + mOriginalBounds.width());
        // SPRD:bug 546302,modify focus position
        mWidthText.requestFocus();
        mHeightText.setText("" + mOriginalBounds.height());
        mExportWidth = mOriginalBounds.width();
        mExportHeight = mOriginalBounds.height();
        mWidthText.addTextChangedListener(new Watcher(mWidthText));
        mHeightText.addTextChangedListener(new Watcher(mHeightText));

        view.findViewById(R.id.cancel).setOnClickListener(this);
        view.findViewById(R.id.done).setOnClickListener(this);
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);

        updateCompressionFactor();
        updateSize();
        return view;
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // Do nothing
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // Do nothing
    }

    @Override
    public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
        mSeekVal.setText(mSliderLabel + arg1);
        mQuality = mSeekBar.getProgress();
        scheduleUpdateCompressionFactor();
    }

    private void scheduleUpdateCompressionFactor() {
        mHandler.removeCallbacks(mUpdateRunnable);
        mHandler.postDelayed(mUpdateRunnable, mUpdateDelay);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                dismiss();
                break;
            case R.id.done:
                requestExportImage();
                break;
            default:
                break;
        }
    }

    public void updateCompressionFactor() {
        Bitmap bitmap = MasterImage.getImage().getFilteredImage();
        if (bitmap == null) {
            return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, mQuality, out);
        mCompressedSize = out.size();
        mCompressedBounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    }

    public void updateSize() {
        if (mCompressedBounds == null) {
            return;
        }
        // This is a rough estimate of the final save size. There's some loose correlation
        // between a compressed jpeg and a larger version of it in function of the image
        // area. Not a perfect estimate by far.
        float originalArea = mCompressedBounds.width() * mCompressedBounds.height();
        float newArea = mExportWidth * mExportHeight;
        float factor = originalArea / (float) mCompressedSize;
        float compressedSize = newArea / factor;
        compressedSize *= mExportCompressionMargin;
        float size = compressedSize / 1024.f / 1024.f;
        size = ((int) (size * 100)) / 100f;
        // Modify for bug547857, use "MB" instead of "Mb" here
        String estimatedSize = "" + size + " MB";
        mEstimatedSize.setText(estimatedSize);
    }

    private void textChanged(EditText text) {
        if (mEditing) {
            return;
        }
        mEditing = true;
        int width = 1;
        int height = 1;
        if (text.getId() == R.id.editableWidth) {
            if (mWidthText.getText() != null) {
                String value = String.valueOf(mWidthText.getText());
                if (value.length() > 0) {
                    width = Integer.parseInt(value);
                    if (width > mOriginalBounds.width()) {
                        width = mOriginalBounds.width();
                        mWidthText.setText("" + width);
                    }
                    if (width <= 0) {
                        width = (int) Math.ceil(mRatio);
                        mWidthText.setText("" + width);
                    }
                    height = (int) (width / mRatio);
                }
                mHeightText.setText("" + height);
            }
        } else if (text.getId() == R.id.editableHeight) {
            if (mHeightText.getText() != null) {
                String value = String.valueOf(mHeightText.getText());
                if (value.length() > 0) {
                    height = Integer.parseInt(value);
                    if (height > mOriginalBounds.height()) {
                        height = mOriginalBounds.height();
                        mHeightText.setText("" + height);
                    }
                    if (height <= 0) {
                        height = 1;
                        mHeightText.setText("" + height);
                    }
                    width = (int) (height * mRatio);
                }
                mWidthText.setText("" + width);
            }
        }
        mExportWidth = width;
        mExportHeight = height;
        updateSize();
        mEditing = false;
    }

    private void requestExportImage() {
        final FilterShowActivity activity = (FilterShowActivity) getActivity();
        final File dest = SaveImage.getNewFile(activity, activity.getSelectedImageUri());
        if (!GalleryStorageUtil.isInInternalStorage(dest.getAbsolutePath())
                && !SdCardPermission.hasStoragePermission(dest.getAbsolutePath())) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    exportImage(activity, dest);
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(activity, null);
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(dest.getAbsolutePath());
            SdCardPermission.requestSdcardPermission(activity, storagePaths, activity, sdCardPermissionListener);
        } else {
            exportImage(activity, dest);
        }
    }

    private void exportImage(FilterShowActivity activity, File dest) {
        Uri sourceUri = MasterImage.getImage().getUri();
        float scaleFactor = mExportWidth / (float) mOriginalBounds.width();
        MasterImage.getImage().setSavedPreset(MasterImage.getImage().getPreset());
        Intent processIntent = ProcessingService.getSaveIntent(activity, dest,
                activity.getSelectedImageUri(), sourceUri, true, mSeekBar.getProgress(),
                scaleFactor, false);
        activity.startService(processIntent);
        dismiss();
    }
}

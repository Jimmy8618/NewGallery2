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

package com.android.gallery3d.filtershow.controller;

import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.editors.EditorGrad;

public class ActionSlider extends TitledSlider {
    private static final String LOGTAG = "ActionSlider";
    ImageButton mLeftButton;
    ImageButton mRightButton;

    public ActionSlider() {
        mLayoutID = R.layout.filtershow_control_action_slider;
    }

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        super.setUp(container, parameter, editor);
        mLeftButton = mTopView.findViewById(R.id.leftActionButton);
        mLeftButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                ((ParameterActionAndInt) mParameter).fireLeftAction();
            }
        });

        mRightButton = mTopView.findViewById(R.id.rightActionButton);
        mRightButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                ((ParameterActionAndInt) mParameter).fireRightAction();
            }
        });
        updateUI();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        /* SPRD: fix bug 532097, java.lang.ClassCastException @{ */
        if (!(mParameter instanceof ParameterActionAndInt)) {
            Log.d(LOGTAG, "mParameter not instanceof ParameterActionAndInt");
            if (mLeftButton != null && mEditor instanceof EditorGrad) {
                Log.d(LOGTAG, "mEditor instanceof EditorGrad");
                int iconId = ((ParameterActionAndInt) mEditor).getLeftIcon();
                mLeftButton.setImageResource(iconId);
            }
            if (mRightButton != null && mEditor instanceof EditorGrad) {
                Log.d(LOGTAG, "mEditor instanceof EditorGrad");
                int iconId = ((ParameterActionAndInt) mEditor).getRightIcon();
                mRightButton.setImageResource(iconId);
            }
            return;
        }
        /* @} */

        if (mLeftButton != null) {
            int iconId = ((ParameterActionAndInt) mParameter).getLeftIcon();
            mLeftButton.setImageResource(iconId);
        }
        if (mRightButton != null) {
            int iconId = ((ParameterActionAndInt) mParameter).getRightIcon();
            mRightButton.setImageResource(iconId);
        }
    }
}

/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

import java.util.HashMap;
import java.util.Map;

public class ActionBarTopControls implements OnClickListener {
    public interface ActionBarListener {
        void onTopControlClicked(int control);
    }

    private final ActionBarListener mListener;
    private final ViewGroup mParentLayout;
    private final ViewGroup mContainer;

    private final boolean mContainerVisible = false;
    private final Map<View, Boolean> mControlsVisible = new HashMap<View, Boolean>();

    private final Animation mContainerAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mContainerAnimOut = new AlphaAnimation(1f, 0f);
    private static final int CONTAINER_ANIM_DURATION_MS = 200;

    private static final int CONTROL_ANIM_DURATION_MS = 150;

    private static Animation getControlAnimForVisibility(boolean visible) {
        Animation anim = visible ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);
        anim.setDuration(CONTROL_ANIM_DURATION_MS);
        return anim;
    }

    public ActionBarTopControls(ActionBarListener listener, Context context, RelativeLayout layout) {
        mListener = listener;
        mParentLayout = layout;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContainer = (ViewGroup) inflater
                .inflate(R.layout.photopage_top_controls, mParentLayout, false);
        mParentLayout.addView(mContainer);
        mContainer.setVisibility(View.GONE);

        for (int i = mContainer.getChildCount() - 1; i >= 0; i--) {
            View child = mContainer.getChildAt(i);
            child.setOnClickListener(this);
            if (child instanceof ViewGroup) {
                for (int j = ((ViewGroup) child).getChildCount() - 1; j >= 0; j--) {
                    View children = ((ViewGroup) child).getChildAt(j);
                    children.setOnClickListener(this);
                    mControlsVisible.put(children, true);
                }
            }
        }

        mContainerAnimIn.setDuration(CONTAINER_ANIM_DURATION_MS);
        mContainerAnimOut.setDuration(CONTAINER_ANIM_DURATION_MS);
//        showTopControls();

    }

    public void hideTopControls() {
        mContainer.clearAnimation();
        mContainerAnimOut.reset();
        mContainer.startAnimation(mContainerAnimOut);
        mContainer.setVisibility(View.INVISIBLE);
    }

    public void showTopControls() {
        mContainer.clearAnimation();
        mContainerAnimIn.reset();
        mContainer.startAnimation(mContainerAnimIn);
        mContainer.setVisibility(View.VISIBLE);
    }

    public void refresh() {

    }

    public void cleanup() {
        mParentLayout.removeView(mContainer);
        mControlsVisible.clear();
    }

    @Override
    public void onClick(View view) {
        Boolean controlVisible = mControlsVisible.get(view);
        if (controlVisible != null && controlVisible.booleanValue()) {
            mListener.onTopControlClicked(view.getId());
        }
    }
}

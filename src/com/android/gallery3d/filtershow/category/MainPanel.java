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

package com.android.gallery3d.filtershow.category;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.state.StatePanel;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;

public class MainPanel extends Fragment {

    private static final String LOGTAG = "MainPanel";

    private LinearLayout mMainView;
    private ImageView looksButton;
    private ImageView bordersButton;
    private ImageView geometryButton;
    private ImageView filtersButton;
    private ImageView eraseButton;

    public static final String FRAGMENT_TAG = "MainPanel";
    public static final int LOOKS = 1;
    public static final int BORDERS = 0;
    public static final int GEOMETRY = 2;
    public static final int FILTERS = 3;
    public static final int VERSIONS = 4;

    private int mCurrentSelected = -1;
    private int mPrevSelected = -1;
    private int mPreviousToggleVersions = -1;

    private void selection(int position, boolean value) {
        if (value) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.setCurrentPanel(position);
        }
        switch (position) {
            case LOOKS: {
                looksButton.setSelected(value);
                break;
            }
            case BORDERS: {
                bordersButton.setSelected(value);
                break;
            }
            case GEOMETRY: {
                geometryButton.setSelected(value);
                break;
            }
            case FILTERS: {
                filtersButton.setSelected(value);
                break;
            }
        }
        setPanelVisible(position, value);
    }

    public int getPrevSelected() {
        return mPrevSelected;
    }

    private void setPanelVisible(int position, boolean selected) {
        if (!selected) {
            mPrevSelected = position;
        }
        View categoryPanel = mMainView == null ? null : mMainView
                .findViewById(R.id.category_panel_container);
        View bottomPanel = mMainView == null ? null : mMainView.findViewById(R.id.bottom_panel);
        View categoryGeometryPanel = mMainView == null ? null : mMainView
                .findViewById(R.id.category_geometry_panel_container);
        if (!selected) {
            if (position == GEOMETRY) {
                if (categoryPanel != null) {
                    categoryPanel.setVisibility(View.VISIBLE);
                }
                if (bottomPanel != null) {
                    bottomPanel.setVisibility(View.VISIBLE);
                }
                if (categoryGeometryPanel != null) {
                    categoryGeometryPanel.setVisibility(View.GONE);
                }
            }
        } else {
            if (position == GEOMETRY) {
                if (categoryPanel != null) {
                    categoryPanel.setVisibility(View.GONE);
                }
                if (bottomPanel != null) {
                    bottomPanel.setVisibility(View.GONE);
                }
                if (categoryGeometryPanel != null) {
                    categoryGeometryPanel.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMainView != null) {
            if (mMainView.getParent() != null) {
                ViewGroup parent = (ViewGroup) mMainView.getParent();
                parent.removeView(mMainView);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mMainView = (LinearLayout) inflater.inflate(
                R.layout.filtershow_main_panel, null, false);

        looksButton = mMainView.findViewById(R.id.fxButton);
        bordersButton = mMainView.findViewById(R.id.borderButton);
        geometryButton = mMainView.findViewById(R.id.geometryButton);
        filtersButton = mMainView.findViewById(R.id.colorsButton);
        eraseButton = mMainView.findViewById(R.id.eraseButton);
        if (!GalleryUtils.isSupportSmartErase()) {
            eraseButton.setVisibility(View.GONE);
        } else {
            eraseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MasterImage.getImage().getPreset() == null) {
                        return;
                    }
                    GalleryUtils.launchSmartErase(getContext(),
                            ((FilterShowActivity) getActivity()).getSelectedImageUri());
                }
            });
        }

        looksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* SPRD: fix bug 500338,crash when loading @{ */
                if (MasterImage.getImage().getPreset() == null) {
                    return;
                }
                /* @} */
                showPanel(LOOKS);
            }
        });
        bordersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* SPRD: fix bug 500338,crash when loading @{ */
                if (MasterImage.getImage().getPreset() == null) {
                    return;
                }
                /* @} */
                showPanel(BORDERS);
            }
        });
        geometryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* SPRD: fix bug 500338,crash when loading @{ */
                if (MasterImage.getImage().getPreset() == null) {
                    return;
                }
                /* @} */
                showPanel(GEOMETRY);
            }
        });
        filtersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* SPRD: fix bug 500338,crash when loading @{ */
                if (MasterImage.getImage().getPreset() == null) {
                    return;
                }
                /* @} */
                showPanel(FILTERS);
            }
        });
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        showImageStatePanel(activity.isShowingImageStatePanel());
        showPanel(activity.getCurrentPanel());
        return mMainView;
    }

    private boolean isRightAnimation(int newPos) {
        return newPos >= mCurrentSelected;
    }

    private void setCategoryFragment(CategoryPanel category, boolean fromRight, boolean animation) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (animation) {
            if (fromRight) {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right);
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left);
            }
        }
        transaction.replace(R.id.category_panel_container, category, CategoryPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    private void setCategoryGeometryPanel(CategoryGeometryPanel category, boolean fromRight, boolean animation) {
        View container = mMainView.findViewById(R.id.category_geometry_panel_container);
        FragmentTransaction transaction = null;
        if (container == null) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            container = activity.findViewById(R.id.category_geometry_panel_container);
            if (container == null) {
                return;
            } else {
                transaction = getFragmentManager().beginTransaction();
                Log.d(LOGTAG, "setCategoryGeometryPanel container find from FilterShowActivity,and use FragmentManager");
            }
        } else {
            transaction = getChildFragmentManager().beginTransaction();
            Log.d(LOGTAG, "setCategoryGeometryPanel container find from MainView, and use ChildFragmentManager");
        }

        if (animation) {
            if (fromRight) {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right);
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left);
            }
        }
        transaction.replace(R.id.category_geometry_panel_container, category, CategoryPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    public void loadCategoryLookPanel(boolean force, boolean animation) {
        if (!force && mCurrentSelected == LOOKS) {
            return;
        }
        boolean fromRight = isRightAnimation(LOOKS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(LOOKS);
        setCategoryFragment(categoryPanel, fromRight, animation);
        mCurrentSelected = LOOKS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryBorderPanel(boolean force, boolean animation) {
        if (!force && mCurrentSelected == BORDERS) {
            return;
        }
        boolean fromRight = isRightAnimation(BORDERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(BORDERS);
        setCategoryFragment(categoryPanel, fromRight, animation);
        mCurrentSelected = BORDERS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryGeometryPanel(boolean force, boolean animation) {
        if (!force && mCurrentSelected == GEOMETRY) {
            return;
        }
        if (MasterImage.getImage().hasTinyPlanet()) {
            return;
        }
        boolean fromRight = isRightAnimation(GEOMETRY);
        selection(mCurrentSelected, false);
        // CategoryPanel categoryPanel = new CategoryPanel();
        CategoryGeometryPanel categoryPanel = new CategoryGeometryPanel();
        categoryPanel.setAdapter(GEOMETRY);
        // setCategoryFragment(categoryPanel, fromRight);
        setCategoryGeometryPanel(categoryPanel, fromRight, animation);
        mCurrentSelected = GEOMETRY;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryFiltersPanel(boolean force, boolean animation) {
        if (!force && mCurrentSelected == FILTERS) {
            return;
        }
        boolean fromRight = isRightAnimation(FILTERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(FILTERS);
        setCategoryFragment(categoryPanel, fromRight, animation);
        mCurrentSelected = FILTERS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryVersionsPanel() {
        if (mCurrentSelected == VERSIONS) {
            return;
        }
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        activity.updateVersions();
        boolean fromRight = isRightAnimation(VERSIONS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(VERSIONS);
        setCategoryFragment(categoryPanel, fromRight, true);
        mCurrentSelected = VERSIONS;
        selection(mCurrentSelected, true);
    }

    public void showPanel(int currentPanel) {
        switch (currentPanel) {
            case LOOKS: {
                loadCategoryLookPanel(false, true);
                break;
            }
            case BORDERS: {
                loadCategoryBorderPanel(false, true);
                break;
            }
            case GEOMETRY: {
                loadCategoryGeometryPanel(false, true);
                break;
            }
            case FILTERS: {
                loadCategoryFiltersPanel(false, true);
                break;
            }
            case VERSIONS: {
                loadCategoryVersionsPanel();
                break;
            }
        }
    }

    public void setToggleVersionsPanelButton(ImageButton button) {
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentSelected == VERSIONS) {
                    showPanel(mPreviousToggleVersions);
                } else {
                    mPreviousToggleVersions = mCurrentSelected;
                    showPanel(VERSIONS);
                }
            }
        });
    }

    public void showImageStatePanel(boolean show) {
        View container = mMainView.findViewById(R.id.state_panel_container);
        Log.d(LOGTAG, "showImageStatePanel container = " + container + ", show = " + show);
        FragmentTransaction transaction = null;
        /* SPRD: bug 579629,Incorrect use of FragmentManager,Exception for No view found @{ */
        boolean child = false;
        if (container == null) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            container = activity.getMainStatePanelContainer(R.id.state_panel_container);
            Log.d(LOGTAG, "showImageStatePanel getMainStatePanelContainer " + container);
            if (container == null) {
                return;
            } else {
                transaction = getFragmentManager().beginTransaction();
                Log.d(LOGTAG, "container find from FilterShowActivity,and use FragmentManager");
            }
        } else {
            transaction = getChildFragmentManager().beginTransaction();
            child = true;
            Log.d(LOGTAG, "container find from MainView,and use ChildFragmentManager");
        }
        /* @} */

        int currentPanel = mCurrentSelected;
        if (show) {
            container.setVisibility(View.VISIBLE);
            StatePanel statePanel = new StatePanel();
            statePanel.setMainPanel(this);
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.updateVersions();
            transaction.replace(R.id.state_panel_container, statePanel, StatePanel.FRAGMENT_TAG);
        } else {
            /* SPRD: bug 579629,Incorrect use of FragmentManager,Exception for No view found @{ */
            container.setVisibility(View.GONE);
            Fragment statePanel = getFragmentManager().findFragmentByTag(StatePanel.FRAGMENT_TAG);
            if (child || statePanel == null) {
                statePanel = getChildFragmentManager().findFragmentByTag(StatePanel.FRAGMENT_TAG);
            }
            /* @} */
            if (statePanel != null) {
                transaction.remove(statePanel);
            }
            if (currentPanel == VERSIONS) {
                currentPanel = BORDERS;
            }
        }
        mCurrentSelected = -1;
        showPanel(currentPanel);
        // SPRD: fix bug 502700,IllegalStateException in monkey test
        // transaction.commit();
        transaction.commitAllowingStateLoss();
    }
}

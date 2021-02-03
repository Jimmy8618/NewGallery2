package com.android.gallery3d.v2.tab;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.gallery3d.v2.util.PermissionUtil;

public abstract class TabBaseFragment extends Fragment {
    private boolean mLoadDataCalled = false;
    private int mIcon;
    private int mTitle;
    private int mTabId;

    public abstract void loadData();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isStateSaved() && PermissionUtil.hasPermissions(getContext())) {
            mLoadDataCalled = true;
            loadData();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStateSaved() && !mLoadDataCalled && PermissionUtil.hasPermissions(getContext())) {
            mLoadDataCalled = true;
            loadData();
        }
    }

    public void setIcon(int icon) {
        mIcon = icon;
    }

    public int getIcon() {
        return mIcon;
    }

    public void setTitle(int title) {
        mTitle = title;
    }

    public int getTitle() {
        return mTitle;
    }

    public void setTabId(int tabId) {
        mTabId = tabId;
    }

    public int getTabId() {
        return mTabId;
    }
}

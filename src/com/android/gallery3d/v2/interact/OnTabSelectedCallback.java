package com.android.gallery3d.v2.interact;

import androidx.annotation.Nullable;

import com.android.gallery3d.v2.tab.TabItemView;

/**
 * @author baolin.li
 */
public interface OnTabSelectedCallback {
    /**
     * 底部tab切换选中时回调
     *
     * @param tab tab
     */
    void onTabSelected(@Nullable TabItemView tab);
}

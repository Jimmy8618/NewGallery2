package com.android.gallery3d.v2.page;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.AlbumSetLoadingListener;
import com.android.gallery3d.v2.data.AlbumSetPageDataLoader;
import com.android.gallery3d.v2.ui.DiscoverContentPageUI;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;

import java.util.ArrayList;
import java.util.List;

public class DiscoverContentPageFragment extends BasePageFragment implements AlbumSetLoadingListener,
        DiscoverContentPageUI.OnAlbumSetItemClickListener {
    private static final String TAG = DiscoverContentPageFragment.class.getSimpleName();

    private MediaSet mMediaSet;
    private AlbumSetPageDataLoader mDataLoader;

    private DiscoverContentPageUI mPageUI;
    private final List<AlbumSetItem> mData = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discover_content_page, container, false);
        mPageUI = v.findViewById(R.id.discover_content_page_ui);
        mPageUI.setOnAlbumSetItemClickListener(this);
        mPageUI.bind(this);

        if (isNextPage()) {//隐藏底部Tab
            setTabsVisible(false);
        }
        return v;
    }

    public List<AlbumSetItem> getData() {
        return mData;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (PermissionUtil.hasPermissions(getContext())) {
            if (mMediaSet == null) {
                String mediaPath = getArguments().getString(Constants.KEY_BUNDLE_MEDIA_SET_PATH);
                mMediaSet = getDataManager().getMediaSet(mediaPath);
                mDataLoader = new AlbumSetPageDataLoader(mMediaSet, null);
                mDataLoader.setLoadingListener(this);
                Log.d(TAG, "mMediaSet(" + mMediaSet + "), mediaPath(" + mediaPath + ").");
            }
            //如果Fragment被隐藏,则不load task 加载数据, 在 onShow 中会调用
            if (!isHidden()) {
                mDataLoader.resume();
            }
        }
        if (isNextPage() && mMediaSet != null) {
            setNavigationTitle(mMediaSet.getName());
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
        if (mDataLoader != null) {
            mDataLoader.resume();
        }
        if (isNextPage() && mMediaSet != null) {
            setNavigationTitle(mMediaSet.getName());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (mDataLoader != null) {
            mDataLoader.pause();
        }
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
        if (mDataLoader != null) {
            mDataLoader.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void loadStart() {
        mPageUI.loadStart();
    }

    @Override
    public void loading(int index, int size, AlbumSetItem[] item) {
        mPageUI.loading(index, size, item);
    }

    @Override
    public void loadEnd() {
        mPageUI.loadEnd();
    }

    @Override
    public void loadEmpty() {
        mPageUI.loadEmpty();
    }

    @Override
    public void onAlbumSetItemClick(View v, MediaSet mediaSet) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onAlbumSetItemClick ignore");
            return;
        }
        if (isStateSaved()) {
            Log.d(TAG, "onAlbumSetItemClick: fragment's state has been saved, ignore");
            return;
        }
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "onAlbumSetItemClick fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment != null) {
            AlbumPageFragment albumPageFragment = new AlbumPageFragment();
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, mediaSet.getPath().toString());
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            albumPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .hide(fragment)
                    .add(containerId, albumPageFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}

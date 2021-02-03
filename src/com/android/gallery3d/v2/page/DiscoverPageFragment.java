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
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.AlbumSetLoadingListener;
import com.android.gallery3d.v2.data.AlbumSetPageDataLoader;
import com.android.gallery3d.v2.data.DiscoverItem;
import com.android.gallery3d.v2.discover.data.LocationAlbumSet;
import com.android.gallery3d.v2.discover.data.PeopleAlbumSet;
import com.android.gallery3d.v2.discover.data.StoryAlbumSet;
import com.android.gallery3d.v2.discover.data.ThingsAlbumSet;
import com.android.gallery3d.v2.interact.OnTabSelectedCallback;
import com.android.gallery3d.v2.tab.TabDiscoverFragment;
import com.android.gallery3d.v2.tab.TabItemView;
import com.android.gallery3d.v2.ui.DiscoverPageUI;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.sprd.frameworks.StandardFrameworks;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class DiscoverPageFragment extends BasePageFragment implements DiscoverPageUI.DiscoverItemClickListener,
        OnTabSelectedCallback {
    private static final String TAG = DiscoverPageFragment.class.getSimpleName();

    private static boolean isSupportThings = true;
    private static boolean isSupportPeople = true;
    private static boolean isSupportLocation = StandardFrameworks.getInstances().isSupportLocation();
    private static boolean isSupportStory = StandardFrameworks.getInstances().isSupportStory();

    private List<DiscoverItem> mData = null;
    private DiscoverPageUI mPageUI;

    private MediaSet mThingsAlbumSet;
    private AlbumSetPageDataLoader mThingsAlbumSetDataLoader;

    private MediaSet mPeopleAlbumSet;
    private AlbumSetPageDataLoader mPeopleAlbumSetDataLoader;

    private MediaSet mLocationAlbumSet;
    private AlbumSetPageDataLoader mLocationAlbumSetDataLoader;

    private MediaSet mStoryAlbumSet;
    private AlbumSetPageDataLoader mStoryAlbumSetDataLoader;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
    }

    public synchronized List<DiscoverItem> getData() {
        if (mData == null) {
            mData = new ArrayList<>();
            if (isSupportThings) {
                mData.add(new DiscoverItem(DiscoverItem.TYPE_THINGS, R.string.tf_discover_things, mData.size()));
            }
            if (isSupportPeople) {
                mData.add(new DiscoverItem(DiscoverItem.TYPE_PEOPLE, R.string.tf_discover_people, mData.size()));
            }
            if (isSupportLocation) {
                mData.add(new DiscoverItem(DiscoverItem.TYPE_LOCATION, R.string.tf_discover_location, mData.size()));
            }
            if (isSupportStory) {
                mData.add(new DiscoverItem(DiscoverItem.TYPE_STORY, R.string.tf_discover_story, mData.size()));
            }
        }
        return mData;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discover_page, container, false);
        mPageUI = v.findViewById(R.id.discover_page_ui);
        mPageUI.bind(this);
        mPageUI.setDiscoverItemClickListener(this);
        registerTabSelectedCallback(this);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (PermissionUtil.hasPermissions(getContext())) {
            if (isSupportThings) {
                if (mThingsAlbumSet == null) {
                    mThingsAlbumSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(ThingsAlbumSet.PATH_ALBUM_SET);
                    mThingsAlbumSetDataLoader = new AlbumSetPageDataLoader(mThingsAlbumSet, null);
                    mThingsAlbumSetDataLoader.setLoadingListener(new AlbumSetLoadingListener() {
                        @Override
                        public void loadStart() {
                            mPageUI.loadStart(DiscoverItem.TYPE_THINGS);
                        }

                        @Override
                        public void loading(int index, int size, AlbumSetItem[] item) {
                            mPageUI.loading(DiscoverItem.TYPE_THINGS, index, size, item);
                        }

                        @Override
                        public void loadEnd() {
                            mPageUI.loadEnd(DiscoverItem.TYPE_THINGS);
                        }

                        @Override
                        public void loadEmpty() {
                            mPageUI.loadEmpty(DiscoverItem.TYPE_THINGS);
                        }
                    });
                }
                //mThingsAlbumSetDataLoader.resume();
            }
            if (isSupportPeople) {
                if (mPeopleAlbumSet == null) {
                    mPeopleAlbumSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(PeopleAlbumSet.PATH_ALBUM_SET);
                    mPeopleAlbumSetDataLoader = new AlbumSetPageDataLoader(mPeopleAlbumSet, null);
                    mPeopleAlbumSetDataLoader.setLoadingListener(new AlbumSetLoadingListener() {
                        @Override
                        public void loadStart() {
                            mPageUI.loadStart(DiscoverItem.TYPE_PEOPLE);
                        }

                        @Override
                        public void loading(int index, int size, AlbumSetItem[] item) {
                            mPageUI.loading(DiscoverItem.TYPE_PEOPLE, index, size, item);
                        }

                        @Override
                        public void loadEnd() {
                            mPageUI.loadEnd(DiscoverItem.TYPE_PEOPLE);
                        }

                        @Override
                        public void loadEmpty() {
                            mPageUI.loadEmpty(DiscoverItem.TYPE_PEOPLE);
                        }
                    });
                }
                //mPeopleAlbumSetDataLoader.resume();
            }
            if (isSupportLocation) {
                if (mLocationAlbumSet == null) {
                    mLocationAlbumSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(LocationAlbumSet.PATH_ALBUM_SET);
                    mLocationAlbumSetDataLoader = new AlbumSetPageDataLoader(mLocationAlbumSet, null);
                    mLocationAlbumSetDataLoader.setLoadingListener(new AlbumSetLoadingListener() {
                        @Override
                        public void loadStart() {
                            mPageUI.loadStart(DiscoverItem.TYPE_LOCATION);
                        }

                        @Override
                        public void loading(int index, int size, AlbumSetItem[] item) {
                            mPageUI.loading(DiscoverItem.TYPE_LOCATION, index, size, item);
                        }

                        @Override
                        public void loadEnd() {
                            mPageUI.loadEnd(DiscoverItem.TYPE_LOCATION);
                        }

                        @Override
                        public void loadEmpty() {
                            mPageUI.loadEmpty(DiscoverItem.TYPE_LOCATION);
                        }
                    });
                }
                //mLocationAlbumSetDataLoader.resume();
            }
            if (isSupportStory) {
                if (mStoryAlbumSet == null) {
                    mStoryAlbumSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(StoryAlbumSet.PATH_ALBUM_SET);
                    mStoryAlbumSetDataLoader = new AlbumSetPageDataLoader(mStoryAlbumSet, null);
                    mStoryAlbumSetDataLoader.setLoadingListener(new AlbumSetLoadingListener() {
                        @Override
                        public void loadStart() {
                            mPageUI.loadStart(DiscoverItem.TYPE_STORY);
                        }

                        @Override
                        public void loading(int index, int size, AlbumSetItem[] item) {
                            mPageUI.loading(DiscoverItem.TYPE_STORY, index, size, item);
                        }

                        @Override
                        public void loadEnd() {
                            mPageUI.loadEnd(DiscoverItem.TYPE_STORY);
                        }

                        @Override
                        public void loadEmpty() {
                            mPageUI.loadEmpty(DiscoverItem.TYPE_STORY);
                        }
                    });
                }
                //mStoryAlbumSetDataLoader.resume();
            }
            //如果Fragment被隐藏,则不load task 加载数据, 在 onShow 中会调用
            if (!isHidden()) {
                onTabSelected(getCurrentTab());
            }
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
        if (mThingsAlbumSetDataLoader != null) {
            mThingsAlbumSetDataLoader.resume();
        }
        if (mPeopleAlbumSetDataLoader != null) {
            mPeopleAlbumSetDataLoader.resume();
        }
        if (mLocationAlbumSetDataLoader != null) {
            mLocationAlbumSetDataLoader.resume();
        }
        if (mStoryAlbumSetDataLoader != null) {
            mStoryAlbumSetDataLoader.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (mThingsAlbumSetDataLoader != null) {
            mThingsAlbumSetDataLoader.pause();
        }
        if (mPeopleAlbumSetDataLoader != null) {
            mPeopleAlbumSetDataLoader.pause();
        }
        if (mLocationAlbumSetDataLoader != null) {
            mLocationAlbumSetDataLoader.pause();
        }
        if (mStoryAlbumSetDataLoader != null) {
            mStoryAlbumSetDataLoader.pause();
        }
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
        if (mThingsAlbumSetDataLoader != null) {
            mThingsAlbumSetDataLoader.pause();
        }
        if (mPeopleAlbumSetDataLoader != null) {
            mPeopleAlbumSetDataLoader.pause();
        }
        if (mLocationAlbumSetDataLoader != null) {
            mLocationAlbumSetDataLoader.pause();
        }
        if (mStoryAlbumSetDataLoader != null) {
            mStoryAlbumSetDataLoader.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onDiscoverItemClicked(DiscoverItem item) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onDiscoverItemClicked ignore");
            return;
        }
        MediaSet mediaSet = null;
        if (item.getType() == DiscoverItem.TYPE_THINGS) {
            mediaSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(ThingsAlbumSet.PATH_ALBUM_SET);
        } else if (item.getType() == DiscoverItem.TYPE_PEOPLE) {
            mediaSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(PeopleAlbumSet.PATH_ALBUM_SET);
        } else if (item.getType() == DiscoverItem.TYPE_LOCATION) {
            mediaSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(LocationAlbumSet.PATH_ALBUM_SET);
        } else if (item.getType() == DiscoverItem.TYPE_STORY) {
            mediaSet = GalleryAppImpl.getApplication().getDataManager().getMediaSet(StoryAlbumSet.PATH_ALBUM_SET);
        }

        if (mediaSet == null) {
            return;
        }

        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "onDiscoverItemClicked fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment != null) {
            DiscoverContentPageFragment discoverContentPageFragment = new DiscoverContentPageFragment();
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, mediaSet.getPath().toString());
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            discoverContentPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .hide(fragment)
                    .add(containerId, discoverContentPageFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onTabSelected(@Nullable TabItemView tab) {
        if (tab == null) {
            return;
        }
        if (TabDiscoverFragment.class.getSimpleName().equals(tab.getCurrentTab())) {
            Log.d(TAG, "onTabSelected " + tab.getCurrentTab());
            runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!isPaused()) {
                        if (mThingsAlbumSetDataLoader != null) {
                            mThingsAlbumSetDataLoader.resume();
                        }
                        if (mPeopleAlbumSetDataLoader != null) {
                            mPeopleAlbumSetDataLoader.resume();
                        }
                        if (mLocationAlbumSetDataLoader != null) {
                            mLocationAlbumSetDataLoader.resume();
                        }
                        if (mStoryAlbumSetDataLoader != null) {
                            mStoryAlbumSetDataLoader.resume();
                        }
                    }
                }
            }, LOAD_DELAY);
        } else {
            //滑动到非当前界面, 停止加载数据
            if (mThingsAlbumSetDataLoader != null) {
                mThingsAlbumSetDataLoader.pause();
            }
            if (mPeopleAlbumSetDataLoader != null) {
                mPeopleAlbumSetDataLoader.pause();
            }
            if (mLocationAlbumSetDataLoader != null) {
                mLocationAlbumSetDataLoader.pause();
            }
            if (mStoryAlbumSetDataLoader != null) {
                mStoryAlbumSetDataLoader.pause();
            }
        }
    }
}

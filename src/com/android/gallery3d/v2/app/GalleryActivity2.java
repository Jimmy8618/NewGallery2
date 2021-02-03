package com.android.gallery3d.v2.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.gallery3d.v2.cust.BasePageFragment;
import com.google.android.material.tabs.TabLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.appcompat.widget.Toolbar;

import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.cust.SprdViewPager;
import com.android.gallery3d.v2.interact.OnTabSelectedCallback;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.interact.ViewActionListener;
import com.android.gallery3d.v2.page.PhotoViewPageFragment;
import com.android.gallery3d.v2.tab.TabAlbumFragment;
import com.android.gallery3d.v2.tab.TabBaseFragment;
import com.android.gallery3d.v2.tab.TabDiscoverFragment;
import com.android.gallery3d.v2.tab.TabPhotoFragment;
import com.android.gallery3d.v2.tab.TabItemView;
import com.android.gallery3d.v2.util.Config;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.GalleryActivityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * @author baolin.li
 */
public class GalleryActivity2 extends BaseActivity implements TabLayout.OnTabSelectedListener,
        ViewActionListener, Animation.AnimationListener, SdCardPermissionAccessor,BasePageFragment.HideMenuListener{
    private static final String TAG = GalleryActivity2.class.getSimpleName();

    private Toolbar mToolbar;
    private SprdViewPager mViewPager;
    private View mTabContainer;
    private TabLayout mTabLayout;
    private View mInterceptView;
    private View mCoverView;
    private boolean mHasPermission;

    private static final int FADE_DURATION = 400;
    private final Animation mAnimIn = new AlphaAnimation(0f, 1f);
    private final Animation mAnimOut = new AlphaAnimation(1f, 0f);

    private SdCardPermissionListener mSdCardPermissionListener;

    private final WeakHashMap<OnTabSelectedCallback, Object> mTabSelectedCallbacks = new WeakHashMap<>();

    @Override
    public int getContentViewLayoutId() {
        initAlbumSet();
        return R.layout.activity_gallery_v2;
    }

    private void initAlbumSet() {
        if (!PermissionUtil.hasPermissions(this)) {
            return;
        }
        String action = getIntent().getAction();
        if (Intent.ACTION_MAIN.equalsIgnoreCase(action)) {
            //String mediaSetPath = getDataManager().getTopSetPath(DataManager.INCLUDE_ALL);
            String mediaSetPath = getDataManager().getTopSetPath(DataManager.INCLUDE_CAMERA_ONLY);
            if (mediaSetPath == null) {
                return;
            }
            MediaSet mediaSet = getDataManager().getMediaSet(mediaSetPath);
            if (mediaSet == null) {
                return;
            }
            Log.d(TAG, "initAlbumSet mediaSet = " + mediaSet);
            mediaSet.reload();
        }
    }

    @Override
    public void initViews(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "initViews fm(" + getSupportFragmentManager() + ")");
        mAnimIn.setDuration(FADE_DURATION);
        mAnimIn.setAnimationListener(this);
        mAnimOut.setDuration(FADE_DURATION);
        mAnimOut.setAnimationListener(this);
        mToolbar = findViewById(R.id.toolbar);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mToolbar.getLayoutParams();
        params.topMargin = GalleryUtils.getStatusBarHeight(this);
        mToolbar.setLayoutParams(params);
        setOtherNavigation(null);

        mViewPager = findViewById(R.id.pager);
        mTabContainer = findViewById(R.id.tab_container);
        mTabLayout = findViewById(R.id.tabs);
        mInterceptView = findViewById(R.id.intercept_view);
        mCoverView = findViewById(R.id.cover_view);

        mHasPermission = PermissionUtil.hasPermissions(this);

        //add for google photos view special images.
        if (GalleryUtils.isSprdPhotoEdit()) {
            if (getIntent().getData() != null) {
                getIntent().setAction(Intent.ACTION_VIEW);
            }
        }

        if (isViewActionIntent()) {
            if (!mHasPermission) {
                if (GalleryUtils.isSprdPhotoEdit()) {
                    mCoverView.setVisibility(View.VISIBLE);
                } else {
                    setCoverVisible(true);
                }
            }
        } else {
            //combine ViewPager and TabLayout
            initTabs(-1);
        }
    }

    private void initTabs(int tabIndex) {
        MyPagerAdapter adapter = new MyPagerAdapter(getSupportFragmentManager(), getIntent());
        mViewPager.setOffscreenPageLimit(adapter.getCount());
        mViewPager.setAdapter(adapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

        mTabLayout.addOnTabSelectedListener(this);
        mTabLayout.setupWithViewPager(mViewPager);

        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            if (tab != null) {
                tab.setCustomView(adapter.getTabView(i));
            }
        }

        if (mTabLayout.getTabCount() < 2) {
            mTabContainer.setVisibility(View.GONE);
        } else {
            mTabContainer.setVisibility(View.VISIBLE);
        }

        if (isMainIntent()) {
            int index = Config.getPref(Constants.KEY_LAST_SELECTED_TAB_INDEX, -1);
            if (tabIndex >= 0) {
                index = tabIndex;
            }
            mViewPager.setCurrentItem(index);
        }

        resetNavigationTitle();
    }

    @Override
    public void loadData() {
        if (isViewActionIntent()) {
            Log.d(TAG, "loadData view-action");
            if (!mHasPermission) {
                setCoverVisible(false);
            }
            GalleryUtils.onViewAction(this, this);
        }
        mHasPermission = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        resetNavigationTitle();

        if (getIntent().getBooleanExtra(Constants.KEY_SECURE_CAMERA, false)) {
            WindowManager.LayoutParams winParams = getWindow().getAttributes();
            winParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            getWindow().setAttributes(winParams);
        }
    }

    private boolean needDestroy() {
        return getIntent().getBooleanExtra(Constants.KEY_BUNDLE_NEED_DESTROY_ACTIVITY, false);
    }

    @Override
    public void onBackPressed() {
        if (isBackConsumed()) {
            Log.d(TAG, "onBackPressed consumed.");
        } else {
            //如果是 MainIntent 的, 按  back 键就不走 onDestroy , 就相当于按了 home 键处理
            if (getSupportFragmentManager().getBackStackEntryCount() == 0 && isMainIntent()
                    && !needDestroy()) {//如果是从相机进来的, 按back键需要走destroy流程
                moveTaskToBack(true);
            } else {
                //其它方式就按正常 back 流程走
                super.onBackPressed();
            }
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTabsVisible(true);
                resetNavigationTitle();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        /*
        if (isMainIntent()) {
            Config.setPref(Constants.KEY_LAST_SELECTED_TAB_INDEX, mViewPager.getCurrentItem());
        }
        */
    }

    public void onGalleryIconClicked() {
        if (!isViewActionIntent()) {
            return;
        }
        Log.d(TAG, "on gallery icon clicked at PhotoViewPageFragment");

        //设置为 Main Action, 加载 Main Action 的UI界面
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(Constants.KEY_BUNDLE_NEED_DESTROY_ACTIVITY, true);
        setIntent(intent);
        initTabs(0);

        //去掉PhotoViewPageFragment显示
        int containerId = R.id.fragment_full_container;
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        if (fragment != null) {
            fm.beginTransaction()
                    .remove(fragment)
                    .commit();
        }
    }

    @Override
    public void onViewAction(String mediaSetPath, String mediaItemPath) {
        Log.d(TAG, "onViewAction mediaSetPath = " + mediaSetPath + ", mediaItemPath = " + mediaItemPath);

        //如果都为null, 则直接加载tabs页, 不起动 PhotoViewPageFragment
        if (mediaSetPath == null && mediaItemPath == null) {
            //设置为 Main Action, 加载 Main Action 的UI界面
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            setIntent(intent);
            initTabs(0);
            return;
        }

        boolean singleItemOnly = (mediaSetPath == null) || getIntent().getBooleanExtra("SingleItemOnly", false);
        boolean startFromWidget = getIntent().getBooleanExtra(Constants.KEY_START_FROM_WIDGET, false);
        boolean readOnly = getIntent().getBooleanExtra(Constants.KEY_BUNDLE_MEDIA_ITEM_READ_ONLY, !getIntent().getBooleanExtra(Constants.KEY_CAMERA_ALBUM, false));

        Bundle bundle = getIntent().getExtras() != null ? new Bundle(getIntent().getExtras()) : new Bundle();
        int containerId = R.id.fragment_full_container;
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "onViewAction fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment == null) {
            PhotoViewPageFragment photoViewPageFragment = new PhotoViewPageFragment();
            bundle.putInt(Constants.KEY_BUNDLE_CONTAINER_ID, containerId);
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, mediaSetPath);
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, mediaItemPath);
            bundle.putBoolean(Constants.KEY_BUNDLE_MEDIA_ITEM_READ_ONLY, false);
            bundle.putBoolean(Constants.SINGLE_ITEM_ONLY, singleItemOnly);
            bundle.putBoolean(Constants.KEY_START_FROM_WIDGET, startFromWidget);
            bundle.putBoolean(Constants.KEY_BUNDLE_MEDIA_ITEM_READ_ONLY, readOnly);
            photoViewPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .add(containerId, photoViewPageFragment)
                    .commit();
        }
    }

    private class MyPagerAdapter extends FragmentPagerAdapter {
        private final List<TabBaseFragment> mFragments = new ArrayList<>();

        MyPagerAdapter(FragmentManager fm, Intent intent) {
            super(fm);
            if (isGetContentIntent() || isWidgetGetAlbumIntent()) {
                if (Intent.ACTION_PICK.equalsIgnoreCase(intent.getAction())) {
                    String type = Utils.ensureNotNull(intent.getType());
                    if (type.startsWith("vnd.android.cursor.dir/")) {
                        if (type.endsWith("/image")) {
                            intent.setType("image/*");
                        }
                        if (type.endsWith("/video")) {
                            intent.setType("video/*");
                        }
                    }
                }
                //添加"相册"
                Bundle data = intent.getExtras() != null ? new Bundle(intent.getExtras()) : new Bundle();
                GalleryActivityUtils.getInstance().startGetContentSetAs(intent, data);
                int typeBits;
                if (isWidgetGetAlbumIntent()) {
                    typeBits = DataManager.INCLUDE_IMAGE;
                } else {
                    typeBits = GalleryUtils.determineTypeBits(GalleryActivity2.this, intent);
                }
                data.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(typeBits));
                //TabAlbumFragment
                TabAlbumFragment fragment = instanceOfTabAlbum(fm);
                fragment.setTitle(R.string.v2_album);
                fragment.setTabId(R.id.v2_album);
                fragment.setIcon(R.drawable.ic_tab_album);
                fragment.setArguments(data);
                mFragments.add(fragment);
            } else if (isViewActionIntent()) {
                //do nothing
            } else {
                //添加"照片"
                Bundle photoBundle = intent.getExtras() != null ? new Bundle(intent.getExtras()) : new Bundle();
                photoBundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_CAMERA_ONLY));
                //TabPhotoFragment
                TabPhotoFragment tabPhoto = instanceOfTabPhoto(fm);
                tabPhoto.setTitle(R.string.v2_photo);
                tabPhoto.setTabId(R.id.v2_photo);
                tabPhoto.setIcon(R.drawable.ic_tab_photo);
                tabPhoto.setArguments(photoBundle);
                mFragments.add(tabPhoto);
                //添加"相册"
                Bundle albumBundle = intent.getExtras() != null ? new Bundle(intent.getExtras()) : new Bundle();
                albumBundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
                //TabAlbumFragment
                TabAlbumFragment tabAlbum = instanceOfTabAlbum(fm);
                tabAlbum.setHideMenuListener(GalleryActivity2.this);
                tabAlbum.setTitle(R.string.v2_album);
                tabAlbum.setTabId(R.id.v2_album);
                tabAlbum.setIcon(R.drawable.ic_tab_album);
                tabAlbum.setArguments(albumBundle);
                mFragments.add(tabAlbum);
                if (StandardFrameworks.getInstances().isSupportAIEngine()) {
                    //添加"发现"
                    Bundle discoverBundle = intent.getExtras() != null ? new Bundle(intent.getExtras()) : new Bundle();
                    discoverBundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
                    //TabDiscoverFragment
                    TabDiscoverFragment tabDiscover = instanceOfTabDiscover(fm);
                    tabDiscover.setTitle(R.string.v2_discover);
                    tabDiscover.setTabId(R.id.v2_discover);
                    tabDiscover.setIcon(R.drawable.ic_tab_discover);
                    tabDiscover.setArguments(discoverBundle);
                    mFragments.add(tabDiscover);
                }
            }
        }

        private TabPhotoFragment instanceOfTabPhoto(FragmentManager fragmentManager) {
            List<Fragment> fragments = fragmentManager.getFragments();
            if (null == fragments) {
                return new TabPhotoFragment();
            }
            for (Fragment fragment : fragments) {
                if (fragment instanceof TabPhotoFragment) {
                    return (TabPhotoFragment) fragment;
                }
            }
            return new TabPhotoFragment();
        }

        private TabAlbumFragment instanceOfTabAlbum(FragmentManager fragmentManager) {
            List<Fragment> fragments = fragmentManager.getFragments();
            if (null == fragments) {
                return new TabAlbumFragment();
            }
            for (Fragment fragment : fragments) {
                if (fragment instanceof TabAlbumFragment) {
                    return (TabAlbumFragment) fragment;
                }
            }
            return new TabAlbumFragment();
        }

        private TabDiscoverFragment instanceOfTabDiscover(FragmentManager fragmentManager) {
            List<Fragment> fragments = fragmentManager.getFragments();
            if (null == fragments) {
                return new TabDiscoverFragment();
            }
            for (Fragment fragment : fragments) {
                if (fragment instanceof TabDiscoverFragment) {
                    return (TabDiscoverFragment) fragment;
                }
            }
            return new TabDiscoverFragment();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return getString(mFragments.get(position).getTitle());
        }

        View getTabView(int position) {
            TabItemView view = (TabItemView) getLayoutInflater().inflate(R.layout.tab_item_view, null);
            view.setImage(mFragments.get(position).getIcon());
            view.setText(mFragments.get(position).getTitle());
            view.setCurrentTab(mFragments.get(position).getClass());
            view.setId(mFragments.get(position).getTabId());
            return view;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }
    }
    /* Bug 1184608 */
    @Override
    public void onMenuShow(boolean visible) {
        Log.d(TAG, "onMenuShow:"+visible);
        setMenuItemVisible(mToolbar.getMenu(), R.id.action_hide_albums_v2, visible);
    }
    /* @ */

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mToolbar.setTitle(tab.getText());
        mViewPager.setCurrentItem(tab.getPosition(), true);
        /* Bug 1184608
         * If not TabAlbumFragment,set action_hide_albums_v2 gone
         * else show by the callback onMenuShow
         * */
        if(tab.getPosition() != 1){
            setMenuItemVisible(mToolbar.getMenu(), R.id.action_hide_albums_v2, false);
        }
        /* @ */

        synchronized (mTabSelectedCallbacks) {
            for (OnTabSelectedCallback callback : mTabSelectedCallbacks.keySet()) {
                callback.onTabSelected((TabItemView) tab.getCustomView());
            }
        }
    }

    public TabItemView getCurrentTab() {
        TabLayout.Tab tab = mTabLayout.getTabAt(mViewPager.getCurrentItem());
        if (tab != null) {
            return (TabItemView) tab.getCustomView();
        }
        return null;
    }

    @Override
    public int getTabPosition() {
        return mViewPager.getCurrentItem();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    public void setTabsVisible(boolean visible) {
        if (mTabLayout.getTabCount() < 2) {
            return;
        }
        mTabContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        mViewPager.setScrollable(visible);
    }

    public void setScrollable(boolean scrollable) {
        mViewPager.setScrollable(scrollable);
        mInterceptView.setVisibility(scrollable ? View.GONE : View.VISIBLE);
    }

    public void setNavigationTitle(String title) {
        mToolbar.setTitle(title);
        mToolbar.setNavigationIcon(R.drawable.ic_back_gray);
        mToolbar.setTitleTextAppearance(this, R.style.ToolbarTextAppearance);
    }

    public void setNavigationTitle(String title, int drawableId) {
        mToolbar.setTitle(title);
        mToolbar.setNavigationIcon(drawableId);
        mToolbar.setTitleTextAppearance(this, R.style.Toolbar3TextAppearance);
    }

    public CharSequence getNavigationTitle() {
        return mToolbar.getTitle();
    }

    public void setNavigationTitleOnly(String title) {
        mToolbar.setTitle(title);
    }

    public void resetNavigationAppearance() {
        mToolbar.setTitleTextAppearance(this, R.style.ToolbarTextAppearance);
    }

    private void resetNavigationTitle() {
        //set toolbar title
        if (mTabLayout.getTabCount() > 0) {
            TabLayout.Tab tab = mTabLayout.getTabAt(mTabLayout.getSelectedTabPosition());
            if (tab != null) {
                mToolbar.setTitle(tab.getText());
            }
        }
        mToolbar.setNavigationIcon(null);
    }

    public void setNavigationVisible(boolean visible) {
        //UNISOC added for bug 1209707, avoid gallery crash.
        if (mToolbar != null) {
            mToolbar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void setOtherNavigation(@Nullable Toolbar toolbar) {
        Toolbar t;
        if (toolbar == null) {
            t = mToolbar;
            setSupportActionBar(mToolbar);
        } else {
            t = toolbar;
            setSupportActionBar(toolbar);
        }
        t.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    public void setCoverVisible(boolean visible) {
        if (mCoverView == null) {
            return;
        }
        if (visible) {
            mAnimIn.reset();
            mCoverView.startAnimation(mAnimIn);
        } else {
            mAnimOut.reset();
            mCoverView.startAnimation(mAnimOut);
        }
        mCoverView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        if (animation == mAnimOut) {
            if (!isPhotoViewPage()) {
                setStatusBarColor(R.color.colorPrimaryDark);
            }
        }
    }

    private boolean isPhotoViewPage() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm != null) {
            Fragment f = fm.findFragmentById(R.id.fragment_full_container);
            return f instanceof PhotoViewPageFragment;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE:
                if (data == null || data.getData() == null) {
                    if (mSdCardPermissionListener != null) {
                        mSdCardPermissionListener.onSdCardPermissionDenied();
                        mSdCardPermissionListener = null;
                    }
                } else {
                    Uri uri = data.getData();
                    //
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    if (!documentId.endsWith(":") || "primary:".equals(documentId)) {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionDenied();
                            mSdCardPermissionListener = null;
                        }
                        return;
                    }
                    //
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        String path = SdCardPermission.getInvalidatePermissionStoragePath(0);
                        SdCardPermission.saveStorageUriPermission(path, uri.toString());
                        SdCardPermission.removeInvalidatePermissionStoragePath(0);
                        Log.d(TAG, "onActivityResult uri = " + uri + ", storage = " + path);
                    }

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        Intent accessIntent = SdCardPermission.getAccessStorageIntent(
                                SdCardPermission.getInvalidatePermissionStoragePath(0)
                        );
                        if (accessIntent == null) {
                            if (mSdCardPermissionListener != null) {
                                mSdCardPermissionListener.onSdCardPermissionDenied();
                                mSdCardPermissionListener = null;
                            }
                        } else {
                            startActivityForResult(accessIntent, SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE);
                        }
                    } else {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionAllowed();
                            mSdCardPermissionListener = null;
                        }
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }

    public void registerTabSelectedCallback(OnTabSelectedCallback callback) {
        synchronized (mTabSelectedCallbacks) {
            mTabSelectedCallbacks.put(callback, null);
        }
    }
}

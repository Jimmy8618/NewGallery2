/**
 * Created By Spreadst
 */

package com.sprd.gallery3d.app;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;

import androidx.legacy.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ActionMode;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.fw.StorageEventListener;
import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NewVideoActivity extends Activity {
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mPlanetTitles;
    private DrawerListAdapter mAdapter;
    public int id;
    private ArrayList<VideoItems> mVideoList = new ArrayList<VideoItems>();
    private ArrayList<RecentVideoInfo> mRecentVideoList = new ArrayList<RecentVideoInfo>();
    public static Fragment sInstance;
    private VideosFragment mAllVideosFragment, mLocalVideosFragment, mFilmedVideosFragment;
    private HistoryVideosFragment mHistoryVideosFragment;
    public int mCurrentFragmentId = 0;
    private List<Fragment> mList;
    private Menu mMenu;
    private static final String TAG = "NewVideoActivity";
    private static final String ALL_VIDEOS_FRAGMENT = "AllVideosFragment";
    private static final String LOCAL_VIDEOS_FRAGMENT = "LocalVideosFragment";
    private static final String FILMED_VIDEOS_FRAGMENT = "FilmedVideosFragment";
    private static final String OTG_VIDEOS_FRAGMENT = "OtgVideosFragment";
    private static final int START_SHOW_FRAGMENT = 1;
    /* SRPD: Add for drm new feature @{ */
    private static final int DRM_RIGHTS_UNKNOWN = 0;
    private static final int DRM_RIGHTS_INACTIVE = 1;
    private static final int DRM_RIGHTS_NO_LIMIT = 2;
    private static final int DRM_DATE_FORMAT = 3;
    /* Drm new feature end @}*/
    private final Handler mHandler = new MyHandler(this);
    private static NewVideoActivity mNewVideoActivity;
    private boolean mIsDrawerClosed = true;

    private List<String> mPlaneTitleList = new ArrayList<String>();
    private StorageManager mStorageManager;
    boolean mPreviousIsDrawerClosed = true;
    int mPreviousCurrentFragmentId = 0;
    private Bundle mSavedInstanceState = null;

    public static Fragment getInstance() {
        return sInstance;
    }

    /**
     * SPRD: Video OTG feature @{
     */
    private String[] mPanetFirstTitle = new String[3];
    private String[] mPanetLastTitle = new String[1];
    private List<String> mOTGdevicesPathList = new ArrayList<String>();
    private List<VideosFragment> mOtgVideoFragmentList = new ArrayList<VideosFragment>();
    private static final int STATE_UNMOUNTED = 0;
    private static final int STATE_MOUNTED = 2;

    private Object mStorageListener = null;
    private final int ALLVIDEOFRAGMENT_POSITION = 0;

    /**
     * @}
     */

    public static NewVideoActivity getNewVideoActivity() {
        return mNewVideoActivity;
    }

    private class MyStorageInfoListener implements StorageEventListener {

        @Override
        public void onVolumeStateChanged(String volumeName, String volumePath,
                                         boolean is_sd, int state) {
            if (mStorageListener == null) {
                return;
            }
            if (!is_sd) {
                if (state == StandardFrameworks.STATE_MOUNTED) {
                    addOtgDevice(volumeName, volumePath);
                    refresh();
                } else if (state == StandardFrameworks.STATE_UNMOUNTED) {
                    removeOtgDevice(volumeName, volumePath);
                    removeVideoFragment(volumePath);
                    refresh();
                    /*SPRD:Bug721521 After unmounted show history fragment @{*/
                    if (mCurrentFragmentId == mPlaneTitleList.size() - 1) {
                        selectItem(ALLVIDEOFRAGMENT_POSITION);
                        mAdapter.setSelectedPosition(ALLVIDEOFRAGMENT_POSITION);
                        mAdapter.notifyDataSetChanged();
                    }
                    /* end @}*/
                } else {
                    if (state == StandardFrameworks.STATE_MOUNTED || state == StandardFrameworks.STATE_UNMOUNTED) {
                        refresh();
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        /* SPRD: Fix bug619765 avoid ANR in monkey test cause by create too many NewVideoActivity @{ */
        if (GalleryUtils.isMonkey() && mNewVideoActivity != null) {
            Log.e(TAG, "NewVideoActivity in monkey test -> last activity is not finished");
            mNewVideoActivity.finish();
            mNewVideoActivity = null;
        }
        /* @} */
        mSavedInstanceState = savedInstanceState;
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate()");
        mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        mStorageListener = StandardFrameworks.getInstances().
                registerStorageManagerListener(mStorageManager, new MyStorageInfoListener());
        mNewVideoActivity = this;
        // enable ActionBar app icon to behave as action to toggle nav drawer
        VideoUtil.updateStatusBarColor(this, true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        mPlanetTitles = getResources().getStringArray(R.array.planets_array);
        initPlaneTitle();
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerList = findViewById(R.id.left_drawer);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mAdapter = new DrawerListAdapter(this, mPlaneTitleList);
        mDrawerList.setAdapter(mAdapter);
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
        /**mList.remove
         * ActionBarDrawerToggle ties together the the proper interactions between the sliding
         * drawer and the action bar app icon
         */
        mDrawerToggle = new ActionBarDrawerToggle(
                this, // host Activity
                mDrawerLayout, //DrawerLayout object
                R.drawable.ic_menu_wht_24dp, // nav drawer image to replace 'Up' caret
                R.string.drawer_open, //open drawer" description
                R.string.drawer_close //close drawer" description
        ) {
            @Override
            public void onDrawerClosed(View view) {
                mIsDrawerClosed = true;
                getActionBar().setTitle(mTitle);
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_wht_24dp);
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                mIsDrawerClosed = false;
                getActionBar().setTitle(R.string.video_list);
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_back_white);
                invalidateOptionsMenu();
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        if (!GalleryUtils.checkStoragePermissions(this) ||
                !GalleryUtils.checkReadPhonePermissions(this)) {
            /* SPRD:Add for bug592616 The recent video list cannot play normally @{ */
//            Intent startIntent = new Intent(this, NewVideoPermissionsActivity.class);
//            startIntent.putExtra(PermissionsActivity.UI_START_BY, PermissionsActivity.START_FROM_NEW_VIDEO);
//            startActivity(startIntent);
            GalleryUtils.requestPermission(this, NewVideoPermissionsActivity.class, PermissionsActivity.START_FROM_NEW_VIDEO);
            finish();
            return;
            /* Bug592616 @} */
        } else {
            /* SPRD: Add for bug593851 When long press the recent task key,the gallery will crash@} */
            Thread thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    if (mVideoList.size() == 0) {
                        mVideoList = VideoUtil.getVideoList(NewVideoActivity.this);
                    }
                    mHandler.sendEmptyMessage(START_SHOW_FRAGMENT);
                }
            });
            thread.start();
            /* Bug593851 @} */
        }
    }

    private void onstart() {
        // TODO Auto-generated method stub
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    /**
     * The click listner for ListView in the navigation drawer
     */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mDrawerLayout != null) {
                mDrawerLayout.closeDrawer(mDrawerList);
            }
            selectItem(position);
            mAdapter.setSelectedPosition(position);
            // SPRD:Add for bug592616 The slidemenu cannot change normally
            mAdapter.notifyDataSetChanged();
        }

    }

    /**
     * SPRD:toggle fragment we use "replace().commit" when the former fragment wont be used anymore.
     * when using here,the
     */
    private void selectItem(int position) {
        // TODO Auto-generated method stub
        if (mList != null) {
            if (position >= mList.size()) {
                position = mList.size() - 1;
            }
            sInstance = mList.get(position);
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.hide(mList.get(mCurrentFragmentId)).show(mList.get(position)).commitAllowingStateLoss();
            mCurrentFragmentId = position;
            // update selected item and title, then close the drawer
            if (mPlaneTitleList != null) {
                setTitle(mPlaneTitleList.get(position));
            }
            if (mDrawerList != null) {
                mDrawerList.setItemChecked(position, true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sync the toggle state after onRestoreInstanceState has occurred.
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.d(TAG, "mDrawerToggle.syncState();");
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getActionBar().setTitle(mTitle);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void handleMyHandlerMessage(Message msg) {
        switch (msg.what) {
            case START_SHOW_FRAGMENT:
                /* SPRD: Bug609393:java.lang.IllegalStateException,Activity has been destroyed @{ */
                if (!NewVideoActivity.mNewVideoActivity.isDestroyed()) {
                    updateVideoList();
                }
                /*Bug609393 end@}*/
                break;
            default:
                break;
        }
    }

    private static class MyHandler extends Handler {
        private WeakReference<NewVideoActivity> mNewVideoActivity;

        public MyHandler(NewVideoActivity newVideoActivity) {
            mNewVideoActivity = new WeakReference<>(newVideoActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            NewVideoActivity newVideoActivity = mNewVideoActivity.get();
            if (newVideoActivity != null) {
                newVideoActivity.handleMyHandlerMessage(msg);
            }

        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            android.util.Log.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            Toast.makeText(this, R.string.exit_multiwindow_video_tips, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        GalleryUtils.killActivityInMultiWindow(this, GalleryUtils.DONT_SUPPORT_VIEW_VIDEO);
        super.onResume();
        if (!GalleryUtils.checkStoragePermissions(this) || !GalleryUtils.checkReadPhonePermissions(this)) {
            finish();
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /* SPRD: Add for bug593851 long press recent task key,gallery will crash @{ */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("isDrawerClosed", mIsDrawerClosed);
        outState.putInt("itemSelected", mCurrentFragmentId);
    }

    @Override
    public void onRestoreInstanceState(Bundle outState) {
        mPreviousIsDrawerClosed = outState.getBoolean("isDrawerClosed", true);
        mPreviousCurrentFragmentId = outState.getInt("itemSelected", 0);
    }
    /* Bug593851 end @} */

    @Override
    protected void onDestroy() {
        if (mStorageManager != null) {
            StandardFrameworks.getInstances().unregisterStorageManagerListener(mStorageManager, mStorageListener);
            mStorageListener = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // TODO Auto-generated method stub
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public void updateVideoList() {
        if (mList == null) {
            mList = new ArrayList<Fragment>();
            mAllVideosFragment = new VideosFragment(mVideoList, ALL_VIDEOS_FRAGMENT);
            mLocalVideosFragment = new VideosFragment(VideoUtil.getLocalVideos(mVideoList),
                    LOCAL_VIDEOS_FRAGMENT);
            mFilmedVideosFragment = new VideosFragment(VideoUtil.getFilmedVideos(mVideoList),
                    FILMED_VIDEOS_FRAGMENT);
            mHistoryVideosFragment = new HistoryVideosFragment();
            mList.add(mAllVideosFragment);
            mList.add(mLocalVideosFragment);
            mList.add(mFilmedVideosFragment);
            addOtgFragmentList();
            mList.add(mHistoryVideosFragment);
        }
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        for (int i = 0; i < mList.size(); i++) {
            if (!mList.get(i).isAdded()) {
                fragmentTransaction.add(R.id.content_frame, mList.get(i)).show(mList.get(i)).hide(mList.get(i));
            }
        }
        /* SPRD: Add for bug593851 595344:When long press recent tash key,the gallery will crash @{ */
        try {
            fragmentTransaction.commitAllowingStateLoss();
            if (mSavedInstanceState != null) {
                selectItem(mPreviousCurrentFragmentId);
                mAdapter.setSelectedPosition(mPreviousCurrentFragmentId);
                mAdapter.notifyDataSetChanged();
                if (!mPreviousIsDrawerClosed && mDrawerLayout != null) {
                    mDrawerLayout.openDrawer(mDrawerList);
                }
            } else {
                selectItem(mCurrentFragmentId);
                if (mAdapter != null) {
                    mAdapter.setSelectedPosition(mCurrentFragmentId);// add for item transition
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        /* Bug593851 595344 end @} */
    }

    private void refresh() {
        /*Sprd:Bug624475 When mount or unmout more times,VideoPlayer maybe crash @{*/
        try {
            if (mList != null && mCurrentFragmentId >= mList.size()) {
                mCurrentFragmentId = mList.size() - 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        /*Bug624475 end @}*/
        /*Sprd:Bug1205232  When ActionMode is existed, VideoPlayer crash @{*/
        ActionMode allVideosFragmentActionMode = mAllVideosFragment.getActionMode();
        ActionMode filmedVideosFragmentActionMode = mFilmedVideosFragment.getActionMode();
        if (allVideosFragmentActionMode != null) {
            allVideosFragmentActionMode.finish();
        }
        if (filmedVideosFragmentActionMode != null) {
            filmedVideosFragmentActionMode.finish();
        }
        /*Bug1205232 end @}*/
        mAllVideosFragment.notifyChange();
        mLocalVideosFragment.notifyChange();
        mFilmedVideosFragment.notifyChange();
        mAdapter.setPlaneList(mPlaneTitleList);
        mDrawerList.setAdapter(mAdapter);
        updateVideoList();
    }

    private void initPlaneTitle() {
        if (mPlanetTitles.length == 0) {
            return;
        }
        for (int i = 0; i < mPlanetTitles.length; ++i) {
            if (i > 2) {
                mPanetLastTitle[i - 3] = mPlanetTitles[i];
            } else {
                mPanetFirstTitle[i] = mPlanetTitles[i];
                mPlaneTitleList.add(mPlanetTitles[i]);
            }
            Log.d(TAG, mPlanetTitles[i].toString());
        }
        addOTGvolumes();
        addLastPanetTitle();
    }

    private void addOTGvolumes() {
        HashMap<String, String> volumnInfo = StandardFrameworks.getInstances().
                getOtgVolumesInfo(mStorageManager);

        Iterator iter = volumnInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String volumeName = (String) entry.getKey();
            String volumePath = (String) entry.getValue();
            if (volumeName != null && volumePath != null) {
                mPlaneTitleList.add(volumeName);
                mOTGdevicesPathList.add(volumePath);
                Log.d(TAG, volumeName);
            }
        }
    }

    private void addLastPanetTitle() {
        if (mPanetLastTitle.length == 0 || mPlaneTitleList == null) {
            return;
        }
        for (String title : mPanetLastTitle) {
            mPlaneTitleList.add(title);
        }
    }

    private void removeLastPanetTitle() {
        if (mPanetLastTitle.length == 0 || mPlaneTitleList == null) {
            return;
        }
        for (String title : mPanetLastTitle) {
            for (int i = 0; i < mPlaneTitleList.size(); ++i) {
                if (mPlaneTitleList.get(i).contains(title)) {
                    mPlaneTitleList.remove(i);
                    break;
                }
            }
        }
    }

    private void addOtgFragmentList() {
        if (mOTGdevicesPathList == null) {
            return;
        }
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        VideosFragment otgdevice;
        for (int i = 0; i < mOTGdevicesPathList.size(); ++i) {
            otgdevice = new VideosFragment(VideoUtil.getOtgVideos(mVideoList,
                    mOTGdevicesPathList.get(i)), mOTGdevicesPathList.get(i),
                    OTG_VIDEOS_FRAGMENT);
            fragmentTransaction.add(R.id.content_frame, otgdevice, mOTGdevicesPathList.get(i)).hide(otgdevice);
            mOtgVideoFragmentList.add(otgdevice);
            mList.add(otgdevice);
        }
    }

    private void removeOtgDevice(String volumeName, String volumePath) {
        if (mPlaneTitleList == null || mOTGdevicesPathList == null || mList == null) {
            return;
        }
        Log.d(TAG, "removed title Name: " + volumeName);
        for (int i = 0; i < mPlaneTitleList.size(); ++i) {
            if (volumeName != null && volumeName.equals(mPlaneTitleList.get(i))) {
                mPlaneTitleList.remove(i);
                break;
            }
        }
        for (int i = 0; i < mOTGdevicesPathList.size(); ++i) {
            if (volumePath != null && volumePath.equals(mOTGdevicesPathList.get(i))) {
                mOTGdevicesPathList.remove(i);
                break;
            }
        }
    }

    private void removeVideoFragment(String volumePath) {
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Log.d(TAG, "removeVideoFragment: " + volumePath);
        String fragmentTag;
        if (volumePath == null || mList == null) {
            return;
        }
        for (int i = 0; i < mList.size(); ++i) {
            fragmentTag = mList.get(i).getTag();
            if (fragmentTag != null && fragmentTag.equals(volumePath)) {
                Log.d(TAG, "Fragment is remove!");
                if (mList.get(i).isVisible()) {
                    fragmentTransaction.hide(mList.get(i));
                }
                fragmentTransaction.remove(mList.get(i));
                fragmentTransaction.commitAllowingStateLoss();
                mList.remove(i);
                break;
            }
        }
    }

    private void addOtgDevice(String volumeName, String volumePath) {
        if (mPlaneTitleList == null || mOTGdevicesPathList == null) {
            return;
        }
        Log.d(TAG, "removed title Name: " + volumeName);
        removeLastPanetTitle();
        mPlaneTitleList.add(volumeName);
        addLastPanetTitle();
        if (volumePath != null) {
            mOTGdevicesPathList.add(volumePath);
        }
        addVideoFragment(volumePath);
    }

    private void addVideoFragment(String volumepath) {
        if (volumepath == null || mList == null) {
            return;
        }
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        VideosFragment otgdevice = new VideosFragment(
                VideoUtil.getOtgVideos(mVideoList, volumepath), volumepath,
                OTG_VIDEOS_FRAGMENT);
        mOtgVideoFragmentList.add(otgdevice);
        fragmentTransaction.add(R.id.content_frame, otgdevice, volumepath).hide(otgdevice);
        int index = mList.size();
        if (index >= 1) {
            mList.remove(index - 1);
        }
        mList.add(otgdevice);
        mList.add(mHistoryVideosFragment);
        if (index < mList.size()
                && mCurrentFragmentId == (index - 1)) {
            mCurrentFragmentId = mList.size() - 1;
        }
    }

    public String getStringForDrm(int num) {
        if (num == DRM_RIGHTS_UNKNOWN) {
            return this.getResources().getString(R.string.drm_rights_unknown);
        } else if (num == DRM_RIGHTS_INACTIVE) {
            return this.getResources().getString(R.string.drm_rights_inactive);
        } else if (num == DRM_RIGHTS_NO_LIMIT) {
            return this.getResources().getString(R.string.drm_rights_no_limit);
        } else if (num == DRM_DATE_FORMAT) {
            return this.getResources().getString(R.string.drm_date_format);
        }
        return null;
    }

    /*SPRD:Bug664144 In select mode ,the drawlayout still can slide @{*/
    public void setDrawerLockClosed() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    public void setDrawerUnLocked() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }
    /*end @}*/
}

package com.android.gallery3d.v2.page;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.cust.NewAlbumDialog;
import com.android.gallery3d.v2.data.AlbumSetItem;
import com.android.gallery3d.v2.data.AlbumSetLoadingListener;
import com.android.gallery3d.v2.data.AlbumSetPageDataLoader;
import com.android.gallery3d.v2.data.AllMediaAlbum;
import com.android.gallery3d.v2.data.HideAlbumSet;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.interact.OnTabSelectedCallback;
import com.android.gallery3d.v2.tab.TabAlbumFragment;
import com.android.gallery3d.v2.tab.TabItemView;
import com.android.gallery3d.v2.ui.AlbumSetPageUI;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.Config;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.android.gallery3d.v2.widget.PickerActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author baolin.li
 */
public class AlbumSetPageFragment extends BasePageFragment implements AlbumSetLoadingListener,
        AlbumSetPageUI.OnAlbumSetItemClickListener, NewAlbumDialog.OnNewAlbumCreatedListener,
        BasePageFragment.PageDataBackListener, OnTabSelectedCallback {
    private static final String TAG = AlbumSetPageFragment.class.getSimpleName();

    private MediaSet mMediaSet;
    private AlbumSetPageDataLoader mDataLoader;

    private AlbumSetPageUI mPageUI;
    private final List<AlbumSetItem> mData = new ArrayList<>();
    private Set<String> mHidedAlbums = new HashSet<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_albumset_page, container, false);
        mPageUI = v.findViewById(R.id.albumset_page_ui);
        mPageUI.bind(this);
        mPageUI.setOnAlbumSetItemClickListener(this);

        if (isNextPage()) {
            //隐藏底部Tab
            setTabsVisible(false);
        } else {
            registerTabSelectedCallback(this);
        }
        if (isHideAlbums()) {
            mHidedAlbums = Config.getPref(SelectionManager.KEY_SELECT_ALBUM_ITEMS, (Set<String>) null);
            if (mHidedAlbums == null) {
                mHidedAlbums = new HashSet<>();
            } else {
                mHidedAlbums = new HashSet<>(mHidedAlbums);
            }
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
                ArrayList<String> hideSets = new ArrayList<>();
                if (isAddToAlbum()) {
                    String fromMediaSet = getArguments().getString(Constants.KEY_ADD_FROM_MEDIA_SET);
                    Log.d(TAG, "onResume fromMediaSet = " + fromMediaSet);
                    if (fromMediaSet != null) {
                        hideSets.add(fromMediaSet);
                    }
                    hideSets.add(AllMediaAlbum.PATH.toString());
                } else if (isWidgetGetAlbumIntent()) {
                    hideSets.add(AllMediaAlbum.PATH.toString());
                    hideSets.add(AllMediaAlbum.IMAGE_PATH.toString());
                    hideSets.add(AllMediaAlbum.VIDEO_PATH.toString());
                }
                mDataLoader = new AlbumSetPageDataLoader(mMediaSet, hideSets);
                mDataLoader.setLoadingListener(this);
                Log.d(TAG, "mMediaSet(" + mMediaSet + "), mediaPath(" + mediaPath + ").");
            }
            reLoadMediaSets();
            //如果Fragment被隐藏,则不load task 加载数据, 在 onShow 中会调用
            if (!isHidden()) {
                if (isNextPage()) {
                    mDataLoader.resume();
                } else {
                    onTabSelected(getCurrentTab());
                }
            }
        }

        if (isNextPage()) {
            if (isAddToAlbum()) {
                setNavigationTitle(getString(R.string.add_to_album));
            } else if (isAddAlbumSelectItems()) {
                setNavigationTitle(getString(R.string.select_images_or_videos), R.drawable.action_mode_close);
            } else if (isHideAlbums()) {
                setNavigationTitle(getString(R.string.hide_albums_v2));
            }
        } else {
            resetNavigationAppearance();
        }
    }

    private void reLoadMediaSets() {
        if (isHideAlbums()) {
            Config.setPref(SelectionManager.KEY_EDIT_ALBUMS_SCREEN, true);
            mMediaSet.reForceDirty(true);
        } else if (Config.getPref(SelectionManager.KEY_EDIT_ALBUMS_SCREEN, false)) {
            Config.setPref(SelectionManager.KEY_EDIT_ALBUMS_SCREEN, false);
            mMediaSet.reForceDirty(true);
            if (getContext() != null) {
                //更新discover页数据
                getContext().getContentResolver().update(DiscoverStore.Things.Media.CONTENT_URI, null, null, null);
                getContext().getContentResolver().update(DiscoverStore.FaceInfo.Media.CONTENT_URI, null, null, null);
            }
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
        if (mDataLoader != null) {
            reLoadMediaSets();
            mDataLoader.resume();
        }
        if (isNextPage()) {
            if (isAddToAlbum()) {
                setNavigationTitle(getString(R.string.add_to_album));
            } else if (isAddAlbumSelectItems()) {
                setNavigationTitle(getString(R.string.select_images_or_videos), R.drawable.action_mode_close);
            } else if (isHideAlbums()) {
                setNavigationTitle(getString(R.string.hide_albums_v2));
            }
        } else {
            resetNavigationAppearance();
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
    public void onDestroyView() {
        super.onDestroyView();
        if (isHideAlbums()) {
            Config.setPref(SelectionManager.KEY_SELECT_ALBUM_ITEMS, mHidedAlbums);
            Config.setPref(SelectionManager.KEY_SELECT_ALBUM_FLAG, true);
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
    /* Bug 1184608 */
    private boolean isHasCusAlbum(AlbumSetItem item){
        if(item == null){
            return false;
        }
        MediaSet mediaSet = item.getMediaSet();
        if(mediaSet instanceof LocalMergeAlbum){
            int bucketId = mediaSet.getBucketId();
            if(bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID
                    || bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID){
                return false;
            }
        }
        String mediaSetPath = item.getMediaSetPath();
        if(mediaSetPath == null){
            return false;
        }
        if(!"".equals(mediaSetPath) && mediaSetPath.startsWith("/local/all")){
            return true;
        }
        return false;
    }
    /* @ */
    @Override
    public void loadEnd() {
        /* Bug 1184608 */
        if(mHideMenuListener != null){
            /* @Bug 1232884 */
            boolean isHasCustomAlbum = false;
            Set<String> hideAlbums = Config.getPref(SelectionManager.KEY_SELECT_ALBUM_ITEMS, (Set<String>) null);
            boolean isHasHideAlbums = hideAlbums != null && hideAlbums.size() > 0;
            if(!isHasHideAlbums){
                /*
                 * If there is no album has been hidden,check whether there is any album can be hidden
                 * */
                for(AlbumSetItem albumSetItem: mData){
                    isHasCustomAlbum = isHasCusAlbum(albumSetItem);
                    if(isHasCustomAlbum){
                        break;
                    }
                }
            }
            mHideMenuListener.onMenuShow(isHasHideAlbums || isHasCustomAlbum);
            /* @ */
        }
        /* @ */
        mPageUI.loadEnd();
    }

    @Override
    public void loadEmpty() {
        mPageUI.loadEmpty();
    }

    public boolean isAddToAlbum() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_ADD_TO_ALBUM, false);
    }

    public boolean isAddAlbumSelectItems() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_ADD_ALBUM_SELECT_ITEMS, false);
    }

    public boolean isHideAlbums() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_HIDE_ALBUMS, false);
    }

    @Override
    public void onAlbumSetItemClick(View v, AlbumSetItem item) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onAlbumSetItemClick ignore");
            return;
        }
        if (isStateSaved()) {
            Log.d(TAG, "onAlbumSetItemClick: fragment's state has been saved, ignore");
            return;
        }
        if (isWidgetGetAlbumIntent()) {
            Intent intent = new Intent();
            intent.putExtra(PickerActivity.KEY_ALBUM_PATH, item.getMediaSetPath());
            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        } else {
            Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
            int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
            FragmentManager fm = getActivity().getSupportFragmentManager();
            Fragment fragment = fm.findFragmentById(containerId);
            Log.d(TAG, "onAlbumSetItemClick fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
            if (fragment != null) {
                AlbumPageFragment albumPageFragment = new AlbumPageFragment();
                if (bundle.getBoolean(Constants.KEY_BUNDLE_IS_ADD_ALBUM_SELECT_ITEMS, false)) {
                    albumPageFragment.setDataBackListener(this);
                } else {
                    albumPageFragment.setDataBackListener(this);
                }
                bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, item.getMediaSetPath());
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_TRASH_ALBUM, item.isTrash());
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_ALL_ALBUM, item.isAllAlbum());
                albumPageFragment.setArguments(bundle);
                fm.beginTransaction()
                        .hide(fragment)
                        .add(containerId, albumPageFragment)
                        .addToBackStack(null)
                        .commit();
            }
        }
    }

    @Override
    public void onAddToAlbum(@Nullable String dir) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onAddToAlbum ignore");
            return;
        }
        if (TextUtils.isEmpty(dir)) {
            Toast.makeText(getContext(), R.string.failed_add_to_album, Toast.LENGTH_SHORT).show();
            onBackPressed();
            return;
        }
        File file = new File(dir);
        if (Build.VERSION.SDK_INT < ApiHelper.VERSION_CODES.Q) {
            if (!file.exists() && !file.mkdirs()) {
                Toast.makeText(getContext(), R.string.failed_add_to_album, Toast.LENGTH_SHORT).show();
                onBackPressed();
                return;
            }
        }

        Bundle data = new Bundle();
        data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_ADD_TO_ALBUM);
        data.putString(Constants.KEY_ADD_TO_ALBUM_DIR, dir);
        data.putStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST, getArguments().getStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST));
        checkDataBackValid(R.string.fragment_state_lost_msg);
        setDataBack(data);

        onBackPressed();
    }

    @Override
    public void onHideAlbum(String path, boolean hide) {
        if (hide) {
            mHidedAlbums.add(path);
        } else {
            mHidedAlbums.remove(path);
        }
    }

    @Override
    public void onNewAlbumCreated(String dir) {
        Log.d(TAG, "onNewAlbumCreated dir = " + dir);
        if (isAddToAlbum()) {
            onAddToAlbum(dir);
        } else {
            Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
            int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
            FragmentManager fm = getActivity().getSupportFragmentManager();
            Fragment fragment = fm.findFragmentById(containerId);
            Log.d(TAG, "onNewAlbumCreated fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
            if (fragment != null) {
                AlbumSetPageFragment albumSetPageFragment = new AlbumSetPageFragment();
                albumSetPageFragment.setDataBackListener(this);
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
                bundle.putBoolean(Constants.KEY_BUNDLE_IS_ADD_ALBUM_SELECT_ITEMS, true);
                bundle.putString(Constants.KEY_ADD_TO_ALBUM_DIR, dir);
                bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
                albumSetPageFragment.setArguments(bundle);
                fm.beginTransaction()
                        .hide(fragment)
                        .add(containerId, albumSetPageFragment)
                        .addToBackStack(null)
                        .commit();
            }
        }
    }

    @Override
    public void onDataBack(Bundle data) {
        switch (data.getInt(Constants.KEY_DATA_BACK, -1)) {
            case Constants.DATA_BACK_FROM_ADD_ALBUM_SELECT_ITEMS:
                data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_ADD_ALBUM_SELECT_ALBUM);
                data.putString(Constants.KEY_ADD_TO_ALBUM_DIR, getArguments().getString(Constants.KEY_ADD_TO_ALBUM_DIR));
                onBackPressed();
                checkDataBackValid(R.string.fragment_state_lost_msg);
                setDataBack(data);
                break;
            case Constants.DATA_BACK_FROM_ADD_ALBUM_SELECT_ALBUM:
                String dir = data.getString(Constants.KEY_ADD_TO_ALBUM_DIR);
                if (TextUtils.isEmpty(dir)) {
                    Toast.makeText(getContext(), R.string.failed_add_to_album, Toast.LENGTH_SHORT).show();
                } else {
                    File file = new File(dir);
                    if (Build.VERSION.SDK_INT < ApiHelper.VERSION_CODES.Q && !file.mkdirs()) {
                        Toast.makeText(getContext(), R.string.failed_add_to_album, Toast.LENGTH_SHORT).show();
                    } else {
                        addToAlbumTask(dir, data.getStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST));
                    }
                }
                break;
            case Constants.DATA_BACK_FROM_ALBUM_PAGE:
                boolean needUpdate = data.getBoolean(Constants.KEY_IS_NEED_UPDATE, false);
                if (needUpdate) {
                    mPageUI.updateUI();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!isNextPage() && isMainIntent()) {
            menu.clear();
            inflater.inflate(R.menu.menu_hide_albums_v2, menu);
            setMenuItemVisible(menu, R.id.action_hide_albums_v2, getTabPosition() == 1);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_hide_albums_v2:
                launchHideAlbumsUI();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void launchHideAlbumsUI() {
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        if (fm.isStateSaved()) {
            Log.e(TAG, "launchHideAlbumsUI stateSaved, cannot commit.");
            return;
        }
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "launchHideAlbumsUI fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment != null) {
            AlbumSetPageFragment albumSetPageFragment = new AlbumSetPageFragment();
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_HIDE_ALBUMS, true);
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, HideAlbumSet.PATH/*getDataManager().getTopSetPath(DataManager.INCLUDE_ALL)*/);
            albumSetPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .hide(fragment)
                    .add(containerId, albumSetPageFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onTabSelected(@Nullable TabItemView tab) {
        if (tab == null) {
            return;
        }
        if (TabAlbumFragment.class.getSimpleName().equals(tab.getCurrentTab())) {
            Log.d(TAG, "onTabSelected " + tab.getCurrentTab());
            runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!isPaused() && mDataLoader != null) {
                        mDataLoader.resume();
                    }
                }
            }, LOAD_DELAY);
        } else {
            //滑动到非当前界面, 停止加载数据
            if (mDataLoader != null) {
                mDataLoader.pause();
            }
        }
    }

    public Set<String> getHidedAlbums() {
        return mHidedAlbums;
    }
}

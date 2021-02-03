package com.android.gallery3d.v2.page;

import android.app.Activity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.cust.MergeDialog;
import com.android.gallery3d.v2.cust.SingleEditTextDialog;
import com.android.gallery3d.v2.data.AlbumItem;
import com.android.gallery3d.v2.data.AlbumLoadingListener;
import com.android.gallery3d.v2.data.AlbumPageDataLoader;
import com.android.gallery3d.v2.data.ImageItem;
import com.android.gallery3d.v2.data.LabelItem;
import com.android.gallery3d.v2.discover.data.PeopleMergeAlbum;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.interact.OnTabSelectedCallback;
import com.android.gallery3d.v2.option.ActionModeHandler;
import com.android.gallery3d.v2.option.SelectionManager;
import com.android.gallery3d.v2.tab.TabItemView;
import com.android.gallery3d.v2.tab.TabPhotoFragment;
import com.android.gallery3d.v2.ui.AlbumPageUI;
import com.android.gallery3d.v2.util.ClickInterval;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.gallery3d.drm.SomePageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class AlbumPageFragment extends BasePageFragment implements AlbumLoadingListener,
        AlbumPageUI.OnAlbumItemClickListener, ActionModeHandler.OnSelectionModeChangeListener,
        ActionModeHandler.OnDetailsClickListener, ActionModeHandler.OnAddToAlbumClickListener,
        ActionModeHandler.OnDrmDetailsClickListener, BasePageFragment.PageDataBackListener,
        OnTabSelectedCallback {
    private static final String TAG = AlbumPageFragment.class.getSimpleName();

    private MediaSet mMediaSet;
    private AlbumPageDataLoader mDataLoader;

    private AlbumPageUI mPageUI;
    private final List<AlbumItem> mData = new ArrayList<>();

    private ActionModeHandler mActionModeHandler;
    private SelectionManager mSelectionManager;
    private MenuItem mDoneMenu;

    private Toast mToast;
    private List<String> mNamedFaces;

    private DetailsHelper mDetailsHelper;
    private DrmDetailsSource mDrmDetailsSource;
    private boolean mNeedUpdate = false;
    private boolean mIsDrmUpdated = false;

    private int mSize;

    private boolean mIsFirstLoad;

    private static class DrmDetailsSource implements DetailsHelper.DetailsSource {
        private MediaItem mMediaItem;

        public void setMediaItem(MediaItem mediaItem) {
            mMediaItem = mediaItem;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public int setIndex() {
            return 0;
        }

        @Override
        public MediaDetails getDetails() {
            if (mMediaItem != null) {
                return mMediaItem.getDetails();
            }
            return null;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setHasOptionsMenu(true);
        mIsFirstLoad = true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_album_page, container, false);
        mPageUI = v.findViewById(R.id.album_page_ui);
        mPageUI.bind(this);
        mPageUI.setOnAlbumItemClickListener(this);

        if (isNextPage()) {
            //隐藏底部Tab
            setTabsVisible(false);
        } else {
            registerTabSelectedCallback(this);
        }
        return v;
    }

    public boolean isAddAlbumSelectItems() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_ADD_ALBUM_SELECT_ITEMS, false);
    }

    public boolean isTrashAlbum() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_TRASH_ALBUM, false);
    }

    public boolean isALLAlbum() {
        return getArguments() != null
                && getArguments().getBoolean(Constants.KEY_BUNDLE_IS_ALL_ALBUM, false);
    }

    public List<AlbumItem> getData() {
        return mData;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        setPageFragment(this);
        if (PermissionUtil.hasPermissions(getContext())) {
            if (mMediaSet == null) {
                String mediaPath = getArguments().getString(Constants.KEY_BUNDLE_MEDIA_SET_PATH);
                mMediaSet = getDataManager().getMediaSet(mediaPath);
                mDataLoader = new AlbumPageDataLoader(mMediaSet);
                mDataLoader.setAlbumLoadingListener(this);
                Log.d(TAG, "mMediaSet(" + mMediaSet + "), mediaPath(" + mediaPath + ").");
            }
            //如果Fragment被隐藏,则不load task 加载数据, 在 onShow 中会调用
            if (!isHidden()) {
                if (isNextPage()) {
                    mDataLoader.resume();
                } else {
                    onTabSelected(getCurrentTab());
                }
            }
            if (mActionModeHandler == null) {
                mActionModeHandler = new ActionModeHandler(getActivity(), this, mMediaSet);
                mActionModeHandler.setOnSelectionModeChangeListener(this);
                mActionModeHandler.setOnAddToAlbumClickListener(this);
                mActionModeHandler.setOnDrmDetailsClickListener(this);
            }
            if (mSelectionManager == null) {
                this.mSelectionManager = new SelectionManager(mMediaSet);
            }
        }
        if (isNextPage()) {
            if (isAddAlbumSelectItems()) {
                setNavigationTitle(getString(R.string.select_images_or_videos), R.drawable.action_mode_close);
                mPageUI.onSelectionModeChanged(ActionModeHandler.SELECT_IMAGE_OR_VIDEO_MODE);
            } else if (mMediaSet != null) {
                setNavigationTitle(mMediaSet.getName());
            }
        }
        if (mActionModeHandler != null) {
            mActionModeHandler.resume();
        }

        if (mNeedUpdate) {
            mNeedUpdate = false;
            mPageUI.updateUI();
            mIsDrmUpdated = true;
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
        setPageFragment(this);
        if (mDataLoader != null) {
            mDataLoader.resume();
        }
        if (isNextPage()) {
            if (isAddAlbumSelectItems()) {
                setNavigationTitle(getString(R.string.select_images_or_videos), R.drawable.action_mode_close);
                mPageUI.onSelectionModeChanged(ActionModeHandler.SELECT_IMAGE_OR_VIDEO_MODE);
            } else if (mMediaSet != null) {
                setNavigationTitle(mMediaSet.getName());
            }
        }
        if (mActionModeHandler != null) {
            mActionModeHandler.resume();
        }

        if (mNeedUpdate) {
            mNeedUpdate = false;
            mPageUI.updateUI();
            mIsDrmUpdated = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        //setPageFragment(null);
        if (mDataLoader != null) {
            mDataLoader.pause();
        }
        if (mActionModeHandler != null) {
            mActionModeHandler.pause();
        }
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
        //setPageFragment(null);
        if (mDataLoader != null) {
            mDataLoader.pause();
        }
        if (mActionModeHandler != null) {
            mActionModeHandler.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mActionModeHandler != null) {
            mActionModeHandler.destroy();
        }
        if (isAddAlbumSelectItems()) {
            if (mSelectionManager != null) {
                mSelectionManager.onSelectNon();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (isAddAlbumSelectItems()) {
            menu.clear();
            inflater.inflate(R.menu.menu_done, menu);
            mDoneMenu = menu.findItem(R.id.action_done);
        } else if (mMediaSet instanceof PeopleMergeAlbum) {
            menu.clear();
            inflater.inflate(R.menu.menu_edit_name, menu);
            mNamedFaces = DiscoverStore.getNamedFaces(mMediaSet.getName(), null);
            menu.findItem(R.id.action_merge_to).setVisible(mNamedFaces.size() > 0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isAddAlbumSelectItems()
                || (mMediaSet instanceof PeopleMergeAlbum)) {
        } else {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_done:
                Bundle data = new Bundle();
                data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_ADD_ALBUM_SELECT_ITEMS);
                data.putStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST, mSelectionManager.getSelectedItemsWithString());
                onBackPressed();
                checkDataBackValid(R.string.fragment_state_lost_msg);
                setDataBack(data);
                return true;
            case R.id.action_edit_name:
                editPeopleName();
                return true;
            case R.id.action_merge_to:
                mergePeople();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void loadStart() {
        mPageUI.loadStart();
    }

    @Override
    public void loading(int index, int size, List<AlbumItem> items, int loadedSize) {
        if (mSize != size) {
            mSize = size;
        }
        if (index == 0) {
            if (isAddAlbumSelectItems()) {
                if (mSelectionManager != null) {
                    mSelectionManager.onSelectNon();
                }
                if (mDoneMenu != null) {
                    mDoneMenu.setVisible(false);
                }
            }
        }
        mPageUI.loading(index, size, items, loadedSize);
    }

    @Override
    public void loadEnd() {
        mPageUI.loadEnd();
        if (mMediaSet != null && mMediaSet.getName() != null && !mMediaSet.getName().contentEquals(getNavigationTitle())) {
            if (isNextPage() && !isAddAlbumSelectItems() && !isHidden()) {
                setNavigationTitle(mMediaSet.getName());
            }
        }
        //如果数据刷新, 而且 action mode 显示的, 则取消 action mode 显示
        /*
        if (mActionModeHandler != null && mActionModeHandler.isActive()) {
            mActionModeHandler.finishActionMode();
        }
        */
    }

    @Override
    public void loadEmpty() {
        mPageUI.loadEmpty();

        if (!isTrashAlbum() && !isALLAlbum() && isNextPage()) {
            onBackPressed();
        }
    }

    @Override
    public void onImageClick(final ImageItem item) {
        if (ClickInterval.ignore()) {
            Log.d(TAG, "onImageClick ignore");
            return;
        }
        if (isGetContentIntent()) {
            if (item.isDrm() && !DrmUtil.newIsSupportShare(item.getFilePath())) {
                mToast = ToastUtil.showMessage(getContext(), mToast, R.string.choose_drm_alert, Toast.LENGTH_SHORT);
                return;
            }
            Intent intent = new Intent();
            intent.setData(item.getUri());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        } else {
            android.app.AlertDialog.OnClickListener drmOnClickListener = new android.app.AlertDialog.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    reviewImage(item);
                }
            };

            if (item.getMediaType() != MediaObject.MEDIA_TYPE_VIDEO && SomePageUtils.getInstance()
                    .checkPressedIsDrm(getActivity(), (MediaItem) getDataManager().getMediaObject(item.getItemPath())
                            , drmOnClickListener, null, null, isGetContentIntent())) {
                mNeedUpdate = true;
                return;
            }

            if (item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO && item.isDrm()) {
                mNeedUpdate = true;
            }

            reviewImage(item);
        }
    }

    private void reviewImage(ImageItem item) {
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = R.id.fragment_full_container;
        FragmentManager fm = getActivity().getSupportFragmentManager();
        if (fm.isStateSaved()) {
            Log.e(TAG, "reviewImage stateSaved, cannot commit.");
            return;
        }
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "reviewImage fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment == null) {
            PhotoViewPageFragment photoViewPageFragment = new PhotoViewPageFragment();
            if (isNextPage()) {
                photoViewPageFragment.setDataBackListener(this);
            }
            bundle.putInt(Constants.KEY_BUNDLE_CONTAINER_ID, containerId);
            bundle.putInt(Constants.KEY_BUNDLE_MEDIA_ITEM_INDEX, item.getIndexInMediaSet());
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, item.getMediaSetPath());
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, item.getItemPath());
            bundle.putBoolean(Constants.KEY_BUNDLE_MEDIA_ITEM_READ_ONLY, false);
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            bundle.putInt(Constants.KEY_BUNDLE_ALBUM_PAGE_ITEM_COUNT, mSize);
            photoViewPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .add(containerId, photoViewPageFragment)
                    .hide(this)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public boolean onImageLongClick(ImageItem item) {
        if (!isMainIntent()) {
            return false;
        }
        mActionModeHandler.startActionMode();
        return true;
    }

    @Override
    public void onImageSelected(ImageItem item) {
        if (isAddAlbumSelectItems()) {
            mSelectionManager.onImageSelected(item);
            updateSelectItemTitle();
        } else {
            mActionModeHandler.onImageSelected(item);
        }
    }

    @Override
    public void onLabelSelected(LabelItem item) {
        if (isAddAlbumSelectItems()) {
            mSelectionManager.onLabelSelected(item);
            updateSelectItemTitle();
        } else {
            mActionModeHandler.onLabelSelected(item);
        }
    }

    private void updateSelectItemTitle() {
        int count = mSelectionManager.getSelectedCount();
        setNavigationTitleOnly(String.format(getString(R.string.already_select_count), count));
        if (mDoneMenu != null) {
            mDoneMenu.setVisible(count > 0);
        }
    }

    @Override
    public void onSelectionModeChanged(int mode) {
        Log.d(TAG, "onSelectionModeChanged mode(" + mode + ")");
        mPageUI.onSelectionModeChanged(mode);
        if (mode == ActionModeHandler.LEAVE_SELECTION_MODE) {
            if (!isNextPage()) {
                ((GalleryActivity2) getActivity()).setScrollable(true);
            }
            mActionModeHandler.onSelectNon();
            resumeDataLoader();
        } else {
            if (!isNextPage()) {
                ((GalleryActivity2) getActivity()).setScrollable(false);
            }
            pauseDataLoader();
        }
    }

    @Override
    public void onDetailsClicked(MediaItem mediaItem) {
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "onDetailsClicked fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment != null) {
            DetailsPageFragment detailsPageFragment = new DetailsPageFragment();
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, mediaItem.getPath().toString());
            detailsPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .hide(fragment)
                    .add(containerId, detailsPageFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onAddToAlbumClicked(ArrayList<String> itemList) {
        Bundle bundle = getArguments() == null ? new Bundle() : new Bundle(getArguments());
        int containerId = bundle.getInt(Constants.KEY_BUNDLE_CONTAINER_ID);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(containerId);
        Log.d(TAG, "onAddToAlbumClicked fragment(" + fragment + "), fm(" + fm + "), containerId(" + containerId + ").");
        if (fragment != null) {
            AlbumSetPageFragment albumSetPageFragment = new AlbumSetPageFragment();
            albumSetPageFragment.setDataBackListener(this);
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_NEXT_PAGE, true);
            bundle.putBoolean(Constants.KEY_BUNDLE_IS_ADD_TO_ALBUM, true);
            bundle.putString(Constants.KEY_ADD_FROM_MEDIA_SET, mMediaSet.getPath().toString());
            bundle.putStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST, itemList);
            bundle.putString(Constants.KEY_BUNDLE_MEDIA_SET_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));
            albumSetPageFragment.setArguments(bundle);
            fm.beginTransaction()
                    .hide(fragment)
                    .add(containerId, albumSetPageFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onDataBack(Bundle data) {
        switch (data.getInt(Constants.KEY_DATA_BACK, -1)) {
            case Constants.DATA_BACK_FROM_PHOTO_VIEW:
                int focusIndex = data.getInt(Constants.KEY_RETURN_INDEX_HINT, -1);
                if (focusIndex >= 0) {
                    mPageUI.makeSlotVisible(focusIndex);
                }
                boolean needUpdate = data.getBoolean(Constants.KEY_IS_NEED_UPDATE, false);
                if (needUpdate) {
                    mPageUI.updateUI();
                    mIsDrmUpdated = true;
                }
                break;
            case Constants.DATA_BACK_FROM_ADD_TO_ALBUM:
                String dir = data.getString(Constants.KEY_ADD_TO_ALBUM_DIR);
                List<String> itemList = data.getStringArrayList(Constants.KEY_ADD_TO_ALBUM_ITEM_LIST);
                addToAlbumTask(dir, itemList);
                break;
            default:
                break;
        }
    }

    private void editPeopleName() {
        SingleEditTextDialog dialog = new SingleEditTextDialog();
        dialog.setTitle(getString(R.string.add_name));
        dialog.setTint(mMediaSet.getName());
        dialog.setOnPositiveButtonClickedListener(new SingleEditTextDialog.OnPositiveButtonClickedListener() {
            @Override
            public void onPositiveButtonClicked(String text) {
                onPeopleNameChanged(text);
            }
        });
        FragmentManager fm = getActivity().getSupportFragmentManager();
        dialog.show(fm.beginTransaction().setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE),
                SingleEditTextDialog.class.getSimpleName());
    }

    private void onPeopleNameChanged(String name) {
        Log.d(TAG, "onPeopleNameChanged name = " + name);
        PeopleMergeAlbum mediaSet = (PeopleMergeAlbum) mMediaSet;
        if (name.equals(mMediaSet.getName())) {
            Log.d(TAG, "onPeopleNameChanged name is not changed.");
            return;
        }
        int[] faceIds = mediaSet.getFaceIds();
        StringBuilder sb = new StringBuilder();
        for (int id : faceIds) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(id);
        }
        boolean hasSameName = false;
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(DiscoverStore.FaceInfo.Media.CONTENT_URI,
                new String[]{
                        DiscoverStore.FaceInfo.Columns.NAME
                }, DiscoverStore.FaceInfo.Columns._ID + " not in (" + sb.toString() + ")", null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(cursor.getColumnIndex(DiscoverStore.FaceInfo.Columns.NAME)))) {
                    hasSameName = true;
                    break;
                }
            }
        }
        Utils.closeSilently(cursor);

        if (hasSameName) {
            mToast = ToastUtil.showMessage(getContext(), mToast, R.string.repeated_name, Toast.LENGTH_SHORT);
            return;
        }

        ContentValues values = new ContentValues();
        values.put(DiscoverStore.FaceInfo.Columns.NAME, name);
        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.FaceInfo.Media.CONTENT_URI,
                values, DiscoverStore.FaceInfo.Columns._ID + " in (" + sb.toString() + ")", null);
    }

    private void mergePeople() {
        MergeDialog dialog = new MergeDialog();
        dialog.setTitle(getString(R.string.merge_to));
        dialog.setNames(mNamedFaces);
        dialog.setOnItemSelectListener(new MergeDialog.OnItemSelectListener() {
            @Override
            public void onItemSelected(String text) {
                onMergeToPeople(text);
            }
        });
        FragmentManager fm = getActivity().getSupportFragmentManager();
        dialog.show(fm.beginTransaction().setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE),
                MergeDialog.class.getSimpleName());
    }

    private void onMergeToPeople(String name) {
        Log.d(TAG, "onMergeToPeople " + name);
        PeopleMergeAlbum mediaSet = (PeopleMergeAlbum) mMediaSet;
        int[] faceIds = mediaSet.getFaceIds();
        StringBuilder sb = new StringBuilder();
        for (int id : faceIds) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(id);
        }

        ContentValues values = new ContentValues();
        values.put(DiscoverStore.FaceInfo.Columns.NAME, name);

        GalleryAppImpl.getApplication().getContentResolver().update(DiscoverStore.FaceInfo.Media.CONTENT_URI, values,
                DiscoverStore.FaceInfo.Columns._ID + " in (" + sb.toString() + ")", null);

        //back
        onBackPressed();
    }

    @Override
    public void onDrmDetailsClicked(MediaItem mediaItem) {
        if (mDrmDetailsSource == null) {
            mDrmDetailsSource = new DrmDetailsSource();
        }
        mDrmDetailsSource.setMediaItem(mediaItem);

        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(getActivity(), null, mDrmDetailsSource);
            mDetailsHelper.setCloseListener(new DetailsHelper.CloseListener() {
                @Override
                public void onClose() {
                    mDetailsHelper.hide();
                }
            });
        }
        mDetailsHelper.reloadDrmDetails(true);
        mDetailsHelper.show();
    }

    @Override
    public boolean isBackConsumed() {
        Bundle data = new Bundle();
        data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_ALBUM_PAGE);
        data.putBoolean(Constants.KEY_IS_NEED_UPDATE, mIsDrmUpdated);
        setDataBack(data);
        return false;
    }

    @Override
    public void onTabSelected(@Nullable TabItemView tab) {
        if (tab == null) {
            return;
        }
        if (TabPhotoFragment.class.getSimpleName().equals(tab.getCurrentTab())) {
            Log.d(TAG, "onTabSelected " + tab.getCurrentTab());
            if (mIsFirstLoad) {
                mIsFirstLoad = false;
                if (mDataLoader != null) {
                    mDataLoader.resume();
                }
            } else {
                runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isPaused() && mDataLoader != null) {
                            mDataLoader.resume();
                        }
                    }
                }, LOAD_DELAY);
            }
        } else {
            //滑动到非当前界面, 停止加载数据
            if (mDataLoader != null) {
                mDataLoader.pause();
            }
        }
    }

    private void pauseDataLoader() {
        Log.d(TAG, "pauseDataLoader");
        if (mDataLoader != null) {
            mDataLoader.pause();
        }
    }

    private void resumeDataLoader() {
        Log.d(TAG, "resumeDataLoader");
        if (mDataLoader != null) {
            mDataLoader.resume();
        }
    }
}

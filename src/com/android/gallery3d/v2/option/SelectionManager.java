package com.android.gallery3d.v2.option;

import android.os.Handler;

import androidx.print.PrintHelper;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.data.AllMediaAlbum;
import com.android.gallery3d.v2.data.ImageItem;
import com.android.gallery3d.v2.data.LabelItem;
import com.android.gallery3d.v2.discover.data.PeopleMergeAlbum;
import com.android.gallery3d.v2.discover.data.ThingsAlbum;
import com.android.gallery3d.v2.trash.data.TrashAlbum;
import com.sprd.gallery3d.drm.MenuExecutorUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SelectionManager {
    private final HashSet<String> mSelectedItems;
    private final List<Path> mSelectedPathList;

    private Future mMenuTask;

    private final Handler mMainHandler;
    private Toast mToast = null;

    private boolean mIsTrashAlbum;
    private boolean mIsAllAlbum;

    private boolean mIsThingsAlbum;
    private boolean mIsPeopleAlbum;

    private boolean mIsDiscoverAlbum;

    public SelectionManager(MediaSet mediaSet) {
        mSelectedItems = new HashSet<>();
        mSelectedPathList = new ArrayList<>();
        mMainHandler = new Handler();
        mIsTrashAlbum = mediaSet != null && mediaSet instanceof TrashAlbum;
        mIsAllAlbum = mediaSet != null && mediaSet instanceof AllMediaAlbum;

        mIsThingsAlbum = mediaSet != null && mediaSet instanceof ThingsAlbum;
        mIsPeopleAlbum = mediaSet != null && mediaSet instanceof PeopleMergeAlbum;

        mIsDiscoverAlbum = mIsThingsAlbum | mIsPeopleAlbum;
    }

    public void resume() {

    }

    public void pause() {
        if (mMenuTask != null) {
            mMenuTask.cancel();
            mMenuTask = null;
        }
    }

    public void onImageSelected(ImageItem item) {
        if (item.isSelected()) {
            mSelectedItems.add(item.getItemPath());
        } else {
            mSelectedItems.remove(item.getItemPath());
        }
    }

    public void onLabelSelected(LabelItem item) {
        List<ImageItem> children = item.getChildren();
        if (item.isSelected()) {
            for (ImageItem imageItem : children) {
                mSelectedItems.add(imageItem.getItemPath());
            }
        } else {
            for (ImageItem imageItem : children) {
                mSelectedItems.remove(imageItem.getItemPath());
            }
        }
    }

    public void onSelectNon() {
        mSelectedItems.clear();
    }

    public int getSelectedCount() {
        return mSelectedItems.size();
    }

    public ArrayList<Path> getSelectedItems() {
        return new ArrayList<>(mSelectedPathList);
    }

    public ArrayList<String> getSelectedItemsWithString() {
        return new ArrayList<>(mSelectedItems);
    }

    private void updateMenuOperation(Menu menu, int supported) {
        boolean supportShare = (supported & MediaObject.SUPPORT_SHARE) != 0;
        boolean supportDelete = (supported & MediaObject.SUPPORT_DELETE) != 0;
        boolean supportEdit = (supported & MediaObject.SUPPORT_EDIT) != 0;
        boolean supportRotate = (supported & MediaObject.SUPPORT_ROTATE) != 0;
        boolean supportCrop = (supported & MediaObject.SUPPORT_CROP) != 0;
        boolean supportSetAs = (supported & MediaObject.SUPPORT_SETAS) != 0;
        boolean supportInfo = (supported & MediaObject.SUPPORT_INFO) != 0;
        boolean supportShowOnMap = (supported & MediaObject.SUPPORT_SHOW_ON_MAP) != 0;
        boolean supportPrint = (supported & MediaObject.SUPPORT_PRINT) != 0;
        supportPrint &= PrintHelper.systemSupportsPrint();
        boolean supportAddToAlbum = (supported & MediaObject.SUPPORT_ADD_TO_ALBUM) != 0;
        boolean supportTrashRestore = (supported & MediaObject.SUPPORT_TRASH_RESTORE) != 0;
        boolean supportTrashDelete = (supported & MediaObject.SUPPORT_TRASH_DELETE) != 0;
        boolean supportMoveThingsOut = (supported & MediaObject.SUPPORT_MOVE_THINGS_OUT) != 0;
        boolean supportMovePeopleOut = (supported & MediaObject.SUPPORT_MOVE_PEOPLE_OUT) != 0;

        setMenuItemVisible(menu.findItem(R.id.action_trash_restore), supportTrashRestore);
        setMenuItemVisible(menu.findItem(R.id.action_trash_delete), supportTrashDelete);
        setMenuItemVisible(menu.findItem(R.id.action_add_to_album), supportAddToAlbum);
        setMenuItemVisible(menu.findItem(R.id.action_share), supportShare);
        setMenuItemVisible(menu.findItem(R.id.action_delete), supportDelete);
        setMenuItemVisible(menu.findItem(R.id.action_edit), supportEdit);
        setMenuItemVisible(menu.findItem(R.id.action_rotate_ccw), supportRotate);
        setMenuItemVisible(menu.findItem(R.id.action_rotate_cw), supportRotate);
        setMenuItemVisible(menu.findItem(R.id.action_crop), supportCrop);
        setMenuItemVisible(menu.findItem(R.id.action_setas), supportSetAs);
        setMenuItemVisible(menu.findItem(R.id.action_details), supportInfo);
        setMenuItemVisible(menu.findItem(R.id.action_show_on_map), supportShowOnMap);
        setMenuItemVisible(menu.findItem(R.id.print), supportPrint);
        setMenuItemVisible(menu.findItem(R.id.action_move_out_things), supportMoveThingsOut);
        setMenuItemVisible(menu.findItem(R.id.action_move_out_people), supportMovePeopleOut);

        MenuExecutorUtils.getInstance().updateDrmMenuOperation(menu, supported);
    }

    private void setMenuItemVisible(MenuItem menuItem, boolean visible) {
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    private void showToast(String msg) {
        if (mToast == null) {
            mToast = Toast.makeText(GalleryAppImpl.getApplication(), msg, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(msg);
        }
        mToast.show();
    }

    public void updateMenu(final Menu menu) {
        if (menu == null) {
            return;
        }

        if (mMenuTask != null) {
            mMenuTask.cancel();
            mMenuTask = null;
        }

        mMenuTask = GalleryAppImpl.getApplication().getThreadPool().submitInThread(new ThreadPool.Job<Void>() {

            @Override
            public Void run(ThreadPool.JobContext jc) {
                mSelectedPathList.clear();
                for (String path : mSelectedItems) {
                    if (jc.isCancelled()) {
                        break;
                    }
                    mSelectedPathList.add(Path.fromString(path));
                }
                if (jc.isCancelled()) {
                    return null;
                }

                if (mSelectedPathList.size() == 0) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateMenuOperation(menu, 0);
                        }
                    });
                    return null;
                }

                /*
                if (!mIsTrashAlbum && mSelectedPathList.size() > MAX_SELECTED_ITEMS_FOR_SHARE_INTENT) {
                    mMainHandler.post(new Runnable() {
                        @SuppressLint({"StringFormatInvalid", "StringFormatMatches"})
                        @Override
                        public void run() {
                            showToast(GalleryAppImpl.getApplication().getString(R.string.can_not_share, MAX_SELECTED_ITEMS_FOR_SHARE_INTENT));
                        }
                    });
                }
                */

                int operation = 0;

                if (mIsTrashAlbum) {
                    operation |= MediaObject.SUPPORT_TRASH_RESTORE;
                    operation |= MediaObject.SUPPORT_TRASH_DELETE;
                } else if (!mIsDiscoverAlbum && !mIsAllAlbum) {
                    operation |= MediaObject.SUPPORT_ADD_TO_ALBUM;
                }

                if (mIsThingsAlbum) {
                    operation |= MediaObject.SUPPORT_MOVE_THINGS_OUT;
                }

                if (mIsPeopleAlbum) {
                    operation |= MediaObject.SUPPORT_MOVE_PEOPLE_OUT;
                }

                //share
                if (!mIsTrashAlbum/* && mSelectedPathList.size() <= MAX_SELECTED_ITEMS_FOR_SHARE_INTENT*/) {
                    boolean supportShare = true;
                    for (Path path : mSelectedPathList) {
                        if (jc.isCancelled()) {
                            break;
                        }
                        MediaObject mediaObject = GalleryAppImpl.getApplication().getDataManager().getMediaObject(path);
                        if (mediaObject != null
                                && ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_SHARE) != 0)) {
                        } else {
                            supportShare = false;
                            break;
                        }
                    }
                    if (supportShare) {
                        operation |= MediaObject.SUPPORT_SHARE;
                    }
                }

                //delete
                if (!mIsTrashAlbum) {
                    operation |= MediaObject.SUPPORT_DELETE;
                }

                if (mSelectedPathList.size() == 1) {
                    MediaObject mediaObject = GalleryAppImpl.getApplication().getDataManager().getMediaObject(mSelectedPathList.get(0));
                    if (mediaObject != null && mediaObject instanceof LocalImage) {
                        //edit
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_EDIT) != 0) {
                            operation |= MediaObject.SUPPORT_EDIT;
                        }
                        //crop
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_CROP) != 0) {
                            operation |= MediaObject.SUPPORT_CROP;
                        }
                        //setas
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_SETAS) != 0) {
                            operation |= MediaObject.SUPPORT_SETAS;
                        }
                        //show_on_map
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_SHOW_ON_MAP) != 0) {
                            operation |= MediaObject.SUPPORT_SHOW_ON_MAP;
                        }
                        //print
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_PRINT) != 0) {
                            operation |= MediaObject.SUPPORT_PRINT;
                        }
                    }

                    if (mediaObject != null) {
                        //details
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_INFO) != 0) {
                            operation |= MediaObject.SUPPORT_INFO;
                        }
                        //drm info
                        if ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_DRM_RIGHTS_INFO) != 0) {
                            operation |= MediaObject.SUPPORT_DRM_RIGHTS_INFO;
                        }
                    }
                }

                //rotate
                boolean supportRotate = true;
                for (Path path : mSelectedPathList) {
                    if (jc.isCancelled()) {
                        break;
                    }
                    MediaObject mediaObject = GalleryAppImpl.getApplication().getDataManager().getMediaObject(path);
                    if (mediaObject != null && mediaObject instanceof LocalImage
                            && ((mediaObject.getSupportedOperations() & MediaObject.SUPPORT_ROTATE) != 0)) {
                    } else {
                        supportRotate = false;
                        break;
                    }
                }
                if (jc.isCancelled()) {
                    return null;
                }

                if (supportRotate) {
                    operation |= MediaObject.SUPPORT_ROTATE;
                }

                final int op = operation;
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateMenuOperation(menu, op);
                    }
                });

                return null;
            }
        });
    }
}

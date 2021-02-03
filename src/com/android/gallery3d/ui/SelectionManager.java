/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.data.CameraMergeAlbum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SelectionManager {
    @SuppressWarnings("unused")
    private static final String TAG = "SelectionManager";

    public static final int ENTER_SELECTION_MODE = 1;
    public static final int LEAVE_SELECTION_MODE = 2;
    public static final int SELECT_ALL_MODE = 3;

    public static final String KEY_SELECT_ALBUM_ITEMS = "select.thirdparty.albums";
    public static final String KEY_SELECT_ALBUM_FLAG = "select.albums.flag";
    public static final String KEY_SELECT_LOCAL_ALBUMS = "select.local.albums";
    public static final String KEY_EDIT_ALBUMS_SCREEN = "enter.select.albums";

    private Set<String> mClickedSet;
    private MediaSet mSourceMediaSet;
    private SelectionListener mListener;
    private DataManager mDataManager;
    private boolean mInverseSelection;
    private boolean mIsAlbumSet;
    private boolean mInSelectionMode;
    private boolean mAutoLeave = true;
    private int mTotal;

    private Set<String> mClickedAlbumSet;

    private Set<String> mBuckIdSet = new HashSet<String>();
    private SharedPreferences mPrefs;
    private Set<String> mClickedPathSet;
    private boolean mIsSelectHide = false;
    private boolean mInAlbumSelectionMode = false;
    private Set<String> mLocalAlbumsSet;

    private SelectionClearListener mSelectionClearListener;

    public interface SelectionClearListener {
        void onSelectionCleared();
    }

    public void setSelectionClearListener(SelectionClearListener l) {
        mSelectionClearListener = l;
    }

    public interface SelectionListener {
        void onSelectionModeChange(int mode);

        void onSelectionChange();
    }

    public SelectionManager(Activity activity, boolean isAlbumSet) {
        if (activity instanceof AbstractGalleryActivity) {
            mDataManager = ((AbstractGalleryActivity) activity).getDataManager();
        } else if (activity instanceof GalleryActivity2) {
            mDataManager = ((GalleryActivity2) activity).getDataManager();
        }
        Utils.checkNotNull(mDataManager);
        mClickedSet = new HashSet<String>();
        mClickedAlbumSet = new HashSet<String>();
        mIsAlbumSet = isAlbumSet;
        mTotal = -1;
        mLocalAlbumsSet = mDataManager.getAllBucketId();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        mIsSelectHide = mPrefs.getBoolean(KEY_SELECT_ALBUM_FLAG, false);
    }

    // Whether we will leave selection mode automatically once the number of
    // selected items is down to zero.
    public void setAutoLeaveSelectionMode(boolean enable) {
        mAutoLeave = enable;
    }

    public void setSelectionListener(SelectionListener listener) {
        mListener = listener;
    }

    public void selectAll() {
        mInverseSelection = true;
        mClickedSet.clear();
        mTotal = -1;
        enterSelectionMode();
        if (mListener != null) {
            mListener.onSelectionModeChange(SELECT_ALL_MODE);
        }
    }

    public void deSelectAll() {
        leaveSelectionMode();
        mInverseSelection = false;
        mClickedSet.clear();
    }

    public boolean inSelectAllMode() {
        // SPRD: fix bug 377082,title is wrong when leave selectionAllMode
        // return mInverseSelection;
        return getSelectedCount() == getTotalCount();
    }

    public boolean inSelectionMode() {
        return mInSelectionMode;
    }

    public void enterSelectionMode() {
        if (mInSelectionMode) {
            return;
        }

        mInSelectionMode = true;
        if (mListener != null) {
            mListener.onSelectionModeChange(ENTER_SELECTION_MODE);
        }
    }

    public void leaveSelectionMode() {
        if (!mInSelectionMode) {
            return;
        }

        mInSelectionMode = false;
        mInverseSelection = false;
        mClickedSet.clear();
        mClickedAlbumSet.clear();
        if (mListener != null) {
            mListener.onSelectionModeChange(LEAVE_SELECTION_MODE);
        }
    }

    public boolean inAlbumSelectionMode() {
        return mInAlbumSelectionMode;
    }

    public void clearData() {
        if (mClickedSet != null) {
            mClickedSet.clear();
        }
        if (mClickedAlbumSet != null) {
            mClickedAlbumSet.clear();
        }

        if (mSelectionClearListener != null) {
            mSelectionClearListener.onSelectionCleared();
        }

        if (inSelectionMode()) {
            leaveSelectionMode();
        }
    }

    public void enterAlbumSelectionMode() {
        if (mInAlbumSelectionMode) {
            return;
        }
        mInAlbumSelectionMode = true;
        mClickedPathSet = mPrefs.getStringSet(KEY_SELECT_ALBUM_ITEMS, null);
        if (mClickedPathSet == null) {
            mClickedPathSet = new HashSet<String>();
        } else {
            mClickedPathSet = new HashSet<String>(mClickedPathSet);
        }
        /*remove not exist buckid  */
        mBuckIdSet = mDataManager.getAllBucketEntry();
        removeBuckIdInPathSet();
        /*remove not exist buckid  */
        if (mListener != null) {
            mListener.onSelectionModeChange(ENTER_SELECTION_MODE);
        }
    }

    public void leaveAlbumSelectionMode() {
        if (!mInAlbumSelectionMode) {
            return;
        }
        mInAlbumSelectionMode = false;
        mClickedAlbumSet.clear();
        mClickedPathSet.clear();
        if (mListener != null) {
            mListener.onSelectionModeChange(LEAVE_SELECTION_MODE);
        }
    }

    public boolean isItemSelected(Path itemId) {
        return mInverseSelection ^ mClickedSet.contains(itemId.toString());
    }

    private int getTotalCount() {
        if (mSourceMediaSet == null) {
            return -1;
        }

        if (mTotal < 0) {
            mTotal = mIsAlbumSet
                    ? mSourceMediaSet.getSubMediaSetCount()
                    : mSourceMediaSet.getMediaItemCount();
        }
        return mTotal;
    }

    public int getSelectedCount() {
        int count = mClickedSet.size();
        if (mInverseSelection) {
            count = getTotalCount() - count;
        }
        return count;
    }

    public boolean toggle(Path path) {
        boolean result = false;
        if (mClickedSet.contains(path.toString())) {
            mClickedSet.remove(path.toString());
            result = false;
        } else {
            enterSelectionMode();
            mClickedSet.add(path.toString());
            result = true;
        }

        // Convert to inverse selection mode if everything is selected.
        int count = getSelectedCount();
        //if (count == getTotalCount()) {
        //    selectAll();
        //}

        if (mListener != null) {
            mListener.onSelectionChange();
        }
        if (count == 0 && mAutoLeave) {
            leaveSelectionMode();
        }
        return result;
    }

    public void toggle(Path path, boolean labelSelected) {
        if (mClickedSet.contains(path.toString())) {
            if (!labelSelected) {
                mClickedSet.remove(path.toString());
            }
        } else {
            enterSelectionMode();
            mClickedSet.add(path.toString());
        }
    }

    public void updateMenu() {
        int count = getSelectedCount();
        if (mListener != null) {
            mListener.onSelectionChange();
        }
        if (count == 0 && mAutoLeave) {
            leaveSelectionMode();
        }
    }

    private static boolean expandMediaSet(ArrayList<Path> items, MediaSet set, int maxSelection) {
        int subCount = set.getSubMediaSetCount();
        for (int i = 0; i < subCount; i++) {
            if (!expandMediaSet(items, set.getSubMediaSet(i), maxSelection)) {
                return false;
            }
        }
        int total = set.getMediaItemCount();
        int batch = 50;
        int index = 0;

        while (index < total) {
            int count = index + batch < total
                    ? batch
                    : total - index;
            ArrayList<MediaItem> list = set.getMediaItem(index, count);
            if (list != null
                    && list.size() > (maxSelection - items.size())) {
                return false;
            }
            // SPRD for bug 502899,[coverity] CID70705 list maybe is null
            if (list != null) {
                for (MediaItem item : list) {
                    items.add(item.getPath());
                }
            }
            index += batch;
        }
        return true;
    }

    public ArrayList<Path> getSelected(boolean expandSet) {
        return getSelected(expandSet, Integer.MAX_VALUE);
    }

    public ArrayList<Path> getSelected(boolean expandSet, int maxSelection) {
        ArrayList<Path> selected = new ArrayList<Path>();
//        if (mIsAlbumSet) {
//            if (mInverseSelection) {
//                int total = getTotalCount();
//                for (int i = 0; i < total; i++) {
//                    MediaSet set = mSourceMediaSet.getSubMediaSet(i);
//                    if(set == null){
//                        return null;
//                    }
//                    Path id = set.getPath();
//                    if (!mClickedSet.contains(id)) {
//                        if (expandSet) {
//                            if (!expandMediaSet(selected, set, maxSelection)) {
//                                return null;
//                            }
//                        } else {
//                            selected.add(id);
//                            if (selected.size() > maxSelection) {
//                                return null;
//                            }
//                        }
//                    }
//                }
//            } else {
//                for (Path id : mClickedSet) {
//                    if (expandSet) {
//                        if (!expandMediaSet(selected, mDataManager.getMediaSet(id),
//                                maxSelection)) {
//                            return null;
//                        }
//                    } else {
//                        selected.add(id);
//                        if (selected.size() > maxSelection) {
//                            return null;
//                        }
//                    }
//                }
//            }
//        } else {
        if (mInverseSelection) {
            int total = getTotalCount();
            int index = 0;
            while (index < total) {
                int count = Math.min(total - index, MediaSet.MEDIAITEM_BATCH_FETCH_COUNT);
                ArrayList<MediaItem> list = mSourceMediaSet.getMediaItem(index, count);
                for (MediaItem item : list) {
                    if (item == null) {
                        return null;
                    }
                    Path id = item.getPath();
                    if (!mClickedSet.contains(id.toString())) {
                        selected.add(id);
                        if (selected.size() > maxSelection) {
                            return null;
                        }
                    }
                }
                index += count;
            }
        } else {
            for (String id : mClickedSet) {
                selected.add(Path.fromString(id));
                if (selected.size() > maxSelection) {
                    return null;
                }
            }
        }
//        }
        return selected;
    }

    public void setSourceMediaSet(MediaSet set) {
        mSourceMediaSet = set;
        mTotal = -1;
    }

    /**
     * SPRD: Performance issues porting Get the flag of album set @{
     *
     * @return Whether is album set.
     */
    public boolean getIsAlbumSet() {
        return mIsAlbumSet;
    }

    /**
     * @{
     */

    // SPRD: Modify 20151230 for bug518277, selected count do not update after
    //  receiving incoming files via bluetooth. @{
    public void onSourceContentChanged() {
        // reset and reload total count since source set data has changed
        mTotal = -1;
        int count = getTotalCount();
        if (count == 0) {
            leaveSelectionMode();
        }
    }
    // @}

    public void addItemPath(Path path) {
        if (!mClickedSet.contains(path.toString())) {
            mClickedSet.add(path.toString());
        }
    }

    public void removeItemPath(Path path) {
        if (mClickedSet.contains(path.toString())) {
            mClickedSet.remove(path.toString());
        }
    }

    public void addAlbumPath(Path path) {
        if (!mClickedAlbumSet.contains(path.toString())) {
            mClickedAlbumSet.add(path.toString());

        }
        if (mInAlbumSelectionMode && !mClickedPathSet.contains(path.toString())) {
            mClickedPathSet.add(path.toString());
        }
    }

    public void removeAlbumPath(Path path) {
        if (mClickedAlbumSet.contains(path.toString())) {
            mClickedAlbumSet.remove(path.toString());
        }
        if (mInAlbumSelectionMode) {
            mClickedPathSet.remove(path.toString());
        }
    }

    public boolean isAlbumSelected(Path path) {
        if (mInAlbumSelectionMode) {
            return isHideAlbumSelected(path);
        }
        return mInverseSelection ^ mClickedAlbumSet.contains(path.toString());
    }

    public boolean isHideAlbumSelected(Path path) {
        return mInverseSelection ^ mClickedPathSet.contains(path.toString());
    }

    public boolean isLocalAlbum(Path path) {
        for (String localAlbum : mLocalAlbumsSet) {
            if (path.toString().contains(localAlbum)) {
                return true;
            }
        }
        if (CameraMergeAlbum.PATH.equals(path)) {
            return true;
        }
        return false;
    }

    public void logSelectedItem() {
        Object[] clickItems = mClickedSet.toArray();
        int i = 0;
        for (Object path : clickItems) {
            Log.d(TAG, "selected item " + i + " : " + path);
            i++;
        }
    }

    public void logSelectedAlbum() {
        Object[] clickAlbums = mClickedAlbumSet.toArray();
        int i = 0;
        for (Object path : clickAlbums) {
            Log.d(TAG, "selected album " + i + " : " + path);
            i++;
        }
    }

    public void toggleAlbum(Path path, ArrayList<MediaItem> items) {
        boolean selected = false;
        if (isAlbumSelected(path)) {
            Log.d(TAG, "toggleAlbum remove " + path);
            removeAlbumPath(path);
            for (MediaItem item : items) {
                removeItemPath(item.getPath());
            }
            selected = false;
        } else {
            Log.d(TAG, "toggleAlbum add " + path);
            addAlbumPath(path);
            if (!mInAlbumSelectionMode) {
                for (MediaItem item : items) {
                    addItemPath(item.getPath());
                }
            }
            selected = true;
        }
        if (mListener != null) {
            mListener.onSelectionChange();
        }
    }

    public void setSelectAlbumItems() {
        //if (mClickedPathSet.size() == 0)
        //    return;
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putStringSet(KEY_SELECT_ALBUM_ITEMS, mClickedPathSet);
        editor.putBoolean(KEY_SELECT_ALBUM_FLAG, true);
        editor.commit();
    }

    public boolean alreadyHaveHideAlbum() {
        Set<String> stringSet = mPrefs.getStringSet(KEY_SELECT_ALBUM_ITEMS, null);
        return stringSet != null && stringSet.size() > 0;
    }

    public void setLocalAlbumsFlags(boolean localAlbum) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(KEY_SELECT_LOCAL_ALBUMS, localAlbum);
        editor.commit();
    }

    public boolean getLocalAlbumsFlags() {
        boolean localAlbum = mPrefs.getBoolean(KEY_SELECT_LOCAL_ALBUMS, false);
        return localAlbum;
    }

    public boolean isAlbumHided() {
        return mPrefs.getBoolean(KEY_SELECT_ALBUM_FLAG, false);
    }

    public void setEditAblumFlags(boolean selectAlbum) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(KEY_EDIT_ALBUMS_SCREEN, selectAlbum);
        editor.commit();
    }

    public int getSelectedAlbumCount() {
        if (mClickedPathSet != null) {
            return mClickedPathSet.size();
        } else {
            return 0;
        }
    }

    private void removeBuckIdInPathSet() {
        /* can't operate data when traverse */
        Set<String> removeItems = new HashSet<String>();
        if (mClickedPathSet.size() > 0) {
            Log.i(TAG, " removeBuckIdInPathSet mClickedPathSet = " + mClickedPathSet);
            for (String entry : mClickedPathSet) {
                if (mBuckIdSet.contains(entry)) {
                    continue;
                } else {
                    removeItems.add(entry);
                }
            }
        } /* can't operate data when traverse */

        for (String entry : removeItems) {
            mClickedPathSet.remove(entry);
        }
    }

}

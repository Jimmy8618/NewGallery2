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

package com.android.gallery3d.data;

import android.database.ContentObserver;
import android.net.Uri;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.v2.data.DeleteManager;
import com.android.gallery3d.v2.trash.TrashManager;

import java.util.concurrent.atomic.AtomicBoolean;

// This handles change notification for media sets.
public class ChangeNotifier extends ContentObserver implements ContentListener {

    private MediaSet mMediaSet;
    private AtomicBoolean mContentDirty = new AtomicBoolean(true);

    public ChangeNotifier(MediaSet set, Uri[] uris, GalleryApp application) {
        super(application.getDataManager().getDefaultMainHandler());
        mMediaSet = set;
        //注册文件更新监听, DeleteManager 调用 onContentDirty 方法会回调下面的 onContentDirty 方法
        DeleteManager.getDefault().registerContentListener(this);
        DataManager.from(application.getAndroidContext()).registerContentListener(this);
        for (int i = 0; i < uris.length; i++) {
            if (uris[i] != null) {
                application.getContentResolver().registerContentObserver(uris[i], true, this);
            }
        }
    }

    // Returns the dirty flag and clear it.
    public boolean isDirty() {
        return mContentDirty.compareAndSet(true, false);
    }

    public void fakeChange() {
        onChange(false, Uri.parse("content://com.android.gallery3d.data.ChangeNotifier/fakeChange"));
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        //若文件正在删除, 不更新数据, 其他如图片添加, 旋转, 也将无法得到即时更新, 目前无解, 需等图片删完后一起更新
        if (DeleteManager.getDefault().isBusy()) {
            return;
        }
        //最近删除 : 删除或恢复时, 不更新数据
        if (TrashManager.getDefault().isBusy()) {
            return;
        }
        if (mContentDirty.compareAndSet(false, true)) {
            mMediaSet.notifyContentChanged(uri);
        }
    }

    /**
     * 由 DeleteManager 调用, 更新数据
     *
     * @param uri null
     */
    @Override
    public void onContentDirty(Uri uri) {
        if (mContentDirty.compareAndSet(false, true)) {
            mMediaSet.notifyContentChanged(uri);
        }
    }
}
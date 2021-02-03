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

import com.android.gallery3d.app.GalleryApp;

public class ClusterSource extends MediaSource {
    static final int CLUSTER_ALBUMSET_ALL = 0;
    static final int CLUSTER_ALBUMSET_PHOTO = 1;
    static final int CLUSTER_ALBUMSET_VIDEO = 4;

    static final int CLUSTER_ALBUMSET_TAG = 2;
    static final int CLUSTER_ALBUMSET_SIZE = 3;

    static final int CLUSTER_ALBUM_ALL = 0x100;
    static final int CLUSTER_ALBUM_PHOTO = 0x101;
    static final int CLUSTER_ALBUM_VIDEO = 0x104;

    static final int CLUSTER_ALBUM_TAG = 0x102;
    static final int CLUSTER_ALBUM_SIZE = 0x103;

    GalleryApp mApplication;
    PathMatcher mMatcher;

    public ClusterSource(GalleryApp application) {
        super("cluster");
        mApplication = application;
        mMatcher = new PathMatcher();
        mMatcher.add("/cluster/*/all", CLUSTER_ALBUMSET_ALL);
        mMatcher.add("/cluster/*/photo", CLUSTER_ALBUMSET_PHOTO);
        mMatcher.add("/cluster/*/tag", CLUSTER_ALBUMSET_TAG);
        mMatcher.add("/cluster/*/size", CLUSTER_ALBUMSET_SIZE);
        mMatcher.add("/cluster/*/video", CLUSTER_ALBUMSET_VIDEO);

        mMatcher.add("/cluster/*/all/*", CLUSTER_ALBUM_ALL);
        mMatcher.add("/cluster/*/photo/*", CLUSTER_ALBUM_PHOTO);
        mMatcher.add("/cluster/*/tag/*", CLUSTER_ALBUM_TAG);
        mMatcher.add("/cluster/*/size/*", CLUSTER_ALBUM_SIZE);
        mMatcher.add("/cluster/*/video/*", CLUSTER_ALBUM_VIDEO);
    }

    // The names we accept are:
    // /cluster/{set}/time      /cluster/{set}/time/k
    // /cluster/{set}/location  /cluster/{set}/location/k
    // /cluster/{set}/tag       /cluster/{set}/tag/encoded_tag
    // /cluster/{set}/size      /cluster/{set}/size/min_size
    @Override
    public MediaObject createMediaObject(Path path) {
        int matchType = mMatcher.match(path);
        String setsName = mMatcher.getVar(0);
        DataManager dataManager = mApplication.getDataManager();
        MediaSet[] sets = dataManager.getMediaSetsFromString(setsName);
        switch (matchType) {
            case CLUSTER_ALBUMSET_ALL:
            case CLUSTER_ALBUMSET_PHOTO:
            case CLUSTER_ALBUMSET_TAG:
            case CLUSTER_ALBUMSET_SIZE:
            case CLUSTER_ALBUMSET_VIDEO:
                return new ClusterAlbumSet(path, mApplication, sets[0], matchType);
            case CLUSTER_ALBUM_ALL:
            case CLUSTER_ALBUM_PHOTO:
            case CLUSTER_ALBUM_TAG:
            case CLUSTER_ALBUM_SIZE:
            case CLUSTER_ALBUM_VIDEO: {
                MediaSet parent = dataManager.getMediaSet(path.getParent());
                // The actual content in the ClusterAlbum will be filled later
                // when the reload() method in the parent is run.
                return new ClusterAlbum(path, dataManager, parent);
            }
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}

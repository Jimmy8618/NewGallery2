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

package com.android.gallery3d.util;

import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.sprd.gallery3d.app.StorageInfos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MediaSetUtils {
    public static final Comparator<MediaSet> NAME_COMPARATOR = new NameComparator();

    public static final String INTERNAL_CAMERA = StorageInfos.getInternalStorageDirectory().toString() + "/"
            + BucketNames.CAMERA;

    /* internal path */
    public static final int INTERNAL_STORAGE_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString());
    public static final int DCIM_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString() + "/"
                    + BucketNames.DCIM);
    public static final int CAMERA_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() + "/"
                    + BucketNames.CAMERA);
    public static final int DOWNLOAD_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() + "/"
                    + BucketNames.DOWNLOAD);
    public static final int EDITED_ONLINE_PHOTOS_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() + "/"
                    + BucketNames.EDITED_ONLINE_PHOTOS);
    public static final int IMPORTED_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() + "/"
                    + BucketNames.IMPORTED);
    public static final int SNAPSHOT_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.SCREENSHOTS);

    public static final int BLUETOOTH_BUCKET_ID = GalleryUtils.getBucketId(
            /*Environment.getExternalStorageDirectory().toString() + "/"*/
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.BLUETOOTH1);
    public static final int BLUETOOTH_BUCKET_2_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.BLUETOOTH2);
    public static final int VIDEO_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.VIDEO);
    public static final int BROWSER_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.BROWSER);
    public static final int QQ_IMAGES_ID = GalleryUtils.getBucketId(
            StorageInfos.getInternalStorageDirectory().toString() +
                    "/" + BucketNames.QQ_IMAGES);
    /* internal path */

    /* extrernal path */
    public static final int EXTERNAL_STORAGE_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString());
    public static final int EXTERNAL_DCIM_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() + "/"
                    + BucketNames.DCIM);
    public static final int EXTERNAL_CAMERA_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() + "/"
                    + BucketNames.CAMERA);
    public static final int EXTERNAL_DOWNLOAD_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() + "/"
                    + BucketNames.DOWNLOAD);
    public static final int EXTERNAL_EDITED_ONLINE_PHOTOS_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() + "/"
                    + BucketNames.EDITED_ONLINE_PHOTOS);
    public static final int EXTERNAL_IMPORTED_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() + "/"
                    + BucketNames.IMPORTED);
    public static final int EXTERNAL_SNAPSHOT_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.SCREENSHOTS);
    public static final int EXTERNAL_BLUETOOTH_BUCKET_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.BLUETOOTH1);
    public static final int EXTERNAL_BLUETOOTH_BUCKET_2_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.BLUETOOTH2);
    public static final int EXTERNAL_VIDEO_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.VIDEO);
    public static final int EXTERNAL_BROWSER_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.BROWSER);
    public static final int EXTERNAL_QQ_IMAGES_ID = GalleryUtils.getBucketId(
            StorageInfos.getExternalStorageDirectory().toString() +
                    "/" + BucketNames.QQ_IMAGES);
    /* extrernal path */


    private static final Path[] CAMERA_PATHS = {
            Path.fromString("/local/all/" + CAMERA_BUCKET_ID),
            Path.fromString("/local/image/" + CAMERA_BUCKET_ID),
            Path.fromString("/local/video/" + CAMERA_BUCKET_ID),
            Path.fromString("/local/all/" + DCIM_BUCKET_ID),
            Path.fromString("/local/image/" + DCIM_BUCKET_ID),
            Path.fromString("/local/video/" + DCIM_BUCKET_ID),
            Path.fromString("/local/all/" + EXTERNAL_CAMERA_BUCKET_ID),
            Path.fromString("/local/image/" + EXTERNAL_CAMERA_BUCKET_ID),
            Path.fromString("/local/video/" + EXTERNAL_CAMERA_BUCKET_ID),
            Path.fromString("/local/all/" + EXTERNAL_DCIM_BUCKET_ID),
            Path.fromString("/local/image/" + EXTERNAL_DCIM_BUCKET_ID),
            Path.fromString("/local/video/" + EXTERNAL_DCIM_BUCKET_ID),};

    public static boolean isCameraSource(Path path) {
        return CAMERA_PATHS[0] == path || CAMERA_PATHS[1] == path
                || CAMERA_PATHS[2] == path || CAMERA_PATHS[3] == path
                || CAMERA_PATHS[4] == path || CAMERA_PATHS[5] == path
                || CAMERA_PATHS[6] == path || CAMERA_PATHS[7] == path
                || CAMERA_PATHS[8] == path || CAMERA_PATHS[9] == path
                || CAMERA_PATHS[10] == path || CAMERA_PATHS[11] == path;
    }

    // Sort MediaSets by name
    public static class NameComparator implements Comparator<MediaSet> {
        @Override
        public int compare(MediaSet set1, MediaSet set2) {
            int result = set1.getName().compareToIgnoreCase(set2.getName());
            if (result != 0) {
                return result;
            }
            return set1.getPath().toString().compareTo(set2.getPath().toString());
        }
    }

    public static List<Integer> getLocalAlbumBuckedIds() {
        List<Integer> ids = new ArrayList<>();
        ids.add(CAMERA_BUCKET_ID);
        ids.add(DOWNLOAD_BUCKET_ID);
        ids.add(SNAPSHOT_BUCKET_ID);
        ids.add(BLUETOOTH_BUCKET_ID);
        ids.add(BLUETOOTH_BUCKET_2_ID);
        ids.add(VIDEO_ID);
        ids.add(BROWSER_ID);

        ids.add(EXTERNAL_CAMERA_BUCKET_ID);
        ids.add(EXTERNAL_DOWNLOAD_BUCKET_ID);
        ids.add(EXTERNAL_SNAPSHOT_BUCKET_ID);
        ids.add(EXTERNAL_BLUETOOTH_BUCKET_ID);
        ids.add(EXTERNAL_BLUETOOTH_BUCKET_2_ID);
        ids.add(EXTERNAL_VIDEO_ID);
        ids.add(EXTERNAL_BROWSER_ID);
        return ids;
    }
}

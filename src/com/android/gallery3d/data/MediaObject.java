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

import android.net.Uri;
import android.util.Log;

public abstract class MediaObject {
    @SuppressWarnings("unused")
    private static final String TAG = "MediaObject";
    public static final long INVALID_DATA_VERSION = -1;

    // These are the bits returned from getSupportedOperations():
    public static final int SUPPORT_DELETE = 1 << 0;
    public static final int SUPPORT_ROTATE = 1 << 1;
    public static final int SUPPORT_SHARE = 1 << 2;
    public static final int SUPPORT_CROP = 1 << 3;
    public static final int SUPPORT_SHOW_ON_MAP = 1 << 4;
    public static final int SUPPORT_SETAS = 1 << 5;
    public static final int SUPPORT_FULL_IMAGE = 1 << 6;
    public static final int SUPPORT_PLAY = 1 << 7;
    public static final int SUPPORT_CACHE = 1 << 8;
    public static final int SUPPORT_EDIT = 1 << 9;
    public static final int SUPPORT_INFO = 1 << 10;
    public static final int SUPPORT_TRIM = 1 << 11;
    public static final int SUPPORT_UNLOCK = 1 << 12;
    public static final int SUPPORT_BACK = 1 << 13;
    public static final int SUPPORT_ACTION = 1 << 14;
    public static final int SUPPORT_CAMERA_SHORTCUT = 1 << 15;
    public static final int SUPPORT_MUTE = 1 << 16;
    public static final int SUPPORT_PRINT = 1 << 17;
    public static final int SUPPORT_DRM_RIGHTS_INFO = 1 << 18;
    public static final int SUPPORT_BLENDING = 1 << 19;
    public static final int SUPPORT_ADD_TO_ALBUM = 1 << 20;
    public static final int SUPPORT_TRASH_RESTORE = 1 << 21;
    public static final int SUPPORT_TRASH_DELETE = 1 << 22;
    public static final int SUPPORT_MOVE_THINGS_OUT = 1 << 23;
    public static final int SUPPORT_MOVE_PEOPLE_OUT = 1 << 24;

    public static final int SUPPORT_ALL = 0xffffffff;

    // These are the bits returned from getMediaType():
    public static final int MEDIA_TYPE_UNKNOWN = 1 << 0;
    public static final int MEDIA_TYPE_IMAGE = 1 << 1;
    public static final int MEDIA_TYPE_VIDEO = 1 << 2;
    //SPRD:add for suport gif
    public static final int MEDIA_TYPE_GIF = 1 << 3;
    // SPRD: fix bug 387548, WBMP don't support edit
    public static final int MEDIA_TYPE_IMAGE_WBMP = 1 << 4;
    public static final int MEDIA_TYPE_IMAGE_BURST_COVER = 1 << 5;
    public static final int MEDIA_TYPE_IMAGE_PHOTO_VOICE = 1 << 6;
    public static final int MEDIA_TYPE_IMAGE_HDR = 1 << 7;
    public static final int MEDIA_TYPE_IMAGE_VHDR = 1 << 8;
    public static final int MEDIA_TYPE_IMAGE_BURST = 1 << 9;
    public static final int MEDIA_TYPE_IMAGE_THUMB = 1 << 10;
    //    public static final int MEDIA_TYPE_IMAGE_REFOCUS = 1 << 11;
    //    public static final int MEDIA_TYPE_IMAGE_REFOCUS_NOBOKEH = 1 << 12;

    public static final int MEDIA_TYPE_IMAGE_BLUR = 1 << 11; // blur
    public static final int MEDIA_TYPE_IMAGE_BOKEH = 1 << 12; //do bokeh in camera
    public static final int MEDIA_TYPE_IMAGE_BOKEH_GALLERY = 1 << 13; // bokeh and save in gallery
    public static final int MEDIA_TYPE_IMAGE_AI_SCENE = 1 << 14;
    public static final int MEDIA_TYPE_IMAGE_BOKEH_HDR = 1 << 15;//do bokeh in camera
    public static final int MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY = 1 << 16;// bokeh and save in gallery
    public static final int MEDIA_TYPE_IMAGE_MOTION_PHOTO = 1 << 17;//motion photo
    public static final int MEDIA_TYPE_IMAGE_AI_SCENE_HDR = 1 << 18;
    public static final int MEDIA_TYPE_IMAGE_RAW = 1 << 19;
    public static final int MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO = 1 << 20;//motion hdr photo
    public static final int MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO = 1 << 21;//motion AI photo
    public static final int MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO = 1 << 22;//motion hdr AI photo

    /*support FDR pic*/
    public static final int MEDIA_TYPE_IMAGE_FDR = 1 << 23;
    public static final int MEDIA_TYPE_IMAGE_VFDR = 1 << 24;
    public static final int MEDIA_TYPE_IMAGE_BOKEH_FDR = 1 << 25;
    public static final int MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY = 1 << 26;
    public static final int MEDIA_TYPE_IMAGE_AI_SCENE_FDR = 1 << 27;
    public static final int MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO = 1 << 28;
    public static final int MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO = 1 << 29;

    public static final int MEDIA_TYPE_ALL = MEDIA_TYPE_IMAGE | MEDIA_TYPE_VIDEO;

    public static final String MEDIA_TYPE_IMAGE_STRING = "image";
    public static final String MEDIA_TYPE_VIDEO_STRING = "video";
    public static final String MEDIA_TYPE_ALL_STRING = "all";

    // These are flags for cache() and return values for getCacheFlag():
    public static final int CACHE_FLAG_NO = 0;
    public static final int CACHE_FLAG_SCREENNAIL = 1;
    public static final int CACHE_FLAG_FULL = 2;

    // These are return values for getCacheStatus():
    public static final int CACHE_STATUS_NOT_CACHED = 0;
    public static final int CACHE_STATUS_CACHING = 1;
    public static final int CACHE_STATUS_CACHED_SCREENNAIL = 2;
    public static final int CACHE_STATUS_CACHED_FULL = 3;

    private static long sVersionSerial = 0;

    protected long mDataVersion;

    protected final Path mPath;

    public interface PanoramaSupportCallback {
        void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                   boolean isPanorama360);
    }

    public MediaObject(Path path, long version) {
        path.setObject(this);
        mPath = path;
        mDataVersion = version;
    }

    public Path getPath() {
        return mPath;
    }

    public int getSupportedOperations() {
        return 0;
    }

    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        callback.panoramaInfoAvailable(this, false, false);
    }

    public void clearCachedPanoramaSupport() {
    }

    public void delete() {
        throw new UnsupportedOperationException();
    }

    public void restore() {
        throw new UnsupportedOperationException();
    }

    public void moveOutThings() {
        throw new UnsupportedOperationException();
    }

    public void moveOutPeople() {
        throw new UnsupportedOperationException();
    }

    public void rotate(int degrees) {
        throw new UnsupportedOperationException();
    }

    public Uri getContentUri() {
        String className = getClass().getName();
        Log.e(TAG, "Class " + className + "should implement getContentUri.");
        Log.e(TAG, "The object was created from path: " + getPath());
        throw new UnsupportedOperationException();
    }

    public Uri getPlayUri() {
        throw new UnsupportedOperationException();
    }

    public int getMediaType() {
        return MEDIA_TYPE_UNKNOWN;
    }

    public MediaDetails getDetails() {
        MediaDetails details = new MediaDetails();
        return details;
    }

    public long getDataVersion() {
        return mDataVersion;
    }

    public int getCacheFlag() {
        return CACHE_FLAG_NO;
    }

    public int getCacheStatus() {
        throw new UnsupportedOperationException();
    }

    public long getCacheSize() {
        throw new UnsupportedOperationException();
    }

    public void cache(int flag) {
        throw new UnsupportedOperationException();
    }

    public static synchronized long nextVersionNumber() {
        return ++MediaObject.sVersionSerial;
    }

    public static int getTypeFromString(String s) {
        if (MEDIA_TYPE_ALL_STRING.equals(s)) {
            return MediaObject.MEDIA_TYPE_ALL;
        }
        if (MEDIA_TYPE_IMAGE_STRING.equals(s)) {
            return MediaObject.MEDIA_TYPE_IMAGE;
        }
        if (MEDIA_TYPE_VIDEO_STRING.equals(s)) {
            return MediaObject.MEDIA_TYPE_VIDEO;
        }
        throw new IllegalArgumentException(s);
    }

    public static String getTypeString(int type) {
        switch (type) {
            case MEDIA_TYPE_IMAGE:
                return MEDIA_TYPE_IMAGE_STRING;
            case MEDIA_TYPE_VIDEO:
                return MEDIA_TYPE_VIDEO_STRING;
            case MEDIA_TYPE_ALL:
                return MEDIA_TYPE_ALL_STRING;
        }
        throw new IllegalArgumentException();
    }

    public String getDurationString() {
        return "";
    }
}

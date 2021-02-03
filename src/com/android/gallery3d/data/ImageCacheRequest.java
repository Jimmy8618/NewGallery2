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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.data.BytesBufferPool.BytesBuffer;
import com.android.gallery3d.data.LocalImage.LocalImageRequest;
import com.android.gallery3d.data.LocalVideo.LocalVideoRequest;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.LocalMediaItemUtils;

abstract class ImageCacheRequest implements Job<Bitmap> {
    private static final String TAG = "ImageCacheRequest";

    protected GalleryApp mApplication;
    private Path mPath;
    private int mType;
    private int mTargetSize;
    private long mTimeModified;
    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();

    public ImageCacheRequest(GalleryApp application,
                             Path path, long timeModified, int type, int targetSize) {
        mApplication = application;
        mPath = path;
        mType = type;
        mTargetSize = targetSize;
        mTimeModified = timeModified;
    }

    private String debugTag() {
        return mPath + "," + mTimeModified + "," +
                ((mType == MediaItem.TYPE_THUMBNAIL) ? "THUMB" :
                        (mType == MediaItem.TYPE_MICROTHUMBNAIL) ? "MICROTHUMB" : "?");
    }

    @Override
    public Bitmap run(JobContext jc) {
        GalleryUtils.start(this.getClass(), "run() mPath =" + mPath);
        ImageCacheService cacheService = mApplication.getImageCacheService();

        BytesBuffer buffer = MediaItem.getBytesBufferPool().get();
        try {
            boolean found = cacheService.getImageData(mPath, mTimeModified, mType, buffer);
            /* SPRD: Drm feature start @{ */
            boolean isLocalImageRequest = (this instanceof LocalImageRequest);
            Uri uri = isLocalImageRequest ? ((LocalImageRequest) this).mUri : ((LocalVideoRequest) this).mUri;
            String path = isLocalImageRequest ? ((LocalImageRequest) this).mLocalFilePath : ((LocalVideoRequest) this).mLocalFilePath;
            if (uri != null) {
                found = LocalMediaItemUtils.getInstance().isCanFound(found, uri,
                        isLocalImageRequest ? MediaObject.MEDIA_TYPE_IMAGE
                                : MediaObject.MEDIA_TYPE_VIDEO);
            } else {
                found = LocalMediaItemUtils.getInstance().isCanFound(found, path,
                        isLocalImageRequest ? MediaObject.MEDIA_TYPE_IMAGE
                                : MediaObject.MEDIA_TYPE_VIDEO);
            }
            GalleryUtils.logs(this.getClass(), "run(), cache found = " + found);
            /* SPRD: Drm feature end @} */
            if (jc.isCancelled()) {
                return null;
            }
            if (found) {
                GalleryUtils.start(this.getClass(), "run(), decode use cache");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = isLowRam ? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
                Bitmap bitmap;
                if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
                    bitmap = DecodeUtils.decodeUsingPool(jc,
                            buffer.data, buffer.offset, buffer.length, options);
                } else {
                    bitmap = DecodeUtils.decodeUsingPool(jc,
                            buffer.data, buffer.offset, buffer.length, options);
                }
                if (bitmap == null && !jc.isCancelled()) {
                    Log.w(TAG, "decode cached failed " + debugTag());
                }
                GalleryUtils.end(this.getClass(), "run(), decode use cache mPath =" + mPath);
                return bitmap;
            }
        } finally {
            MediaItem.getBytesBufferPool().recycle(buffer);
        }
        GalleryUtils.start(this.getClass(), "run(), onDecodeOriginal");
        Bitmap bitmap = onDecodeOriginal(jc, mType);
        GalleryUtils.end(this.getClass(), "run(), onDecodeOriginal");
        if (jc.isCancelled()) {
            return null;
        }

        if (bitmap == null) {
            Log.w(TAG, "decode orig failed " + debugTag());
            return null;
        }
        GalleryUtils.start(this.getClass(), "run(),resize bitmap");
        if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
            bitmap = BitmapUtils.resizeAndCropCenter(bitmap, mTargetSize, true);
        } else {
            bitmap = BitmapUtils.resizeDownBySideLength(bitmap, mTargetSize, true);
        }
        GalleryUtils.end(this.getClass(), "run(),resize bitmap");
        if (jc.isCancelled()) {
            return null;
        }

        mApplication.getThreadPool().submit(
                new BitmapCacheJob(cacheService, bitmap, mPath, mTimeModified, mType));
        /*
        GalleryUtils.start(this.getClass(), "run(), compress bitmap to Bytes,and cache");
        byte[] array = BitmapUtils.compressToBytes(bitmap);
        if (jc.isCancelled()) return null;
        cacheService.putImageData(mPath, mTimeModified, mType, array);
        GalleryUtils.end(this.getClass(), "run(), compress bitmap to Bytes,and cache, mPath = " + mPath);
        */
        return bitmap;
    }

    public abstract Bitmap onDecodeOriginal(JobContext jc, int targetSize);

    private class BitmapCacheJob implements Job<Void> {
        private ImageCacheService mImageCacheService;
        private Bitmap mBitmap;
        private Path mPath;
        private int mType;
        private long mTimeModified;


        public BitmapCacheJob(ImageCacheService cacheService, Bitmap bitmap, Path path, long timeModified, int type) {
            this.mImageCacheService = cacheService;
            this.mBitmap = bitmap;
            this.mPath = path;
            this.mTimeModified = timeModified;
            this.mType = type;
        }

        @Override
        public Void run(JobContext jc) {
            GalleryUtils.start(this.getClass(), "run(), cache, mPath = " + mPath);
            byte[] array = BitmapUtils.compressToBytes(mBitmap);
            if (jc.isCancelled()) {
                return null;
            }
            mImageCacheService.putImageData(mPath, mTimeModified, mType, array);
            GalleryUtils.end(this.getClass(), "run(), cache, mPath = " + mPath);
            return null;
        }
    }

}

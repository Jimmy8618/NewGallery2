package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.data.BytesBufferPool;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.gallery3d.drm.LocalMediaItemUtils;

/**
 * Created by ruili on 2/7/17.
 */

public class DrmThumbImageLoader extends BitmapLoader {

    private static final String TAG = "DrmThumbImageLoader";
    private final ThreadPool mThreadPool;
    private ImageView mTargetView;
    private int mType;
    private ImageView mDrmIcon;
    private boolean mIsUnLock = false;
    private RecyclerView mRecyclerView;

    private String mFilePath;
    private long mModifiedInSec;
    private Path mPath;

    private static final Object sLock = new Object();

    private DrmThumbImageLoader(ThreadPool threadPool, ImageView imageView, String filePath,
                                long modifiedInSec, Path path, ImageView drmIcon, RecyclerView recyclerView) {
        mThreadPool = threadPool;
        mTargetView = imageView;
        mType = MediaItem.TYPE_MICROTHUMBNAIL;
        mDrmIcon = drmIcon;
        mRecyclerView = recyclerView;
        mFilePath = filePath;
        mModifiedInSec = modifiedInSec;
        mPath = path;
    }

    public static BitmapLoader submit(ThreadPool threadPool, ImageView targetView, String filePath,
                                      long modifiedInSec, Path path, ImageView drmIcon, RecyclerView recyclerView) {
        BitmapLoader loader = new DrmThumbImageLoader(threadPool, targetView, filePath, modifiedInSec, path, drmIcon, recyclerView);
        loader.startLoad();
        return loader;
    }

    @Override
    protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
        return mThreadPool.submit(new DrmThumbImageJob(), l);
    }

    @Override
    protected void onLoadComplete(final Bitmap bitmap) {
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                mTargetView.setImageBitmap(bitmap);
                if (mIsUnLock) {
                    mDrmIcon.setImageResource(R.drawable.ic_drm_unlock);
                } else {
                    mDrmIcon.setImageResource(R.drawable.ic_drm_lock);
                }
                mDrmIcon.setVisibility(View.VISIBLE);
            }
        });
    }

    private class DrmThumbImageJob implements ThreadPool.Job<Bitmap> {
        ImageCacheService mCacheService;

        public DrmThumbImageJob() {
            mCacheService = GalleryAppImpl.getApplication().getImageCacheService();
        }

        @Override
        public Bitmap run(ThreadPool.JobContext jc) {
            synchronized (sLock) {
                return getDRMThumbImage(jc);
            }
        }

        private Bitmap getDRMThumbImage(ThreadPool.JobContext jc) {
            mIsUnLock = DrmUtil.isDrmValid(mFilePath);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = null;
            if (jc.isCancelled()) {
                return null;
            }
            bitmap = decodeThumbImageFromCache(jc, options);
            if (jc.isCancelled()) {
                return null;
            }
            if (bitmap == null) {
                int targetSize = MediaItem.getTargetSize(mType);
                MediaObject mo = DataManager.from(mRecyclerView.getContext()).getMediaObject(mPath);
                if (mo.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
                    bitmap = BitmapUtils.createVideoThumbnail(mFilePath);
                } else {
                    bitmap = LocalMediaItemUtils.getInstance().decodeThumbnailWithDrm(jc, mType, mFilePath,
                            options, targetSize);
                }
                Log.d(TAG, "getDRMThumbImage decode " + mFilePath + ", bitmap=" + bitmap);
                if (jc.isCancelled()) {
                    return null;
                }

                if (bitmap != null) {
                    bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
                    if (jc.isCancelled()) {
                        return null;
                    }
                    byte[] array = BitmapUtils.compressToBytes(bitmap, 75);
                    if (jc.isCancelled()) {
                        return null;
                    }
                    mCacheService.putImageData(mPath, mModifiedInSec, mType, array);
                }
            }
            return bitmap;
        }

        private Bitmap decodeThumbImageFromCache(ThreadPool.JobContext jc, BitmapFactory.Options
                options) {

            BytesBufferPool.BytesBuffer buffer = MediaItem.getBytesBufferPool().get();

            boolean found = mCacheService.getImageData(mPath, mModifiedInSec, mType, buffer);
            Log.d(TAG, "decodeThumbImageFromCache found " + mFilePath + " ? " + found);
            Bitmap bitmap = null;
            if (found) {
                try {
                    bitmap = DecodeUtils.decodeUsingPool(jc, buffer.data, buffer
                            .offset, buffer.length, options);
                    if (bitmap == null && !jc.isCancelled()) {
                        Log.w(TAG, "decode cached failed " + mFilePath);
                    }
                } finally {
                    MediaItem.getBytesBufferPool().recycle(buffer);
                }
            }
            Log.d(TAG, "decodeThumbImageFromCache found " + mFilePath + " , bitmap=" + bitmap);
            return bitmap;
        }
    }
}

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

package com.android.gallery3d.app;

import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.UriImage;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.ui.BitmapScreenNail;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;

import java.lang.ref.WeakReference;

public class SinglePhotoDataAdapter extends TileImageViewAdapter
        implements PhotoPage.Model {

    private static final String TAG = "SinglePhotoDataAdapter";
    private static final int SIZE_BACKUP = 1024;
    private static final int MSG_UPDATE_IMAGE = 1;

    private MediaItem mItem;
    private boolean mHasFullImage;
    private boolean mRegionDecoderSuccess = true;
    private Future<?> mTask;
    private Handler mHandler;

    private PhotoView mPhotoView;
    private ThreadPool mThreadPool;
    private int mLoadingState = LOADING_INIT;
    private BitmapScreenNail mBitmapScreenNail;
    private AbstractGalleryActivity mActivity;

    private ContentObserver mContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (mItem instanceof UriImage) {
                Task task = new Task();
                task.execute();
            }
        }
    };

    public SinglePhotoDataAdapter(
            AbstractGalleryActivity activity, PhotoView view, MediaItem item) {
        /* SPRD: Modify bug496296, pictures can not be displayed sometimes @{ */
        // mItem = Utils.checkNotNull(item);
        mActivity = activity;
        mPhotoView = Utils.checkNotNull(view);
        try {
            mItem = Utils.checkNotNull(item);
        } catch (Exception e) {
            mPhotoView.pause();
            throw new NullPointerException();
        }
        /* @} */
        mHasFullImage = (item.getSupportedOperations() &
                MediaItem.SUPPORT_FULL_IMAGE) != 0;
        // SPRD: Modify  bug496296, pictures can not be displayed sometimes
        // mPhotoView = Utils.checkNotNull(view);
        mHandler = new MySynchronizedHandler(activity.getGLRoot(), this);
        mThreadPool = activity.getThreadPool();
    }

    private void hanldeMySynchronizedHandlerMsg(Message message) {
        Utils.assertTrue(message.what == MSG_UPDATE_IMAGE);
        // SPRD: bug 555912 change suffix of wbmp or gif, image can't show
        Log.i(TAG, "hanldeMySynchronizedHandlerMsg message.obj = " + message.obj);
        if (mHasFullImage && mRegionDecoderSuccess) {
            if (message.obj instanceof ImageBundle) {
                onDecodeLargeComplete((ImageBundle) message.obj);
            }
        } else {
            if (message.obj instanceof Future) {
                onDecodeThumbComplete((Future<Bitmap>) message.obj);
            }
        }
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SinglePhotoDataAdapter> mSinglePhotoDataAdapter;

        public MySynchronizedHandler(GLRoot root, SinglePhotoDataAdapter singlePhotoDataAdapter) {
            super(root);
            mSinglePhotoDataAdapter = new WeakReference<>(singlePhotoDataAdapter);
        }

        @Override
        public void handleMessage(Message message) {
            SinglePhotoDataAdapter singlePhotoDataAdapter = mSinglePhotoDataAdapter.get();
            if (singlePhotoDataAdapter != null) {
                singlePhotoDataAdapter.hanldeMySynchronizedHandlerMsg(message);
            }
        }
    }

    private static class ImageBundle {
        public final BitmapRegionDecoder decoder;
        public final Bitmap backupImage;

        public ImageBundle(BitmapRegionDecoder decoder, Bitmap backupImage) {
            this.decoder = decoder;
            this.backupImage = backupImage;
        }
    }

    private FutureListener<BitmapRegionDecoder> mLargeListener =
            new FutureListener<BitmapRegionDecoder>() {
                @Override
                public void onFutureDone(Future<BitmapRegionDecoder> future) {
                    BitmapRegionDecoder decoder = future.get();
                    Log.d(TAG, "mLargeListener.onFutureDone decoder=" + decoder);
                    if (decoder == null) {
                        /* SPRD: Modify 20150413 for bug424053, reset mTask to null here @{ */
                        if (future.isCancelled()) {
                            mTask = null;
                        }
                        /* @} */
                        /* SPRD: bug 555912 change suffix of wbmp or gif, image can't show @{ */
                        //mRegionDecoderSuccess = false;
                        if (mThreadPool != null && mItem != null) {
                            mTask = mThreadPool.submit(
                                    mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                                    mThumbListener);
                        }
                        /* @} */
                        return;
                    }
                    // SPRD: bug 555912 change suffix of wbmp or gif, image can't show
                    mRegionDecoderSuccess = true;
                    int width = decoder.getWidth();
                    int height = decoder.getHeight();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = BitmapUtils.computeSampleSize(
                            (float) SIZE_BACKUP / Math.max(width, height));
                    Bitmap bitmap = decoder.decodeRegion(new Rect(0, 0, width, height), options);
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_UPDATE_IMAGE, new ImageBundle(decoder, bitmap)));
                }
            };

    private FutureListener<Bitmap> mThumbListener =
            new FutureListener<Bitmap>() {
                @Override
                public void onFutureDone(Future<Bitmap> future) {
                    /* SPRD: Modify 20150413 for bug424053, reset mTask to null here @{ */
                    Log.d(TAG, "mThumbListener.onFutureDone future.get()=" + future.get() +
                            ", future.isCancelled() ? " + future.isCancelled());
                    if (future.get() == null) {
                        if (future.isCancelled()) {
                            mTask = null;
                            return;
                        }
                    }
                    /* @} */
                    mRegionDecoderSuccess = false;
                    mHandler.sendMessage(
                            mHandler.obtainMessage(MSG_UPDATE_IMAGE, future));
                }
            };

    @Override
    public boolean isEmpty() {
        return false;
    }

    private void setScreenNail(Bitmap bitmap, int width, int height) {
        mBitmapScreenNail = new BitmapScreenNail(bitmap);
        setScreenNail(mBitmapScreenNail, width, height);
    }

    private void onDecodeLargeComplete(ImageBundle bundle) {
        try {
            setScreenNail(bundle.backupImage,
                    bundle.decoder.getWidth(), bundle.decoder.getHeight());
            setRegionDecoder(bundle.decoder);
            mPhotoView.notifyImageChange(0);
        } catch (Throwable t) {
            mLoadingState = LOADING_FAIL;
            mPhotoView.notifyImageChange(0);
            Log.w(TAG, "fail to decode large", t);
        }
    }

    private void onDecodeThumbComplete(Future<Bitmap> future) {
        try {
            Bitmap backup = future.get();
            if (backup == null) {
                mLoadingState = LOADING_FAIL;
                mPhotoView.notifyImageChange(0);
                return;
            } else {
                mLoadingState = LOADING_COMPLETE;
            }
            setScreenNail(backup, backup.getWidth(), backup.getHeight());
            mPhotoView.notifyImageChange(0);
        } catch (Throwable t) {
            mLoadingState = LOADING_FAIL;
            mPhotoView.notifyImageChange(0);
            Log.w(TAG, "fail to decode thumb", t);
        }
    }

    @Override
    public void resume() {
        Log.i(TAG, " resume mhasFullImage  = " + mHasFullImage);
        if (mTask == null) {
            if (mHasFullImage) {
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            } else {
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
            }
        }
        if (isGif(0)) {
            TiledTexture.prepareResources();
        }
        mActivity.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mContentObserver);

    }

    @Override
    public void pause() {
        Future<?> task = mTask;
        /* SPRD: bug 581455,if mTask has been cancelled,it will be null @{ */
        if (task != null) {
            task.cancel();
            mTask = null;
        }
        /* @} */
        /* SPRD: Modify 20150413 for bug424053, do not waitDone here to avoid ANR,
         * reset mTask to null in onFutureDone() @{
        task.waitDone();
        if (task.get() == null) {
            mTask = null;
        }
        */
        /* @} */

        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
        if (isGif(0)) {
            TiledTexture.freeResources();
        }
        mActivity.getContentResolver().unregisterContentObserver(mContentObserver);
        mHandler.removeCallbacksAndMessages(null);
        mItem.getPath().recycleObject();
    }

    @Override
    public void moveTo(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getImageSize(int offset, PhotoView.Size size) {
        if (offset == 0) {
            size.width = mItem.getWidth();
            size.height = mItem.getHeight();
        } else {
            size.width = 0;
            size.height = 0;
        }
    }

    @Override
    public int getImageRotation(int offset) {
        return (offset == 0) ? mItem.getFullImageRotation() : 0;
    }

    @Override
    public ScreenNail getScreenNail(int offset) {
        return (offset == 0) ? getScreenNail() : null;
    }

    @Override
    public void setNeedFullImage(boolean enabled) {
        // currently not necessary.
    }

    @Override
    public boolean isCamera(int offset) {
        return false;
    }

    @Override
    public boolean isPanorama(int offset) {
        return false;
    }

    @Override
    public boolean isStaticCamera(int offset) {
        return false;
    }

    @Override
    public boolean isVideo(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO;
    }

    @Override
    public boolean isRefocusNoBokeh(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_GALLERY
                || mItem.getMediaType() == MediaItem.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY;
    }

    @Override
    public void updateBokehPicture(MediaItem item, byte[] bokehPicture) {
        // ignore
    }

    @Override
    public boolean isDeletable(int offset) {
        return (mItem.getSupportedOperations() & MediaItem.SUPPORT_DELETE) != 0;
    }

    @Override
    public MediaItem getMediaItem(int offset) {
        return offset == 0 ? mItem : null;
    }

    @Override
    public int getCurrentIndex() {
        return 0;
    }

    @Override
    public void setCurrentPhoto(Path path, int indexHint) {
        // ignore
    }

    @Override
    public void setFocusHintDirection(int direction) {
        // ignore
    }

    @Override
    public void setFocusHintPath(Path path) {
        // ignore
    }

    @Override
    public void needReDecode(MediaItem item) {
        // ignore
    }

    @Override
    public int getLoadingState(int offset) {
        return mLoadingState;
    }

    @Override
    public boolean isGif(int offset) {
        return mItem.getMediaType() == MediaItem.MEDIA_TYPE_GIF;
    }

    @Override
    public Uri getItemUri(int offset) {
        MediaItem item = getCurrentMediaItem();
        if (item != null) {
            return item.getContentUri();
        }
        return null;
    }

    public MediaItem getCurrentMediaItem() {
        return mItem;
    }

    private class Task extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mItem instanceof UriImage) {
                ((UriImage) mItem).queryFileFlag();
                mPhotoView.reloadPicture();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mTask != null) {
                if (mHasFullImage) {
                    mTask = mThreadPool.submit(
                            mItem.requestLargeImage(), mLargeListener);
                } else {
                    mTask = mThreadPool.submit(
                            mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                            mThumbListener);
                }
            }
        }
    }
}

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

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SlideshowView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Random;

public class SlideshowPage extends ActivityState implements IStorageUtil.StorageChangedListener {
    private static final String TAG = "SlideshowPage";

    public static final String KEY_SET_PATH = "media-set-path";
    public static final String KEY_ITEM_PATH = "media-item-path";
    public static final String KEY_PHOTO_INDEX = "photo-index";
    public static final String KEY_RANDOM_ORDER = "random-order";
    public static final String KEY_REPEAT = "repeat";
    public static final String KEY_DREAM = "dream";

    private static final long SLIDESHOW_DELAY = 3000; // 3 seconds

    private static final int MSG_LOAD_NEXT_BITMAP = 1;
    private static final int MSG_SHOW_PENDING_BITMAP = 2;
    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private AudioManager mAudioManager;
    private MediaPlayer mMediaPlayer;
    private static final int MUSIC_VOLUME = 100;
    /* @} */
    private boolean isMusicInExternal = false;
    /* add pause button*/
    private RelativeLayout mGalleryRoot;
    private ImageView mButton;
    private boolean mIsPlaying = true;

    @Override
    public void onStorageChanged(String path, String action) {
        Log.d(TAG, "onStorageChanged path=" + path + ", action=" + action + ", isMusicInExternal=" + isMusicInExternal);
        if (action == null || path == null) {
            return;
        }
        if (isMusicInExternal && GalleryStorageUtil.isInExternalStorage(path) && (action.equals(Intent.ACTION_MEDIA_EJECT)
                || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                || action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL))) {
            stopMediaPlayer();
            mAudioManager.abandonAudioFocus(mAudioListener);
        }
    }

    public interface Model {
        void pause();

        void resume();

        Future<Slide> nextSlide(FutureListener<Slide> listener);
    }

    public static class Slide {
        public Bitmap bitmap;
        public MediaItem item;
        public int index;

        public Slide(MediaItem item, int index, Bitmap bitmap) {
            this.bitmap = bitmap;
            this.item = item;
            this.index = index;
        }
    }

    private Handler mHandler;
    private Model mModel;
    private SlideshowView mSlideshowView;

    private Slide mPendingSlide = null;
    private boolean mIsActive = false;
    private final Intent mResultIntent = new Intent();

    @Override
    protected int getBackgroundColorId() {
        return R.color.slideshow_background;
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mSlideshowView.layout(0, 0, right - left, bottom - top);
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // onBackPressed();
                int visibility = mButton.getVisibility();
                if (visibility == View.GONE) {
                    mIsPlaying = true;
                    mButton.setVisibility(View.VISIBLE);
                } else {
                    mButton.setVisibility(View.GONE);
                    mGalleryRoot.removeView(mButton);
                    onBackPressed();
                }
            }
            return true;
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            canvas.clearBuffer(getBackgroundColor());
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mFlags |= (FLAG_HIDE_ACTION_BAR | FLAG_HIDE_STATUS_BAR);
        if (data.getBoolean(KEY_DREAM)) {
            // Dream screensaver only keeps screen on for plugged devices.
            mFlags |= FLAG_SCREEN_ON_WHEN_PLUGGED | FLAG_SHOW_WHEN_LOCKED;
        } else {
            // User-initiated slideshow would always keep screen on.
            mFlags |= FLAG_SCREEN_ON_ALWAYS;
        }

        mHandler = new MySynchronizedHandler(mActivity.getGLRoot(), this);
        initializeViews();
        initializeData(data);
        initializePlayButton();
        /* SPRD: Fix Bug 535131, add slide music feature @{ */
        mAudioManager = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        /* @} */
        GalleryStorageUtil.addStorageChangeListener(this);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SlideshowPage> mSlideshowPage;

        public MySynchronizedHandler(GLRoot root, SlideshowPage slideshowPage) {
            super(root);
            mSlideshowPage = new WeakReference<>(slideshowPage);
        }

        @Override
        public void handleMessage(Message message) {
            SlideshowPage slideshowPage = mSlideshowPage.get();
            if (slideshowPage != null) {
                slideshowPage.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        switch (message.what) {
            case MSG_SHOW_PENDING_BITMAP:
                showPendingBitmap();
                break;
            case MSG_LOAD_NEXT_BITMAP:
                loadNextBitmap();
                break;
            default:
                throw new AssertionError();
        }
    }

    private void loadNextBitmap() {
        mModel.nextSlide(new FutureListener<Slide>() {
            @Override
            public void onFutureDone(Future<Slide> future) {
                mPendingSlide = future.get();
                mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP);
            }
        });
    }

    private void showPendingBitmap() {
        // mPendingBitmap could be null, if
        // 1.) there is no more items
        // 2.) mModel is paused
        Slide slide = mPendingSlide;
        if (slide == null) {
            if (mIsActive) {
                mActivity.getStateManager().finishState(SlideshowPage.this);
            }
            return;
        }

        mSlideshowView.next(slide.bitmap, slide.item.getRotation());

        setStateResult(Activity.RESULT_OK, mResultIntent
                .putExtra(KEY_ITEM_PATH, slide.item.getPath().toString())
                .putExtra(KEY_PHOTO_INDEX, slide.index));
        mHandler.sendEmptyMessageDelayed(MSG_LOAD_NEXT_BITMAP, SLIDESHOW_DELAY);
        mButton.setVisibility(View.GONE);
    }

    private void showBackgroundBitmap() {
        // mPendingBitmap could be null, if
        // 1.) there is no more items
        // 2.) mModel is paused
        Slide slide = mPendingSlide;
        if (slide == null) {
            if (mIsActive) {
                mActivity.getStateManager().finishState(SlideshowPage.this);
            }
            return;
        }

        mSlideshowView.showBackgroundBitmap(slide.bitmap, slide.item.getRotation());
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        mModel.pause();
        mSlideshowView.release();
        pauseSlideshow();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume, mIsPlaying = " + mIsPlaying);
        mIsActive = true;
        mModel.resume();
        mActivity.findViewById(R.id.toolbar).setVisibility(View.GONE);
        mActivity.findViewById(R.id.toolbar2).setVisibility(View.GONE);
        if (mIsPlaying) {
            if (mPendingSlide != null) {
                showPendingBitmap();
            } else {
                loadNextBitmap();
            }
            /* SPRD: Fix Bug 535131, add slide music feature @{ */
            playSlideMusic();
            /* @} */
        } else {
            showBackgroundBitmap();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGalleryRoot.removeView(mButton);
        mAudioManager.abandonAudioFocus(mAudioListener);
        GalleryStorageUtil.removeStorageChangeListener(this);
    }

    private void playSlideMusic() {
        Context context = mActivity.getAndroidContext();
        String music = GalleryUtils.getSlideMusicUri(context);
        Uri musicUri = null;
        Log.d(TAG, "playSlideMusic  musicUriString = " + music);
        if (music != null) {
            musicUri = Uri.parse(music);
            String FilePath = GalleryUtils.getFilePath(musicUri, mActivity);
            isMusicInExternal = GalleryStorageUtil.isInExternalStorage(FilePath);
            Log.d(TAG, "playSlideMusic  music filePath= " + FilePath +
                    " , isMusicInExternal = " + isMusicInExternal);
        } else {
            return;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnErrorListener(new OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (mMediaPlayer != null) {
                        mAudioManager.abandonAudioFocus(mAudioListener);
                        try {
                            mMediaPlayer.stop();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Stop music error:" + e);
                        } finally {
                            mMediaPlayer.release();
                            mMediaPlayer = null;
                        }
                    }
                    return false;
                }
            });
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            /* SPRD: Modify bug 562156, slide music still play when music is playing @{ */
            if (mAudioManager.requestAudioFocus(mAudioListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Toast.makeText(mActivity, R.string.cannot_play_music, Toast.LENGTH_SHORT).show();
                if (mMediaPlayer != null) {
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
                return;
            }
            /* @} */
            mMediaPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setDataSource(mActivity.getAndroidContext(), musicUri);
            mMediaPlayer.prepare();
        } catch (Exception e) {
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            Toast.makeText(mActivity, R.string.slide_music_play_fail, Toast.LENGTH_SHORT).show();
            mAudioManager.abandonAudioFocus(mAudioListener);
            e.printStackTrace();
            return;
        }
        // SPRD: Modify for bug589685, music play 2-3 seconds if quickly click power key twice @{ */
        if (!isKeyguardOn()) {
            mMediaPlayer.start();
        }
    }

    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private final AudioManager.OnAudioFocusChangeListener mAudioListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
                    mMediaPlayer.start();
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    // Modify bug 562156, slide music still play when music is playing
                    // mAudioManager.abandonAudioFocus(mAudioListener);
                    mMediaPlayer.pause();
                    Toast.makeText(mActivity, R.string.slide_music_stop, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    /* @} */

    private void initializeData(Bundle data) {
        boolean random = data.getBoolean(KEY_RANDOM_ORDER, false);

        // We only want to show slideshow for images only, not videos.
        String mediaPath = data.getString(KEY_SET_PATH);
        /* SPRD: bug473914 add to support play gif @{ */
        mediaPath = FilterUtils.newFilterPath(mediaPath, FilterUtils.FILTER_ALL);
        //mediaPath = FilterUtils.newFilterPath(mediaPath, FilterUtils.FILTER_IMAGE_ONLY);
        /* @}*/
        MediaSet mediaSet = mActivity.getDataManager().getMediaSet(mediaPath);

        if (random) {
            boolean repeat = data.getBoolean(KEY_REPEAT);
            mModel = new SlideshowDataAdapter(mActivity,
                    new ShuffleSource(mediaSet, repeat), 0, null);
            setStateResult(Activity.RESULT_OK, mResultIntent.putExtra(KEY_PHOTO_INDEX, 0));
        } else {
            int index = data.getInt(KEY_PHOTO_INDEX);
            String itemPath = data.getString(KEY_ITEM_PATH);
            Path path = itemPath != null ? Path.fromString(itemPath) : null;
            boolean repeat = data.getBoolean(KEY_REPEAT);
            mModel = new SlideshowDataAdapter(mActivity, new SequentialSource(mediaSet, repeat),
                    index, path);
            setStateResult(Activity.RESULT_OK, mResultIntent.putExtra(KEY_PHOTO_INDEX, index));
        }
    }

    private void initializeViews() {
        mSlideshowView = new SlideshowView();
        mRootPane.addComponent(mSlideshowView);
        setContentPane(mRootPane);
    }

    private void initializePlayButton() {
        mGalleryRoot = mActivity.findViewById(R.id.gallery_root);
        ImageView button = new ImageView(mActivity);
        mButton = button;
        button.setImageResource(R.drawable.icn_pause_slideshow);
        button.setContentDescription(mActivity.getResources().getString(R.string.slideshow_status_pause));
        button.setScaleType(ImageView.ScaleType.CENTER);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        button.setLayoutParams(params);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPlaying) {
                    pauseSlideshow();
                } else {
                    startSlideshow();
                }
            }
        });

        mGalleryRoot.addView(button);
        button.setVisibility(View.GONE);


    }

    void stopMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Stop music error:" + e);
            } finally {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    }

    private void pauseSlideshow() {
        Log.d(TAG, "pauseSlideshow ");
        mIsPlaying = false;
        stopMediaPlayer();
        mAudioManager.abandonAudioFocus(mAudioListener);
        mSlideshowView.onPauseSlideshow();
        mSlideshowView.slideShowStop();
        mHandler.removeMessages(MSG_LOAD_NEXT_BITMAP);
        mHandler.removeMessages(MSG_SHOW_PENDING_BITMAP);
        mButton.setVisibility(View.VISIBLE);
        mButton.setImageResource(R.drawable.icn_play_slideshow);
        mButton.setContentDescription(mActivity.getResources().getString(R.string.slideshow_status_play));
    }

    private void startSlideshow() {
        Log.d(TAG, "startSlideshow ");
        mIsPlaying = true;
        playSlideMusic();
        mButton.setImageResource(R.drawable.icn_pause_slideshow);
        mButton.setContentDescription(mActivity.getResources().getString(R.string.slideshow_status_pause));
        mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP);
    }

    private static MediaItem findMediaItem(MediaSet mediaSet, int index) {
        for (int i = 0, n = mediaSet.getSubMediaSetCount(); i < n; ++i) {
            MediaSet subset = mediaSet.getSubMediaSet(i);
            int count = subset.getTotalMediaItemCount();
            if (index < count) {
                return findMediaItem(subset, index);
            }
            index -= count;
        }
        ArrayList<MediaItem> list = mediaSet.getMediaItem(index, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private static class ShuffleSource implements SlideshowDataAdapter.SlideshowSource {
        private static final int RETRY_COUNT = 5;
        private final MediaSet mMediaSet;
        private final Random mRandom = new Random();
        private int mOrder[] = new int[0];
        private final boolean mRepeat;
        private long mSourceVersion = MediaSet.INVALID_DATA_VERSION;
        private int mLastIndex = -1;

        public ShuffleSource(MediaSet mediaSet, boolean repeat) {
            mMediaSet = Utils.checkNotNull(mediaSet);
            mRepeat = repeat;
        }

        @Override
        public int findItemIndex(Path path, int hint) {
            return hint;
        }

        @Override
        public MediaItem getMediaItem(int index) {
            if (!mRepeat && index >= mOrder.length) {
                return null;
            }
            if (mOrder.length == 0) {
                return null;
            }
            mLastIndex = mOrder[index % mOrder.length];
            MediaItem item = findMediaItem(mMediaSet, mLastIndex);
            for (int i = 0; i < RETRY_COUNT && item == null; ++i) {
                Log.w(TAG, "fail to find image: " + mLastIndex);
                mLastIndex = mRandom.nextInt(mOrder.length);
                item = findMediaItem(mMediaSet, mLastIndex);
            }
            return item;
        }

        @Override
        public long reload() {
            long version = mMediaSet.reload();
            if (version != mSourceVersion) {
                mSourceVersion = version;
                int count = mMediaSet.getTotalMediaItemCount();
                if (count != mOrder.length) {
                    generateOrderArray(count);
                }
            }
            return version;
        }

        private void generateOrderArray(int totalCount) {
            if (mOrder.length != totalCount) {
                mOrder = new int[totalCount];
                for (int i = 0; i < totalCount; ++i) {
                    mOrder[i] = i;
                }
            }
            for (int i = totalCount - 1; i > 0; --i) {
                Utils.swap(mOrder, i, mRandom.nextInt(i + 1));
            }
            if (mOrder[0] == mLastIndex && totalCount > 1) {
                Utils.swap(mOrder, 0, mRandom.nextInt(totalCount - 1) + 1);
            }
        }

        @Override
        public void addContentListener(ContentListener listener) {
            mMediaSet.addContentListener(listener);
        }

        @Override
        public void removeContentListener(ContentListener listener) {
            mMediaSet.removeContentListener(listener);
        }
    }

    private static class SequentialSource implements SlideshowDataAdapter.SlideshowSource {
        private static final int DATA_SIZE = 32;

        private ArrayList<MediaItem> mData = new ArrayList<MediaItem>();
        private int mDataStart = 0;
        private long mDataVersion = MediaObject.INVALID_DATA_VERSION;
        private final MediaSet mMediaSet;
        private final boolean mRepeat;

        public SequentialSource(MediaSet mediaSet, boolean repeat) {
            mMediaSet = mediaSet;
            mRepeat = repeat;
        }

        @Override
        public int findItemIndex(Path path, int hint) {
            return mMediaSet.getIndexOfItem(path, hint);
        }

        @Override
        public MediaItem getMediaItem(int index) {
            int dataEnd = mDataStart + mData.size();

            if (mRepeat) {
                int count = mMediaSet.getMediaItemCount();
                if (count == 0) {
                    return null;
                }
                index = index % count;
            }
            if (index < mDataStart || index >= dataEnd) {
                mData = mMediaSet.getMediaItem(index, DATA_SIZE);
                mDataStart = index;
                dataEnd = index + mData.size();
            }

            return (index < mDataStart || index >= dataEnd) ? null : mData.get(index - mDataStart);
        }

        @Override
        public long reload() {
            long version = mMediaSet.reload();
            if (version != mDataVersion) {
                mDataVersion = version;
                mData.clear();
            }
            return mDataVersion;
        }

        @Override
        public void addContentListener(ContentListener listener) {
            mMediaSet.addContentListener(listener);
        }

        @Override
        public void removeContentListener(ContentListener listener) {
            mMediaSet.removeContentListener(listener);
        }
    }

    /* SPRD: Add for bug589685, music play 2-3 seconds if quickly click power key twice @{ */
    private boolean isKeyguardOn() {
        KeyguardManager keyguardManager = (KeyguardManager) mActivity.getSystemService(
                Service.KEYGUARD_SERVICE);
        return keyguardManager.isKeyguardLocked();
    }
    /* @} */
}

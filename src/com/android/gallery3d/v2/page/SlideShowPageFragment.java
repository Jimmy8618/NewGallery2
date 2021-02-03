package com.android.gallery3d.v2.page;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.FilterUtils;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.IStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SlideshowView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ToastUtil;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.cust.BasePageFragment;
import com.android.gallery3d.v2.data.SlideShowPageDataAdapter;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.PermissionUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Random;

/**
 * @author baolin.li
 */
public class SlideShowPageFragment extends BasePageFragment implements IStorageUtil.StorageChangedListener {
    private static final String TAG = SlideShowPageFragment.class.getSimpleName();

    private static final int MSG_LOAD_NEXT_BITMAP = 1;
    private static final int MSG_SHOW_PENDING_BITMAP = 2;

    // 3 seconds
    private static final long SLIDESHOW_DELAY = 3000;
    private static final int MUSIC_VOLUME = 100;

    private GLRoot mGLRoot;
    private Model mModel;

    private Slide mPendingSlide = null;
    private boolean mIsActive = false;
    private SlideshowView mSlideshowView;
    private Handler mHandler;

    private ImageView mButton;

    private boolean isMusicInExternal = false;
    private AudioManager mAudioManager;
    private MediaPlayer mMediaPlayer;
    private boolean mIsPlaying = true;

    private FrameLayout mRootView;

    private float[] mBackgroundColor;

    private Toast mToast;

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

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mSlideshowView.layout(0, 0, right - left, bottom - top);
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (mPendingSlide == null) {
                    Log.d(TAG, "onTouch mPendingSlide is null.");
                    return true;
                }
                int visibility = mButton.getVisibility();
                if (visibility == View.GONE) {
                    mIsPlaying = true;
                    mButton.setVisibility(View.VISIBLE);
                } else {
                    mButton.setVisibility(View.GONE);
                    mRootView.removeView(mButton);
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        keepScreenOn(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setStatusBarHideWhite();
        mRootView = (FrameLayout) inflater.inflate(R.layout.fragment_slide_show_page, container, false);
        mGLRoot = mRootView.findViewById(R.id.gl_root_view);
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(getResources().getColor(R.color.slideshow_background));

        if (isNextPage()) {//隐藏底部Tab
            setTabsVisible(false);
        }

        mHandler = new MySynchronizedHandler(mGLRoot, this);
        initializeViews();
        initializeData(getArguments());
        initializePlayButton();
        mAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        GalleryStorageUtil.addStorageChangeListener(this);
        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mIsActive = true;
        ((GLRootView) mGLRoot).onResume();
        if (PermissionUtil.hasPermissions(getContext())) {
            mModel.resume();
            if (mIsPlaying) {
                if (mPendingSlide != null) {
                    showPendingBitmap();
                } else {
                    loadNextBitmap();
                }
                playSlideMusic();
            } else {
                showBackgroundBitmap();
            }
        }
    }

    @Override
    public void onShow() {
        Log.d(TAG, "onShow");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        ((GLRootView) mGLRoot).onPause();
        mIsActive = false;
        mModel.pause();
        mSlideshowView.release();
        pauseSlideshow();
    }

    @Override
    public void onHide() {
        Log.d(TAG, "onHide");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setStatusBarVisibleWhite();
        mRootView.removeView(mButton);
        mRootPane.removeComponent(mSlideshowView);
        mRootPane.detachFromRoot();
        mAudioManager.abandonAudioFocus(mAudioListener);
        GalleryStorageUtil.removeStorageChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        keepScreenOn(false);
    }

    private void startSlideshow() {
        Log.d(TAG, "startSlideshow");
        mIsPlaying = true;
        playSlideMusic();
        mButton.setImageResource(R.drawable.icn_pause_slideshow);
        mButton.setContentDescription(getString(R.string.slideshow_status_pause));
        mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP);
    }

    private void pauseSlideshow() {
        Log.d(TAG, "pauseSlideshow");
        mIsPlaying = false;
        stopMediaPlayer();
        mAudioManager.abandonAudioFocus(mAudioListener);
        mSlideshowView.onPauseSlideshow();
        mSlideshowView.slideShowStop();
        mHandler.removeMessages(MSG_LOAD_NEXT_BITMAP);
        mHandler.removeMessages(MSG_SHOW_PENDING_BITMAP);
        mButton.setVisibility(View.VISIBLE);
        mButton.setImageResource(R.drawable.icn_play_slideshow);
        mButton.setContentDescription(getString(R.string.slideshow_status_play));
    }

    private void initializeData(Bundle data) {
        boolean random = data.getBoolean(Constants.KEY_RANDOM_ORDER, false);

        // We only want to show slideshow for images only, not videos.
        String mediaPath = data.getString(Constants.KEY_BUNDLE_MEDIA_SET_PATH);
        mediaPath = FilterUtils.newFilterPath(mediaPath, FilterUtils.FILTER_ALL);
        MediaSet mediaSet = getDataManager().getMediaSet(mediaPath);

        if (random) {
            boolean repeat = data.getBoolean(Constants.KEY_BUNDLE_PHOTO_REPEAT);
            mModel = new SlideShowPageDataAdapter((GalleryActivity2) getActivity(),
                    new ShuffleSource(mediaSet, repeat), 0, null);
        } else {
            int index = data.getInt(Constants.KEY_BUNDLE_PHOTO_INDEX);
            String itemPath = data.getString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH);
            Path path = itemPath != null ? Path.fromString(itemPath) : null;
            boolean repeat = data.getBoolean(Constants.KEY_BUNDLE_PHOTO_REPEAT);
            mModel = new SlideShowPageDataAdapter((GalleryActivity2) getActivity(), new SequentialSource(mediaSet, repeat),
                    index, path);
        }
    }

    private void initializeViews() {
        mSlideshowView = new SlideshowView();
        mRootPane.addComponent(mSlideshowView);
        mRootPane.setBackgroundColor(mBackgroundColor);
        mGLRoot.setContentPane(mRootPane);
    }

    private void initializePlayButton() {
        ImageView button = new ImageView(getContext());
        mButton = button;
        button.setImageResource(R.drawable.icn_pause_slideshow);
        button.setContentDescription(getString(R.string.slideshow_status_pause));
        button.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
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

        mRootView.addView(button);
        button.setVisibility(View.GONE);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SlideShowPageFragment> mSlideshowPage;

        public MySynchronizedHandler(GLRoot root, SlideShowPageFragment slideshowPage) {
            super(root);
            mSlideshowPage = new WeakReference<>(slideshowPage);
        }

        @Override
        public void handleMessage(Message message) {
            SlideShowPageFragment slideshowPage = mSlideshowPage.get();
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

    private void showPendingBitmap() {
        // mPendingBitmap could be null, if
        // 1.) there is no more items
        // 2.) mModel is paused
        Slide slide = mPendingSlide;
        if (slide == null) {
            if (mIsActive) {
                mToast = ToastUtil.showMessage(getContext(), mToast, R.string.replay, Toast.LENGTH_LONG);
            }
            return;
        }

        mSlideshowView.next(slide.bitmap, slide.item.getRotation());

        Bundle data = new Bundle();
        data.putInt(Constants.KEY_DATA_BACK, Constants.DATA_BACK_FROM_SLIDE_SHOW);
        data.putString(Constants.KEY_BUNDLE_MEDIA_ITEM_PATH, slide.item.getPath().toString());
        data.putInt(Constants.KEY_BUNDLE_PHOTO_INDEX, slide.index);
        setDataBack(data);

        mHandler.sendEmptyMessageDelayed(MSG_LOAD_NEXT_BITMAP, SLIDESHOW_DELAY);
        mButton.setVisibility(View.GONE);
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

    @Override
    public void onStorageChanged(String path, String action) {
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
                    mMediaPlayer.pause();
                    Toast.makeText(getContext(), R.string.slide_music_stop, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

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

    private void showBackgroundBitmap() {
        // mPendingBitmap could be null, if
        // 1.) there is no more items
        // 2.) mModel is paused
        Slide slide = mPendingSlide;
        if (slide == null) {
            if (mIsActive) {
                mToast = ToastUtil.showMessage(getContext(), mToast, R.string.replay, Toast.LENGTH_LONG);
            }
            return;
        }
        mSlideshowView.showBackgroundBitmap(slide.bitmap, slide.item.getRotation());
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

    private boolean isKeyguardOn() {
        KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(
                Service.KEYGUARD_SERVICE);
        return keyguardManager.isKeyguardLocked();
    }

    private static class ShuffleSource implements SlideShowPageDataAdapter.SlideshowSource {
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

    private static class SequentialSource implements SlideShowPageDataAdapter.SlideshowSource {
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

    private void playSlideMusic() {
        String music = GalleryUtils.getSlideMusicUri(getContext());
        Uri musicUri;
        Log.d(TAG, "playSlideMusic  musicUriString = " + music);
        if (music != null) {
            musicUri = Uri.parse(music);
            String FilePath = GalleryUtils.getFilePath(musicUri, getContext());
            isMusicInExternal = GalleryStorageUtil.isInExternalStorage(FilePath);
            Log.d(TAG, "playSlideMusic  music filePath= " + FilePath +
                    " , isMusicInExternal = " + isMusicInExternal);
        } else {
            return;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

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
            if (mAudioManager.requestAudioFocus(mAudioListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Toast.makeText(getContext(), R.string.cannot_play_music, Toast.LENGTH_SHORT).show();
                if (mMediaPlayer != null) {
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
                return;
            }
            mMediaPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setDataSource(getContext(), musicUri);
            mMediaPlayer.prepare();
        } catch (Exception e) {
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            Toast.makeText(getContext(), R.string.slide_music_play_fail, Toast.LENGTH_SHORT).show();
            mAudioManager.abandonAudioFocus(mAudioListener);
            e.printStackTrace();
            return;
        }
        if (!isKeyguardOn()) {
            mMediaPlayer.start();
        }
    }
}

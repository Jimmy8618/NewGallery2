/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.SaveVideoFileInfo;
import com.android.gallery3d.util.SaveVideoFileUtils;
import com.sprd.gallery3d.app.VideoUtil;

import java.io.File;
import java.io.IOException;

public class TrimVideo extends Activity implements
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        ControllerOverlay.Listener {

    private static final String TAG = "TrimVideo";

    private VideoView mVideoView;
    private TextView mSaveVideoTextView;
    private TrimControllerOverlay mController;
    private Context mContext;
    private Uri mUri;
    private final Handler mHandler = new Handler();
    public static final String TRIM_ACTION = "com.android.camera.action.TRIM";

    public ProgressDialog mProgress;

    private int mTrimStartTime = 0;
    private int mTrimEndTime = 0;
    private int mVideoPosition = 0;
    public static final String KEY_TRIM_START = "trim_start";
    public static final String KEY_TRIM_END = "trim_end";
    public static final String KEY_VIDEO_POSITION = "video_pos";
    /*
     * SPRD add for new feature:568552
     * trim video@{
     */
    private static final String FLAG_GALLERY = "startByGallery";
    private String mFragmentName;
    private ImageView mBackOutTrimVideo;
    /*@}*/
    private boolean mHasPaused = false;

    private String mSrcVideoPath = null;
    private static final String TIME_STAMP_NAME = "'TRIM'_yyyyMMdd_HHmmss";
    private SaveVideoFileInfo mDstFileInfo = null;
    /**
     * SRRD:Bug474600 improve video control functions
     * add new parameters @{
     */
    private boolean isFlv = false;
    /**@}*/

    /**
     * SPRD:Bug 474631 add headset Control @{
     */
    private AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;

    /**
     * @}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mContext = getApplicationContext();
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        ActionBar actionBar = getActionBar();
        int displayOptions = ActionBar.DISPLAY_SHOW_HOME;
        actionBar.setDisplayOptions(0, displayOptions);
        displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM;
        actionBar.setDisplayOptions(displayOptions, displayOptions);
        actionBar.setCustomView(R.layout.trim_menu);

        mSaveVideoTextView = findViewById(R.id.start_trim);
        mSaveVideoTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                trimVideo();
            }
        });
        mSaveVideoTextView.setEnabled(false);
        /*
         * SPRD:add for new feature 568552
         * back out of the trim video @{
         */
        mBackOutTrimVideo = findViewById(R.id.back);
        mBackOutTrimVideo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                TrimVideo.this.finish();
            }
        });
        /*@}*/
        /** SPRD:Bug 474631 add headset Control @{ */
        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();
        /**@}*/
        Intent intent = getIntent();
        mUri = intent.getData();
        /*
         * SPRD: add for new feature 568552
         * trim video@{
         */
        mFragmentName = intent.getStringExtra("mFragmentName");
        mSrcVideoPath = intent.getStringExtra(PhotoPage.KEY_MEDIA_ITEM_PATH);
        Log.d(TAG, "mSrcVideoPath=" + mSrcVideoPath);
        /* @} */
        setContentView(R.layout.trim_view);
        View rootView = findViewById(R.id.trim_view_root);

        mVideoView = rootView.findViewById(R.id.surface_view);

        mController = new TrimControllerOverlay(mContext);
        ((ViewGroup) rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setFloatWindowInvisible(true);//SPRD: add for new feature 568552
        mController.setCanReplay(true);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setVideoURI(mUri);

        playVideo();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            android.util.Log.d(TAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            Toast.makeText(this, R.string.exit_multiwindow_video_tips, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onResume() {
        GalleryUtils.killActivityInMultiWindow(this, GalleryUtils.DONT_SUPPORT_VIEW_VIDEO);
        super.onResume();
        if (mHasPaused) {
            mVideoView.seekTo(mVideoPosition);
            mVideoView.resume();
            mHasPaused = false;
        }
        mHandler.post(mProgressChecker);
    }

    @Override
    public void onPause() {
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        /**SPRD:Bug474600 improve video control functions
         * improve Trim Video functions @{*/
        isFlv = !mVideoView.canSeekBackward();
        if (mVideoView.isPlaying() && !isFlv) {
            mVideoPosition = mVideoView.getCurrentPosition();
        }
        mVideoView.pause();
        /**@}*/
        mVideoView.suspend();
        /**SRPD:Bug474600 improve video control functions
         * improve Trim Video functions old bug 271036 add@{ */
        if (isFlv) {
            mController.resetTime();
            mVideoPosition = 0;
        }
        /** @} */
        super.onPause();
    }

    @Override
    public void onStop() {
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mVideoView != null) {
            mVideoView.stopPlayback();
        }
        /** SPRD:Bug 474631 add headset Control @{ */
        if (mAudioBecomingNoisyReceiver != null) {
            mAudioBecomingNoisyReceiver.unregister();
        }
        /** @} */
        super.onDestroy();
    }

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            mHandler.postDelayed(mProgressChecker, 200 - (pos % 200));
        }
    };

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(KEY_TRIM_START, mTrimStartTime);
        savedInstanceState.putInt(KEY_TRIM_END, mTrimEndTime);
        savedInstanceState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTrimStartTime = savedInstanceState.getInt(KEY_TRIM_START, 0);
        mTrimEndTime = savedInstanceState.getInt(KEY_TRIM_END, 0);
        mVideoPosition = savedInstanceState.getInt(KEY_VIDEO_POSITION, 0);
    }

    // This updates the time bar display (if necessary). It is called by
    // mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        mVideoPosition = mVideoView.getCurrentPosition();
        // If the video position is smaller than the starting point of trimming,
        // correct it.
        if (mVideoPosition < mTrimStartTime) {
            mVideoView.seekTo(mTrimStartTime);
            mVideoPosition = mTrimStartTime;
        }
        // If the position is bigger than the end point of trimming, show the
        // replay button and pause.
        if (mVideoPosition >= mTrimEndTime && mTrimEndTime > 0) {
            if (mVideoPosition > mTrimEndTime) {
                mVideoView.seekTo(mTrimEndTime);
                mVideoPosition = mTrimEndTime;
            }
            mController.showEnded();
            mVideoView.pause();
        }

        int duration = mVideoView.getDuration();
        if (duration > 0 && mTrimEndTime == 0) {
            mTrimEndTime = duration;
        }
        mController.setTimes(mVideoPosition, duration, mTrimStartTime, mTrimEndTime);
        // Enable save if there's modifications
        mSaveVideoTextView.setEnabled(isModified());
        return mVideoPosition;
    }

    private void playVideo() {
        mVideoView.start();
        mController.showPlaying();
        setProgress();
    }

    private void pauseVideo() {
        mVideoView.pause();
//        mController.showPaused();//SPRD:Modify for new feature 568552
    }


    private boolean isModified() {
        int delta = mTrimEndTime - mTrimStartTime;

        // Considering that we only trim at sync frame, we don't want to trim
        // when the time interval is too short or too close to the origin.
        return delta >= 100 && Math.abs(mVideoView.getDuration() - delta) >= 100;
    }

    private void trimVideo() {

        mDstFileInfo = SaveVideoFileUtils.getDstMp4FileInfo(TIME_STAMP_NAME,
                getContentResolver(), mUri, getString(R.string.folder_download));
        final File mSrcFile = new File(mSrcVideoPath);

        showProgressDialog();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "mSrcFile=" + mSrcFile + " mDstFileInfo.mFile=" + mDstFileInfo.mFile + " mTrimStartTime=" + mTrimStartTime + " mTrimEndTime=" + mTrimEndTime);
                    VideoUtils.startTrim(mSrcFile, mDstFileInfo.mFile,
                            mTrimStartTime, mTrimEndTime);
                    // Update the database for adding a new video file.
                    SaveVideoFileUtils.insertContent(mDstFileInfo,
                            getContentResolver(), mUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // After trimming is done, trigger the UI changed.
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.save_into, mDstFileInfo.mFolderName),
                                Toast.LENGTH_SHORT)
                                .show();
                        // TODO: change trimming into a service to avoid
                        // this progressDialog and add notification properly.
                        if (mProgress != null) {
                            mProgress.dismiss();
                            mProgress = null;
                            // Show the result only when the activity not stopped.
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
                            Log.d(TAG, "id=" + VideoUtil.getIdFromPath(String.valueOf(mDstFileInfo.mFile), TrimVideo.this));
                            intent.setDataAndType(getContentUri(VideoUtil.getIdFromPath(String.valueOf(mDstFileInfo.mFile), TrimVideo.this)), "video/*")
                                    /*
                                     * SPRD:add for new feature 568552 @{
                                     */
                                    .putExtra(FLAG_GALLERY, true)
                                    .putExtra("mFragmentName", mFragmentName);
                            /* @} */
                            intent.putExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, false);
                            startActivity(intent);
                            finish();
                        }
                    }
                });
            }
        }).start();
    }

    public Uri getContentUri(int id) {
        Uri baseUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
    }

    private void showProgressDialog() {
        // create a background thread to trim the video.
        // and show the progress.
        mProgress = new ProgressDialog(this);
        mProgress.setTitle(getString(R.string.trimming));
        mProgress.setMessage(getString(R.string.please_wait));
        // TODO: make this cancelable.
        mProgress.setCancelable(false);
        mProgress.setCanceledOnTouchOutside(false);
        mProgress.show();
    }

    @Override
    public void onPlayPause() {
        if (mVideoView.isPlaying()) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    @Override
    public void onSeekStart() {
        pauseVideo();
    }

    @Override
    public void onSeekMove(int time) {
        mVideoView.seekTo(time);
    }

    @Override
    public void onSeekEnd(int time, int start, int end) {
        mVideoView.seekTo(time);
        mTrimStartTime = start;
        mTrimEndTime = end;
        setProgress();
    }

    @Override
    public void onReplay() {
        mVideoView.seekTo(mTrimStartTime);
        playVideo();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mController.showEnded();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    /**
     * SPRD:Bug 474631 add headset Control @{
     */
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVideoView.isPlaying()) {
                Log.d(TAG, "onReceive pauseVideo()");
            }
            pauseVideo();
        }
    }
    /** @} */
}

package com.android.gallery3d.encoder;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.refocus.RefocusUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioImageEncoder {
    private static final String TAG = AudioImageEncoder.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int MAX_VIDEO_WIDTH = 1280;
    private static final int MAX_VIDEO_HEIGHT = 720;

    private static final int FRAME_RATE = 10; //frame rate
    private static final int I_FRAME_INTERVAL = 1; //1 sec between I-Frame

    private static final long TIME_OUT_US = 1 * 1000 * 1000; //1 sec
    private static final long STAMP_GAP = (long) ((1.0f / FRAME_RATE) * 1000 * 1000);

    private String mVideoPath;
    private int mVideoWidth;
    private int mVideoHeight;

    private int mSampleSize;

    private MediaCodec mMediaCodec;
    private MediaMuxer mMediaMuxer;
    private MediaExtractor mMediaExtractor;

    private ProgressDialog mProgressDialog;
    private boolean mCanceled;

    private Context mContext;
    private String mImagePath;
    private int mRotation;
    private Listener mListener;
    private FileInputStream mFis;
    private long mJpegSize;

    public interface Listener {
        void onPreExecute();

        void onPostExecute(Uri uri);

        void onError(Throwable t);

        void onCanceled();
    }

    private void prepare() throws IOException {
        //Get image width and height
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mImagePath, options);
        Log.d(TAG, "prepare imageWidth = " + options.outWidth + ", imageHeight = " + options.outHeight);

        //SampleSize
        mSampleSize = calculateSampleSize(Math.max(options.outWidth, options.outHeight),
                Math.min(options.outWidth, options.outHeight));
        Log.d(TAG, "prepare sampleSize = " + mSampleSize);

        //Get video width and height
        int videoWidth = options.outWidth / mSampleSize;
        int videoHeight = options.outHeight / mSampleSize;

        if (mRotation == 90 || mRotation == 270) {
            int tmp = videoWidth;
            videoWidth = videoHeight;
            videoHeight = tmp;
        }

        // ReSize video width and height
        mVideoWidth = (videoWidth / 16) * 16;
        mVideoHeight = (videoHeight / 16) * 16;

        Log.d(TAG, "prepare videoWidth = " + mVideoWidth + ", videoHeight = " + mVideoHeight + ", mRotation = " + mRotation);

        //Delete cached videos, video name should like 'VID_xxx.mp4'
        deleteCachedFile();

        //Video path
        if (DEBUG) {
            mVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + GalleryUtils.generateVideoName() + ".mp4";
        } else {
            mVideoPath = mContext.getCacheDir() + "/" + GalleryUtils.generateVideoName() + ".mp4";
        }
        Log.d(TAG, "prepare videoPath = " + mVideoPath);

        //Create video format
        MediaFormat videoMediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                mVideoWidth, mVideoHeight);
        //bit rate
        videoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                (int) (mVideoWidth * mVideoHeight * FRAME_RATE * 0.3f));
        //frame rate
        videoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        //color format
        videoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        //I frame interval
        videoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        //Create mediaCodec
        mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMediaCodec.configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        //Create mediaMuxer
        mMediaMuxer = new MediaMuxer(mVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        if (GalleryStorageUtil.isInInternalStorage(mImagePath)) {
            mFis = new FileInputStream(mImagePath);
        } else {
            mFis = (FileInputStream) SdCardPermission.createExternalInputStream(mImagePath);
        }
        FileDescriptor fd = mFis.getFD();
        //Create mediaExtractor
        mMediaExtractor = new MediaExtractor();
        mMediaExtractor.setDataSource(fd, mJpegSize, mFis.available());
    }

    class Task extends AsyncTask<Void, Integer, Uri> {
        private long MAX_TIME_MS = 1500L;// 1.5 sec

        private void publish(long delta) {
            if (delta >= MAX_TIME_MS) {
                publishProgress(100);
                return;
            }
            publishProgress((int) ((1.0f * delta / MAX_TIME_MS) * 100));
        }

        @Override
        protected Uri doInBackground(Void... voids) {
            long t = System.currentTimeMillis();
            byte[] frameData = null;
            int audioTrackIndex = -1;
            MediaFormat audioMediaFormat = null;

            long audioDurationsUs = 0;
            long videoTimeStamp = 0;
            int frameIndex = 0;

            int outAudioTrackIndex = -1;
            int outVideoTrackIndex = -1;

            boolean videoOutputDone = false;
            boolean videoInputDone = false;

            //Create data with image
            try {
                frameData = decodeImage(mImagePath, mSampleSize);
            } catch (Exception e) {
                Log.e(TAG, "decodeImage failed. " + e.toString());
                closeSilently();
                return null;
            }

            //Get audio track and audio format from audio file
            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                audioMediaFormat = mMediaExtractor.getTrackFormat(i);
                if (audioMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioTrackIndex = i;
                    audioDurationsUs = audioMediaFormat.getLong(MediaFormat.KEY_DURATION);
                    break;
                }
            }
            if (audioTrackIndex == -1) {
                Log.e(TAG, "can not find audio track in audio file!");
                closeSilently();
                return null;
            }

            //Add audio track
            outAudioTrackIndex = mMediaMuxer.addTrack(audioMediaFormat);

            //Video
            //Encode video data and write to muxer
            while (!videoOutputDone) {
                //Canceled
                if (mCanceled) {
                    break;
                }
                publish(System.currentTimeMillis() - t);

                //Put data to mediaCodec
                if (!videoInputDone) {
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIME_OUT_US);
                    if (inputBufferIndex >= 0) {
                        long pts = computePresentationTime(frameIndex);
                        if (videoTimeStamp >= audioDurationsUs) {
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, pts,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            videoInputDone = true;
                        } else {
                            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                            inputBuffer.clear();
                            inputBuffer.put(frameData);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, frameData.length, pts, 0);
                        }
                        videoTimeStamp += STAMP_GAP;
                        frameIndex++;
                    }
                }

                //Get encoded data
                MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(videoInfo, TIME_OUT_US);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "info try again later!");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat mediaFormat = mMediaCodec.getOutputFormat();
                    outVideoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
                    Log.d(TAG, "------media muxer start-------");
                    mMediaMuxer.start();
                } else if (outputBufferIndex < 0) {
                } else {
                    ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                    if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        MediaFormat mediaFormat = mMediaCodec.getOutputFormat();
                        mediaFormat.setByteBuffer("csd-0", outputBuffer);
                    } else if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoOutputDone = true;
                    } else {
                        if (videoInfo.size != 0) {
                            outputBuffer.position(videoInfo.offset);
                            outputBuffer.limit(videoInfo.offset + videoInfo.size);
                            Log.d(TAG, "writeSampleData video : track index = " + outVideoTrackIndex + ", pts = " + videoInfo.presentationTimeUs);
                            mMediaMuxer.writeSampleData(outVideoTrackIndex, outputBuffer, videoInfo);
                        }
                    }
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

            //Audio
            //Start read audio data from audio file
            ByteBuffer audioByteBuffer = ByteBuffer.allocate(10 * 1024);
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            mMediaExtractor.selectTrack(audioTrackIndex);
            while (true) {
                //Canceled
                if (mCanceled) {
                    break;
                }
                publish(System.currentTimeMillis() - t);

                int size = mMediaExtractor.readSampleData(audioByteBuffer, 0);
                if (size < 0) {
                    mMediaExtractor.unselectTrack(audioTrackIndex);
                    break;
                }
                long presentationTimeUs = mMediaExtractor.getSampleTime();
                int flags = mMediaExtractor.getSampleFlags();
                mMediaExtractor.advance();

                audioBufferInfo.offset = 0;
                audioBufferInfo.size = size;
                audioBufferInfo.presentationTimeUs = presentationTimeUs;
                audioBufferInfo.flags = flags;
                //Write audio to out file
                Log.d(TAG, "writeSampleData audio : track index = " + outAudioTrackIndex + ", pts = " + presentationTimeUs);
                mMediaMuxer.writeSampleData(outAudioTrackIndex, audioByteBuffer, audioBufferInfo);
            }

            try {
                close();
            } catch (Exception ex) {
                return null;
            }

            while (System.currentTimeMillis() - t < MAX_TIME_MS) {
                //Canceled
                if (mCanceled) {
                    return null;
                }
                publish(System.currentTimeMillis() - t);
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                }
            }
            publish(MAX_TIME_MS);

            return GalleryUtils.transFileToContentUri(mContext, new File(mVideoPath));
        }

        private long computePresentationTime(int frameIndex) {
            return 132 + frameIndex * 1000000L / FRAME_RATE;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mProgressDialog != null) {
                mProgressDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (mCanceled) {
                AudioImageEncoder.this.onCanceled();
                return;
            }
            if (uri != null) {
                Log.d(TAG, "encode success");
                AudioImageEncoder.this.onPostExecute(uri);
            } else {
                Log.e(TAG, "encode error");
                AudioImageEncoder.this.onError(new Exception("some error happened, please check."));
            }
        }
    }

    private void onPreExecute() {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setTitle(R.string.conversing);
        mProgressDialog.setMax(100);
        mProgressDialog.setProgress(0);
        mProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, mContext.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mCanceled = true;
            }
        });
        mProgressDialog.show();
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }

    private void onPostExecute(Uri uri) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (mListener != null) {
            mListener.onPostExecute(uri);
        }
    }

    private void onError(Throwable t) {
        if (mVideoPath != null) {
            delete(mVideoPath);
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (mListener != null) {
            mListener.onError(t);
        }
    }

    private void onCanceled() {
        if (mVideoPath != null) {
            delete(mVideoPath);
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (mListener != null) {
            mListener.onCanceled();
        }
    }

    private void startEncode() {
        try {
            prepare();
        } catch (Exception e) {
            closeSilently();
            onError(e);
            return;
        }

        Task task = new Task();
        task.execute();
    }

    private void delete(String file) {
        try {
            new File(file).delete();
        } catch (Exception e) {
        }
    }

    private void deleteCachedFile() {
        try {
            File dir = mContext.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                File[] videos = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File file, String s) {
                        return s.startsWith("VID_") && s.endsWith(".mp4");
                    }
                });
                for (File video : videos) {
                    video.delete();
                }
            }
        } catch (Exception e) {
        }
    }

    private void closeSilently() {
        try {
            close();
        } catch (Exception e) {
        }
    }

    private void close() {
        Log.d(TAG, "close");
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
        if (mMediaExtractor != null) {
            mMediaExtractor.release();
            mMediaExtractor = null;
        }
        Utils.closeSilently(mFis);
    }

    private AudioImageEncoder(Builder builder) {
        this.mContext = builder.mContext;
        this.mImagePath = builder.mImagePath;
        this.mRotation = builder.mRotation;
        this.mListener = builder.mListener;
        this.mJpegSize = builder.mJpegSize;

    }

    public static Builder with(Context context) {
        return new Builder(context);
    }

    public static class Builder {
        private Context mContext;
        private String mImagePath;
        private long mJpegSize;
        private int mRotation;
        private Listener mListener;

        private Builder(Context context) {
            mContext = context;
        }

        public Builder load(String imagePath, long jpegSize) {
            Log.d(TAG, "load imagePath = " + imagePath);
            this.mImagePath = imagePath;
            this.mJpegSize = jpegSize;
            return this;
        }

        public Builder rotation(int rotation) {
            this.mRotation = rotation;
            return this;
        }

        public Builder listen(Listener listener) {
            this.mListener = listener;
            return this;
        }

        public void start() {
            AudioImageEncoder encoder = new AudioImageEncoder(this);
            encoder.start();
        }
    }

    private void start() {
        onPreExecute();

        if (mContext == null
                || mImagePath == null) {
            onError(new IllegalArgumentException("Context = " + mContext + ", ImagePath = " + mImagePath));
            return;
        }
        if (!(new File(mImagePath).exists())) {
            onError(new RuntimeException("ImageFile or AudioFile not exists!"));
            return;
        }

        startEncode();
    }

    private int calculateSampleSize(int imageWidth, int imageHeight) {
        Log.d(TAG, "calculateSampleSize MAX_VIDEO_WIDTH : " + MAX_VIDEO_WIDTH
                + ", imageWidth : " + imageWidth
                + ", MAX_VIDEO_HEIGHT : " + MAX_VIDEO_HEIGHT
                + ", imageHeight : " + imageHeight);
        if (MAX_VIDEO_WIDTH < imageWidth || MAX_VIDEO_HEIGHT < imageHeight) {
            float ratioW = 1.0f * imageWidth / MAX_VIDEO_WIDTH;
            float ratioH = 1.0f * imageHeight / MAX_VIDEO_HEIGHT;
            float sampleSize = Math.max(ratioW, ratioH);
            return sampleSize < 1 ? 1 : (int) (sampleSize + 0.99f);
        }
        return 1;
    }

    private byte[] decodeImage(String img, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;

        Bitmap bitmap = scaleBitmap(BitmapUtils.rotateBitmap(BitmapFactory.decodeFile(img, options), mRotation, true), mVideoWidth, mVideoHeight);

        Log.d(TAG, "decodeImage bitmap width = " + bitmap.getWidth() + ", height = " + bitmap.getHeight());

        return RefocusUtils.bitmap2yuv(bitmap);
    }

    private Bitmap scaleBitmap(Bitmap source, int targetW, int targetH) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (targetW > width || targetH > height) {
            return source;
        }
        return Bitmap.createBitmap(source, (width - targetW) / 2, (height - targetH) / 2, targetW, targetH);
    }
}

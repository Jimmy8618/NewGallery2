package com.android.gallery3d.v2.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.v2.data.MotionMeta;
import com.android.gallery3d.v2.util.UIHandler;
import com.android.gallery3d.v2.util.UIMessageHandler;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SimplePlayer implements UIMessageHandler {
    private static final String TAG = SimplePlayer.class.getSimpleName();

    private static final int MSG_ON_ERROR = 1;
    private static final int MSG_ON_COMPLETE = 2;
    private static final int MSG_ON_VIDEO_SIZE_CHANGED = 3;

    private OnErrorListener mOnErrorListener;
    private OnCompletionListener mOnCompletionListener;
    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;

    private Source mSource;
    private Surface mSurface;
    private int mAudioStreamType = AudioManager.STREAM_MUSIC;

    private DecoderThread mAudioThread;
    private DecoderThread mVideoThread;
    private MediaPlayer mMediaPlayer;
    private FileInputStream mPlayStream;

    private UIHandler<SimplePlayer> mHandler;

    public SimplePlayer() {
        mHandler = new UIHandler<>(this);
    }

    public void setDataSource(MediaItem mediaItem)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mSource = new Source(mediaItem);
    }

    public void setDataSource(String path)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mSource = new Source(path);
    }

    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mSource = new Source(fd, offset, length);
    }

    public void setAudioStreamType(int streamtype) {
        this.mAudioStreamType = streamtype;
    }

    public void setSurface(Surface surface) {
        this.mSurface = surface;
    }

    public void prepare() throws IOException, IllegalStateException {
        if (mSource == null) {
            throw new IllegalStateException("You must call setDataSource first!");
        }

        if (_prepare()) {
            Log.d(TAG, "prepare we have audio track, use MediaPlayer to play.");
            mMediaPlayer = new MediaPlayer();
            setPlayerSource(mMediaPlayer);
            mMediaPlayer.setSurface(mSurface);
            mMediaPlayer.setAudioStreamType(mAudioStreamType);
            mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    SimplePlayer.this.onVideoSize(width, height);
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    SimplePlayer.this.onError(what, extra);
                    return false;
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    SimplePlayer.this.onComplete();
                }
            });
            mMediaPlayer.prepare();
        } else {
            Log.d(TAG, "prepare no audio track found, use DecoderThread to play.");
            mVideoThread = new DecoderThread(this, DecoderThread.TYPE_VIDEO);
            mAudioThread = new DecoderThread(this, DecoderThread.TYPE_AUDIO);
        }
    }

    public void start() throws IllegalStateException {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
        } else if (mVideoThread != null && mAudioThread != null) {
            mVideoThread.start();
            mAudioThread.start();
        } else {
            throw new IllegalStateException("You must call prepare first!");
        }
    }

    public void stop() {
        try {
            if (mVideoThread != null) {
                mVideoThread.terminate();
                mVideoThread.join(1000);
                mVideoThread = null;
            }
            if (mAudioThread != null) {
                mAudioThread.terminate();
                mAudioThread.join(1000);
                mAudioThread = null;
            }
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            Utils.closeSilently(mPlayStream);
            mPlayStream = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mSource = null;
    }

    private boolean _prepare() {
        FileInputStream fs = null;
        MediaExtractor extractor = null;
        boolean hasAudio = false;
        try {
            extractor = new MediaExtractor();
            if (mSource.mediaItem != null) {
                if (mSource.mediaItem.isMotionPhoto()) {
                    if (mSource.mediaItem.getMotionMeta() == null) {
                        mSource.mediaItem.setMotionMeta(MotionMeta.parse(GalleryAppImpl.getApplication(),
                                mSource.mediaItem.getContentUri()));
                    }
                    fs = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mSource.mediaItem.getContentUri());
                    long offset = fs.available() - mSource.mediaItem.getMotionMeta().getVideoLength();
                    long length = mSource.mediaItem.getMotionMeta().getVideoLength();
                    extractor.setDataSource(fs.getFD(), offset, length);
                } else {
                    fs = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mSource.mediaItem.getContentUri());
                    extractor.setDataSource(fs.getFD());
                }
            } else if (mSource.fd != null) {
                extractor.setDataSource(mSource.fd, mSource.offset, mSource.length);
            } else {
                extractor.setDataSource(mSource.path);
            }
            int numOfTracks = extractor.getTrackCount();
            for (int i = 0; i < numOfTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    hasAudio = true;
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "_prepare failed", e);
            return hasAudio;
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            Utils.closeSilently(fs);
        }
        return hasAudio;
    }

    private void setPlayerSource(MediaPlayer player) throws IOException {
        if (mSource.mediaItem != null) {
            if (mSource.mediaItem.isMotionPhoto()) {
                if (mSource.mediaItem.getMotionMeta() == null) {
                    mSource.mediaItem.setMotionMeta(MotionMeta.parse(GalleryAppImpl.getApplication(),
                            mSource.mediaItem.getContentUri()));
                }
                mPlayStream = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mSource.mediaItem.getContentUri());
                long offset = mPlayStream.available() - mSource.mediaItem.getMotionMeta().getVideoLength();
                long length = mSource.mediaItem.getMotionMeta().getVideoLength();
                player.setDataSource(mPlayStream.getFD(), offset, length);
            } else {
                mPlayStream = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mSource.mediaItem.getContentUri());
                player.setDataSource(mPlayStream.getFD());
            }
        } else if (mSource.fd != null) {
            player.setDataSource(mSource.fd, mSource.offset, mSource.length);
        } else {
            player.setDataSource(mSource.path);
        }
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    private void onError(int what, int extra) {
        mHandler.obtainMessage(MSG_ON_ERROR, what, extra).sendToTarget();
    }

    private void onVideoSize(int width, int height) {
        mHandler.obtainMessage(MSG_ON_VIDEO_SIZE_CHANGED, width, height).sendToTarget();
    }

    private void onComplete() {
        mHandler.obtainMessage(MSG_ON_COMPLETE).sendToTarget();
    }

    @Override
    public void handleUIMessage(Message msg) {
        switch (msg.what) {
            case MSG_ON_ERROR:
                if (mOnErrorListener != null) {
                    mOnErrorListener.onError(this, msg.arg1, msg.arg2);
                }
                break;
            case MSG_ON_COMPLETE:
                if (mOnCompletionListener != null) {
                    mOnCompletionListener.onCompletion(this);
                }
                break;
            case MSG_ON_VIDEO_SIZE_CHANGED:
                if (mOnVideoSizeChangedListener != null) {
                    mOnVideoSizeChangedListener.onVideoSizeChanged(this, msg.arg1, msg.arg2);
                }
                break;
            default:
                break;
        }
    }

    public interface OnErrorListener {
        boolean onError(SimplePlayer mp, int what, int extra);
    }

    public interface OnCompletionListener {
        void onCompletion(SimplePlayer mp);
    }

    public interface OnVideoSizeChangedListener {
        void onVideoSizeChanged(SimplePlayer mp, int width, int height);
    }

    private static final class Source {
        private MediaItem mediaItem;
        private String path;
        private FileDescriptor fd;
        private long offset;
        private long length;

        public Source(MediaItem mediaItem) {
            this.mediaItem = mediaItem;
        }

        public Source(String path) {
            this.path = path;
        }

        public Source(FileDescriptor fd, long offset, long length) {
            this.fd = fd;
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class DecoderThread extends Thread {
        static final int TYPE_VIDEO = 1;
        static final int TYPE_AUDIO = 2;
        private static final int TIMEOUT_U_SEC = 10000;

        private boolean mExit = false;
        private boolean mInputDone = false;
        private boolean mOutputDone = false;

        private SimplePlayer mPlayer;
        private int mType;
        private int mTrackIndex;

        private MediaExtractor mExtractor;
        private MediaCodec mDecoder;
        private AudioTrack mAudioTrack;

        private MediaCodec.BufferInfo mBufferInfo;

        private FileInputStream mFis;

        DecoderThread(SimplePlayer player, int type) {
            this.mPlayer = player;
            this.mType = type;
            this.mTrackIndex = -1;
            this.mBufferInfo = new MediaCodec.BufferInfo();
        }

        boolean init() {
            mExtractor = new MediaExtractor();
            try {
                if (mPlayer.mSource.mediaItem != null) {
                    if (mPlayer.mSource.mediaItem.isMotionPhoto()) {
                        synchronized (DecoderThread.class) {
                            if (mPlayer.mSource.mediaItem.getMotionMeta() == null) {
                                mPlayer.mSource.mediaItem.setMotionMeta(
                                        MotionMeta.parse(GalleryAppImpl.getApplication(),
                                                mPlayer.mSource.mediaItem.getContentUri()));
                                if (mExit) {
                                    return false;
                                }
                            }
                        }
                        mFis = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mPlayer.mSource.mediaItem.getContentUri());
                        long offset = mFis.available() - mPlayer.mSource.mediaItem.getMotionMeta().getVideoLength();
                        long length = mPlayer.mSource.mediaItem.getMotionMeta().getVideoLength();
                        mExtractor.setDataSource(mFis.getFD(), offset, length);
                    } else {
                        mFis = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mPlayer.mSource.mediaItem.getContentUri());
                        mExtractor.setDataSource(mFis.getFD());
                    }
                } else if (mPlayer.mSource.fd != null) {
                    mExtractor.setDataSource(mPlayer.mSource.fd, mPlayer.mSource.offset, mPlayer.mSource.length);
                } else {
                    mExtractor.setDataSource(mPlayer.mSource.path);
                }

                int numOfTracks = mExtractor.getTrackCount();
                for (int i = 0; i < numOfTracks; i++) {
                    MediaFormat format = mExtractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mType == TYPE_VIDEO) {
                        if (mTrackIndex == -1 && mime.startsWith("video/")) {
                            mTrackIndex = i;
                            Log.d(TAG, "init TYPE_VIDEO, mTrackIndex = " + mTrackIndex + ", format = " + format);
                        }
                    } else if (mType == TYPE_AUDIO) {
                        if (mTrackIndex == -1 && mime.startsWith("audio/")) {
                            mTrackIndex = i;
                            Log.d(TAG, "init TYPE_AUDIO, mTrackIndex = " + mTrackIndex + ", format = " + format);
                        }
                    }
                }

                if (mTrackIndex == -1) {
                    if (mType == TYPE_VIDEO) {
                        Log.d(TAG, "init TYPE_VIDEO failed, track not find!");
                    } else if (mType == TYPE_AUDIO) {
                        Log.d(TAG, "init TYPE_AUDIO failed, track not find!");
                    }
                    return false;
                }

                mExtractor.selectTrack(mTrackIndex);
                MediaFormat format = mExtractor.getTrackFormat(mTrackIndex);
                String mime = format.getString(MediaFormat.KEY_MIME);
                mDecoder = MediaCodec.createDecoderByType(mime);
                Surface surface = null;
                if (mime.startsWith("video/")) {
                    int width = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    mPlayer.onVideoSize(width, height);
                    surface = mPlayer.mSurface;
                } else if (mime.startsWith("audio/")) {
                    int audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int audioChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate * audioChannelCount,
                            audioChannelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
                    mAudioTrack = new AudioTrack(mPlayer.mAudioStreamType,
                            audioSampleRate * audioChannelCount,
                            audioChannelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            minBufferSize,
                            AudioTrack.MODE_STREAM);
                    mAudioTrack.play();
                }
                mDecoder.configure(format, surface, null, 0);
                mDecoder.start();
            } catch (IOException e) {
                Log.e(TAG, "Error in init.", e);
                mPlayer.onError(0, 0);
                return false;
            }
            return true;
        }

        void deInit() {
            if (mDecoder != null) {
                mDecoder.release();
                mDecoder = null;
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mAudioTrack != null) {
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
            Utils.closeSilently(mFis);
        }

        void terminate() {
            mExit = true;
        }

        @Override
        public void run() {
            if (init()) {
                Log.d(TAG, "DecoderThread run, mTrackIndex = " + mTrackIndex + ", type = " + mType);
                long startMillis = System.currentTimeMillis();
                while (!mOutputDone && !mExit) {
                    if (!mInputDone) {
                        int inputBufIndex = mDecoder.dequeueInputBuffer(TIMEOUT_U_SEC);
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuf = mDecoder.getInputBuffer(inputBufIndex);
                            int chunkSize = mExtractor.readSampleData(inputBuf, 0);
                            if (chunkSize < 0) {
                                mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                mInputDone = true;
                            } else {
                                long presentationTimeUs = mExtractor.getSampleTime();
                                mDecoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                        presentationTimeUs, 0);
                                mExtractor.advance();
                            }
                        }
                    }
                    if (!mOutputDone) {
                        int decoderStatus = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_U_SEC);
                        if (decoderStatus >= 0) {
                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                mOutputDone = true;
                            }
                            boolean doRender = (mBufferInfo.size != 0);
                            if (doRender) {
                                decodeDelay(mBufferInfo, startMillis);
                                if (mType == TYPE_AUDIO) {
                                    ByteBuffer buf = mDecoder.getOutputBuffer(decoderStatus);
                                    if (buf != null) {
                                        byte[] data = new byte[mBufferInfo.size];
                                        buf.position(mBufferInfo.offset);
                                        buf.limit(mBufferInfo.offset + mBufferInfo.size);
                                        buf.get(data);
                                        mAudioTrack.write(data, 0, mBufferInfo.size);
                                    }
                                }
                            }
                            mDecoder.releaseOutputBuffer(decoderStatus, doRender);
                        }
                    }
                }
                Log.d(TAG, "DecoderThread exit, mTrackIndex = " + mTrackIndex + ", type = " + mType);
                if (!mExit) {
                    mPlayer.onComplete();
                }
            }
            deInit();
        }

        private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
            while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMillis) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }
}

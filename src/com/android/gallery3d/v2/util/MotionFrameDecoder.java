package com.android.gallery3d.v2.util;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Message;

import androidx.annotation.NonNull;

import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.data.MotionMeta;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class MotionFrameDecoder implements UIMessageHandler {
    private static final String TAG = MotionFrameDecoder.class.getSimpleName();
    private static final boolean DEBUG = true;

    //获取的缩略图目标大小
    private static final int TARGET_THUMB = 512;

    //当开始解码视频帧时回调
    private static final int MSG_DECODING_START = 1;
    //当解码到一帧视频时回调
    private static final int MSG_DECODING = 2;
    //当结束解码视频帧时回调
    private static final int MSG_DECODING_END = 3;
    //当获取到图片缩略图时回调
    private static final int MSG_SCREEN_NAIL_DONE = 4;

    public interface OnFrameAvailableListener {
        void onScreenNail(Bitmap bitmap);//图片缩略图

        void onFrameDecodeStart(boolean forSave);//如果为true, 表示保存图片; 否则表示解析所有帧

        void onFrameAvailable(MotionThumbItem item);//解析所有帧时回调缩略图

        void onFrameDecodeEnd(boolean forSave);//如果为true, 表示保存图片; 否则表示解析所有帧

        void onSaveFrame(String path, Bitmap bitmap, long saveTime);//保存图片回调

        void onSaveFrame(Uri uri, Bitmap bitmap, long saveTime);//保存图片回调
    }

    //回调监听
    private OnFrameAvailableListener mOnFrameAvailableListener;
    //主线程Handler
    private UIHandler<MotionFrameDecoder> mMainHandler;
    //获取图片缩略图任务
    private ScreenNailJob mScreenNailJob;
    private Future<Bitmap> mScreenNailFuture;
    //解析视频帧线程
    private FrameDecoderThread mFrameDecoderThread;

    private Path mPath;

    public MotionFrameDecoder(Path path, OnFrameAvailableListener l) {
        mPath = path;
        mOnFrameAvailableListener = l;
        mMainHandler = new UIHandler<>(this);
        mScreenNailJob = new ScreenNailJob(path, mMainHandler);
        mFrameDecoderThread = new FrameDecoderThread(path, mMainHandler);
        mFrameDecoderThread.start();
    }

    public void startSaveTask(@NonNull MotionThumbItem item) {
        FrameDecoderThread t = new FrameDecoderThread(mPath, mMainHandler,
                item, mOnFrameAvailableListener);
        t.start();
        t.resumeTask();
    }

    public void resume() {
        Log.d(TAG, "resume");
        //获取缩略图
        mScreenNailFuture = getThreadPool().submit(mScreenNailJob, mScreenNailJob);
        //开启获取视频帧
        mFrameDecoderThread.resumeTask();
    }

    public void pause() {
        Log.d(TAG, "pause");
        //取消获取缩略图任务
        mScreenNailFuture.cancel();
        mScreenNailFuture = null;
        //暂停解析视频帧
        mFrameDecoderThread.pauseTask();
    }

    public void destroy() {
        Log.d(TAG, "destroy");
        //终止任务
        mFrameDecoderThread.terminateTask();
        mFrameDecoderThread = null;
    }

    @Override
    public void handleUIMessage(Message msg) {
        switch (msg.what) {
            case MSG_DECODING_START:
                if (mOnFrameAvailableListener != null) {
                    mOnFrameAvailableListener.onFrameDecodeStart(msg.obj != null);
                }
                break;
            case MSG_DECODING:
                if (mOnFrameAvailableListener != null) {
                    mOnFrameAvailableListener.onFrameAvailable((MotionThumbItem) msg.obj);
                }
                break;
            case MSG_DECODING_END:
                if (mOnFrameAvailableListener != null) {
                    mOnFrameAvailableListener.onFrameDecodeEnd(msg.obj != null);
                }
                break;
            case MSG_SCREEN_NAIL_DONE:
                if (mOnFrameAvailableListener != null) {
                    mOnFrameAvailableListener.onScreenNail((Bitmap) msg.obj);
                }
                break;
            default:
                break;
        }
    }

    private ThreadPool getThreadPool() {
        return GalleryAppImpl.getApplication().getThreadPool();
    }

    private static BitmapFactory.Options getOptions(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        float scale = (float) TARGET_THUMB / Math.max(options.outWidth, options.outHeight);
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        options.inJustDecodeBounds = false;
        return options;
    }

    private static BitmapFactory.Options getOptions(Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        InputStream is = null;
        try {
            is = GalleryAppImpl.getApplication().getContentResolver().openInputStream(uri);
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            float scale = (float) TARGET_THUMB / Math.max(options.outWidth, options.outHeight);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
            options.inJustDecodeBounds = false;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        return options;
    }

    private static class ScreenNailJob implements ThreadPool.Job<Bitmap>, FutureListener<Bitmap> {
        private Path mPath;
        private UIHandler<MotionFrameDecoder> mHandler;

        ScreenNailJob(Path path, UIHandler<MotionFrameDecoder> handler) {
            this.mPath = path;
            this.mHandler = handler;
        }

        @Override
        public Bitmap run(ThreadPool.JobContext jc) {
            MediaItem mediaItem = (MediaItem) GalleryAppImpl.getApplication().getDataManager().getMediaObject(mPath);
            BitmapFactory.Options options = null;
            Bitmap bitmap = null;

            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                InputStream is = null;
                options = getOptions(mediaItem.getContentUri());
                try {
                    is = GalleryAppImpl.getApplication().getContentResolver().openInputStream(mediaItem.getContentUri());
                    bitmap = BitmapFactory.decodeStream(is, null, options);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Utils.closeSilently(is);
                }
            } else {
                options = getOptions(mediaItem.getFilePath());
                bitmap = BitmapFactory.decodeFile(mediaItem.getFilePath(), options);
            }

            if (jc.isCancelled() || bitmap == null) {
                return null;
            }
            bitmap = BitmapUtils.rotateBitmap(bitmap,
                    mediaItem.getRotation() - mediaItem.getFullImageRotation(), true);
            return bitmap;
        }

        @Override
        public void onFutureDone(Future<Bitmap> future) {
            Bitmap bitmap = future.get();
            if (bitmap != null) {
                Log.d(TAG, "onScreenNail (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
                mHandler.obtainMessage(MSG_SCREEN_NAIL_DONE, bitmap).sendToTarget();
            }
        }
    }

    private static class FrameDecoderThread extends Thread {
        private final int TIMEOUT_USEC = 10000;
        //由于视频帧可能太多, 全部解析出来占用内存, 因此设置每秒大概解析10帧数据
        private final int MAX_FRAME_RATE = 10;

        private volatile boolean mActive = true;
        private volatile boolean mPaused = true;
        private volatile boolean mLoading = false;

        private MediaItem mMediaItem;

        private UIHandler<MotionFrameDecoder> mHandler;

        //如果为null, 遍历获取所有帧; 否则获取该时间戳的一帧
        private MotionThumbItem mMotionThumbItem;

        private OnFrameAvailableListener mOnFrameAvailableListener;

        private MediaExtractor mMediaExtractor;
        private MediaMetadataRetriever mRetriever;
        private FileInputStream mFis;

        //索引0, 存放低分辨率视频track, 必须存在
        //索引1, 存放高分辨率视频track, 可选
        private int[] mVideoTrackIndex;

        private int mRotation = 0;

        private MediaCodec mMediaCodec0; //0
        private MediaCodec mMediaCodec1; //1

        private boolean mInputDone0;     //0
        private boolean mOutputDone0;    //0

        private boolean mInputDone1;     //1
        private boolean mOutputDone1;    //1

        private CodecOutputSurface mCodecOutputSurface0;  //0
        private CodecOutputSurface mCodecOutputSurface1;  //1

        private MediaCodec.BufferInfo mBufferInfo;

        //存放高分辨率帧时间戳信息
        private ArrayList<MotionThumbItem> mVideoTrackFrames1;

        private int mFrameInterval;
        private int mFrameIndex;
        private int mFrameCount;

        FrameDecoderThread(Path path, UIHandler<MotionFrameDecoder> handler) {
            this.mMediaItem = (MediaItem) GalleryAppImpl.getApplication().getDataManager().getMediaObject(path);
            this.mHandler = handler;
            this.mMotionThumbItem = null;
            mVideoTrackIndex = new int[]{-1, -1};
            mVideoTrackFrames1 = new ArrayList<>();
            mBufferInfo = new MediaCodec.BufferInfo();
        }

        FrameDecoderThread(Path path, UIHandler<MotionFrameDecoder> handler,
                           MotionThumbItem motionThumbItem, OnFrameAvailableListener l) {
            this.mMediaItem = (MediaItem) GalleryAppImpl.getApplication().getDataManager().getMediaObject(path);
            this.mHandler = handler;
            this.mMotionThumbItem = motionThumbItem;
            mVideoTrackIndex = new int[]{-1, -1};
            mVideoTrackFrames1 = new ArrayList<>();
            mBufferInfo = new MediaCodec.BufferInfo();
            mOnFrameAvailableListener = l;
        }

        //是否存在高分辨率帧
        private boolean isHighResolution() {
            boolean isHigh = false;
            if (mVideoTrackFrames1.size() == 0) {
                return false;
            }
            for (MotionThumbItem item : mVideoTrackFrames1) {
                if (item.getPresentationTimeUs() == mBufferInfo.presentationTimeUs) {
                    isHigh = true;
                    break;
                }
            }
            return isHigh;
        }

        @Override
        public void run() {
            Log.d(TAG, "FrameDecoderThread run B.");
            mHandler.obtainMessage(MSG_DECODING_START, mMotionThumbItem).sendToTarget();
            if (init()) {
                while (mActive) {
                    synchronized (this) {
                        if (mActive && mPaused) {
                            loading(false);
                            Log.d(TAG, "FrameDecoderThread run wait...");
                            Utils.waitWithoutInterrupt(this);
                            continue;
                        }
                    }
                    loading(true);
                    //是否存在高分辨率视频帧, 存在就把时间戳保存下来======================================
                    if (mVideoTrackIndex[1] != -1 && (mMotionThumbItem == null || mMotionThumbItem.hasHighResolution())) {
                        while (!mOutputDone1) {
                            if (!mActive || mPaused) {
                                break;
                            }
                            if (mMediaCodec1 == null) {
                                mMediaExtractor.selectTrack(mVideoTrackIndex[1]);
                                MediaFormat videoFormat = mMediaExtractor.getTrackFormat(mVideoTrackIndex[1]);
                                int width = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
                                int height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
                                long durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION);
                                int frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                                int frameCount = (int) ((durationUs / 1000000.0f) * frameRate);
                                Log.d(TAG, "video track = " + mVideoTrackIndex[1]
                                        + ", " + width + "x" + height + ", frameRate = " + frameRate
                                        + ", frameCount = " + frameCount + ", durationUs = " + durationUs);
                                try {
                                    mMediaCodec1 = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                                } catch (IOException e) {
                                    Log.e(TAG, "FrameDecoderThread create decoder error", e);
                                    break;
                                }
                                Log.d(TAG, "video track = " + mVideoTrackIndex[1] + ", we want size "
                                        + width + "x" + height);
                                mCodecOutputSurface1 = new CodecOutputSurface(width, height, mRotation);
                                mMediaCodec1.configure(videoFormat, mCodecOutputSurface1.getSurface(), null, 0);
                                mMediaCodec1.start();
                            }
                            if (!mInputDone1) {
                                int inputBufIndex = mMediaCodec1.dequeueInputBuffer(TIMEOUT_USEC);
                                if (inputBufIndex >= 0) {
                                    ByteBuffer inputBuf = mMediaCodec1.getInputBuffer(inputBufIndex);
                                    int chunkSize = mMediaExtractor.readSampleData(inputBuf, 0);
                                    if (chunkSize < 0) {
                                        mMediaCodec1.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                        mInputDone1 = true;
                                    } else {
                                        long presentationTimeUs = mMediaExtractor.getSampleTime();
                                        mMediaCodec1.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                                presentationTimeUs, 0);
                                        mMediaExtractor.advance();
                                    }
                                }
                            }
                            if (!mOutputDone1) {
                                int decoderStatus = mMediaCodec1.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                                if (decoderStatus >= 0) {
                                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        mOutputDone1 = true;
                                    }
                                    boolean doRender = (mBufferInfo.size != 0);
                                    mMediaCodec1.releaseOutputBuffer(decoderStatus, doRender);
                                    if (doRender) {
                                        if (DEBUG) {
                                            Log.d(TAG, "video track = " + mVideoTrackIndex[1]
                                                    + ", presentationTimeUs = " + mBufferInfo.presentationTimeUs);
                                        }
                                        if (mMotionThumbItem == null) {
                                            //遍历所有
                                            Log.d(TAG, "track:" + mVideoTrackIndex[1] + ", presentationTimeUs = "
                                                    + mBufferInfo.presentationTimeUs);
                                            mVideoTrackFrames1.add(new MotionThumbItem(0,
                                                    mBufferInfo.presentationTimeUs, null,
                                                    false, false));
                                        } else {
                                            //找到对应时间戳的帧, 保存下来
                                            if (mMotionThumbItem.getPresentationTimeUs()
                                                    == mBufferInfo.presentationTimeUs) {
                                                //save frame
                                                try {
                                                    mCodecOutputSurface1.awaitNewImage();
                                                    mCodecOutputSurface1.drawImage();
                                                    save(new File(mMediaItem.getFilePath()).getParent(),
                                                            mCodecOutputSurface1.getBitmap());
                                                } catch (Exception e) {
                                                    Log.e(TAG, "save image failed", e);
                                                }
                                                mActive = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //==============================================================================
                    if (!mActive || mPaused) {
                        continue;
                    }
                    //遍历低分辨率的视频帧=============================================================
                    if (mMotionThumbItem == null || !mMotionThumbItem.hasHighResolution()) {
                        while (!mOutputDone0) {
                            if (!mActive || mPaused) {
                                break;
                            }
                            if (mMediaCodec0 == null) {
                                mMediaExtractor.selectTrack(mVideoTrackIndex[0]);
                                MediaFormat videoFormat = mMediaExtractor.getTrackFormat(mVideoTrackIndex[0]);
                                int width = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
                                int height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
                                long durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION);
                                int frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                                int frameCount = (int) ((durationUs / 1000000.0f) * frameRate);
                                Log.d(TAG, "video track = " + mVideoTrackIndex[0]
                                        + ", " + width + "x" + height + ", frameRate = " + frameRate
                                        + ", frameCount = " + frameCount + ", durationUs = " + durationUs);

                                int suggestFrameCount = (int) ((durationUs / 1000000.0f) * MAX_FRAME_RATE);
                                mFrameInterval = (int) (((float) frameCount / suggestFrameCount) + 0.5f);
                                if (mFrameInterval < 1) {
                                    mFrameInterval = 1;
                                }
                                mFrameCount = frameCount;

                                try {
                                    mMediaCodec0 = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                                } catch (IOException e) {
                                    Log.e(TAG, "FrameDecoderThread create decoder error", e);
                                    break;
                                }
                                float scale = (float) TARGET_THUMB / Math.max(width, height);
                                int inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
                                if (mMotionThumbItem != null) {
                                    inSampleSize = 1;
                                }
                                Log.d(TAG, "video track = " + mVideoTrackIndex[0] + ", we want size "
                                        + width / inSampleSize + "x" + height / inSampleSize);
                                mCodecOutputSurface0 = new CodecOutputSurface(width / inSampleSize,
                                        height / inSampleSize, mRotation);
                                mMediaCodec0.configure(videoFormat, mCodecOutputSurface0.getSurface(), null, 0);
                                mMediaCodec0.start();
                            }
                            if (!mInputDone0) {
                                int inputBufIndex = mMediaCodec0.dequeueInputBuffer(TIMEOUT_USEC);
                                if (inputBufIndex >= 0) {
                                    ByteBuffer inputBuf = mMediaCodec0.getInputBuffer(inputBufIndex);
                                    int chunkSize = mMediaExtractor.readSampleData(inputBuf, 0);
                                    if (chunkSize < 0) {
                                        mMediaCodec0.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                        mInputDone0 = true;
                                    } else {
                                        long presentationTimeUs = mMediaExtractor.getSampleTime();
                                        mMediaCodec0.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                                presentationTimeUs, 0);
                                        mMediaExtractor.advance();
                                    }
                                }
                            }
                            if (!mOutputDone0) {
                                int decoderStatus = mMediaCodec0.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                                if (decoderStatus >= 0) {
                                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        mOutputDone0 = true;
                                    }
                                    boolean doRender = (mBufferInfo.size != 0);

                                    mMediaCodec0.releaseOutputBuffer(decoderStatus, doRender);
                                    if (doRender) {
                                        if (DEBUG) {
                                            Log.d(TAG, "video track = " + mVideoTrackIndex[0]
                                                    + ", presentationTimeUs = " + mBufferInfo.presentationTimeUs);
                                        }

                                        if (mMotionThumbItem == null) {
                                            //遍历所有
                                            try {
                                                boolean isHighResolution = isHighResolution();
                                                if ((mMediaItem.isMotionPhoto() && mMediaItem.getMotionMeta().getMotionPhotoPresentationTimestampUs() == mBufferInfo.presentationTimeUs)
                                                        || (mFrameIndex % mFrameInterval == 0)
                                                        || mFrameIndex == mFrameCount - 1
                                                        || isHighResolution) {
                                                    Log.d(TAG, "track:" + mVideoTrackIndex[0] + ", presentationTimeUs = "
                                                            + mBufferInfo.presentationTimeUs);
                                                    mCodecOutputSurface0.awaitNewImage();
                                                    mCodecOutputSurface0.drawImage();
                                                    mHandler.obtainMessage(MSG_DECODING, new MotionThumbItem(mFrameIndex,
                                                            mBufferInfo.presentationTimeUs, mCodecOutputSurface0.getBitmap(),
                                                            mMediaItem.isMotionPhoto() && mMediaItem.getMotionMeta().getMotionPhotoPresentationTimestampUs()
                                                                    == mBufferInfo.presentationTimeUs, isHighResolution)).sendToTarget();
                                                }
                                                mFrameIndex++;
                                            } catch (Exception e) {
                                                Log.e(TAG, "error when obtain bitmap", e);
                                                break;
                                            }
                                        } else {
                                            //找到对应时间戳的帧, 保存下来
                                            if (mMotionThumbItem.getPresentationTimeUs()
                                                    == mBufferInfo.presentationTimeUs) {
                                                //save frame
                                                try {
                                                    mCodecOutputSurface0.awaitNewImage();
                                                    mCodecOutputSurface0.drawImage();
                                                    save(new File(mMediaItem.getFilePath()).getParent(),
                                                            mCodecOutputSurface0.getBitmap());
                                                } catch (Exception e) {
                                                    Log.e(TAG, "save image failed", e);
                                                }
                                                mActive = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //==============================================================================
                    if (!mActive || mPaused) {
                        continue;
                    }
                    mActive = false;
                }
            }
            deInit();
            loading(false);
            mHandler.obtainMessage(MSG_DECODING_END, mMotionThumbItem).sendToTarget();
            Log.d(TAG, "FrameDecoderThread run E.");
        }

        private boolean init() {
            if (mMediaItem.isMotionPhoto()) {
                if (mMediaItem.getMotionMeta() == null) {
                    mMediaItem.setMotionMeta(MotionMeta.parse(GalleryAppImpl.getApplication(),
                            mMediaItem.getContentUri()));
                }
            } else {
                Log.w(TAG, "init failed: not a motion photo");
            }

            try {
                mRetriever = new MediaMetadataRetriever();
                mMediaExtractor = new MediaExtractor();
                mFis = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(mMediaItem.getContentUri());

                if (mMediaItem.isMotionPhoto()) {
                    long offset = mFis.available() - mMediaItem.getMotionMeta().getVideoLength();
                    long length = mMediaItem.getMotionMeta().getVideoLength();
                    mMediaExtractor.setDataSource(mFis.getFD(), offset, length);
                    mRetriever.setDataSource(mFis.getFD(), offset, length);
                } else {
                    mMediaExtractor.setDataSource(mFis.getFD());
                    mRetriever.setDataSource(mFis.getFD());
                }

                int numOfTracks = mMediaExtractor.getTrackCount();
                int index = 0;
                for (int i = 0; i < numOfTracks; i++) {
                    MediaFormat format = mMediaExtractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        if (index < mVideoTrackIndex.length) {
                            mVideoTrackIndex[index] = i;
                            index++;
                        }
                    }
                }

                if (mVideoTrackIndex[0] == -1) {
                    throw new Exception("Cannot find a video track.");
                }

                try {
                    mRotation = Integer.parseInt(mRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                } catch (Exception ignored) {
                }
                Log.d(TAG, "init video rotation is " + mRotation);

                if (DEBUG) {
                    for (int i = 0; i < mVideoTrackIndex.length; i++) {
                        if (mVideoTrackIndex[i] == -1) {
                            continue;
                        }
                        MediaFormat format = mMediaExtractor.getTrackFormat(mVideoTrackIndex[i]);
                        Log.d(TAG, "init videoTrack[" + i + "] = " + format);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "init failed: ", e);
                return false;
            }
            return true;
        }

        private void deInit() {
            if (mCodecOutputSurface0 != null) {
                mCodecOutputSurface0.release();
                mCodecOutputSurface0 = null;
            }

            if (mCodecOutputSurface1 != null) {
                mCodecOutputSurface1.release();
                mCodecOutputSurface1 = null;
            }

            if (mMediaExtractor != null) {
                mMediaExtractor.release();
                mMediaExtractor = null;
            }

            if (mMediaCodec0 != null) {
                mMediaCodec0.release();
                mMediaCodec0 = null;
            }

            if (mMediaCodec1 != null) {
                mMediaCodec1.release();
                mMediaCodec1 = null;
            }

            if (mRetriever != null) {
                mRetriever.release();
                mRetriever = null;
            }

            Utils.closeSilently(mFis);
        }

        private void loading(boolean loading) {
            if (mLoading == loading) {
                return;
            }
            mLoading = loading;
        }

        synchronized void resumeTask() {
            mPaused = false;
            notifyAll();
        }

        synchronized void pauseTask() {
            mPaused = true;
            notifyAll();
        }

        synchronized void terminateTask() {
            mActive = false;
            notifyAll();
        }

        private void save(String parent, Bitmap bitmap) {
            if (bitmap == null) {
                return;
            }
            /*
            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                String storage = Utils.getStorageDirectory(parent);
                parent = storage + "/" + SaveImage.MOTION_SNAP_SAVE_DIRECTORY;
                long saveTime = System.currentTimeMillis();
                String fileName = GalleryUtils.generateImageName(saveTime);
                fileName = Utils.generateFileName(parent, fileName, ".jpg");
                File path = new File(parent + "/" + fileName + ".jpg");
                Log.d(TAG, "save parent dir = " + path.getParent() + ", bitmap size = "
                        + bitmap.getWidth() + "x" + bitmap.getHeight());
                saveTime /= 1000;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.ImageColumns.TITLE, path.getName());
                values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, path.getName());
                values.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, SaveImage.MOTION_SNAP_SAVE_DIRECTORY);
                values.put(MediaStore.Images.ImageColumns.IS_PENDING, 1);
                values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, saveTime);
                values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, saveTime);
                values.put(MediaStore.Images.ImageColumns.DATE_ADDED, saveTime);
                values.put(MediaStore.Images.ImageColumns.ORIENTATION, 0);
                values.put(MediaStore.Images.ImageColumns.DATA, path.getAbsolutePath());
                Uri c = MediaStore.Images.Media.getContentUri(Utils.getMediaVolumeName(GalleryAppImpl.getApplication(), parent));
                Uri retUri = GalleryAppImpl.getApplication().getContentResolver().insert(c, values);
                if (mOnFrameAvailableListener != null) {
                    mOnFrameAvailableListener.onSaveFrame(retUri, bitmap, saveTime);
                }
            } else {
                */
            Log.d(TAG, "save parent dir = " + parent + ", bitmap size = "
                    + bitmap.getWidth() + "x" + bitmap.getHeight());
            long saveTime = System.currentTimeMillis();
            String fileName = GalleryUtils.generateImageName(saveTime);
            String newName = fileName;
            int copy = 1;
            while ((new File(parent + "/" + newName + ".jpg")).exists()) {
                newName = fileName + "-" + copy++;
            }
            String path = parent + "/" + newName + ".jpg";
            if (mOnFrameAvailableListener != null) {
                mOnFrameAvailableListener.onSaveFrame(path, bitmap, saveTime);
            }
                /*
            }
            */
        }
    }
}
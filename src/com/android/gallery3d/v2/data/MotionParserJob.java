package com.android.gallery3d.v2.data;

import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.v2.util.UIHandler;
import com.android.gallery3d.v2.util.UIMessageHandler;

import java.util.LinkedList;

public class MotionParserJob implements UIMessageHandler {
    private static final String TAG = MotionParserJob.class.getSimpleName();

    private static final int MSG_PARSE_MOTION_DONE = 1;

    public interface OnMotionMetaParseListener {
        void onMotionMetaParsed();
    }

    private Handler mMainHandler;
    private final LinkedList<MediaItem> mLinkedList = new LinkedList<>();
    private JobTask mJobTask;
    private OnMotionMetaParseListener mOnMotionMetaParseListener;

    public void setOnMotionMetaParseListener(OnMotionMetaParseListener l) {
        mOnMotionMetaParseListener = l;
    }

    public MotionParserJob() {
        mMainHandler = new UIHandler<>(this);
    }

    public void resume() {
        Log.d(TAG, "onResume");
        if (mJobTask == null) {
            mJobTask = new JobTask();
            mJobTask.start();
        }
    }

    public void parse(MediaItem item) {
        synchronized (mLinkedList) {
            mLinkedList.add(item);
            if (mJobTask != null) {
                mJobTask.notifyDirty();
            }
        }
    }

    public void pause() {
        Log.d(TAG, "onPause");
        synchronized (mLinkedList) {
            mLinkedList.clear();
        }
        if (mJobTask != null) {
            mJobTask.terminate();
            mJobTask = null;
        }
    }

    private MediaItem post() {
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                return null;
            } else {
                return mLinkedList.remove();
            }
        }
    }

    private class JobTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;

        private MediaItem mMediaItem;

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Log.d(TAG, "run start.");
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty) {
                        Log.d(TAG, "run wait.");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;

                mMediaItem = post();
                if (mMediaItem == null) {
                    continue;
                }
                if (mMediaItem.getMotionMeta() != null) {
                    continue;
                }

                MotionMeta meta = MotionMeta.parse(GalleryAppImpl.getApplication(), mMediaItem.getContentUri());
                mMediaItem.setMotionMeta(meta);
                Log.d(TAG, "motion-photo-" + mMediaItem.getPath() + " = " + meta);
                Message.obtain(mMainHandler, MSG_PARSE_MOTION_DONE).sendToTarget();
            }
            Log.d(TAG, "run end.");
        }

        synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }

    @Override
    public void handleUIMessage(Message msg) {
        switch (msg.what) {
            case MSG_PARSE_MOTION_DONE:
                if (mOnMotionMetaParseListener != null) {
                    mOnMotionMetaParseListener.onMotionMetaParsed();
                }
                break;
            default:
                break;
        }
    }
}

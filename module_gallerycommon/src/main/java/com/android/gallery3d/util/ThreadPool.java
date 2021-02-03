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

package com.android.gallery3d.util;

import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
    @SuppressWarnings("unused")
    private static final String TAG = "ThreadPool";
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int KEEP_ALIVE_TIME = 10; // 10 seconds

    // Resource type
    public static final int MODE_NONE = 0;
    public static final int MODE_CPU = 1;
    public static final int MODE_NETWORK = 2;

    public static final JobContext JOB_CONTEXT_STUB = new JobContextStub();

    ResourceCounter mCpuCounter = new ResourceCounter(2);
    ResourceCounter mNetworkCounter = new ResourceCounter(2);
    // CPU_CORES_NUM:Dynamic control thread number according to CPU Cores from constant number
    // if the CPU Cores <= 2,then let the minium parallel process be 2 to avoid multi_thread activity error
    public static final int CPU_CORES_NUM = Runtime.getRuntime().availableProcessors();
    public static final int PARALLEL_THREAD_NUM_MIN = 2;
    public static final int PARALLEL_THREAD_NUM = PARALLEL_THREAD_NUM_MIN > (CPU_CORES_NUM / 2) ? PARALLEL_THREAD_NUM_MIN : (CPU_CORES_NUM / 2);

    // A Job is like a Callable, but it has an addition JobContext parameter.
    public interface Job<T> {
        T run(JobContext jc);
    }

    public interface JobContext {
        boolean isCancelled();

        void setCancelListener(CancelListener listener);

        boolean setMode(int mode);
    }

    private static class JobContextStub implements JobContext {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelListener(CancelListener listener) {
        }

        @Override
        public boolean setMode(int mode) {
            return true;
        }
    }

    public interface CancelListener {
        void onCancel();
    }

    private static class ResourceCounter {
        public int value;

        public ResourceCounter(int v) {
            value = v;
        }
    }

    private final Executor mExecutor;

    public ThreadPool() {
        this(CORE_POOL_SIZE, MAX_POOL_SIZE);
    }

    public ThreadPool(int initPoolSize, int maxPoolSize) {
        mExecutor = new ThreadPoolExecutor(
                initPoolSize, maxPoolSize, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                new PriorityThreadFactory("thread-pool",
                        android.os.Process.THREAD_PRIORITY_BACKGROUND));
        // SPRD:Add 20150505 by Spreadst for bug434569, RejectedExecutionException is thrown in monkey
        // do not throw RejectedExecutionException here.
        ((ThreadPoolExecutor) mExecutor).setRejectedExecutionHandler(new RejectedExecutionHandler() {

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                if (isExecutorShutDown()) {
                    return;  // do nothing
                } else {
                    Log.w(TAG, new RejectedExecutionException("Task " + r.toString() + " rejected from " + executor.toString()));
                }
            }
        });
    }

    // Submit a job to the thread pool. The listener will be called when the
    // job is finished (or cancelled).
    public <T> Future<T> submit(Job<T> job, FutureListener<T> listener) {
        Worker<T> w = new Worker<T>(job, listener);
        // SPRD:Add 20150505 by Spreadst for bug434569, RejectedExecutionException is thrown in monkey
        // Add to judge whether ThreadPoolExecutor is shutdown or not.
        if (!isExecutorShutDown()) {
            mExecutor.execute(w);
        }
        return w;
    }

    public <T> Future<T> submit(Job<T> job) {
        return submit(job, null);
    }

    // SPRD:for bug432196, thread leak in gallery monkey test
    // Add to shutdown ThreadPoolExecutor.
    public void shutDownExecutor() {
        ((ThreadPoolExecutor) mExecutor).shutdown();
    }

    // SPRD:Add 20150505 by Spreadst for bug434569, RejectedExecutionException is thrown in monkey
    // Add to judge whether ThreadPoolExecutor is shutdown or not.
    public boolean isExecutorShutDown() {
        return ((ThreadPoolExecutor) mExecutor).isShutdown();
    }

    private class Worker<T> implements Runnable, Future<T>, JobContext {
        @SuppressWarnings("hiding")
        private static final String TAG = "Worker";
        Job<T> mJob;
        FutureListener<T> mListener;
        boolean mIsDone;
        T mResult;
        private CancelListener mCancelListener;
        private ResourceCounter mWaitOnResource;
        private volatile boolean mIsCancelled;
        private int mMode;

        public Worker(Job<T> job, FutureListener<T> listener) {
            mJob = job;
            mListener = listener;
        }

        // This is called by a thread in the thread pool.
        @Override
        public void run() {
            T result = null;

            // A job is in CPU mode by default. setMode returns false
            // if the job is cancelled.
            if (setMode(MODE_CPU)) {
                try {
                    result = mJob.run(this);
                } catch (Throwable ex) {
                    Log.w(TAG, "Exception in running a job", ex);
                }
            }

            synchronized (this) {
                setMode(MODE_NONE);
                mResult = result;
                mIsDone = true;
                notifyAll();
            }
            if (mListener != null) {
                mListener.onFutureDone(this);
            }
        }

        // Below are the methods for Future.
        @Override
        public synchronized void cancel() {
            if (mIsCancelled) {
                return;
            }
            mIsCancelled = true;
            if (mWaitOnResource != null) {
                synchronized (mWaitOnResource) {
                    mWaitOnResource.notifyAll();
                }
            }
            if (mCancelListener != null) {
                mCancelListener.onCancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public synchronized boolean isDone() {
            return mIsDone;
        }

        @Override
        public synchronized T get() {
            while (!mIsDone) {
                try {
                    wait();
                } catch (Exception ex) {
                    Log.w(TAG, "ingore exception", ex);
                    // ignore.
                }
            }
            return mResult;
        }

        @Override
        public void waitDone() {
            get();
        }

        // Below are the methods for JobContext (only called from the
        // thread running the job)
        @Override
        public synchronized void setCancelListener(CancelListener listener) {
            mCancelListener = listener;
            if (mIsCancelled && mCancelListener != null) {
                mCancelListener.onCancel();
            }
        }

        @Override
        public boolean setMode(int mode) {
            // Release old resource
            ResourceCounter rc = modeToCounter(mMode);
            if (rc != null) {
                releaseResource(rc);
            }
            mMode = MODE_NONE;

            // Acquire new resource
            rc = modeToCounter(mode);
            if (rc != null) {
                if (!acquireResource(rc)) {
                    return false;
                }
                mMode = mode;
            }

            return true;
        }

        private ResourceCounter modeToCounter(int mode) {
            if (mode == MODE_CPU) {
                return mCpuCounter;
            } else if (mode == MODE_NETWORK) {
                return mNetworkCounter;
            } else {
                return null;
            }
        }

        private boolean acquireResource(ResourceCounter counter) {
            while (true) {
                synchronized (this) {
                    if (mIsCancelled) {
                        mWaitOnResource = null;
                        return false;
                    }
                    mWaitOnResource = counter;
                }

                synchronized (counter) {
                    if (counter.value > 0) {
                        counter.value--;
                        break;
                    } else {
                        try {
                            counter.wait();
                        } catch (InterruptedException ex) {
                            // ignore.
                        }
                    }
                }
            }

            synchronized (this) {
                mWaitOnResource = null;
            }

            return true;
        }

        private void releaseResource(ResourceCounter counter) {
            synchronized (counter) {
                counter.value++;
                counter.notifyAll();
            }
        }
    }

    public <T> Future<T> submitInThread(Job<T> job, FutureListener<T> listener) {
        Worker2<T> w = new Worker2<>(job, listener);
        Thread t = new Thread(w);
        t.start();
        return w;
    }

    public <T> Future<T> submitInThread(Job<T> job) {
        return submitInThread(job, null);
    }

    private class Worker2<T> extends Worker<T> {
        private static final String TAG = "Worker2";

        public Worker2(Job<T> job, FutureListener<T> listener) {
            super(job, listener);
        }

        @Override
        public void run() {
            Log.d(TAG, "run B.");
            T result = null;
            try {
                result = mJob.run(this);
            } catch (Throwable ex) {
                Log.e(TAG, "Exception in running a job", ex);
            }
            Log.d(TAG, "Job run end.");
            synchronized (this) {
                mResult = result;
                mIsDone = true;
                notifyAll();
            }

            if (mListener != null) {
                mListener.onFutureDone(this);
            }
            Log.d(TAG, "run E.");
        }
    }
}

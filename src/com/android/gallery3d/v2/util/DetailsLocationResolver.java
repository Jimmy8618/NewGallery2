package com.android.gallery3d.v2.util;

import android.app.Activity;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;

import java.io.InputStream;

public class DetailsLocationResolver {
    private static final String TAG = DetailsLocationResolver.class.getSimpleName();

    private Activity mContext;
    private Handler mHandler;

    private LocationResolvingListener mListener;
    private Future<float[]> mLocationLookupJob;

    private class LocationLookupJob implements ThreadPool.Job<float[]> {
        private Uri mUri;
        private boolean mIsImage;

        LocationLookupJob(Uri uri, boolean isImage) {
            mUri = uri;
            mIsImage = isImage;
        }

        @Override
        public float[] run(ThreadPool.JobContext jc) {
            float[] loc = new float[2];
            loc[0] = 0;
            loc[1] = 0;

            if (mUri == null) {
                return loc;
            }

            Uri uri = mUri.buildUpon().appendQueryParameter("requireOriginal", "1").build();
            InputStream is = null;
            ParcelFileDescriptor pdf = null;
            MediaMetadataRetriever mmr = null;
            try {
                Log.d(TAG, "LocationLookupJob B uri = " + uri);
                if (mIsImage) {
                    is = mContext.getContentResolver().openInputStream(uri);
                    ExifInterface exif = new ExifInterface(is);
                    exif.getLatLong(loc);
                } else {
                    // UNISOC added for bug 1174952, parsing video location from video files
                    pdf = mContext.getContentResolver().openFile(uri, "r", null);
                    mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(pdf.getFileDescriptor());
                    String videoLocation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                    if (videoLocation != null) {
                        int index = videoLocation.lastIndexOf('-');
                        if (index == -1) {
                            index = videoLocation.lastIndexOf('+');
                        }
                        loc[0] = Float.parseFloat(videoLocation.substring(0, index));
                        loc[1] = Float.parseFloat(videoLocation.substring(index, videoLocation.length() - 1));
                    }
                }
                Log.d(TAG, "LocationLookupJob E latitude = " + loc[0] + ", longitude = " + loc[1]);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.closeSilently(is);
                Utils.closeSilently(pdf);
                if (mmr != null) {
                    mmr.close();
                }
            }

            return loc;
        }
    }

    public DetailsLocationResolver(Activity context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public interface LocationResolvingListener {
        void onLocationAvailable(double latitude, double longitude);
    }

    public void resolveLocation(Uri uri, LocationResolvingListener listener, boolean isImage) {
        mListener = listener;
        mLocationLookupJob = getThreadPool().submit(new LocationLookupJob(uri, isImage),
                new FutureListener<float[]>() {
                    @Override
                    public void onFutureDone(final Future<float[]> future) {
                        mLocationLookupJob = null;
                        if (!future.isCancelled()) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    float[] loc = future.get();
                                    if (loc == null || loc.length != 2 || mListener == null) {
                                        return;
                                    }
                                    mListener.onLocationAvailable(loc[0], loc[1]);
                                }
                            });
                        }
                    }
                });
    }

    public void cancel() {
        if (mLocationLookupJob != null) {
            mLocationLookupJob.cancel();
            mLocationLookupJob = null;
        }
    }

    private ThreadPool getThreadPool() {
        return ((GalleryApp) mContext.getApplication()).getThreadPool();
    }
}

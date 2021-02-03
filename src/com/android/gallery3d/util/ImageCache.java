package com.android.gallery3d.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;

/**
 * Created by apuser on 3/15/17.
 */

public class ImageCache {
    private static final String TAG = ImageCache.class.getSimpleName();
    private static ImageCache imageCache;
    private static final int CACHE_SIZE = 20;
    //private HashMap<Integer, BitmapDrawable> mRestoredCachesMap;
    private SparseArray<BitmapDrawable> mRestoredCachesMap;
    //private HashMap<Integer, BitmapDrawable> mSaveCachesMap;
    private SparseArray<BitmapDrawable> mSaveCachesMap;
    private Context mContext;

    private BitmapBean mCachedGlideBitmap;
    private boolean mIsScrollUp;

    private SaveTask mSaveTask;
    private RestoreTask mRestoreTask;

    private ImageCache(Context context) {
        mRestoredCachesMap = new SparseArray<BitmapDrawable>(CACHE_SIZE);//new HashMap<Integer, BitmapDrawable>(CACHE_SIZE);
        mSaveCachesMap = new SparseArray<BitmapDrawable>(CACHE_SIZE);//new HashMap<Integer, BitmapDrawable>(CACHE_SIZE);
        mContext = context;
    }

    public static ImageCache getImageCache() {
        if (imageCache == null) {
            return null;
        }
        return imageCache;
    }

    public static ImageCache getDefault(Context context) {
        if (imageCache == null) {
            imageCache = new ImageCache(context);
        }
        return imageCache;
    }

    private static class BitmapBean {
        String path;
        Bitmap bitmap;

        public BitmapBean(String path, Bitmap bitmap) {
            this.path = path;
            this.bitmap = bitmap;
        }
    }

    public void setScrollUp(boolean isScrollUp) {
        mIsScrollUp = isScrollUp;
    }

    public void saveGlideBitmap(String path, Bitmap bitmap) {
        if (path != null) {
            mCachedGlideBitmap = new BitmapBean(path, bitmap);
        }
    }

    public Bitmap getGlideBitmap(String path) {
        if (mCachedGlideBitmap != null && mCachedGlideBitmap.path.equals(path)) {
            return mCachedGlideBitmap.bitmap;
        }
        return null;
    }

    public void add(int key, Bitmap bitmap) {
        if (mSaveCachesMap.size() >= CACHE_SIZE) {
            return;
        }
        mSaveCachesMap.put(key, new BitmapDrawable(mContext.getResources(), bitmap));
    }

    public Drawable get(int key) {
        return mRestoredCachesMap.get(key);
    }

    public void commit() {
        AsyncTask.Status status = AsyncTask.Status.FINISHED;
        if (mSaveTask != null) {
            status = mSaveTask.getStatus();
        }
        if (mSaveTask == null || (mSaveTask != null && status == AsyncTask.Status.FINISHED)) {
            mSaveTask = new SaveTask(mContext, mSaveCachesMap, mRestoredCachesMap);
            mSaveTask.execute();
        }
    }

    public void restore() {
        AsyncTask.Status status = AsyncTask.Status.FINISHED;
        if (mRestoreTask != null) {
            status = mRestoreTask.getStatus();
        }
        if (mRestoreTask == null || (mRestoreTask != null && status == AsyncTask.Status.FINISHED)) {
            mRestoreTask = new RestoreTask(mContext, mRestoredCachesMap);
            mRestoreTask.execute();
        }
    }

    private static class RestoreTask extends AsyncTask<Void, Void, Void> {
        private SparseArray<BitmapDrawable> mCachesMap;
        private Context mContext;

        public RestoreTask(Context context, SparseArray<BitmapDrawable> cachesMap) {
            mCachesMap = cachesMap;
            mContext = context;
        }


        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "RestoreTask B");
            File cacheDir = getCacheDir();
            if (cacheDir == null) {
                return null;
            }
            if (!cacheDir.isDirectory()) {
                return null;
            }
            File[] files = cacheDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile() && file.getAbsolutePath().endsWith(".jpg");
                }
            });
            for (File cacheFile : files) {
                mCachesMap.put(lastName(cacheFile.getAbsolutePath()), drawableWithFile(cacheFile));
            }
            Log.d(TAG, "RestoreTask E");
            return null;
        }

        private BitmapDrawable drawableWithFile(File file) {
            return new BitmapDrawable(mContext.getResources(), file.getAbsolutePath());
        }

        private File getCacheDir() {
            return mContext.getExternalCacheDir();
        }
    }

    private static class SaveTask extends AsyncTask<Void, Void, Void> {
        private SparseArray<BitmapDrawable> _saveCachesMap;
        private SparseArray<BitmapDrawable> _restoredCachesMap;
        private Context mContext;

        public SaveTask(Context context, SparseArray<BitmapDrawable> saveCachesMap, SparseArray<BitmapDrawable> restoredCachesMap) {
            _saveCachesMap = saveCachesMap;
            _restoredCachesMap = restoredCachesMap;
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "SaveTask B");
//            for (int key : _restoredCachesMap.keySet()) {
//                delete(key);
//            }
            for (int i = 0; i < _restoredCachesMap.size(); i++) {
                int key = _restoredCachesMap.keyAt(i);
                delete(key);
            }
            Log.d(TAG, "SaveTask D");
//            for (int key : _saveCachesMap.keySet()) {
//                BitmapDrawable drawable = _saveCachesMap.get(key);
//                save(key, drawable);
//            }
            for (int i = 0; i < _saveCachesMap.size(); i++) {
                int key = _saveCachesMap.keyAt(i);
                BitmapDrawable drawable = _saveCachesMap.get(key);
                save(key, drawable);
            }
            Log.d(TAG, "SaveTask E");
            return null;
        }

        private void delete(int key) {
            try {
                File file = new File(getCachePath(key));
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                Log.d(TAG, "delete Exception " + e.toString());
            }
        }

        private void save(int key, BitmapDrawable drawable) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(getCachePath(key));
                drawable.getBitmap().compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
            } catch (Exception e) {
                Log.d(TAG, "save Exception " + e.toString());
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (Exception r) {
                    Log.d(TAG, "save close Exception " + r.toString());
                }
            }
        }

        private String getCachePath(int key) {
            return mContext.getExternalCacheDir().getAbsolutePath() + "/" + key + ".jpg";
        }
    }

    private static int lastName(String key) {
        int a = key.lastIndexOf("/");
        int b = key.lastIndexOf(".");
        String ret;
        if (a < b) {
            ret = key.substring(a + 1, b);
        } else {
            ret = key.substring(a + 1);
        }
        try {
            return Integer.parseInt(ret);
        } catch (Exception e) {
            return 0;
        }
    }

}

package com.android.gallery3d.v2.util;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.GalleryUtils;
import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class FileCopyTask extends AsyncTask<Void, Integer, Void> {
    private static final String TAG = FileCopyTask.class.getSimpleName();
    private static final long MIN_DURATION = 1000L;

    private String mDir;
    private List<String> mPaths;
    private final List<MediaItem> mCopyFiles;

    private ProgressDialog mDialog;

    private FileCopyTask(Context context, String dir, List<String> copyFiles) {
        this.mDir = dir;
        this.mPaths = copyFiles;
        this.mCopyFiles = new ArrayList<>();

        this.mDialog = new ProgressDialog(context);
        this.mDialog.setCancelable(false);
        this.mDialog.setIndeterminate(true);
        this.mDialog.setMax(this.mPaths.size());
        this.mDialog.setTitle(R.string.add_to_album);
        this.mDialog.setMessage(context.getString(R.string.please_wait));
        //this.mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    }

    public void start() {
        if (this.getStatus() == Status.PENDING) {
            Log.d(TAG, "start");
            this.execute();
        }
    }

    public void stop() {
        if (this.getStatus() == Status.RUNNING) {
            Log.d(TAG, "stop");
            this.cancel(true);
        }
    }

    @Override
    protected void onPreExecute() {
        Log.d(TAG, "onPreExecute");
        this.mDialog.show();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Log.d(TAG, "doInBackground");
        DataManager dataManager = GalleryAppImpl.getApplication().getDataManager();
        for (String path : mPaths) {
            MediaItem mediaItem = (MediaItem) dataManager.getMediaObject(path);
            if (mediaItem != null) {
                this.mCopyFiles.add(mediaItem);
            }
        }
        Log.d(TAG, "mCopyFiles size :" + mCopyFiles.size());
        int index = 0;
        long beginTime = System.currentTimeMillis();
        for (MediaItem item : this.mCopyFiles) {
            if (isCancelled()) {
                break;
            }
            publishProgress(++index);
            copy(item, mDir);
        }
        long cost = System.currentTimeMillis() - beginTime;
        if (cost < MIN_DURATION) {
            try {
                Thread.sleep(MIN_DURATION - cost);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void copy(MediaItem item, String dir) {
        Log.d(TAG, "copy " + item.getFilePath() + " to " + dir);
        String fromFilePath = item.getFilePath();
        String name = fromFilePath.substring(fromFilePath.lastIndexOf("/") + 1);
        int indexOfPoint = name.lastIndexOf(".");
        String pName = name.substring(0, indexOfPoint == -1 ? name.length() : indexOfPoint);
        String sName = indexOfPoint == -1 ? "" : name.substring(indexOfPoint);
        String newName = pName + sName;
        int copy = 1;
        while (new File(dir + "/" + newName).exists()) {
            newName = pName + "-" + (copy++) + sName;
        }
        String toFilePath = dir + "/" + newName;
        if (android.os.Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            runCopy(item, new File(toFilePath));
        } else {
            runCopy(item, toFilePath);
        }
    }

    private void runCopy(MediaItem item, String toFile) {
        if (!copyFileOnly(new File(item.getFilePath()), new File(toFile))) {
            return;
        }

        long timeMs = System.currentTimeMillis();
        String displayName = toFile.substring(toFile.lastIndexOf("/") + 1);
        int indexOfPoint = displayName.lastIndexOf(".");
        String title = displayName.substring(0, indexOfPoint == -1 ? displayName.length() : indexOfPoint);
        String dirName = mDir.substring(mDir.lastIndexOf("/") + 1);

        if (item instanceof LocalImage) {
            ContentValues values = new ContentValues();
            //_data
            values.put(MediaStore.Images.ImageColumns.DATA, toFile);
            //_size
            values.put(MediaStore.Images.ImageColumns.SIZE, ((LocalImage) item).fileSize);
            //_display_name
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, displayName);
            //mime_type
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE, ((LocalImage) item).mimeType);
            //title
            values.put(MediaStore.Images.ImageColumns.TITLE, title);
            //date_added
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, ((LocalImage) item).dateAddedInSec);
            //date_modified
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, timeMs / 1000);
            //latitude
            values.put(MediaStore.Images.ImageColumns.LATITUDE, ((LocalImage) item).latitude);
            //longitude
            values.put(MediaStore.Images.ImageColumns.LONGITUDE, ((LocalImage) item).longitude);
            //datetaken
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, ((LocalImage) item).dateTakenInMs);
            //orientation
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, ((LocalImage) item).rotation);
            //bucket_id
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, GalleryUtils.getBucketId(mDir));
            //bucket_display_name
            values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, dirName);
            //width
            values.put(MediaStore.Images.ImageColumns.WIDTH, ((LocalImage) item).width);
            //height
            values.put(MediaStore.Images.ImageColumns.HEIGHT, ((LocalImage) item).height);
            //is_drm
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalImage) item).isDrm);
            }
            //file_flag
            if (StandardFrameworks.getInstances().isSupportFileFlag()) {
                values.put(MediaItem.COLUMN_FILE_FLAG, ((LocalImage) item).mFileFlag);
            }

            GalleryAppImpl.getApplication().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else if (item instanceof LocalVideo) {
            ContentValues values = new ContentValues();
            //_data
            values.put(MediaStore.Video.VideoColumns.DATA, toFile);
            //_display_name
            values.put(MediaStore.Video.VideoColumns.DISPLAY_NAME, displayName);
            //title
            values.put(MediaStore.Video.VideoColumns.TITLE, title);
            //_size
            values.put(MediaStore.Video.VideoColumns.SIZE, ((LocalVideo) item).fileSize);
            //mime_type
            values.put(MediaStore.Video.VideoColumns.MIME_TYPE, ((LocalVideo) item).mimeType);
            //date_added
            values.put(MediaStore.Video.VideoColumns.DATE_ADDED, ((LocalVideo) item).dateAddedInSec);
            //date_modified
            values.put(MediaStore.Video.VideoColumns.DATE_MODIFIED, timeMs / 1000);
            //duration
            values.put(MediaStore.Video.VideoColumns.DURATION, ((LocalVideo) item).durationInMs);
            //resolution
            values.put(MediaStore.Video.VideoColumns.RESOLUTION, ((LocalVideo) item).width + "x" + ((LocalVideo) item).height);
            //latitude
            values.put(MediaStore.Video.VideoColumns.LATITUDE, ((LocalVideo) item).latitude);
            //longitude
            values.put(MediaStore.Video.VideoColumns.LONGITUDE, ((LocalVideo) item).longitude);
            //datetaken
            values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, ((LocalVideo) item).dateTakenInMs);
            //width
            values.put(MediaStore.Video.VideoColumns.WIDTH, ((LocalVideo) item).width);
            //height
            values.put(MediaStore.Video.VideoColumns.HEIGHT, ((LocalVideo) item).height);
            //bucket_id
            values.put(MediaStore.Video.VideoColumns.BUCKET_ID, GalleryUtils.getBucketId(mDir));
            //album
            values.put(MediaStore.Video.VideoColumns.ALBUM, dirName);
            //bucket_display_name
            values.put(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME, dirName);
            //is_drm
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalVideo) item).isDrm);
            }

            GalleryAppImpl.getApplication().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        }
    }

    private boolean copyFileOnly(File src, File dst) {
        boolean success = true;
        FileInputStream in = null;
        FileOutputStream out = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;

        try {
            in = new FileInputStream(src);
            if (GalleryStorageUtil.isInInternalStorage(dst.getAbsolutePath())) {
                out = new FileOutputStream(dst);
            } else {
                //创建文件
                SdCardPermission.mkFile(dst);
                out = (FileOutputStream) SdCardPermission.createExternalOutputStream(dst.getAbsolutePath());
            }
            inChannel = in.getChannel();
            outChannel = out.getChannel();
            //拷贝文件
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            success = false;
            Log.e(TAG, "copyFileOnly failed", e);
        } finally {
            Utils.closeSilently(in);
            Utils.closeSilently(out);
            Utils.closeSilently(inChannel);
            Utils.closeSilently(outChannel);
        }

        if (!success && dst.exists()) {
            if (GalleryStorageUtil.isInInternalStorage(dst.getAbsolutePath())) {
                dst.delete();
            } else {
                SdCardPermission.deleteFile(dst.getAbsolutePath());
            }
        }

        return success;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        this.mDialog.setProgress(values[0]);
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Log.d(TAG, "onPostExecute");
        this.mDialog.dismiss();
    }

    public static class Build {
        private Context context;
        private List<String> filePaths;
        private String dir;

        public Build(Context context) {
            this.context = context;
            this.filePaths = new ArrayList<>();
        }

        public Build setDir(String dir) {
            this.dir = dir;
            return this;
        }

        public Build setCopyFiles(List<String> files) {
            this.filePaths.addAll(files);
            return this;
        }

        public FileCopyTask create() {
            return new FileCopyTask(this.context, this.dir, this.filePaths);
        }
    }

    private void runCopy(MediaItem item, File toFile) {
        String parent = toFile.getParent();
        String storage = Utils.getStorageDirectory(toFile.getAbsolutePath());
        String relativePath = parent.substring(parent.indexOf(storage) + storage.length() + 1);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, toFile.getName());
        values.put(MediaStore.Files.FileColumns.TITLE, toFile.getName());
        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, item.getMimeType());

        String volume = Utils.getMediaVolumeName(GalleryAppImpl.getApplication().getAndroidContext(), toFile.getAbsolutePath());
        Log.d(TAG, "runCopy toFile = " + toFile + ", volume = " + volume);

        Uri c;
        Uri retUri;
        if (item instanceof LocalImage) {
            c = MediaStore.Images.Media.getContentUri(volume);
        } else {
            c = MediaStore.Video.Media.getContentUri(volume);
        }
        retUri = GalleryAppImpl.getApplication().getContentResolver().insert(c, values);

        FileInputStream in = null;
        FileOutputStream out = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;

        try {
            in = (FileInputStream) GalleryAppImpl.getApplication().getContentResolver().openInputStream(item.getContentUri());
            out = (FileOutputStream) GalleryAppImpl.getApplication().getContentResolver().openOutputStream(retUri);
            inChannel = in.getChannel();
            outChannel = out.getChannel();
            //拷贝文件
            inChannel.transferTo(0, inChannel.size(), outChannel);
            Log.d(TAG, "runCopy success.");
            updateContent(item, retUri, toFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "runCopy error", e);
            if (retUri != null) {
                GalleryAppImpl.getApplication().getContentResolver().delete(retUri, null, null);
            }
        } finally {
            Utils.closeSilently(in);
            Utils.closeSilently(out);
            Utils.closeSilently(inChannel);
            Utils.closeSilently(outChannel);
        }
    }

    private void updateContent(MediaItem src, Uri dst, String _data) {
        long timeMs = System.currentTimeMillis();
        if (src instanceof LocalImage) {
            ContentValues values = new ContentValues();
            //_data
            values.put(MediaStore.Images.ImageColumns.DATA, _data);
            //_size
            values.put(MediaStore.Images.ImageColumns.SIZE, ((LocalImage) src).fileSize);
            //date_added
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, ((LocalImage) src).dateAddedInSec);
            //date_modified
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, timeMs / 1000);
            //latitude
            values.put(MediaStore.Images.ImageColumns.LATITUDE, ((LocalImage) src).latitude);
            //longitude
            values.put(MediaStore.Images.ImageColumns.LONGITUDE, ((LocalImage) src).longitude);
            //datetaken
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, ((LocalImage) src).dateTakenInMs);
            //orientation
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, ((LocalImage) src).rotation);
            //bucket_id
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, GalleryUtils.getBucketId(mDir));
            //width
            values.put(MediaStore.Images.ImageColumns.WIDTH, ((LocalImage) src).width);
            //height
            values.put(MediaStore.Images.ImageColumns.HEIGHT, ((LocalImage) src).height);
            //is_drm
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalImage) src).isDrm);
            }
            //file_flag
            if (StandardFrameworks.getInstances().isSupportFileFlag()) {
                values.put(MediaItem.COLUMN_FILE_FLAG, ((LocalImage) src).mFileFlag);
            }
            values.put(MediaStore.Images.ImageColumns.IS_PENDING, 0);

            GalleryAppImpl.getApplication().getContentResolver().update(dst, values, null, null);
        } else if (src instanceof LocalVideo) {
            ContentValues values = new ContentValues();
            //_data
            values.put(MediaStore.Video.VideoColumns.DATA, _data);
            //_size
            values.put(MediaStore.Video.VideoColumns.SIZE, ((LocalVideo) src).fileSize);
            //date_added
            values.put(MediaStore.Video.VideoColumns.DATE_ADDED, ((LocalVideo) src).dateAddedInSec);
            //date_modified
            values.put(MediaStore.Video.VideoColumns.DATE_MODIFIED, timeMs / 1000);
            //duration
            values.put(MediaStore.Video.VideoColumns.DURATION, ((LocalVideo) src).durationInMs);
            //resolution
            values.put(MediaStore.Video.VideoColumns.RESOLUTION, ((LocalVideo) src).width + "x" + ((LocalVideo) src).height);
            //latitude
            values.put(MediaStore.Video.VideoColumns.LATITUDE, ((LocalVideo) src).latitude);
            //longitude
            values.put(MediaStore.Video.VideoColumns.LONGITUDE, ((LocalVideo) src).longitude);
            //datetaken
            values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, ((LocalVideo) src).dateTakenInMs);
            //width
            values.put(MediaStore.Video.VideoColumns.WIDTH, ((LocalVideo) src).width);
            //height
            values.put(MediaStore.Video.VideoColumns.HEIGHT, ((LocalVideo) src).height);
            //bucket_id
            values.put(MediaStore.Video.VideoColumns.BUCKET_ID, GalleryUtils.getBucketId(mDir));
            //is_drm
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalVideo) src).isDrm);
            }
            values.put(MediaStore.Video.VideoColumns.IS_PENDING, 0);

            GalleryAppImpl.getApplication().getContentResolver().update(dst, values, null, null);
        }
    }
}

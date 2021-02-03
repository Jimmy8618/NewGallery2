/**
 * Created By Spreadst
 */

package com.sprd.gallery3d.app;

import android.os.Environment;
import android.os.StatFs;

import com.android.gallery3d.app.GalleryAppImpl;
import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Storage access to information related to the class of functions Main fucntion: according to the
 * corresponding access storage shceme for different information
 */
public class StorageInfos {

    /**
     * judge the internal storage state
     *
     * @return is mounted or not
     */
    public static boolean isInternalStorageMounted() {
        String state = Environment.getExternalStorageState(
                StandardFrameworks.getInstances().getInternalStoragePath());
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * get the internal storage directory
     *
     * @return internal storage directory
     */
    public static File getInternalStorageDirectory() {
        return StandardFrameworks.getInstances().getInternalStoragePath();
    }

    /**
     * judge the External storage state
     *
     * @return is mounted or not
     */
    public static boolean isExternalStorageMounted() {
        String state = StandardFrameworks.getInstances().getExternalStorageState(GalleryAppImpl.getApplication());
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * get the External storage directory
     *
     * @return External storage directory
     */
    public static File getExternalStorageDirectory() {
        return StandardFrameworks.getInstances().getExternalStoragePath(GalleryAppImpl.getApplication());
    }

    /**
     * Get the external storage path wherever sdcard is exist, or not.
     *
     * @return external storage path
     */
    public static String getExternalStorageDir() {
        return getExternalStorageDirectory().getAbsolutePath();
    }

    /**
     * judge whether the scheme for NAND
     *
     * @return true or false
     */
    public static boolean isInternalStorageSupported() {
        boolean support = false;
        if ("1".equals(StandardFrameworks.getInstances().
                getStringSystemProperties("ro.device.support.nand", "0"))) {
            support = true;
        }
        return support;
    }

    /**
     * judge whether the path is in the external storage, or not
     *
     * @param path
     * @return
     */
    public static boolean isInExternalSDCard(String path) {
        if (path == null) {
            return false;
        }

        return StorageInfos.isExternalStorageMounted()
                && path.startsWith(StorageInfos.getExternalStorageDir());
    }

    public static boolean isPathExistAndCanWrite(String path) {
        if (path == null) {
            return false;
        }
        File filePath = new File(path);
        return filePath.exists() && filePath.canWrite();
    }

    /**
     * check the storage space is enough internal available space < 5% external availabel space <
     * 50K return false
     *
     * @return true is enough,or false
     */
    public static boolean haveEnoughStorage(String path) {
        boolean isEnough = true;
        boolean isExternalUsed = StorageInfos.isInExternalSDCard(path);

        File savePath = null;
        if (isExternalUsed) {
            savePath = StorageInfos.getExternalStorageDirectory();
        } else {
            savePath = StorageInfos.getInternalStorageDirectory();
        }
        if (savePath != null) {
            final StatFs stat = new StatFs(savePath.getPath());
            final long blockSize = stat.getBlockSize();
            final long availableBlocks = stat.getAvailableBlocks();
            long mAvailSize = availableBlocks * blockSize;
            isEnough = mAvailSize >= 50 * 1024;
        }
        return isEnough;
    }

    /**
     * check the storage space is enough internal available space < 5% external availabel space <
     * 50K return false
     *
     * @return true is enough,or false
     */
    public static Map<String, String> getStorageInfo(String path) {
        Map<String, String> map = null;
        boolean isEnough = true;
        boolean isExternalUsed = StorageInfos.isInExternalSDCard(path);

        File savePath = null;
        if (isExternalUsed) {
            savePath = StorageInfos.getExternalStorageDirectory();
        } else {
            savePath = StorageInfos.getInternalStorageDirectory();
        }
        if (savePath != null) {
            map = new HashMap<String, String>();
            final StatFs stat = new StatFs(savePath.getPath());
            final long blockSize = stat.getBlockSize();
            final long availableBlocks = stat.getAvailableBlocks();
            long mAvailSize = availableBlocks * blockSize;
            map.put("availableBlocks", "" + (mAvailSize - 50 * 1024));
            isEnough = mAvailSize >= 50 * 1024;
            map.put("isEnough", "" + isEnough);
        }
        return map;
    }
}

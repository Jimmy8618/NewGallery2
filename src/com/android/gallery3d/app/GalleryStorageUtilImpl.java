/*
 * Copyright (C) 2013 Spreadtrum Communications Inc.
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

import android.os.Environment;
import android.util.Log;

import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GalleryStorageUtilImpl implements IStorageUtil {

    public static final String TAG = "GalleryStorageUtilImpl";

    private static boolean MMC_SUPPORT;
    //private static final File EXTERNAL_STORAGE_DIRECTORY;
    //private static final File SECONDRARY_STORAGE_DIRECTORY;
    private static final File USB_STORAGE_DIRECTORY;
    private static boolean mIsNAND = false;

    static {
        MMC_SUPPORT = StandardFrameworks.getInstances().getBooleanFromSystemProperties(
                "ro.device.support.mmc", false);
        String path = System.getenv(getMainStoragePathKey());
        /*
        EXTERNAL_STORAGE_DIRECTORY = path == null ? Environment.getExternalStorageDirectory() : new File(path);
        File internalFile = null;
        try {
            Method method = Environment.class.getMethod("getInternalStoragePath");
            Object receiveObject = method.invoke(null);
            if (receiveObject != null && receiveObject instanceof File) {
                internalFile = (File) receiveObject;
            }
        } catch (Exception e) {
            Log.d(TAG, "getMethod failed call getInternalStoragePath method");
        }
        if (internalFile == null) {
            path = System.getenv(getInternalStoragePathKey());
            path = path == null ? "/mnt/internal/" : path;
            internalFile = new File(path);
        }
        SECONDRARY_STORAGE_DIRECTORY = internalFile;
        */
        USB_STORAGE_DIRECTORY = new File("/storage/usbdisk");
    }

    private static String getMainStoragePathKey() {
        // FIXME: Continue highlight at this one on 12b_pxx branch, there is
        // no SECONDARY_STORAGE_TYPE
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            Log.d(TAG, "version_code = " + Build.VERSION.SDK_INT);
//            return "SECONDARY_STORAGE";
//        }
        try {
            // add a protection to fix if no SECONDARY_STORAGE_TYPE
            if ((null == System.getenv("SECOND_STORAGE_TYPE") || ""
                    .equals(System.getenv("SECOND_STORAGE_TYPE").trim())) && MMC_SUPPORT) {
                Log.d(TAG, "No SECOND_STORAGE_TYPE and support emmc");
                return "SECONDARY_STORAGE";
            }
            switch (Integer.parseInt(System.getenv("SECOND_STORAGE_TYPE"))) {
                case 0:
                    mIsNAND = true;
                    return "EXTERNAL_STORAGE";
                case 1:
                    return "EXTERNAL_STORAGE";
                case 2:
                    return "SECONDARY_STORAGE";
                default:
                    Log.e(TAG, "Please check \"SECOND_STORAGE_TYPE\" "
                            + "\'S value after parse to int in System.getenv for framework");
                    if (MMC_SUPPORT) {
                        return "SECONDARY_SOTRAGE";
                    }
                    return "EXTERNAL_STORAGE";
            }
        } catch (Exception parseError) {
            //Log.e(TAG, "Parsing SECOND_STORAGE_TYPE crashed.\n");
            switch (StandardFrameworks.getInstances().getIntFromSystemProperties
                    ("persist.storage.type", -1)) {
                case 0:
                    mIsNAND = true;
                    return "EXTERNAL_STORAGE";
                case 1:
                    return "EXTERNAL_STORAGE";
                case 2:
                    return "SECONDARY_STORAGE";
                default:
                    if (MMC_SUPPORT) {
                        return "SECONDARY_SOTRAGE";
                    }
            }
            return "EXTERNAL_STORAGE";
        }
    }

    private static String getInternalStoragePathKey() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            return "EXTERNAL_STORAGE";
//        }
        String keyPath = getMainStoragePathKey();
        if (keyPath != null) {
            return keyPath.equals("EXTERNAL_STORAGE") ? "SECONDARY_STORAGE" : "EXTERNAL_STORAGE";
        }
        return "SECONDARY_STORAGE";
    }

    GalleryStorageUtilImpl() {
    }

    private final Object sLock = new Object();

    private List<StorageChangedListener> sListeners = new ArrayList<StorageChangedListener>();

    @Override
    public void addStorageChangeListener(StorageChangedListener scl) {
        synchronized (sLock) {
            sListeners.add(scl);
        }
    }

    @Override
    public void removeStorageChangeListener(StorageChangedListener scl) {
        synchronized (sLock) {
            sListeners.remove(scl);
        }
    }

    @Override
    public File getExternalStorage() {
        File external = StandardFrameworks.getInstances().getExternalStoragePath(GalleryAppImpl.getApplication());
        Log.d(TAG, "ExternalStoragePath is " + external);
        if (external == null) {
            return new File("/storage/sdcard0");
        }
        return external;
    }

    @Override
    public File getInternalStorage() {
        return StandardFrameworks.getInstances().getInternalStoragePath();
    }

    @Override
    public File getUSBStorage() {
        File[] usblist = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
        if (usblist.length == 0
                || usblist[0] == null) {
            return USB_STORAGE_DIRECTORY;
        }
        return usblist[0];
    }

    @Override
    public File[] getUSBVolume() {
        return StandardFrameworks.getInstances().getUsbdiskVolumePaths();
    }

    @Override
    public File getUSBStorage(int num) {
        File[] usblist = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
        if (usblist.length > num && num >= 0) {
            return usblist[num];
        }
        return null;
    }

    @Override
    public int getUSBCount() {
        return StandardFrameworks.getInstances().getUsbdiskVolumesCount();
    }

    @Override
    public boolean getInternalStorageState() {
        try {
            return isStorageMounted(getInternalStorage());
            /* @} */
        } catch (Exception rex) {
            return false;
        }
    }


    @Override
    public boolean getUSBStorageState() {
        File[] usbpath = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
        if (getUSBCount() != 0 && usbpath != null
                && usbpath.length != 0) {
            for (File f : usbpath) {
                if (f != null && isStorageMounted(f)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    @Override
    public boolean isInExternalStorage(String path) {
        if (path == null) {
            return false;
        }
        /* SPRD: Modify for showing the Internal Storage and External Storage. @{ */
        //return path.startsWith(EXTERNAL_STORAGE_DIRECTORY.getAbsolutePath());
        return path.startsWith(getExternalStorage().getAbsolutePath());
        /* @} */
    }

    @Override
    public boolean isInInternalStorage(String path) {
        if (path == null) {
            return false;
        }
        /* SPRD: Modify for showing the Internal Storage and External Storage. @{ */
        //return path.startsWith(SECONDRARY_STORAGE_DIRECTORY.getAbsolutePath());
        return path.startsWith(getInternalStorage().getAbsolutePath());
        /* @} */
    }

    @Override
    public boolean isInUSBStorage(String path) {
        if (path == null) {
            return false;
        }
        // SPRD: Add for bug 578914, 588855, 600321
        File[] usbpath = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
        if (usbpath.length == 0 || usbpath[0] == null) {
            return false;
        }
        return path.startsWith(usbpath[0].getAbsolutePath());
    }

    @Override
    public boolean isInUSBVolume(String path) {
        if (path == null) {
            return false;
        }
        return inWhichUSBStorage(path) != -1;
    }

    @Override
    public int inWhichUSBStorage(String path) {
        /* SPRD: Add for bug607469. @{ */
        if (path == null) {
            return -1;
        }
        /* @} */
        int mCount = getUSBCount();
        for (int i = 0; i < mCount; i++) {
            /* SPRD: Add for bug607469. @{ */
            File usbdiskVolumePath = StandardFrameworks.getInstances().getUsbdiskVolumePaths()[i];
            if (usbdiskVolumePath != null && path.startsWith(usbdiskVolumePath.getAbsolutePath())) {
                return i;
            }
            /* @} */
        }
        return -1;
    }

    /* SPRD: Modify for bug509242. @{ */
    @Override
    public void notifyStorageChanged(String path, String action) {
        synchronized (sLock) {
            for (StorageChangedListener l : sListeners) {
                l.onStorageChanged(path, action);
            }
        }
    }
    /* @} */

    /* SPRD: Modify for bug602265. @{ */
    @Override
    public boolean getExternalStorageState() {

        String state = StandardFrameworks.getInstances().getExternalStorageState(GalleryAppImpl.getApplication());
        Log.d(TAG, "getExternalStorageState(): state = " + state);
        return "mounted".equals(state);
    }
    /* @} */

    @Override
    public boolean isNand() {
        return mIsNAND;
    }

    /* SPRD: Add for showing the Internal Storage and External Storage. @{ */
    @Override
    public boolean isStorageMounted(File path) {
        String state = Environment.getExternalStorageState(path);
        return "mounted".equals(state);
    }
    /* @} */
}

/*
- * Copyright (C) 2013 Spreadtrum Communications Inc.
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

import com.android.gallery3d.app.IStorageUtil.StorageChangedListener;

import java.io.File;

/**
 * This is a interface, you can use this to set <b>
 * {@link StorageChangedListener}</b> to watch storage states, if you want to
 * get ExternalStorage or InternalStorage, using <b>
 */
public class GalleryStorageUtil {

    private static IStorageUtil sStorageUtil = new GalleryStorageUtilImpl();

    public static void addStorageChangeListener(StorageChangedListener scl) {
        sStorageUtil.addStorageChangeListener(scl);
    }

    public static void removeStorageChangeListener(StorageChangedListener scl) {
        sStorageUtil.removeStorageChangeListener(scl);
    }

    /* SPRD: Modify for bug509242. @{ */
    public static void notifyStorageChanged(String path, String action) {
        sStorageUtil.notifyStorageChanged(path, action);
    }

    /* @} */

    public static File getExternalStorage() {
        return sStorageUtil.getExternalStorage();
    }

    public static File getInternalStorage() {
        return sStorageUtil.getInternalStorage();
    }

    public static boolean getExternalStorageState() {
        return sStorageUtil.getExternalStorageState();
    }

    public static boolean getInternalStorageState() {
        return sStorageUtil.getInternalStorageState();
    }

    public static boolean getUSBStorageState() {
        return sStorageUtil.getUSBStorageState();
    }

    public static File getUSBStorage() {
        return sStorageUtil.getUSBStorage();
    }

    public static File[] getUSBVolume() {
        return sStorageUtil.getUSBVolume();
    }

    public static File getUSBStorage(int num) {
        return sStorageUtil.getUSBStorage(num);
    }

    public static int inWhichUSBStorage(String path) {
        return sStorageUtil.inWhichUSBStorage(path);
    }

    public static int getUSBCount() {
        return sStorageUtil.getUSBCount();
    }

    public static boolean isInExternalStorage(String path) {
        return sStorageUtil.isInExternalStorage(path);
    }

    public static boolean isInInternalStorage(String path) {
        return sStorageUtil.isInInternalStorage(path);
    }

    public static boolean isInUSBStorage(String path) {
        return sStorageUtil.isInUSBStorage(path);
    }

    public static boolean isInUSBVolume(String path) {
        return sStorageUtil.isInUSBVolume(path);
    }

    public static boolean isStorageMounted(File path) {
        return sStorageUtil.isStorageMounted(path);
    }
}

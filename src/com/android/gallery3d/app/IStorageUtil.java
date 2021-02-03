package com.android.gallery3d.app;

import java.io.File;

public interface IStorageUtil {

    /**
     * You can use this interface to watch the storage states, if the storage
     * changed, I'll tell you <b>which storage path</b> has changed and <b>if
     * available</b> currently using the call back named <b>
     * {@link StorageChangedListener#onStorageChanged(File, boolean)}</b>
     */
    interface StorageChangedListener {
        // SPRD: Modify for bug509242.
        void onStorageChanged(String path, String status);
    }

    void addStorageChangeListener(StorageChangedListener scl);

    void removeStorageChangeListener(StorageChangedListener scl);

    // SPRD: Modify for bug509242.
    void notifyStorageChanged(String path, String action);

    boolean isNand();

    File getExternalStorage();

    File getInternalStorage();

    File getUSBStorage();

    File[] getUSBVolume();

    File getUSBStorage(int num);

    int getUSBCount();

    int inWhichUSBStorage(String path);

    boolean getExternalStorageState();

    boolean getInternalStorageState();

    boolean getUSBStorageState();

    boolean isInExternalStorage(String path);

    boolean isInInternalStorage(String path);

    boolean isInUSBStorage(String path);

    boolean isInUSBVolume(String path);

    boolean isStorageMounted(File path);
}

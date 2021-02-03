package com.android.fw;

public interface StorageEventListener {
    void onVolumeStateChanged(String volumeName, String volumePath,
                              boolean is_sd, int state);
}

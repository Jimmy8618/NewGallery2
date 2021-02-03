package com.android.gallery3d.v2.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class SdCardPermission {
    private static final String TAG = SdCardPermission.class.getSimpleName();

    /**
     * request code
     */
    public static final int SDCARD_PERMISSION_REQUEST_CODE = 102;

    /**
     * 存储名字
     */
    private static final String STORAGE = "storage";

    /**
     * 存放存储盘路径
     */
    private static ArrayList<String> sStorageList = new ArrayList<>();

    /**
     * 获取没有权限的存储盘个数
     *
     * @return count
     */
    public static int getInvalidatePermissionStorageCount() {
        return sStorageList.size();
    }

    /**
     * 获取一个存储盘路径
     *
     * @param index index
     * @return path
     */
    public static String getInvalidatePermissionStoragePath(int index) {
        return sStorageList.get(index);
    }

    /**
     * 移除一个存储盘路径
     *
     * @param index index
     */
    public static void removeInvalidatePermissionStoragePath(int index) {
        sStorageList.remove(index);
    }

    /**
     * 是否有存储权限
     *
     * @param filePath 文件路径
     * @return true if has permission
     */
    public static boolean hasStoragePermission(String filePath) {
        return getAccessStorageUri(filePath) != null;
    }

    /**
     * 请求存储权限
     *
     * @param activity                 activity
     * @param storagePaths             文件路径列表
     * @param sdCardPermissionAccessor accessor
     * @param sdCardPermissionListener Listener
     */
    public static void requestSdcardPermission(final Activity activity,
                                               final ArrayList<String> storagePaths,
                                               final SdCardPermissionAccessor sdCardPermissionAccessor,
                                               final SdCardPermissionListener sdCardPermissionListener) {
        if (storagePaths.size() < 1) {
            throw new IllegalArgumentException("requestStoragePermission error, storage path size is : " + storagePaths.size());
        }
        sStorageList.clear();
        sStorageList.addAll(storagePaths);
        if (sdCardPermissionAccessor != null) {
            sdCardPermissionAccessor.setSdCardPermissionListener(sdCardPermissionListener);
        }
        new AlertDialog.Builder(activity).setCancelable(false)
                .setTitle(R.string.request_delete_permission_title)
                .setMessage(R.string.request_delete_permission_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent accessIntent = getAccessStorageIntent(sStorageList.get(0));
                        if (accessIntent == null) {
                            if (sdCardPermissionListener != null) {
                                sdCardPermissionListener.onSdCardPermissionDenied();
                            }
                        } else {
                            activity.startActivityForResult(accessIntent, SDCARD_PERMISSION_REQUEST_CODE);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (sdCardPermissionListener != null) {
                            sdCardPermissionListener.onSdCardPermissionDenied();
                        }
                    }
                })
                .create()
                .show();
    }

    /**
     * 显示没有权限提示框
     *
     * @param context  context
     * @param listener listener
     */
    public static void showSdcardPermissionErrorDialog(Context context, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context).setCancelable(false)
                .setTitle(R.string.no_delete_permission_title)
                .setMessage(R.string.no_delete_permission_msg)
                .setPositiveButton(R.string.ok, listener)
                .create()
                .show();
    }

    /**
     * 获取Storage权限 Intent
     *
     * @param filePath 文件路径
     * @return Intent
     */
    public static @Nullable
    Intent getAccessStorageIntent(String filePath) {
        Intent intent = null;
        StorageVolume storage = getStorageVolume(filePath);
        Log.d(TAG, "getAccessStorageIntent storage = " + storage);
        if (storage != null) {
            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                intent = storage.createOpenDocumentTreeIntent();
            } else {
                intent = storage.createAccessIntent(null);
            }
            Log.d(TAG, "getAccessStorageIntent intent = " + intent);
        }
        return intent;
    }

    /**
     * 根据文件路径获得 Storage Permission Uri
     *
     * @param filePath 文件路径
     * @return uri
     */
    public static @Nullable
    Uri getAccessStorageUri(@NonNull String filePath) {
        Uri uri = null;
        String savedStorage = getSavedStorageUriPermission(filePath);
        List<UriPermission> uriPermissionList = GalleryAppImpl.getApplication()
                .getContentResolver().getPersistedUriPermissions();
        for (UriPermission uriPermission : uriPermissionList) {
            String uriPath = uriPermission.getUri().toString();
            if ((uriPath.substring(uriPath.lastIndexOf("/") + 1)).equals(savedStorage)) {
                uri = uriPermission.getUri();
                break;
            }
        }
        return uri;
    }

    /**
     * 创建外部存储输入流
     *
     * @param filePath 文件路径
     * @return InputStream
     */
    public static @Nullable
    InputStream createExternalInputStream(String filePath) {
        FileInputStream fileInputStream = null;
        ParcelFileDescriptor fd = createExternalFileDescriptor(filePath, "r");
        if (fd != null) {
            fileInputStream = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        }
        return fileInputStream;
    }

    /**
     * 创建外部存储输出流
     *
     * @param filePath 文件路径
     * @return OutputStream
     */
    public static @Nullable
    OutputStream createExternalOutputStream(String filePath) {
        FileOutputStream fileOutputStream = null;
        ParcelFileDescriptor fd = createExternalFileDescriptor(filePath, "w");
        if (fd != null) {
            fileOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
        }
        return fileOutputStream;
    }

    /**
     * 创建外部存储 ParcelFileDescriptor
     *
     * @param filePath 文件路径
     * @param mode     r, w, rw
     * @return ParcelFileDescriptor
     */
    public static @Nullable
    ParcelFileDescriptor createExternalFileDescriptor(String filePath, String mode) {
        ParcelFileDescriptor fd = null;
        try {
            Uri uri = getAccessStorageUri(filePath);
            String treeDocumentId = DocumentsContract.getTreeDocumentId(uri);
            String[] s = treeDocumentId.split(":");
            String relativePath = filePath.substring(filePath.indexOf(s[0]) + s[0].length());
            String documentId = treeDocumentId.concat(relativePath);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);
            fd = GalleryAppImpl.getApplication().getContentResolver().openFileDescriptor(doc, mode);
            Log.d(TAG, "createExternalFileDescriptor filePath = " + filePath + ", mode = " + mode + ", fd = " + fd + ", docUri = " + doc);
        } catch (Exception e) {
            Log.e(TAG, "createExternalFileDescriptor error :", e);
        }
        return fd;
    }

    /**
     * 根据文件路径获得到Storage路径
     *
     * @param filePath 文件路径
     * @return Storage路径
     */
    public static @Nullable
    String getStorageName(@NonNull String filePath) {
        String[] sp = filePath.split("/", 6);
        int index = -1;
        for (int i = 0; i < sp.length; i++) {
            if (STORAGE.equalsIgnoreCase(sp[i])) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return null;
        }
        return "/" + sp[index] + "/" + sp[index + 1];
    }

    /**
     * 根据文件路径获得到Storage Permission Uri
     *
     * @param filePath 文件路径
     * @return uri
     */
    private static @Nullable
    String getSavedStorageUriPermission(@NonNull String filePath) {
        String key = getStorageName(filePath);
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        String uri = Config.getPref(key, "");
        if (TextUtils.isEmpty(uri)) {
            return null;
        }
        return uri;
    }

    /**
     * 保存Storage Permission
     *
     * @param filePath      文件路径
     * @param uriPermission uri
     */
    public static void saveStorageUriPermission(@NonNull String filePath, @NonNull String uriPermission) {
        String key = getStorageName(filePath);
        if (!TextUtils.isEmpty(key)) {
            Config.setPref(key, uriPermission.substring(uriPermission.lastIndexOf("/") + 1));
        }
    }

    /**
     * 获取所有的存储设备
     *
     * @return 存储设备 列表
     */
    private static List<StorageVolume> getVolumes() {
        StorageManager sm = (StorageManager) GalleryAppImpl.getApplication().getSystemService(Context.STORAGE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return sm != null ? sm.getStorageVolumes() : new ArrayList<StorageVolume>();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 判断是否是主存储
     *
     * @param volume volume
     * @return true 主存储
     */
    private static boolean isPrimaryVolume(StorageVolume volume) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && volume.isPrimary();
    }

    /**
     * 根据文件路径获得存储设备 StorageVolume
     *
     * @param filePath 文件路径
     * @return StorageVolume
     */
    private static @Nullable
    StorageVolume getStorageVolume(String filePath) {
        StorageVolume retVal = null;
        List<StorageVolume> volumeList = getVolumes();
        for (StorageVolume volume : volumeList) {
            File volumePath = StandardFrameworks.getInstances().getVolumePathFile(volume);
            if (!isPrimaryVolume(volume) && volumePath != null
                    && Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED)
                    && volumePath.getAbsolutePath().equals(getStorageName(filePath))) {
                retVal = volume;
                break;
            }
        }
        return retVal;
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return true if success
     */
    public static boolean mkdir(@NonNull String dir) {
        return mkdir(new File(dir));
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return true if success
     */
    public static boolean mkdir(@NonNull File dir) {
        try {
            Uri storageUri = getAccessStorageUri(dir.getParent());
            String treeDocumentId = DocumentsContract.getTreeDocumentId(storageUri);
            String[] s = treeDocumentId.split(":");
            String relativePath = dir.getParent().substring(dir.getParent().indexOf(s[0]) + s[0].length());
            String documentId = treeDocumentId.concat(relativePath);
            Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(storageUri, documentId);
            DocumentsContract.createDocument(GalleryAppImpl.getApplication().getContentResolver(),
                    parentDocumentUri, DocumentsContract.Document.MIME_TYPE_DIR, dir.getName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return true if create success
     */
    public static boolean mkdirs(@NonNull String dir) {
        return mkdirs(new File(dir));
    }

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return true if create success
     */
    public static boolean mkdirs(@NonNull File dir) {
        if (dir.exists()) {
            return false;
        }

        if (mkdir(dir)) {
            return true;
        }
        return (mkdirs(dir.getParentFile()) || dir.getParentFile().exists()) && mkdir(dir);
    }

    /**
     * 创建新文件
     *
     * @param file 文件
     * @return true if create success
     */
    public static boolean mkFile(@NonNull String file) {
        return mkFile(new File(file));
    }

    /**
     * 创建新文件
     *
     * @param file 文件
     * @return true if create success
     */
    public static boolean mkFile(@NonNull File file) {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            mkdirs(parent);
        }

        if (!parent.exists()) {
            return false;
        }

        if (file.exists()) {
            Log.d(TAG, "mkFile file exists.");
            return false;
        }

        try {
            Uri storageUri = getAccessStorageUri(parent.getAbsolutePath());
            String treeDocumentId = DocumentsContract.getTreeDocumentId(storageUri);
            String[] s = treeDocumentId.split(":");
            String relativePath = parent.getAbsolutePath().substring(parent.getAbsolutePath().indexOf(s[0]) + s[0].length());
            String documentId = treeDocumentId.concat(relativePath);
            Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(storageUri, documentId);
            DocumentsContract.createDocument(GalleryAppImpl.getApplication().getContentResolver(),
                    parentDocumentUri, null, file.getName());
            Log.d(TAG, "mkFile success -> " + file);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "mkFile error -> " + file, e);
            return false;
        }
    }

    /**
     * 删除文件
     *
     * @param file 文件
     * @return true if delete success
     */
    public static boolean deleteFile(@NonNull String file) {
        try {
            Uri storageUri = getAccessStorageUri(file);
            String treeDocumentId = DocumentsContract.getTreeDocumentId(storageUri);
            String[] s = treeDocumentId.split(":");
            String relativePath = file.substring(file.indexOf(s[0]) + s[0].length());
            String documentId = treeDocumentId.concat(relativePath);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(storageUri, documentId);
            return DocumentsContract.deleteDocument(GalleryAppImpl.getApplication().getContentResolver(), doc);
        } catch (Exception e) {
            Log.e(TAG, "deleteFile error -> " + file, e);
            return false;
        }
    }
}

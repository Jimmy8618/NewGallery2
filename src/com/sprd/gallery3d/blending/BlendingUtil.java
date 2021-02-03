package com.sprd.gallery3d.blending;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BlendingUtil {


    private static final String TAG = "BlendingUtil";


    static {
        System.loadLibrary("sprdjni_filtershow_filters2");
    }


    public static byte[] readStream(InputStream inStream) {
        byte[] buffer = new byte[4096];//4m
        int len = -1;
        ByteArrayOutputStream outStream = null;
        try {
            outStream = new ByteArrayOutputStream();
            while ((len = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            byte[] data = outStream.toByteArray();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                outStream.close();
                inStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void dumpPoints(int[] x, int[] y, String path, String filename) {
        File filepath = new File(path);
        if (!filepath.exists()) {
            filepath.mkdirs();
        }
        File points1 = new File(filepath, filename);
        if (points1.exists()) {
            boolean delete = points1.delete();
            if (delete) {
                Log.d(TAG, "blendXandYcorrdinate: old points");
            }
        }
        Log.d(TAG, "blendXandYcorrdinate: points" + points1);
        FileOutputStream fileOutputStream = null;
        try {
            points1.createNewFile();
            fileOutputStream = new FileOutputStream(points1);
            for (int i = 0; i < x.length; i++) {
                fileOutputStream.write((x[i] + "-" + y[i]).getBytes());
                fileOutputStream.write("\r\n".getBytes());
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(fileOutputStream);
        }
    }

    public static File writeToLocalFile(Bitmap bitmap, String name, String path) {
        Log.d(TAG, "writeToLocalFile: path=" + path + ", name=" + name);
        if (TextUtils.isEmpty(name) || bitmap == null) {
            return null;
        }
        OutputStream fileOutputStream = null;
        File file;
        if (GalleryStorageUtil.isInInternalStorage(path)) {
            File filepath = new File(path);
            if (!filepath.exists()) {
                filepath.mkdirs();
            }
            file = new File(filepath, name);
            if (file.exists()) {
                file.delete();
            }
            try {
                if (file.createNewFile()) {
                    fileOutputStream = new FileOutputStream(file);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            file = new File(path, name);
            if (SdCardPermission.mkFile(file)) {
                fileOutputStream = SdCardPermission.createExternalOutputStream(file.getAbsolutePath());
            }
        }

        if (fileOutputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
                fileOutputStream.flush();
                fileOutputStream.close();
                Log.d(TAG, "write new file:" + name + " to " + path);
                fileOutputStream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    public static void writeByteData(byte[] bytes, String name, String path) {
        if (bytes == null || bytes.length == 0) {
            Log.d(TAG, "writeByteData: bytes is null");
            return;
        }
        FileOutputStream depth = null;
        try {
            File filepath = new File(path);
            if (!filepath.exists()) {
                filepath.mkdirs();
            }
            File data = new File(filepath, name);
            if (data.exists()) {
                data.delete();
            }
            data.createNewFile();
            depth = new FileOutputStream(data);
            depth.write(bytes);
            Log.d(TAG, "write Byte Data " + name + " to -->:" + data);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "writeByteData: FileNotFoundException ");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "writeByteData: IOException");
            e.printStackTrace();
        } finally {
            Utils.closeSilently(depth);
        }
    }

    public static void dumpBitmapAsBMPForDebug(Bitmap bitmap, String name, String path) {
        if (bitmap == null) {
            return;
        }
        int nBmpWidth = bitmap.getWidth();
        int nBmpHeight = bitmap.getHeight();
        int bufferSize = nBmpHeight * (nBmpWidth * 3 + nBmpWidth % 4);
        try {

            File filepath = new File(path);
            if (!filepath.exists()) {
                filepath.mkdirs();
            }
            File file = new File(filepath, name);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream fileos = new FileOutputStream(file);
            int bfType = 0x4d42;
            long bfSize = 14 + 40 + bufferSize;
            int bfReserved1 = 0;
            int bfReserved2 = 0;
            long bfOffBits = 14 + 40;
            writeWord(fileos, bfType);
            writeDword(fileos, bfSize);
            writeWord(fileos, bfReserved1);
            writeWord(fileos, bfReserved2);
            writeDword(fileos, bfOffBits);
            long biSize = 40L;
            long biWidth = nBmpWidth;
            long biHeight = nBmpHeight;
            int biPlanes = 1;
            int biBitCount = 24;
            long biCompression = 0L;
            long biSizeImage = 0L;
            long biXpelsPerMeter = 0L;
            long biYPelsPerMeter = 0L;
            long biClrUsed = 0L;
            long biClrImportant = 0L;
            writeDword(fileos, biSize);
            writeLong(fileos, biWidth);
            writeLong(fileos, biHeight);
            writeWord(fileos, biPlanes);
            writeWord(fileos, biBitCount);
            writeDword(fileos, biCompression);
            writeDword(fileos, biSizeImage);
            writeLong(fileos, biXpelsPerMeter);
            writeLong(fileos, biYPelsPerMeter);
            writeDword(fileos, biClrUsed);
            writeDword(fileos, biClrImportant);
            byte bmpData[] = new byte[bufferSize];
            int wWidth = (nBmpWidth * 3 + nBmpWidth % 4);
            for (int nCol = 0, nRealCol = nBmpHeight - 1; nCol < nBmpHeight; ++nCol, --nRealCol) {
                for (int wRow = 0, wByteIdex = 0; wRow < nBmpWidth; wRow++, wByteIdex += 3) {
                    int clr = bitmap.getPixel(wRow, nCol);
                    bmpData[nRealCol * wWidth + wByteIdex] = (byte) Color.blue(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 1] = (byte) Color.green(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 2] = (byte) Color.red(clr);
                }
            }
            fileos.write(bmpData);
            Log.d(TAG, "dumpBitmapAsBMPForDebug() called with: bitmap = [" + bitmap + "], name = [" + name + "], path = [" + path + "]");
            fileos.flush();
            fileos.close();
            Log.d(TAG, "dumpBitmapAsBMPForDebug: write " + name + " to -->>" + path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeWord(FileOutputStream stream, int value) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        stream.write(b);
    }

    public static void writeDword(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }

    public static void writeLong(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }


    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // 目录此时为空，可以删除
        Log.d(TAG, "deleteDir: " + dir.toString());
        return dir.delete();
    }
}

package com.sprd.refocus;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import com.android.fw.YuvCodec;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.blur.CommonBlur;
import com.sprd.refocus.blur.TwoFrameBlur;
import com.sprd.refocus.bokeh.SprdRealBokeh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;

public class RefocusUtils {
    private static final String TAG = "RefocusUtils";
    public static final String DEBUG_ROOT_PATH = "sdcard/refocus";
    public static final String DEBUG_EDIT_YUV = "edit_yuv";

    private final static String TARGET_SR_PROCESSING = "persist.sys.cam.sr.enable";
    private final static String TARGET_TEST_MODE = "refocus.debug";

    private static int mBokehType = 0;

    public static byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }

    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = ((src[offset] & 0xFF)
                | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    public static String bytesToString(byte[] bytes, int offset) {
        char a = (char) (bytes[offset] & 0xFF);
        char b = (char) (bytes[offset + 1] & 0xFF);
        char c = (char) (bytes[offset + 2] & 0xFF);
        char d = (char) (bytes[offset + 3] & 0xFF);
        String s = new String(new char[]{a, b, c, d});
        return s;
    }

    public static int getIntValue(byte[] content, int position) {
        int intValue = bytesToInt(content, content.length - position * 4);
        return intValue;
    }

    public static String getStringValue(byte[] content, int position) {
        String stringValue = bytesToString(content, content.length - position * 4);
        return stringValue;
    }

    public static byte[] streamToByte(InputStream inStream) {
        byte[] data = null;
        try {
            Log.d(TAG, "streamToByte start.");
            data = new byte[inStream.available()];
            inStream.read(data);
            Log.d(TAG, "streamToByte end.");
        } catch (Exception e) {
            Log.e(TAG, "streamToByte Exception ", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (Throwable t) {
                    // do nothing
                }
            }
        }
        return data;
    }

    // yuv rotate 90 Degree
    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    // yuv rotate 180 Degree
    public static byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];

        int i = 0;
        int count = 0;
        for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
            yuv[count] = data[i];
            count++;
        }
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count++] = data[i - 1];
            yuv[count++] = data[i];
        }
        return yuv;
    }

    // yuv rotate 270 Degree
    public static byte[] rotateYUV420Degree270(byte[] data, int imageWidth, int imageHeight) {

        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = imageWidth - 1; x >= 0; x--) {
            for (int y = 0; y < imageHeight; y++) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i++;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i++;
            }
        }
        return yuv;
    }

    public static Bitmap loadBitmap(Context context, Uri uri, Options o) {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("bad argument to loadBitmap");
        }
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            Bitmap result = BitmapFactory.decodeStream(is, null, o);
            return result;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Memory it too low, load bitmap failed." + e);
            System.gc();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException for " + uri, e);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException for " + uri, e);
        } finally {
            Utils.closeSilently(is);
        }
        return null;
    }

    public static Bitmap rotateBitmap(Bitmap origin, float orientation) {
        if (orientation == 0.0f) {
            Log.d(TAG, "not need rotate !");
            return origin;
        }
        Log.d(TAG, "rotateBitmap start.");
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(orientation);
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        Log.d(TAG, "rotateBitmap end.");
        return newBM;
    }

    public static Point srcToYuvPoint(Point srcPoint, int SrcW, int SrcH, int rotate) {
        int yuvX = 0;
        int yuvY = 0;
        switch (rotate) {
            case 0:
                yuvX = srcPoint.x;
                yuvY = srcPoint.y;
                break;
            case 90:
                yuvX = srcPoint.y;
                yuvY = SrcW - srcPoint.x;
                break;
            case 180:
                yuvX = SrcW - srcPoint.x;
                yuvY = SrcH - srcPoint.y;
                break;
            case 270:
                yuvX = SrcH - srcPoint.y;
                yuvY = srcPoint.x;
                break;
            default:
                break;
        }
        Point yuvPoint = new Point(yuvX, yuvY);
        return yuvPoint;
    }

    public static Point yuvToSrcPoint(Point yuvPoint, int SrcW, int SrcH, int rotate) {
        int srcX = 0;
        int srcY = 0;
        switch (rotate) {
            case 0:
                srcX = yuvPoint.x;
                srcY = yuvPoint.y;
                break;
            case 90:
                srcX = SrcW - yuvPoint.y;
                srcY = yuvPoint.x;
                break;
            case 180:
                srcX = SrcW - yuvPoint.x;
                srcY = SrcH - yuvPoint.y;
                break;
            case 270:
                srcX = yuvPoint.y;
                srcY = SrcH - yuvPoint.x;
                break;
            default:
                break;
        }
        Point srcPoint = new Point(srcX, srcY);
        return srcPoint;
    }

    public static void saveByPath(Context context, byte[] picBytes, String filePath) {
        Log.d(TAG, "saveByPath filePath = " + filePath);
        ByteArrayOutputStream outputPicBytes = null;
        RandomAccessFile raf = null;
        try {
            outputPicBytes = new ByteArrayOutputStream();
            ExifInterface exif = new ExifInterface();
            exif.readExif(filePath);
            // change jepg's exif tag value.
            Log.i(TAG, " saveByPath bokehType = " + mBokehType);
            if (mBokehType == BokehType.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
                mBokehType = 0;
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH_HDR));
            } else {
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH));
            }
            exif.writeExif(picBytes, outputPicBytes);
            byte[] exifPicBytes = outputPicBytes.toByteArray();
            raf = new RandomAccessFile(filePath, "rw");
            raf.seek(0);
            raf.write(exifPicBytes);
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, null);
            Log.d(TAG, "saveByPath end.");
        } catch (IOException e) {
            Log.e(TAG, "Exception writing jpeg file", e);
        } finally {
            try {
                if (outputPicBytes != null) {
                    outputPicBytes.close();
                }
                if (raf != null) {
                    raf.close();
                }
            } catch (Throwable t) {
                // do nothing
            }
        }
    }

    public static void saveSrByPath(Context context, byte[] picBytes, byte[] srYuv, int srYuvIndex, int srFlagIndex, String filePath) {
        Log.d(TAG, "saveByPath filePath = " + filePath);
        ByteArrayOutputStream outputPicBytes = null;
        RandomAccessFile raf = null;
        try {
            outputPicBytes = new ByteArrayOutputStream();
            ExifInterface exif = new ExifInterface();
            exif.readExif(filePath);
            // change jepg's exif tag value.
            Log.i(TAG, " saveSrByPath bokehType = " + mBokehType);
            if (mBokehType == BokehType.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH_HDR));
            } else {
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH));
            }
            exif.writeExif(picBytes, outputPicBytes);
            byte[] exifPicBytes = outputPicBytes.toByteArray();
            raf = new RandomAccessFile(filePath, "rw");
            raf.seek(0);
            raf.write(exifPicBytes); //save jpeg
            raf.seek(srYuvIndex);
            raf.write(srYuv);  //save after sr yuv
            raf.seek(srFlagIndex);
            byte[] srSaveFlag = intToBytes(1);
            raf.write(srSaveFlag); //save SrSaveFlag
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, null);
            Log.d(TAG, "saveByPath end.");
        } catch (IOException e) {
            Log.e(TAG, "Exception writing jpeg file", e);
        } finally {
            try {
                if (outputPicBytes != null) {
                    outputPicBytes.close();
                }
                if (raf != null) {
                    raf.close();
                }
            } catch (Throwable t) {
                // do nothing
            }
        }
    }


    public static byte[] jpegAddExif(byte[] picBytes, String path) {
        byte[] exifPicBytes = null;
        ByteArrayOutputStream outputPicBytes = new ByteArrayOutputStream();
        try {
            ExifInterface exif = new ExifInterface();
            exif.readExif(path);
            Log.i(TAG, " jpegAddExif mBokehType = " + mBokehType);
            if (mBokehType == BokehType.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY) {
                mBokehType = 0;
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH_HDR));
            } else if (mBokehType == BokehType.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY){
                mBokehType = 0;
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH_FDR));
            } else {
                exif.setTagValue(ExifInterface.TAG_CAMERATYPE_IFD, new Integer(BokehType.IMG_TYPE_MODE_BOKEH));
            }
            exif.writeExif(picBytes, outputPicBytes);
            exifPicBytes = outputPicBytes.toByteArray();
            //BlendingUtil.writeByteData(exifPicBytes, "exif.jpg", Environment.getExternalStorageDirectory().getAbsolutePath());
        } catch (Exception e) {
            Log.d(TAG, "jpegAddExif fail! ", e);
            return picBytes;
        } finally {
            try {
                outputPicBytes.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return exifPicBytes;
    }

    public static byte[] jpegAddXMP(byte[] picByte, String path) {
        byte[] xmpPicBytes = null;
        ByteArrayOutputStream outputPicBytes = new ByteArrayOutputStream();
        ByteArrayInputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(picByte);
            //for test
            //GDepthData gDepthData = GDepthData.newGDepthData(path);
            //Log.d(TAG, "jpegAddXMP," + gDepthData.toString());

            //XMPMeta xmpMeta = XMPUtils.read(path);
            FullXMPMeta fullXMPMeta = XMPUtils.read(path);
            boolean ret = false;
            if (fullXMPMeta != null) {
                ret = XMPUtils.writeXMPMeta(
                        inputStream,
                        outputPicBytes,
                        fullXMPMeta.getmXMPMeta(),
                        fullXMPMeta.getmExtendedXMPMeta());
            }

            if (ret) {
                xmpPicBytes = outputPicBytes.toByteArray();
            } else {
                xmpPicBytes = picByte;
            }
            //BlendingUtil.writeByteData(xmpPicBytes, "final.jpg", Environment.getExternalStorageDirectory().getAbsolutePath());
        } catch (Exception e) {
            Log.d(TAG, "jpegAddXMP fail!", e);
            return picByte;
        } finally {
            if (outputPicBytes != null) {
                try {
                    outputPicBytes.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return xmpPicBytes;
    }

    public static void saveSr(Context context, String filePath, byte[] afterSrYuv) {
        ByteArrayOutputStream outputPicBytes = null;
        RandomAccessFile raf = null;
        try {
            outputPicBytes = new ByteArrayOutputStream();
            byte[] exifPicBytes = outputPicBytes.toByteArray();
            raf = new RandomAccessFile(filePath, "rw");
            raf.seek(0);
            raf.write(exifPicBytes);
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, null);
            Log.d(TAG, "saveByPath end.");
        } catch (IOException e) {
            Log.e(TAG, "Exception writing jpeg file", e);
        } finally {
            try {
                if (outputPicBytes != null) {
                    outputPicBytes.close();
                }
                if (raf != null) {
                    raf.close();
                }
            } catch (Throwable t) {
                // do nothing
            }
        }
    }

    public static void refSaveDepth(Context context, Uri uri, byte[] depth) {
        RandomAccessFile raf = null;
        Cursor cursor = null;
        String filePath = "";
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{ImageColumns.DATA}, null, null, null);
            if (cursor.moveToFirst()) {
                filePath = cursor.getString(0); // query _data
                Log.d(TAG, "refSaveDepth filePath is = " + filePath);
            }
            byte[] newHasDepthflag = RefocusUtils.intToBytes(1); // new hasDepth flag is 1
            byte[] depthLength = RefocusUtils.intToBytes(depth.length);

            raf = new RandomAccessFile(filePath, "rw");
            long fileLength = raf.length();
            raf.seek(fileLength);
            raf.write(depth);
            raf.seek(fileLength + depth.length);
            raf.write(depthLength);
            raf.seek(fileLength + depth.length + depthLength.length);
            raf.write(newHasDepthflag);
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, null);
            Log.d(TAG, "saveByPath end.");
        } catch (IOException e) {
            Log.e(TAG, "Exception writing jpeg file", e);
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
                if (raf != null) {
                    raf.close();
                }
            } catch (Throwable t) {
                // do nothing
            }
        }
    }

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    // use progress to calculate refocus image current value.
    public static String getRefCurValue(int progress) {
        float percent = progress / 255f;
        float blur = 8f - (percent * 6f);
        DecimalFormat df = new DecimalFormat(".0");
        return "F" + df.format(blur);
    }


    public static void writeByteData(byte[] bytes, String name, String path) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
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
            FileOutputStream depth = new FileOutputStream(data);
            depth.write(bytes);
            Log.d(TAG, "write Byte Data " + name + " to -->:" + data);
            depth.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "writeByteData: FileNotFoundException ");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "writeByteData: IOException");
            e.printStackTrace();
        }
    }

    public static String getDumpPath(String filePath) {
        String dirName = filePath.substring(filePath.lastIndexOf("/"), filePath.lastIndexOf("."));
        Log.d(TAG, "dirName = " + dirName);
        return DEBUG_ROOT_PATH + dirName;
    }

    public static void dumpEditYuv(byte[] yuv, String filePath, String name) {
        String dumpPath = getDumpPath(filePath) + "/" + DEBUG_EDIT_YUV;
        Log.d(TAG, "debug dumpData, dumpPath " + dumpPath);
        RefocusUtils.writeByteData(yuv, name, dumpPath);
    }

    // sprd software decoder
    public static byte[] jpeg2yuv(byte[] oriJpeg) {
        Options options = new Options();
        options.inPreferredConfig = StandardFrameworks.getInstances().isLowRam()
                ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        Log.d(TAG, "jpeg2yuv decode jpeg bitmap start");
        Bitmap oriJpegBitmap = BitmapFactory.decodeByteArray(oriJpeg, 0, oriJpeg.length, options);
        Log.d(TAG, "jpeg2yuv decode jpeg bitmap end");
        if (oriJpegBitmap == null) {
            Log.d(TAG, "jpeg2yuv oriJpegBitmap is null, fail!");
            return null;
        }
        return bitmap2yuv(oriJpegBitmap);
    }

    // sprd software decoder
    public static byte[] bitmap2yuv(Bitmap bitmap) {
        //Log.d(TAG, "bitmap2yuv");
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] buffer = new int[width * height];
        bitmap.getPixels(buffer, 0, width, 0, 0, width, height);
        //Log.d(TAG, "bitmap2yuv, getPixels end!");
        return rgb2yuv(buffer, width, height);
    }

    // sprd software decoder
    public static byte[] rgb2yuv(int[] argb, int width, int height) {
        //Log.d(TAG, "rgb2yuv start.");
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        int yIndex = 0;
        int uvIndex = len;
        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                // a = (argb[index] & 0xff000000) >> 24;// no use
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
                Y = (77 * R + 150 * G + 29 * B) >> 8;
                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    U = ((-43 * R - 85 * G + 128 * B) >> 8) + 128;
                    V = ((128 * R - 107 * G - 21 * B) >> 8) + 128;
                    yuv[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                index++;
            }
        }
        //Log.d(TAG, "return yuv, rgb2yuv end .");
        return yuv;
    }

    // sprd software encoder
    public static Bitmap yuv2bitmap(byte[] yuv, int w, int h, Options opts) {
        if (yuv == null) {
            Log.e(TAG, "yuv2bitmap yuv is null !");
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bitmap bitmap = null;
        try {
            Log.d(TAG, "yuv2bitmap  compressToJpeg  start . ");
            YuvImage yuvimage = new YuvImage(yuv, ImageFormat.NV21, w, h, null);
            yuvimage.compressToJpeg(new Rect(0, 0, w, h), 75, out);
            Log.d(TAG, "yuv2bitmap  compressToJpeg  end . ");
            byte[] jdata = out.toByteArray();
            bitmap = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, opts);
            Log.d(TAG, "yuv2bitmap decodeByteArray success .");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                // do nothing
            }
        }
        return bitmap;
    }

    // sprd software encoder
    public static Bitmap yuv2bitmap(byte[] yuv, int w, int h, int rotation, Options opts) {
        return rotateBitmap(yuv2bitmap(yuv, w, h, opts), (float) rotation);
    }

    // sprd software encoder
    public static byte[] yuv2jpeg(byte[] bytes, int yuvW, int yuvH, int rotation) {
        if (bytes == null) {
            Log.e(TAG, "yuv2jpeg bytes is null !");
            return null;
        }
        Log.d(TAG, "yuv2jpeg compressToJpeg start . ");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvimage;
        switch (rotation) {
            case 0:
                yuvimage = new YuvImage(bytes, ImageFormat.NV21, yuvW, yuvH, null);
                yuvimage.compressToJpeg(new Rect(0, 0, yuvW, yuvH), 90, out);
                break;
            case 90:
                yuvimage = new YuvImage(rotateYUV420Degree90(bytes, yuvW, yuvH), ImageFormat.NV21, yuvH, yuvW, null);
                yuvimage.compressToJpeg(new Rect(0, 0, yuvH, yuvW), 90, out);
                break;
            case 180:
                yuvimage = new YuvImage(rotateYUV420Degree180(bytes, yuvW, yuvH), ImageFormat.NV21, yuvW, yuvH, null);
                yuvimage.compressToJpeg(new Rect(0, 0, yuvW, yuvH), 90, out);
                break;
            case 270:
                yuvimage = new YuvImage(rotateYUV420Degree270(bytes, yuvW, yuvH), ImageFormat.NV21, yuvH, yuvW, null);
                yuvimage.compressToJpeg(new Rect(0, 0, yuvH, yuvW), 90, out);
                break;
        }
        Log.d(TAG, "yuv2jpeg compressToJpeg end . ");
        byte[] picBytes = out.toByteArray();
        try {
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return picBytes;
    }

    // sprd hardware decode
    public static byte[] hwJpeg2yuv(byte[] oriJpeg) {
        Log.d(TAG, "hwJpeg2yuv YuvImageEx.decodeJpegToYuv start .");
        YuvCodec yuvImage = StandardFrameworks.getInstances().decodeJpegToYuv(oriJpeg);
        Log.d(TAG, "hwJpeg2yuv YuvImageEx.decodeJpegToYuv end .");
        if (yuvImage == null || yuvImage.getYuvData() == null
                || yuvImage.getYuvData().length == 0) {
            Log.i(TAG, "hwJpeg2yuv, Hard decode fail .");
            return jpeg2yuv(oriJpeg);
        }
        return yuvImage.getYuvData();
    }

    // sprd hardware encoder
    public static byte[] hwYuv2jpeg(byte[] yuv, int yuvW, int yuvH, int rotation) {
        if (yuv == null) {
            Log.e(TAG, "hwYuv2jpeg bytes is null !");
            return null;
        }
        Log.d(TAG, "hwYuv2jpeg start . yuvW = " + yuvW + ", yuvH = " + yuvH + ", rotation " + rotation);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, yuvW, yuvH);
        if (rotation == 90 || rotation == 270) {
            rect = new Rect(0, 0, yuvH, yuvW);
        }
        Boolean success = StandardFrameworks.getInstances().encodeYuvToJpeg(yuv,
                ImageFormat.NV21, yuvW, yuvH, null, rect, 90, rotation, out);
        Log.d(TAG, "hwYuv2jpeg end . success = " + success);
        byte[] picBytes;
        if (success) {
            picBytes = out.toByteArray();
        } else {
            picBytes = yuv2jpeg(yuv, yuvW, yuvH, rotation);
        }
        try {
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return picBytes;
    }

    // sprd hardware encoder
    public static Bitmap hwYuv2bitmap(byte[] yuv, int yuvW, int yuvH, int rotation, Options opts) {
        byte[] picBytes = hwYuv2jpeg(yuv, yuvW, yuvH, rotation);
        if (picBytes == null) {
            return null;
        }
        Log.d(TAG, "hwYuv2bitmap decodeByteArray start .");
        Bitmap bitmap = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.length, opts);
        Log.d(TAG, "hwYuv2bitmap decodeByteArray end .");
        return bitmap;
    }

    public static void setBokehType(int bokehType) {
        mBokehType = bokehType;
    }

    public static boolean isNeedSR() {
        return StandardFrameworks.getInstances().getBooleanFromSystemProperties(TARGET_SR_PROCESSING, false);
    }

    public static boolean isRefocusTestMode() {
        return StandardFrameworks.getInstances().getBooleanFromSystemProperties(TARGET_TEST_MODE, false);
    }

    public static boolean isSupportBlur() {
        return CommonBlur.isSupportCommonblur() || TwoFrameBlur.ismSupportTFBlur();
    }

    public static boolean isSupportBokeh() {
        return SprdRealBokeh.isSupportBokeh();
    }

    public static byte[] doUpdateBokeh(InputStream inputStream, String path) {
        byte[] content = RefocusUtils.streamToByte(inputStream);
        if (content == null) {
            Log.i(TAG, "read stream fail!");
            return null;
        }
        CommonRefocus commonRefocus = CommonRefocus.getInstance(content);
        if (commonRefocus == null) {
            Log.i(TAG, "CommonRefocus init fail!");
            return null;
        }
        RefocusData data = commonRefocus.getRefocusData();
        if (data == null || data.getMainYuv() == null) {
            Log.i(TAG, "CommonRefocus yuv decode fail!");
            return null;
        }
        commonRefocus.initLib();
        int blurIntensity = data.getBlurIntensity();
        Point yuvPoint = new Point(data.sel_x, data.sel_y);
        byte[] editYuv = data.getMainYuv();
        int yuvWidth = data.getYuvWidth();
        int yuvHeight = data.getYuvHeight();
        int rotation = data.getRotation();
        editYuv = commonRefocus.doRefocus(editYuv, yuvPoint, blurIntensity);
        byte[] picBytes = StandardFrameworks.getInstances().isSupportHwCodec()
                ? RefocusUtils.hwYuv2jpeg(editYuv, yuvWidth, yuvHeight, rotation)
                : RefocusUtils.yuv2jpeg(editYuv, yuvWidth, yuvHeight, rotation);
        Log.d(TAG, " picBytes size = " + picBytes.length);
        commonRefocus.unInitLib();
        if (CommonRefocus.NEW_BOKEH_DATA) {
            int jpegSize = data.getJpegSize();
            byte[] exifPicBytes = RefocusUtils.jpegAddExif(picBytes, path);
            byte[] finalPicBytes = RefocusUtils.jpegAddXMP(exifPicBytes, path);
            if (finalPicBytes != null) {
                Log.d(TAG, " finalPicBytes size = " + finalPicBytes.length);
                byte[] newData = new byte[finalPicBytes.length + content.length - jpegSize];
                Log.d(TAG, " newData size = " + newData.length);
                System.arraycopy(finalPicBytes, 0, newData, 0, finalPicBytes.length);
                System.arraycopy(content, jpegSize, newData, finalPicBytes.length, content.length - jpegSize);
                return newData;
                //RefocusUtils.saveNewJpeg(context, newData, mUri, mPath);
            }
        } else {
            /*
            if (commonRefocus.isNeedSaveSr()) {
                byte[] srYuv = data.getMainYuv();
                int srYuvIndex = data.getJpegSize();
                int srFlagIndex = content.length - 4 * 2;
                RefocusUtils.saveSrByPath(mContext, picBytes, srYuv, srYuvIndex, srFlagIndex, mPath);
            } else {
                RefocusUtils.saveByPath(mContext, picBytes, mPath);
            }*/
            Log.d(TAG, "only support :" + CommonRefocus.NEW_BOKEH_DATA + " data");
        }
        return null;
    }


}

package com.android.gallery3d.v2.discover.people;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.v2.discover.utils.ImageUtils;
import com.sprd.refocus.RefocusUtils;

import java.io.File;
import java.util.Arrays;

public class FaceDetector {
    private static final String TAG = FaceDetector.class.getSimpleName();

    private static final int MAX_SIZE = 1024;

    private static final String FACE_HEAD_DIR_NAME = "face_head";

    private static FaceDetector sFaceDetector;

    private FaceDetector() {
    }

    public static FaceDetector getDefault() {
        if (sFaceDetector == null) {
            synchronized (FaceDetector.class) {
                if (sFaceDetector == null) {
                    sFaceDetector = new FaceDetector();
                }
            }
        }
        return sFaceDetector;
    }

    public synchronized Face[] detectFaces(String path, int orientation) {
        long t0 = System.currentTimeMillis();
        BitmapFactory.Options options = ImageUtils.getOptions(path, MAX_SIZE, MAX_SIZE);
        Bitmap bitmap = ImageUtils.round(ImageUtils.createBitmap(path, options), orientation, true);
        if (bitmap == null) {
            return new Face[]{};
        }
        byte[] yuv = RefocusUtils.bitmap2yuv(bitmap);
        Face[] faces = nativeDetectFaces(yuv, bitmap.getWidth(), bitmap.getHeight());
        long t1 = System.currentTimeMillis();
        Log.d(TAG, "detectFaces (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ") cost " + (t1 - t0) + " ms !!!");
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        return faces;
    }

    public synchronized Face[] detectFaces(ContentResolver resolver, Uri uri, int orientation) {
        long t0 = System.currentTimeMillis();
        BitmapFactory.Options options = ImageUtils.getOptions(resolver, uri, MAX_SIZE, MAX_SIZE);
        Bitmap bitmap = ImageUtils.round(ImageUtils.createBitmap(resolver, uri, options), orientation, true);
        if (bitmap == null) {
            return new Face[]{};
        }
        byte[] yuv = RefocusUtils.bitmap2yuv(bitmap);
        Face[] faces = nativeDetectFaces(yuv, bitmap.getWidth(), bitmap.getHeight());
        long t1 = System.currentTimeMillis();
        Log.d(TAG, "detectFaces (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ") cost " + (t1 - t0) + " ms !!!");
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        return faces;
    }

    public synchronized float match(byte[] va, byte[] vb) {
        return nativeMatch(toShort(va), toShort(vb));
    }

    public static String detectedFaces(@NonNull Face[] faces) {
        StringBuilder sb = new StringBuilder("{");
        for (Face face : faces) {
            sb.append("Face{");
            sb.append("(").append(face.x).append(", ").append(face.y).append(", ").append(face.width).append(", ").append(face.height).append(")");
            sb.append(", yawAngle: ").append(face.yawAngle);
            sb.append(", rollAngle: ").append(face.rollAngle);
            sb.append(", fdScore: ").append(face.fdScore);
            sb.append(", faScore: ").append(face.faScore).append("}, ");
        }
        sb.append("}");
        return sb.toString();
    }

    static String saveHead(String path, int orientation, int x, int y, int width, int height, int vecId) {
        Log.d(TAG, "saveHead: B path=" + path);
        String dir = GalleryAppImpl.getApplication().getFilesDir().getAbsolutePath() + "/" + FACE_HEAD_DIR_NAME;
        mkdir(dir);
        String head_file = dir + "/" + vecId + ".jpg";
        BitmapFactory.Options options = ImageUtils.getOptions(path, MAX_SIZE, MAX_SIZE);
        Bitmap cropped = ImageUtils.cropHead(
                ImageUtils.round(ImageUtils.createBitmap(path, options), orientation, true),
                x, y, width, height, true);
        String file = ImageUtils.save(cropped, head_file);
        if (cropped != null && !cropped.isRecycled()) {
            cropped.recycle();
            cropped = null;
        }
        Log.d(TAG, "saveHead: E head file: " + file);
        return file;
    }

    static String saveHead(ContentResolver resolver, Uri uri, int orientation, int x, int y, int width, int height, int vecId) {
        Log.d(TAG, "saveHead: B uri=" + uri);
        String dir = GalleryAppImpl.getApplication().getFilesDir().getAbsolutePath() + "/" + FACE_HEAD_DIR_NAME;
        mkdir(dir);
        String head_file = dir + "/" + vecId + ".jpg";
        BitmapFactory.Options options = ImageUtils.getOptions(resolver, uri, MAX_SIZE, MAX_SIZE);
        Bitmap cropped = ImageUtils.cropHead(
                ImageUtils.round(ImageUtils.createBitmap(resolver, uri, options), orientation, true),
                x, y, width, height, true);
        String file = ImageUtils.save(cropped, head_file);
        if (cropped != null && !cropped.isRecycled()) {
            cropped.recycle();
            cropped = null;
        }
        Log.d(TAG, "saveHead: E head file: " + file);
        return file;
    }

    private static void mkdir(String dir) {
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static short[] toShort(byte[] src) {
        int count = src.length >> 1;
        short[] dest = new short[count];
        for (int i = 0; i < count; i++) {
            dest[i] = (short) (src[i * 2] << 8 | src[2 * i + 1] & 0xff);
        }
        return dest;
    }

    private static byte[] toByte(short[] src) {
        int count = src.length;
        byte[] dest = new byte[count << 1];
        for (int i = 0; i < count; i++) {
            dest[i * 2] = (byte) (src[i] >> 8);
            dest[i * 2 + 1] = (byte) (src[i]);
        }
        return dest;
    }

    public static class Face {
        private int x;
        private int y;
        private int width;
        private int height;
        private int yawAngle;
        private int rollAngle;
        //In [0, 1000]
        private int fdScore;
        private int faScore;
        private short featureVector[];

        public Face(int x, int y, int width,
                    int height, int yawAngle, int rollAngle,
                    int fdScore, int faScore, short[] featureVector) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.yawAngle = yawAngle;
            this.rollAngle = rollAngle;
            this.fdScore = fdScore;
            this.faScore = faScore;
            this.featureVector = featureVector;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getYawAngle() {
            return yawAngle;
        }

        public int getRollAngle() {
            return rollAngle;
        }

        public int getFdScore() {
            return fdScore;
        }

        public int getFaScore() {
            return faScore;
        }

        public byte[] getFeatureVector() {
            return toByte(featureVector);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("Face{");
            builder.append("(").append(x).append(", ").append(y).append(", ").append(width).append(", ").append(height).append(")");
            builder.append(", yawAngle: ").append(yawAngle);
            builder.append(", rollAngle: ").append(rollAngle);
            builder.append(", fdScore: ").append(fdScore);
            builder.append(", faScore: ").append(faScore);
            if (featureVector != null) {
                builder.append(", ").append(Arrays.toString(featureVector));
            }
            builder.append("}");
            return builder.toString();
        }
    }

    private native Face[] nativeDetectFaces(byte[] yuv, int width, int height);

    private native float nativeMatch(short[] va, short[] vb);

    static {
        System.loadLibrary("jni_sprd_facedetector");
    }
}

package com.android.gallery3d.v2.discover.things;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.v2.discover.utils.ImageUtils;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TFLiteMnasNet extends AbstractClassifier {
    private static final String TAG = TFLiteMnasNet.class.getSimpleName();

    private static final String MODEL_PATH = "mnasnet_1.3_224.tflite";

    private static final String LABEL_PATH = "mnasnet_1.3_224.txt";

    private static final int DEFAULT_THREAD = 4;

    private static final int DIM_BATCH_SIZE = 1;

    private static final int DIM_PIXEL_SIZE = 3;

    private static final int DIM_IMG_SIZE_X = 224;

    private static final int DIM_IMG_SIZE_Y = 224;

    private static final int IMAGE_MEAN = 128;

    private static final float IMAGE_STD = 128.0f;

    private static final int FILTER_STAGES = 3;

    private static final int MAX_SIZE = 400;

    private static final int RESULTS_TO_SHOW = 3;

    private Interpreter tflite;

    private List<String> labelList;

    private ByteBuffer imgData = null;

    private float[][] labelProbArray = null;

    private float[][] filterLabelProbArray = null;

    private int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    private final Object mLock = new Object();

    private PriorityQueue<Recognition> sortedLabels =
            new PriorityQueue<Recognition>(
                    RESULTS_TO_SHOW,
                    new Comparator<Recognition>() {
                        @Override
                        public int compare(Recognition lhs, Recognition rhs) {
                            // Intentionally reversed to put high confidence at the head of the queue.
                            return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                        }
                    });

    @Override
    public AbstractClassifier open() {
        if (tflite == null) {
            try {
                tflite = new Interpreter(loadModelFile(GalleryAppImpl.getApplication().getAndroidContext()));
                tflite.setNumThreads(DEFAULT_THREAD);
                labelList = loadLabelList(GalleryAppImpl.getApplication().getAndroidContext());
                imgData =
                        ByteBuffer.allocateDirect(
                                4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
                imgData.order(ByteOrder.nativeOrder());
                labelProbArray = new float[1][labelList.size()];
                filterLabelProbArray = new float[FILTER_STAGES][labelList.size()];
                Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    @Override
    public List<Recognition> recognize(String path, int orientation) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return new ArrayList<>();
        }

        long t0 = System.currentTimeMillis();
        BitmapFactory.Options options = ImageUtils.getOptions(path, MAX_SIZE, MAX_SIZE);
        Bitmap bitmap = ImageUtils.scale(ImageUtils.createBitmap(path, options), DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, orientation, true);
        long t1 = System.currentTimeMillis();
        if (bitmap == null) {
            Log.e(this.getClass().getSimpleName(), "recognize bitmap is null.");
            return new ArrayList<>();
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        synchronized (mLock) {
            if (tflite != null) {
                Log.d(TAG, "path = " + path);
                tflite.run(imgData, labelProbArray);
            }
        }

        // Find the best classifications.
        ArrayList<Recognition> recognitions = convertToRecognitionList();

        if (!bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }

        Log.d(this.getClass().getSimpleName(), "bitmap cost " + (t1 - t0) + " ms, recognize cost " + (System.currentTimeMillis() - t1) + " ms");
        return recognitions;
    }

    @Override
    public List<Recognition> recognize(Uri uri, int orientation) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return new ArrayList<>();
        }

        long t0 = System.currentTimeMillis();
        BitmapFactory.Options options = ImageUtils.getOptions(GalleryAppImpl.getApplication().getContentResolver(), uri, MAX_SIZE, MAX_SIZE);
        Bitmap bitmap = ImageUtils.scale(ImageUtils.createBitmap(GalleryAppImpl.getApplication().getContentResolver(), uri, options), DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, orientation, true);
        long t1 = System.currentTimeMillis();
        if (bitmap == null) {
            Log.e(this.getClass().getSimpleName(), "recognize bitmap is null.");
            return new ArrayList<>();
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        synchronized (mLock) {
            if (tflite != null) {
                Log.d(TAG, "uri = " + uri);
                tflite.run(imgData, labelProbArray);
            }
        }

        // Find the best classifications.
        ArrayList<Recognition> recognitions = convertToRecognitionList();

        if (!bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }

        Log.d(this.getClass().getSimpleName(), "bitmap cost " + (t1 - t0) + " ms, recognize cost " + (System.currentTimeMillis() - t1) + " ms");
        return recognitions;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (tflite != null) {
                tflite.close();
                tflite = null;
            }
        }
    }

    private List<String> loadLabelList(Context activity) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return labelList;
    }

    private MappedByteBuffer loadModelFile(Context activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
    }

    private ArrayList<Recognition> convertToRecognitionList() {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(
                    new Recognition(
                            labelList.size() > i ? labelList.get(i) : null, labelProbArray[0][i], null));
        }
        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        int recognitionsSize = Math.min(sortedLabels.size(), RESULTS_TO_SHOW);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(sortedLabels.poll());
        }
        sortedLabels.clear();
        return recognitions;
    }
}

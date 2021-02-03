package com.sprd.ui.test;

import android.net.Uri;

import com.sprd.gallery3d.blending.BlendingUtil;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class BlendUtilsTest {

    @Test
    public void readStreamTest() {
        Uri uri = initResource();
        InputStream inputStream = null;
        try {
            inputStream = getApplicationContext().getContentResolver().openInputStream(uri);
            byte[] readByte = BlendingUtil.readStream(inputStream);
            assertThat(readByte, notNullValue());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Uri initResource() {
        String filePath = Utils.RES_DIR + "/SpecialImage/" + "/IMG_bokeh_BP.jpg";
        return Uri.fromFile(new File(filePath));
    }
}
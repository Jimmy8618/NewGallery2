/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.filters;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.BasicEditor;
import com.android.gallery3d.filtershow.pipeline.FilterEnvironment;

public class ImageFilterSharpen extends ImageFilterRS {
    private static final String SERIALIZATION_NAME = "SHARPEN";
    private static final String LOGTAG = "ImageFilterSharpen";
    private ScriptC_convolve3x3 mScript;

    private FilterBasicRepresentation mParameters;

    public ImageFilterSharpen() {
        mName = "Sharpen";
    }

    @Override
    public FilterRepresentation getDefaultRepresentation() {
        FilterRepresentation representation = new FilterBasicRepresentation("Sharpen", 0, 0, 100);
        representation.setSerializationName(SERIALIZATION_NAME);
        representation.setShowParameterValue(true);
        representation.setFilterClass(ImageFilterSharpen.class);
        representation.setTextId(R.string.sharpness);
        representation.setOverlayId(R.drawable.filtershow_button_colors_sharpen);
        representation.setEditorId(BasicEditor.ID);
        representation.setSupportsPartialRendering(true);
        return representation;
    }

    @Override
    public void useRepresentation(FilterRepresentation representation) {
        FilterBasicRepresentation parameters = (FilterBasicRepresentation) representation;
        mParameters = parameters;
    }

    @Override
    protected void resetAllocations() {
        // nothing to do
    }

    @Override
    public void resetScripts() {
        if (mScript != null) {
            mScript.destroy();
            mScript = null;
        }
    }

    @Override
    protected void createFilter(android.content.res.Resources res, float scaleFactor,
                                int quality) {
        if (mScript == null) {
            mScript = new ScriptC_convolve3x3(getRenderScriptContext());
        }
    }

    private void computeKernel() {
        float scaleFactor = computeScaleFactor();
        float p1 = mParameters.getValue() * scaleFactor;
        float value = p1 / 100.0f;
        float f[] = new float[9];
        float p = value;
        f[0] = -p;
        f[1] = -p;
        f[2] = -p;
        f[3] = -p;
        f[4] = 8 * p + 1;
        f[5] = -p;
        f[6] = -p;
        f[7] = -p;
        f[8] = -p;
        mScript.set_gCoeffs(f);
    }

    @Override
    protected void bindScriptValues() {
        int w = getInPixelsAllocation().getType().getX();
        int h = getInPixelsAllocation().getType().getY();
        mScript.set_gWidth(w);
        mScript.set_gHeight(h);
    }

    @Override
    protected void runFilter() {
        if (mParameters == null) {
            return;
        }
        computeKernel();
        mScript.set_gIn(getInPixelsAllocation());
        /* SPRD: Modify for bug625750, use new script convolve3x3.rs now, because ript.bind_gPixels()
         will call Script.bindAllocation() which only allows simple 1D allocations to be used with bind
         in API 20+. @{
        mScript.bind_gPixels(getInPixelsAllocation());
        mScript.forEach_root(getInPixelsAllocation(), getOutPixelsAllocation());
        */
        mScript.forEach_root(getOutPixelsAllocation());
    }

    private float computeScaleFactor() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                sActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int w = getInPixelsAllocation().getType().getX();
        int h = getInPixelsAllocation().getType().getY();
        int maxImageSize = Math.max(w, h);
        int maxScreenSzie = Math.max(metrics.widthPixels, metrics.heightPixels);

        float scaleFactor = 1.0f;
        int quality = getEnvironment().getQuality();
        if (quality == FilterEnvironment.QUALITY_PREVIEW) {
            scaleFactor = 0.5f;
        } else if (quality == FilterEnvironment.QUALITY_FINAL && maxImageSize >= maxScreenSzie) {
            scaleFactor = 1.2f;
        } else {
            scaleFactor = getEnvironment().getScaleFactor();
        }
        android.util.Log.d(LOGTAG, " computeScaleFactor  image size: " + w + "  x " + h
                + "; screen size: " + metrics.widthPixels + " x " + metrics.heightPixels
                + "; quality = " + quality + ", scaleFactor = " + scaleFactor, new Throwable());
        return scaleFactor;
    }
}

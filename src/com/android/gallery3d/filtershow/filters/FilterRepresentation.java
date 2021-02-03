/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.filtershow.editors.BasicEditor;

import java.io.IOException;
import java.util.ArrayList;

public class FilterRepresentation {
    private static final String LOGTAG = "FilterRepresentation";
    private static final boolean DEBUG = false;
    private String mName;
    private int mPriority = TYPE_NORMAL;
    private Class<?> mFilterClass;
    private boolean mSupportsPartialRendering = false;
    private int mTextId = 0;
    private int mEditorId = BasicEditor.ID;
    private int mButtonId = 0;
    private int mOverlayId = 0;
    private boolean mOverlayOnly = false;
    private boolean mShowParameterValue = true;
    private boolean mIsBooleanFilter = false;
    private boolean mIsIgnoreStatus = false;
    private String mSerializationName;
    public static final byte TYPE_BORDER = 1;
    public static final byte TYPE_FX = 2;
    public static final byte TYPE_WBALANCE = 3;
    public static final byte TYPE_VIGNETTE = 4;
    public static final byte TYPE_NORMAL = 5;
    public static final byte TYPE_TINYPLANET = 6;
    public static final byte TYPE_GEOMETRY = 7;
    protected static final String NAME_TAG = "Name";

    public FilterRepresentation(String name) {
        mName = name;
    }

    public FilterRepresentation copy() {
        FilterRepresentation representation = new FilterRepresentation(mName);
        representation.useParametersFrom(this);
        return representation;
    }

    protected void copyAllParameters(FilterRepresentation representation) {
        representation.mSerializationName = mSerializationName;
        representation.setName(getName());
        representation.setFilterClass(getFilterClass());
        representation.setFilterType(getFilterType());
        representation.setSupportsPartialRendering(supportsPartialRendering());
        representation.setTextId(getTextId());
        representation.setEditorId(getEditorId());
        representation.setOverlayId(getOverlayId());
        representation.setOverlayOnly(getOverlayOnly());
        representation.setShowParameterValue(showParameterValue());
        representation.setIsBooleanFilter(isBooleanFilter());
        representation.setIgnoreStatus(isIgnoreStatus());
    }

    public boolean equals(FilterRepresentation representation) {
        if (representation == null) {
            return false;
        }
        return representation.mFilterClass == mFilterClass
                && representation.mName.equalsIgnoreCase(mName)
                && representation.mPriority == mPriority
                // TODO: After we enable partial rendering, we can switch back
                // to use member variable here.
                && representation.supportsPartialRendering() == supportsPartialRendering()
                && representation.mTextId == mTextId
                && representation.mEditorId == mEditorId
                && representation.mButtonId == mButtonId
                && representation.mOverlayId == mOverlayId
                && representation.mOverlayOnly == mOverlayOnly
                && representation.mShowParameterValue == mShowParameterValue
                && representation.mIsBooleanFilter == mIsBooleanFilter
                && representation.mIsIgnoreStatus == mIsIgnoreStatus;
    }

    public boolean isBooleanFilter() {
        return mIsBooleanFilter;
    }

    public void setIsBooleanFilter(boolean value) {
        mIsBooleanFilter = value;
    }

    @Override
    public String toString() {
        return mName;
    }


    public void setName(String name) {
        int[] nameIds = {
                R.string.border_4X5,
                R.string.border_brush,
                R.string.border_grunge,
                R.string.border_sumi,
                R.string.border_tape,
                R.string.border_black,
                R.string.border_black_rounded,
                R.string.border_white,
                R.string.border_white_rounded,
                R.string.border_cream,
                R.string.border_cream_rounded,
                R.string.ffx_punch,
                R.string.ffx_vintage,
                R.string.ffx_bw_contrast,
                R.string.ffx_bleach,
                R.string.ffx_instant,
                R.string.ffx_washout,
                R.string.ffx_blue_crush,
                R.string.ffx_washout_color,
                R.string.ffx_x_process
        };

        String[] serializationNames = {
                "FRAME_4X5",
                "FRAME_BRUSH",
                "FRAME_GRUNGE",
                "FRAME_SUMI_E",
                "FRAME_TAPE",
                "FRAME_BLACK",
                "FRAME_BLACK_ROUNDED",
                "FRAME_WHITE",
                "FRAME_WHITE_ROUNDED",
                "FRAME_CREAM",
                "FRAME_CREAM_ROUNDED",
                "LUT3D_PUNCH",
                "LUT3D_VINTAGE",
                "LUT3D_BW",
                "LUT3D_BLEACH",
                "LUT3D_INSTANT",
                "LUT3D_WASHOUT",
                "LUT3D_BLUECRUSH",
                "LUT3D_WASHOUT_COLOR",
                "LUT3D_XPROCESS"
        };
        for (int i = 0; i < serializationNames.length; i++) {
            if (mSerializationName != null && mSerializationName.equals(serializationNames[i])) {
                mName = GalleryAppImpl.getApplication().getString(nameIds[i]);
                return;
            }
        }
        if (mSerializationName != null && mSerializationName.equals("EXPOSURE")) {
            mName = GalleryAppImpl.getApplication().getString(R.string.editor_grad_brightness);
        } else if (mSerializationName != null && mSerializationName.equals("SHARPEN")) {
            mName = GalleryAppImpl.getApplication().getString(R.string.sharpness);
        } else if (mSerializationName != null && mSerializationName.equals("CONTRAST")) {
            mName = GalleryAppImpl.getApplication().getString(R.string.contrast);
        } else {
            mName = name;
        }
    }

    public String getName() {
        return mName;
    }

    public void setSerializationName(String sname) {
        mSerializationName = sname;
    }

    public String getSerializationName() {
        return mSerializationName;
    }

    public void setFilterType(int priority) {
        mPriority = priority;
    }

    public int getFilterType() {
        return mPriority;
    }

    public boolean isNil() {
        return false;
    }

    public boolean supportsPartialRendering() {
        return mSupportsPartialRendering;
    }

    public void setSupportsPartialRendering(boolean value) {
        mSupportsPartialRendering = value;
    }

    public void useParametersFrom(FilterRepresentation a) {
    }

    public boolean allowsSingleInstanceOnly() {
        return false;
    }

    public Class<?> getFilterClass() {
        return mFilterClass;
    }

    public void setFilterClass(Class<?> filterClass) {
        mFilterClass = filterClass;
    }

    // This same() function is different from equals(), basically it checks
    // whether 2 FilterRepresentations are the same type. It doesn't care about
    // the values.
    public boolean same(FilterRepresentation b) {
        if (b == null) {
            return false;
        }
        return getFilterClass() == b.getFilterClass();
    }

    public int getTextId() {
        return mTextId;
    }

    public void setTextId(int textId) {
        mTextId = textId;
    }

    public int getOverlayId() {
        return mOverlayId;
    }

    public void setOverlayId(int overlayId) {
        mOverlayId = overlayId;
    }

    public boolean getOverlayOnly() {
        return mOverlayOnly;
    }

    public void setOverlayOnly(boolean value) {
        mOverlayOnly = value;
    }

    final public int getEditorId() {
        return mEditorId;
    }

    public int[] getEditorIds() {
        return new int[]{
                mEditorId};
    }

    public void setEditorId(int editorId) {
        mEditorId = editorId;
    }

    public boolean showParameterValue() {
        return mShowParameterValue;
    }

    public void setShowParameterValue(boolean showParameterValue) {
        mShowParameterValue = showParameterValue;
    }

    public String getStateRepresentation() {
        return "";
    }

    /**
     * Method must "beginObject()" add its info and "endObject()"
     *
     * @param writer
     * @throws IOException
     */
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        {
            String[][] rep = serializeRepresentation();
            for (int k = 0; k < rep.length; k++) {
                writer.name(rep[k][0]);
                writer.value(rep[k][1]);
            }
        }
        writer.endObject();
    }

    // this is the old way of doing this and will be removed soon
    public String[][] serializeRepresentation() {
        String[][] ret = {{NAME_TAG, getName()}};
        return ret;
    }

    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        ArrayList<String[]> al = new ArrayList<String[]>();
        reader.beginObject();
        while (reader.hasNext()) {
            String[] kv = {reader.nextName(), reader.nextString()};
            al.add(kv);

        }
        reader.endObject();
        String[][] oldFormat = al.toArray(new String[al.size()][]);

        deSerializeRepresentation(oldFormat);
    }

    // this is the old way of doing this and will be removed soon
    public void deSerializeRepresentation(String[][] rep) {
        for (int i = 0; i < rep.length; i++) {
            if (NAME_TAG.equals(rep[i][0])) {
                mName = rep[i][1];
                break;
            }
        }
    }

    // Override this in subclasses
    public int getStyle() {
        return -1;
    }

    public boolean canMergeWith(FilterRepresentation representation) {
        return getFilterType() == FilterRepresentation.TYPE_GEOMETRY
                && representation.getFilterType() == FilterRepresentation.TYPE_GEOMETRY;
    }

    /*
     * SPRD: Add 20150316 Spreadst of bug410659, display abnormal when rotate
     * and undo and rotate image again. @{
     */
    public void resetRepresentation() {
    }
    /* @} */

    /* SPRD bug592299, ignore some error draw filter @{ */
    public boolean isIgnoreStatus() {
        return mIsIgnoreStatus;
    }

    public void setIgnoreStatus(boolean value) {
        mIsIgnoreStatus = value;
    }
    /* @} */

}

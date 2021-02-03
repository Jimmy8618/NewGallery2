package com.android.gallery3d.v2.discover.things;

import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;

/**
 * An immutable result returned by a Classifier describing what was recognized.
 */
public class Recognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final int id;

    /**
     * Display name for the recognition.
     */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    private final Float confidence;

    /**
     * Optional location within the source image for the location of the recognized object.
     */
    private RectF location;

    public Recognition(
            final String title, final Float confidence, final RectF location) {
        if (TextUtils.isEmpty(title)) {
            this.id = AbstractClassifier.TF_UNKNOWN;
            this.title = "unKnow";
        } else {
            String[] sp = title.split(" ");
            int id = AbstractClassifier.TF_UNKNOWN;
            String name = title;
            if (sp.length >= 2) {
                try {
                    id = Integer.parseInt(sp[0]);
                    name = "";
                    for (int i = 1; i < sp.length; i++) {
                        name += sp[i] + " ";
                    }
                } catch (Exception e) {
                    Log.e("Recognition", "error parse " + title);
                }
            }
            this.id = id;
            this.title = name;
        }
        this.confidence = confidence;
        this.location = location;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Float getConfidence() {
        return confidence;
    }

    public RectF getLocation() {
        return new RectF(location);
    }

    public void setLocation(RectF location) {
        this.location = location;
    }

    @Override
    public String toString() {
        String resultString = "{";
        resultString += "[" + id + "]";

        if (title != null) {
            resultString += "," + title;
        }

        if (confidence != null) {
            resultString += String.format(",(%.1f%%)", confidence * 100.0f);
        }

        if (location != null) {
            resultString += "," + location;
        }

        resultString += "}";

        return resultString.trim();
    }
}

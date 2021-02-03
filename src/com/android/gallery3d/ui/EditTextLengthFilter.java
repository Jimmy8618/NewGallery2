package com.android.gallery3d.ui;

import android.content.Context;
import android.text.Spanned;
import android.widget.Toast;
import android.text.InputFilter;

public class EditTextLengthFilter implements InputFilter {
    private final int mMax;
    private final Context mContext;
    private final int res;
    private Toast mToast = null;

    public EditTextLengthFilter(Context context, int resId, int max) {
        mContext = context;
        res = resId;
        mMax = max;
    }

    private void showMsg() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
        mToast = Toast.makeText(mContext, res, Toast.LENGTH_SHORT);
        mToast.show();
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                               int dstart, int dend) {
        int keep = (mMax - (dest.length() - (dend - dstart)));
        if (keep <= 0) {
            showMsg();
            return "";
        } else if (keep >= (end - start)) {
            return null; // keep original
        } else {
            keep += start;
            if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                --keep;
                if (keep == start) {
                    return "";
                }
            }
            return source.subSequence(start, keep);
        }
    }

    /**
     * @return the maximum length enforced by this input filter
     */
    public int getMax() {
        return mMax;
    }

}

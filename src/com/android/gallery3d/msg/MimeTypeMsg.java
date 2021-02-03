package com.android.gallery3d.msg;

/**
 * Created by apuser on 4/14/17.
 */

public class MimeTypeMsg {
    private String mOriMimeType;
    private String mCurMimeType;

    public MimeTypeMsg(String oriMimeType, String curMimeType) {
        mOriMimeType = oriMimeType;
        mCurMimeType = curMimeType;
    }

    @Override
    public String toString() {
        return "MimeTypeMsg{" +
                "mOriMimeType='" + mOriMimeType + '\'' +
                ", mCurMimeType='" + mCurMimeType + '\'' +
                '}';
    }
}

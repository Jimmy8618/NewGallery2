package com.sprd.blending.bean;

/**
 * Created by cz on 17-10-17.
 */

public class Mask {
    private byte[] mask;
    private int mMaskWidth;
    private int mMaskHeight;

    public Mask(byte[] mask, int mMaskWidth, int mMaskHeight) {
        this.mask = mask;
        this.mMaskWidth = mMaskWidth;
        this.mMaskHeight = mMaskHeight;
    }

    public Mask() {
    }

    public byte[] getMask() {
        return mask;
    }

    public void setMask(byte[] mask) {
        this.mask = mask;
    }

    public int getmMaskWidth() {
        return mMaskWidth;
    }

    public void setmMaskWidth(int mMaskWidth) {
        this.mMaskWidth = mMaskWidth;
    }

    public int getmMaskHeight() {
        return mMaskHeight;
    }

    public void setmMaskHeight(int mMaskHeight) {
        this.mMaskHeight = mMaskHeight;
    }
}

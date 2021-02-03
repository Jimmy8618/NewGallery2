package com.sprd.blending.bean;

/**
 * Created by cz on 17-10-16.
 */

public class Depth {

    private byte[] depth;
    private int mDepthWidth;
    private int mDepthHeight;

    public Depth() {
    }

    public Depth(byte[] depth, int mDepthWidth, int mDepthHeight) {
        this.depth = depth;
        this.mDepthWidth = mDepthWidth;
        this.mDepthHeight = mDepthHeight;
    }

    public byte[] getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        return "Depth{" +
                "depth=" + depth +
                ", mDepthWidth=" + mDepthWidth +
                ", mDepthHeight=" + mDepthHeight +
                '}';
    }

    public void setDepth(byte[] depth) {
        this.depth = depth;
    }

    public int getmDepthWidth() {
        return mDepthWidth;
    }

    public void setmDepthWidth(int mDepthWidth) {
        this.mDepthWidth = mDepthWidth;
    }

    public int getmDepthHeight() {
        return mDepthHeight;
    }

    public void setmDepthHeight(int mDepthHeight) {
        this.mDepthHeight = mDepthHeight;
    }
}

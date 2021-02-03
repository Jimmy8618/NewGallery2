package com.sprd.blending.bean;

import android.graphics.Bitmap;

/**
 * Created by cz on 17-10-17.
 */

public class ExtractResult {
    private Bitmap bitmap;
    private int ResultCode;
    private int width;
    private int height;
    private int coordinateX;
    private int coordinateY;


    public ExtractResult(Bitmap bitmap, int resultCode, int width, int height, int coordinateX, int coordinateY) {
        this.bitmap = bitmap;
        ResultCode = resultCode;
        this.width = width;
        this.height = height;
        this.coordinateX = coordinateX;
        this.coordinateY = coordinateY;
    }

    public ExtractResult() {
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public int getResultCode() {
        return ResultCode;
    }

    public void setResultCode(int resultCode) {
        ResultCode = resultCode;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getCoordinateX() {
        return coordinateX;
    }

    public void setCoordinateX(int coordinateX) {
        this.coordinateX = coordinateX;
    }

    public int getCoordinateY() {
        return coordinateY;
    }

    public void setCoordinateY(int coordinateY) {
        this.coordinateY = coordinateY;
    }

    @Override
    public String toString() {
        return "ExtractResult{" +
                "bitmap=" + bitmap +
                ", ResultCode=" + ResultCode +
                ", width=" + width +
                ", height=" + height +
                ", coordinateX=" + coordinateX +
                ", coordinateY=" + coordinateY +
                '}';
    }
}

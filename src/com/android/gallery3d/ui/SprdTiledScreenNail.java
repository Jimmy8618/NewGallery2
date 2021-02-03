package com.android.gallery3d.ui;

import android.graphics.Bitmap;

public class SprdTiledScreenNail extends TiledScreenNail {

    public SprdTiledScreenNail(Bitmap bitmap) {
        super(bitmap);
    }

    public SprdTiledScreenNail(int width, int height) {
        super(width, height);
    }

    @Override
    public void recycle() {
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
        if (mBitmap != null) {
            // GalleryBitmapPool.getInstance().put(mBitmap);
            mBitmap = null;
        }
    }

    @Override
    public ScreenNail combine(ScreenNail other) {
        if (other == null) {
            return this;
        }

        if (!(other instanceof TiledScreenNail)) {
            recycle();
            return other;
        }

        // Now both are TiledScreenNail. Move over the information about width,
        // height, and Bitmap, then recycle the other.
        TiledScreenNail newer = (TiledScreenNail) other;
        mWidth = newer.mWidth;
        mHeight = newer.mHeight;
        if (newer.mTexture != null) {
            //if (mBitmap != null) GalleryBitmapPool.getInstance().put(mBitmap);
            if (mTexture != null) {
                mTexture.recycle();
            }
            mBitmap = newer.mBitmap;
            mTexture = newer.mTexture;
            newer.mBitmap = null;
            newer.mTexture = null;
        }
        newer.recycle();
        return this;
    }

}

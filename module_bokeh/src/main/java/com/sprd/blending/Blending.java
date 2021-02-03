package com.sprd.blending;

import android.graphics.Bitmap;

import com.sprd.blending.bean.Depth;
import com.sprd.blending.bean.ExtractResult;
import com.sprd.blending.bean.Mask;
import com.sprd.blending.bean.UpdateInfo;
import com.sprd.refocus.CommonRefocus;

import java.util.List;

/**
 * Created by cz on 17-10-16.
 */

public abstract class Blending {

    public abstract void initdepth(Depth Depth, int rotation, CommonRefocus commonRefocus);

    public abstract byte[] createmask(Bitmap srcImg, Depth depthImg, int[] rectedgs);

    public abstract ExtractResult getCreatemaskResult();

    /**
     * use before in sprd, call this before updatemask and getForgroundBitmap
     */
    public abstract Mask scaleMask(Mask mMask, int width, int height);

    public abstract Bitmap getForgroundBitmap(Bitmap srcImg, byte[] maskImg, int start_x, int start_y, int box_width, int box_height);

    public abstract byte[] undoUpdate(byte[] mask);

    public abstract int saveMask(byte[] mMask);

    public abstract byte[] updatemask(Bitmap srcImg, byte[] maskImg, List<UpdateInfo> infos);

    /**
     * need Empty implementation
     */
    public abstract int verify_position(byte[] mask, int mask_width, int mask_height, int start_x0, int start_y0,
                                        int center_x, int center_y, float zoom, float angle);

    /**
     * @param srcImg
     * @param maskImg
     * @param dstImg
     * @param start_x0
     * @param start_y0
     * @param center_x    just used for sprd
     * @param center_y    just used for sprd
     * @param scaleFactor just used for sprd
     * @param angle       just used for sprd
     */
    public abstract Bitmap doBlending(Bitmap srcImg, Mask maskImg, Bitmap dstImg,
                                      int start_x0, int start_y0, int center_x, int center_y,
                                      float scaleFactor, float angle);

    public abstract void destory();
}

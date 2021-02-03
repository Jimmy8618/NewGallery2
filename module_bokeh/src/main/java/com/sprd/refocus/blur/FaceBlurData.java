package com.sprd.refocus.blur;

import java.util.Arrays;

/**
 * blur1.2, face blur
 */

public class FaceBlurData extends CommonBlurData {

    public FaceBlurData(byte[] content) {
        super(content, TYPE_BLUR_FACE);
    }

    @Override
    public void initDatas(byte[] content) {
        initCommonBlurData(content);
        int weightMapSize = (yuvWidth * yuvHeight) * 2 / (Scalingratio * Scalingratio);
        jpegSize = content.length - mainYuvSize - weightMapSize - 116 * 4;
        weightMap = new byte[weightMapSize];
        mainYuv = new byte[mainYuvSize];

        int weightMapIndex = jpegSize;
        int mainYuvIndex = weightMapIndex + weightMapSize;
        System.arraycopy(content, weightMapIndex, weightMap, 0, weightMapSize);
        System.arraycopy(content, mainYuvIndex, mainYuv, 0, mainYuvSize);
    }

    @Override
    public String toString() {
        return "FaceBlurData{" +
                "\ncali_dist_seq=" + Arrays.toString(cali_dist_seq) +
                ", \ncali_dac_seq=" + Arrays.toString(cali_dac_seq) +
                ", \nx1=" + Arrays.toString(x1) +
                ", \ny1=" + Arrays.toString(y1) +
                ", \nx2=" + Arrays.toString(x2) +
                ", \ny2=" + Arrays.toString(y2) +
                ", \nflag=" + Arrays.toString(flag) +
                ", \nwin_peak_pos=" + Arrays.toString(win_peak_pos) +
                ", \nScalingratio=" + Scalingratio +
                ", \nSmoothWinSize=" + SmoothWinSize +
                ", \nvcm_dac_up_bound=" + vcm_dac_up_bound +
                ", \nvcm_dac_low_bound=" + vcm_dac_low_bound +
                ", \nvcm_dac_info=" + vcm_dac_info +
                ", \nvcm_dac_gain=" + vcm_dac_gain +
                ", \nvalid_depth_clip=" + valid_depth_clip +
                ", \nmethod=" + method +
                ", \nrow_num=" + row_num +
                ", \ncolumn_num=" + column_num +
                ", \nboundary_ratio=" + boundary_ratio +
                ", \nsel_size=" + sel_size +
                ", \nvalid_depth=" + valid_depth +
                ", \nslope=" + slope +
                ", \nvalid_depth_up_bound=" + valid_depth_up_bound +
                ", \nvalid_depth_low_bound=" + valid_depth_low_bound +
                ", \ncali_seq_len=" + cali_seq_len +
                ", \nrotation=" + rotation +
                ", \nyuvWidth=" + yuvWidth +
                ", \nyuvHeight=" + yuvHeight +
                ", \nmainYuvSize=" + mainYuvSize +
                ", \nrear_cam_en=" + rear_cam_en +
                ", \nroi_type=" + roi_type +
                ", \nblurIntensity=" + blurIntensity +
                ", \ncircle_size=" + circle_size +
                ", \nvalid_roi=" + valid_roi +
                ", \ntotal_roi=" + total_roi +
                ", \nmin_slope=" + min_slope +
                ", \nmax_slope=" + max_slope +
                ", \nfindex2gamma_adjust_ratio=" + findex2gamma_adjust_ratio +
                ", \nsel_x=" + sel_x +
                ", \nsel_y=" + sel_y +
                ", \nscaleSmoothWidth=" + scaleSmoothWidth +
                ", \nscaleSmoothHeight=" + scaleSmoothHeight +
                ", \nbox_filter_size=" + box_filter_size +
                ", \nversion=" + version +
                ", \nblurFlag='" + blurFlag + '\'' +
                '}';
    }
}

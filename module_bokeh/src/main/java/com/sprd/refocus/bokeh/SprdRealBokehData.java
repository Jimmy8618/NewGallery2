package com.sprd.refocus.bokeh;

import com.sprd.refocus.RefocusData;

public abstract class SprdRealBokehData extends RefocusData {

    //saveSr: 0 -> no save sr,need do sr and save, 1 -> do sr and save, defalt is 1;
    protected int saveSr = 1;
    protected int param_state;
    protected String bokehFlag;
    protected int dataVersion;
    protected int params_size;

    public SprdRealBokehData(byte[] content, int type) {
        super(content, type);
    }

    public int getSaveSr() {
        return saveSr;
    }

    public void setSaveSr(int saveSr) {
        this.saveSr = saveSr;
    }

    @Override
    public byte[] getDepthData() {
        return depthData;
    }

    @Override
    public void setDepthData(byte[] depthData) {
        this.depthData = depthData;
    }

    public int getParam_state() {
        return param_state;
    }

    public void setParam_state(int param_state) {
        this.param_state = param_state;
    }

    @Override
    public int getDepthHeight() {
        return depthHeight;
    }

    @Override
    public void setDepthHeight(int depthHeight) {
        this.depthHeight = depthHeight;
    }

    @Override
    public int getDepthWidth() {
        return depthWidth;
    }

    @Override
    public void setDepthWidth(int depthWidth) {
        this.depthWidth = depthWidth;
    }

    @Override
    public int getDepthSize() {
        return depthSize;
    }

    @Override
    public void setDepthSize(int depthSize) {
        this.depthSize = depthSize;
    }

    public String getBokehFlag() {
        return bokehFlag;
    }

    public void setBokehFlag(String bokehFlag) {
        this.bokehFlag = bokehFlag;
    }
}

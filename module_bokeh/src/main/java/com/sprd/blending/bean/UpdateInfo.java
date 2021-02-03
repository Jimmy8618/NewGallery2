package com.sprd.blending.bean;

import java.util.Arrays;

/**
 * Created by cz on 17-10-16.
 */

public class UpdateInfo {

    private int[] movex;
    private int[] movey;
    private boolean isupdate;
    private boolean isforground;

    public UpdateInfo(int[] movex, int[] movey, boolean isupdate, boolean isforground) {
        this.movex = movex;
        this.movey = movey;
        this.isupdate = isupdate;
        this.isforground = isforground;
    }

    public UpdateInfo() {
    }

    public int[] getMovex() {
        return movex;
    }

    public void setMovex(int[] movex) {
        this.movex = movex;
    }

    public int[] getMovey() {
        return movey;
    }

    public void setMovey(int[] movey) {
        this.movey = movey;
    }

    public boolean isupdate() {
        return isupdate;
    }

    public void setIsupdate(boolean isupdate) {
        this.isupdate = isupdate;
    }

    public boolean isforground() {
        return isforground;
    }

    public void setIsforground(boolean isforground) {
        this.isforground = isforground;
    }


    @Override
    public String toString() {
        return "UpdateInfo{" +
                "movex=" + Arrays.toString(movex) +
                ", movey=" + Arrays.toString(movey) +
                ", isupdate=" + isupdate +
                ", isforground=" + isforground +
                '}';
    }
}

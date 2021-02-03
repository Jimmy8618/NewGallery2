
package com.android.gallery3d.sidebar;

public class SideBarItem {
    public static final String ALL = "all";
    public static final String PHOTO = "photo";
    public static final String VIDEO = "video";
    public static final String ALBUM = "album";

    private String key;
    private int normalDrawableResId;
    private int selectedDrawableResId;
    private String title;
    private boolean selected;

    public SideBarItem(String key, String title, int normalDrawableResId, int selectedDrawableResId) {
        this.key = key;
        this.title = title;
        this.normalDrawableResId = normalDrawableResId;
        this.selectedDrawableResId = selectedDrawableResId;
    }

    public int getNormalDrawable() {
        return normalDrawableResId;
    }

    public int getSelectedDrawable() {
        return selectedDrawableResId;
    }

    public String getTitle() {
        return title;
    }

    public String getKey() {
        return key;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

}

package com.android.gallery3d.v2.util;

/**
 * 同时点击两个图片查看(或者点击两个相册,等), 会加载两次浏览 fragment,
 * 因此为了避免出现此情况, 需要两次点击事件 间隔时间 大于 INTERVAL 值才触发
 */
public class ClickInterval {
    private static final long INTERVAL = 500L; //500ms

    private static long sClickedTime = 0L;

    /**
     * 判断是否处理点击事件
     *
     * @return true 就不要处理
     */
    public static boolean ignore() {
        long currentTime = System.currentTimeMillis();
        long delta = currentTime - sClickedTime;
        sClickedTime = currentTime;
        return delta < INTERVAL;
    }
}

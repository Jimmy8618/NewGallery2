package com.android.gallery3d.util;

import android.content.Context;

import com.android.gallery3d.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by apuser on 3/6/17.
 */

public class DateUtils {
    private static final String TAG = DateUtils.class.getSimpleName();

    public static String timeStringWithDateInMs(Context context, long ms) {
        int[] DATE_IN_WEEK_INDEX = new int[]{0, 6, 0, 1, 2, 3, 4, 5};
        StringBuffer timeString = new StringBuffer();
        Date date = new Date(ms);

        Calendar calendar = Calendar.getInstance();
        int curYear = calendar.get(Calendar.YEAR);
        int curDayInYear = calendar.get(Calendar.DAY_OF_YEAR);
        int curDayInWeek = DATE_IN_WEEK_INDEX[calendar.get(Calendar.DAY_OF_WEEK)];

        String country = context.getResources().getConfiguration().locale.getCountry();
        SimpleDateFormat afterTodayFormat;
        SimpleDateFormat inAWeekFormat;
        SimpleDateFormat inAYearFormat;
        SimpleDateFormat beforeThisYearFormat;

        if (country.equals("XA")) {
            afterTodayFormat = new SimpleDateFormat("EEEE, MMM d, yyyy");
            inAWeekFormat = new SimpleDateFormat("EEEE");
            inAYearFormat = new SimpleDateFormat("EEEE, MMM d");
            beforeThisYearFormat = new SimpleDateFormat("EEEE, MMM d, yyyy");
        } else {
            afterTodayFormat = new SimpleDateFormat(context.getString(R.string.label_after_today_format));
            inAWeekFormat = new SimpleDateFormat(context.getString(R.string.label_in_a_week_format));
            inAYearFormat = new SimpleDateFormat(context.getString(R.string.label_in_a_year_format));
            beforeThisYearFormat = new SimpleDateFormat(context.getString(R.string.label_before_this_year_format));
        }

        try {
            calendar.setTime(date);
            int msYear = calendar.get(Calendar.YEAR);
            int msDayInYear = calendar.get(Calendar.DAY_OF_YEAR);
            int msDayInWeek = DATE_IN_WEEK_INDEX[calendar.get(Calendar.DAY_OF_WEEK)];

            if ((msYear > curYear) || (msYear == curYear && msDayInYear > curDayInYear)) {//图片日期 在 今天 之后
                timeString.append(afterTodayFormat.format(date));
            } else if ((msYear == curYear) && (msDayInYear == curDayInYear)) {// 图片日期 等于 今天
                timeString.append(context.getString(R.string.label_today));
            } else if ((msYear == curYear) && (msDayInYear + 1 == curDayInYear)) {// 图片日期 等于 昨天
                timeString.append(context.getString(R.string.label_yesterday));
            } else if ((msYear == curYear) && (curDayInYear > msDayInYear && curDayInYear - msDayInYear < 7) && (curDayInWeek > msDayInWeek)) {// 图片日期 在 今天之前 一周以内
                timeString.append(inAWeekFormat.format(date));
            } else if (msYear < curYear) {// 图片日期 在 今年之前
                timeString.append(beforeThisYearFormat.format(date));
            } else {// 图片日期 在 今天之前 一周以外
                timeString.append(inAYearFormat.format(date));
            }
        } catch (Exception e) {
        }
        return timeString.toString();
    }
}

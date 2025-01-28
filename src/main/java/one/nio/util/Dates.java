/*
 * Copyright 2025 VK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.util;

import java.util.TimeZone;

/**
 * Fast garbage-free algorithms for manipulating calendar dates
 */
public class Dates {
    public static final TimeZone TIME_ZONE = TimeZone.getDefault();

    public static final long JAN_1_1600 = -11676096000000L;
    public static final long MS_IN_DAY = 24 * 3600 * 1000;
    public static final int DAYS_IN_400_YEARS = 146097;
    public static final int DAYS_IN_4_YEARS = 1461;
    public static final int MONTH_AND_DAY_MASK = 0x1ff;
    public static final int FEB_29 = 2 << 5 | 29;

    // Map dayNum -> year|month|day
    private static final short[] CALENDAR = new short[DAYS_IN_4_YEARS];

    static {
        int dayNum = 0;
        for (int year = 0; year < 4; year++) {
            for (int month = 1; month <= 12; month++) {
                int days = daysInMonth(month, year);
                for (int day = 1; day <= days; day++) {
                    CALENDAR[dayNum++] = (short) (year << 9 | month << 5 | day);
                }
            }
        }
    }

    // Encode year|month|day into a single integer date
    public static int encode(int year, int month, int day) {
        return year << 9 | month << 5 | day;
    }

    public static int year(int date) {
        return date >> 9;
    }

    public static int month(int date) {
        return (date >>> 5) & 15;
    }

    public static int day(int date) {
        return date & 31;
    }

    public static boolean isLeapYear(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    public static int daysInMonth(int month, int year) {
        return month == 2 ? (isLeapYear(year) ? 29 : 28) : 30 + ((0b1010110101010 >>> month) & 1);
    }

    // Translate UTC timestamp to local time zone
    public static long asLocal(long millis) {
        return millis + TIME_ZONE.getOffset(millis);
    }

    // Number of days since Jan 1, 1600
    public static int dayNum(long millis) {
        return (int) ((millis - JAN_1_1600) / MS_IN_DAY);
    }

    // Parse timestamp into year|month|day
    public static int dateOfMillis(long millis) {
        int dayNum = dayNum(millis);
        int y400 = dayNum / DAYS_IN_400_YEARS;
        dayNum %= DAYS_IN_400_YEARS;
        dayNum += (dayNum - 60) / 36524;
        int y4 = dayNum / DAYS_IN_4_YEARS;
        return ((y400 * 400 + y4 * 4 + 1600) << 9) + CALENDAR[dayNum % DAYS_IN_4_YEARS];
    }

    // Number of full years between two timestamps. Positive when fromMillis < toMillis
    public static int yearsBetween(long fromMillis, long toMillis) {
        int fromDate = dateOfMillis(fromMillis);
        int toDate = dateOfMillis(toMillis);
        if ((fromDate & MONTH_AND_DAY_MASK) == FEB_29 && !isLeapYear(toDate >> 9)) {
            fromDate--;
        }
        return (toDate - fromDate) >> 9;
    }

    // Number of days between two timestamps. Positive when fromMillis < toMillis
    public static int daysBetween(long fromMillis, long toMillis) {
        return dayNum(toMillis) - dayNum(fromMillis);
    }
}

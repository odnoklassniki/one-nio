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

import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

public class DateFormatTest {

    @Test
    public void testPatterns() {
        testPattern("yyyy-MM-dd HH:mm:ss.SSS");
        testPattern("EEE, dd MMM yyyy HH:mm:ssZZZ");
        testPattern("EEE, dd MMM yyyy HH:mm:ssZ", "GMT");
        testPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", "GMT");
        testPattern("dd.MM.yy", "PST");
        testPattern("HH:mm");
        testPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", "UTC");
    }

    private void testPattern(String pattern) {
        testPattern(pattern, DateFormat.ofPattern(pattern), TimeZone.getDefault());
    }

    private void testPattern(String pattern, String timeZone) {
        testPattern(pattern, DateFormat.ofPattern(pattern, timeZone), TimeZone.getTimeZone(timeZone));
    }

    private void testPattern(String pattern, DateFormat format, TimeZone timeZone) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
        sdf.setTimeZone(timeZone);

        long date = Dates.JAN_1_1600 + Dates.MS_IN_DAY;
        for (int i = 0; i < 200_000; i++) {
            String orig = sdf.format(new Date(date));
            String my = format.format(date);
            Assert.assertEquals(orig, my);
            date += ThreadLocalRandom.current().nextLong(Dates.MS_IN_DAY * 2);
        }
    }
}

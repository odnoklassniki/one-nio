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
 * High performance thread-safe date and time formatter
 * that uses dynamic bytecode generation to precompile patterns.
 * Supports a subset of SimpleDateFormat. Produces almost no garbage.
 */
public abstract class DateFormat {

    public abstract String format(long millis);

    public static DateFormat ofPattern(String pattern) {
        return ofPattern(pattern, Dates.TIME_ZONE);
    }

    public static DateFormat ofPattern(String pattern, TimeZone timeZone) {
        return ofPattern(pattern, timeZone.getID());
    }

    public static DateFormat ofPattern(String pattern, String timeZone) {
        return DateFormatGenerator.generateForPattern(pattern, timeZone);
    }
}

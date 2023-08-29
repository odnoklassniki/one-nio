package one.nio.util;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Parser for subset of rfc2616 (http) and subset of ISO 1806 date formats.
 */
public class DateParser {

    public static long parse(String input) {
        if (input == null) {
            throw new NullPointerException("Cannot provide null input to date parser.");
        }
        // simple detection for rfc2616
        if (input.endsWith("GMT")) {
            return parseRfc2616(input);
        }
        return parseIso1806(input);
    }

    private static long parseIso1806(String input) {
        // expecting format
        // DDDD-DD-DD'T'DD:DD:DD[.D*]
        // DDDD-DD-DD'T'DD:DD:DD[.D*]Z
        // DDDD-DD-DD'T'DD:DD:DD[.D*]+DD:DD
        // DDDD-DD-DD'T'DD:DD:DD[.D*]-DD:DD
        // DDDD-DD-DD'T'DD:DD:DD[.D*]+DDDD
        // DDDD-DD-DD'T'DD:DD:DD[.D*]-DDDD

        if (input.charAt(10) != 'T') {
            throw new IllegalArgumentException("Expecting T in position 10.");
        }

        if (input.charAt(4) != '-' && input.charAt(7) != '-') {
            throw new IllegalArgumentException("Expecting - at position 4 and 7.");
        }

        if (input.charAt(13) != ':' && input.charAt(16) != ':') {
            throw new IllegalArgumentException("Expecting : at position 13 and 16.");
        }

        int year = parseNumber4(input, 0);
        int month = parseNumber2(input, 5);
        int day = parseNumber2(input, 8);

        int hour = parseNumber2(input, 11);
        int minute = parseNumber2(input, 14);
        int second = parseNumber2(input, 17);

        int extraPrecisionDigits = 0;
        int nanoseconds = 0;
        if (input.length() >= 20) {
            if (input.charAt(19) == '.') {
                for (int i = 20; i < input.length(); i++) {
                    final char ch = input.charAt(i);
                    if (ch >= '0' && ch <= '9') {
                        extraPrecisionDigits++;
                    } else {
                        break;
                    }
                }
                if (extraPrecisionDigits > 9) {
                    throw new IllegalArgumentException("Cannot parse more than 9 digits for subsecond time.");
                }

                int multiplier = 100_000_000;
                for (int i = 20; i < 20 + extraPrecisionDigits; i++) {
                    final char ch = input.charAt(i);
                    if (ch >= '0' && ch <= '9') {
                        nanoseconds += (ch - 48) * multiplier;
                        multiplier /= 10;
                    }
                }
                extraPrecisionDigits++; // incremented as used for offset in charAt next
            }
            int candidateForTimeZoneOffset = 19 + extraPrecisionDigits;
            if (candidateForTimeZoneOffset < input.length()) {
                // must have time zone
                char chTz = input.charAt(candidateForTimeZoneOffset);
                if (chTz == '+' || chTz == '-') {
                    // expecting format +DD:DD or -DD:DD or +DDDD -DDDD
                    int hours = parseNumber2(input, candidateForTimeZoneOffset + 1);

                    int minutes;
                    if (input.charAt(candidateForTimeZoneOffset + 3) == ':') {
                        minutes = parseNumber2(input, candidateForTimeZoneOffset + 4);
                    } else {
                        minutes = parseNumber2(input, candidateForTimeZoneOffset + 3);
                    }

                    ZoneOffset zoneOffset;
                    if (chTz == '-') {
                        zoneOffset = ZoneOffset.ofTotalSeconds(-hours * 60 * 60 - minutes * 60);
                    } else {
                        zoneOffset = ZoneOffset.ofTotalSeconds(hours * 60 * 60 + minutes * 60);
                    }
                    return ZonedDateTime.of(year, month, day, hour, minute, second, nanoseconds, zoneOffset).toInstant().toEpochMilli();
                } else if (chTz != 'Z') {
                    throw new IllegalArgumentException("Failed to parse timezone info.");
                }
            }
        }
        return ZonedDateTime.of(year, month, day, hour, minute, second, nanoseconds, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static long parseRfc2616(String input) {
        // expecting format
        // Sun, 06 Nov 1994 08:49:37 GMT
        // CCC, DD CCC DDDD DD:DD:DD GMT

        if (input.charAt(3) != ',' || input.charAt(4) != ' ' || input.charAt(7) != ' ' || input.charAt(11) != ' '
                || input.charAt(16) != ' ' || input.charAt(25) != ' ') {
            throw new IllegalArgumentException("Invalid or unsupported rfc2616 date expecting format 'CCC, DD CCC DDDD DD:DD:DD GMT'.");
        }

        int day = parseNumber2(input, 5);

        char chM1 = input.charAt(8);
        char chM2 = input.charAt(9);
        char chM3 = input.charAt(10);

        // Apr, Aug
        // Feb,
        // Jan, Jun, Jul
        // Mar, May
        // Nov
        // Oct
        // Sep
        // Dec

        int month = -1;
        if (chM1 == 'J' && chM2 == 'a') {
            month = 1;
        }
        if (chM1 == 'F' && chM2 == 'e') {
            month = 2;
        }
        if (chM1 == 'M' && chM2 == 'a' && chM3 == 'r') {
            month = 3;
        }
        if (chM1 == 'A' && chM2 == 'p') {
            month = 4;
        }
        if (chM1 == 'M' && chM2 == 'a' && chM3 == 'y') {
            month = 5;
        }
        if (chM1 == 'J' && chM2 == 'u' && chM3 == 'n') {
            month = 6;
        }
        if (chM1 == 'J' && chM2 == 'u' && chM3 == 'l') {
            month = 7;
        }
        if (chM1 == 'A' && chM2 == 'u') {
            month = 8;
        }
        if (chM1 == 'S' && chM2 == 'e') {
            month = 9;
        }
        if (chM1 == 'O' && chM2 == 'c') {
            month = 10;
        }
        if (chM1 == 'N' && chM2 == 'o') {
            month = 11;
        }
        if (chM1 == 'D' && chM2 == 'e') {
            month = 12;
        }

        if (month == -1) {
            throw new IllegalArgumentException("Failed to parse month.");
        }

        int year = parseNumber4(input, 12);
        int hour = parseNumber2(input, 17);
        int minute = parseNumber2(input, 20);
        int second = parseNumber2(input, 23);

        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static int parseNumber2(CharSequence offsetId, int pos) {
        char ch1 = offsetId.charAt(pos);
        char ch2 = offsetId.charAt(pos + 1);
        if (ch1 < '0' || ch1 > '9' || ch2 < '0' || ch2 > '9') {
            throw new IllegalArgumentException("non numeric characters found: " + offsetId);
        }
        return (ch1 - 48) * 10 + (ch2 - 48);
    }

    private static int parseNumber4(CharSequence offsetId, int pos) {
        char ch1 = offsetId.charAt(pos);
        char ch2 = offsetId.charAt(pos + 1);
        char ch3 = offsetId.charAt(pos + 2);
        char ch4 = offsetId.charAt(pos + 3);
        if (ch1 < '0' || ch1 > '9' || ch2 < '0' || ch2 > '9' || ch3 > '9' || ch3 < '0' || ch4 > '9' || ch4 < '0') {
            throw new IllegalArgumentException("non numeric characters found: " + offsetId);
        }
        return (ch1 - 48) * 1000 + (ch2 - 48) * 100 + (ch3 - 48) * 10 + (ch4 - 48);
    }
}

package one.nio.http;

import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public final class HttpDate extends GregorianCalendar {
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final String[] DAYS = { null, "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    private static final String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    private char[] chars = "Thu, 01 Jan 1970 00:00:00 GMT".toCharArray();

    public HttpDate() {
        super(GMT, Locale.US);
    }

    public char[] toCharArray() {
        return chars;
    }

    public String toString() {
        return new String(chars);
    }

    public void setTime(long time) {
        super.setTimeInMillis(time);
        DAYS[fields[DAY_OF_WEEK]].getChars(0, 3, chars, 0);
        MONTHS[fields[MONTH]].getChars(0, 3, chars, 8);
        setField( 5, fields[DAY_OF_MONTH]);
        setField(12, fields[YEAR] / 100);
        setField(14, fields[YEAR] % 100);
        setField(17, fields[HOUR_OF_DAY]);
        setField(20, fields[MINUTE]);
        setField(23, fields[SECOND]);
    }

    private void setField(int pos, int value) {
        chars[pos]     = (char) ('0' + value / 10);
        chars[pos + 1] = (char) ('0' + value % 10);
    }
}

package one.nio.util;

import junit.framework.TestCase;

public class URLEncoderTest extends TestCase {

    public static void testEncode() {
        final String[] pairs = {
                "SimpleString1_SimpleString2_SimpleString3", "SimpleString1_SimpleString2_SimpleString3",
                "param1=value1&param2=value", "param1%3Dvalue1%26param2%3Dvalue",
                "Просто-Строка", "%D0%9F%D1%80%D0%BE%D1%81%D1%82%D0%BE-%D0%A1%D1%82%D1%80%D0%BE%D0%BA%D0%B0",
                "one.nio.mem:type=MallocMT,*#FreeMemory", "one.nio.mem%3Atype%3DMallocMT%2C*%23FreeMemory"
        };

        for (int i = 0; i < pairs.length; i += 2) {
            assertEquals(pairs[i], URLEncoder.decode(pairs[i + 1]));
            assertEquals(pairs[i + 1], URLEncoder.encode(pairs[i]));
        }

        assertEquals(" A B ", URLEncoder.decode("+A+B+"));
    }
}

package one.nio.util;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class DateParserTest {

    @Test
    public void testParsing() {
        // rfc2616
        Assert.assertEquals(757846177000L, DateParser.parse("Thu, 06 Jan 1994 08:49:37 GMT"));
        Assert.assertEquals(760524577000L, DateParser.parse("Sun, 06 Feb 1994 08:49:37 GMT"));
        Assert.assertEquals(762943777000L, DateParser.parse("Sun, 06 Mar 1994 08:49:37 GMT"));
        Assert.assertEquals(765622177000L, DateParser.parse("Wed, 06 Apr 1994 08:49:37 GMT"));
        Assert.assertEquals(768214177000L, DateParser.parse("Fri, 06 May 1994 08:49:37 GMT"));
        Assert.assertEquals(770892577000L, DateParser.parse("Mon, 06 Jun 1994 08:49:37 GMT"));
        Assert.assertEquals(773484577000L, DateParser.parse("Wed, 06 Jul 1994 08:49:37 GMT"));
        Assert.assertEquals(776162977000L, DateParser.parse("Sat, 06 Aug 1994 08:49:37 GMT"));
        Assert.assertEquals(778841377000L, DateParser.parse("Tue, 06 Sep 1994 08:49:37 GMT"));
        Assert.assertEquals(781433377000L, DateParser.parse("Thu, 06 Oct 1994 08:49:37 GMT"));
        Assert.assertEquals(784111777000L, DateParser.parse("Sun, 06 Nov 1994 08:49:37 GMT"));
        Assert.assertEquals(786703777000L, DateParser.parse("Sun, 06 Dec 1994 08:49:37 GMT"));

        Assert.assertEquals(1677701552000L, DateParser.parse("Wed, 01 Mar 2023 20:12:32 GMT"));
        Assert.assertEquals(1692785121000L, DateParser.parse("Wed, 23 Aug 2023 10:05:21 GMT"));

        // subset of iso1806
        Assert.assertEquals(1677701552000L, DateParser.parse("2023-03-01T20:12:32Z"));
        Assert.assertEquals(1677701552000L, DateParser.parse("2023-03-01T21:12:32+01:00"));
        Assert.assertEquals(1677699752000L, DateParser.parse("2023-03-01T21:12:32+01:30"));
        Assert.assertEquals(1677710552000L, DateParser.parse("2023-03-01T21:12:32-01:30"));
        Assert.assertEquals(1677701552000L, DateParser.parse("2023-03-01T21:12:32+0100"));
        Assert.assertEquals(1677699752000L, DateParser.parse("2023-03-01T21:12:32+0130"));
        Assert.assertEquals(1677710552000L, DateParser.parse("2023-03-01T21:12:32-0130"));

        // also support without Z, for extra compatibility
        Assert.assertEquals(1677701552000L, DateParser.parse("2023-03-01T20:12:32"));

        // support millis
        Assert.assertEquals(1692785121234L, DateParser.parse("2023-08-23T10:05:21.234"));
        Assert.assertEquals(1692785121234L, DateParser.parse("2023-08-23T10:05:21.234Z"));
        Assert.assertEquals(1692785121234L, DateParser.parse("2023-08-23T12:05:21.234+02:00"));

        // decided to support micros and nanos as well, they just get trimmed, for extra compatibility
        Assert.assertEquals(1692785121234L, DateParser.parse("2023-08-23T10:05:21.234567Z"));
        Assert.assertEquals(1692785121234L, DateParser.parse("2023-08-23T12:05:21.234567890+02:00"));
        Assert.assertEquals(1660816887967L, DateParser.parse("2022-08-18T12:01:27.967875+0200"));
    }

    @Test
    @Ignore
    public void nonSupportedFormats() {
        // part of rfc2616
        // RFC 850, obsoleted by RFC 1036
        Assert.assertNotEquals(784111777000L, DateParser.parse("Sunday, 06-Nov-94 08:49:37 GMT"));
        // ANSI C's asctime() format
        Assert.assertNotEquals(784111777000L, DateParser.parse("Sun Nov  6 08:49:37 1994"));

        // other iso1806 and similar
        Assert.assertNotEquals(1677701552000L, DateParser.parse("20230301T211232+01:00"));
        Assert.assertNotEquals(1677701552000L, DateParser.parse("2023-03-01 20:12:32+00:00"));
        Assert.assertNotEquals(1677701552000L, DateParser.parse("2023-03-01 21:12:32+01:00"));
        Assert.assertNotEquals(1692785121234L, DateParser.parse("20230823T100521.234Z"));
        Assert.assertNotEquals(1692785121234L, DateParser.parse("20230823T120521.234+02:00"));
        Assert.assertNotEquals(1692785121234L, DateParser.parse("2023-08-23 10:05:21.234+00:00"));
        Assert.assertNotEquals(1692785121234L, DateParser.parse("2023-08-23 12:05:21.234+02:00"));
    }
}
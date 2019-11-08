/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.http;

import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RequestParametersTest {
    private static Iterator<Map.Entry<String, String>> from(final String path) {
        return new Request(Request.METHOD_GET, path, true).getParameters().iterator();
    }

    @Test
    public void nothing() {
        assertFalse(from("").hasNext());
    }

    @Test
    public void emptyQuery() {
        assertFalse(from("?").hasNext());
    }

    @Test
    public void emptyParameter() {
        assertFalse(from("?&").hasNext());
    }

    @Test
    public void one() {
        final Iterator<Map.Entry<String, String>> params = from("?key=value");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key", param1.getKey());
        assertEquals("value", param1.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void compositeValue() {
        final Iterator<Map.Entry<String, String>> params = from("?key=value=something");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key", param1.getKey());
        assertEquals("value=something", param1.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void emptyValue() {
        final Iterator<Map.Entry<String, String>> params = from("?key=");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key", param1.getKey());
        assertEquals("", param1.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void onlyKey() {
        final Iterator<Map.Entry<String, String>> params = from("?key");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key", param1.getKey());
        assertEquals("", param1.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void two() {
        final Iterator<Map.Entry<String, String>> params =
                from("?key1=value1&key2=value2");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key1", param1.getKey());
        assertEquals("value1", param1.getValue());
        final Map.Entry<String, String> param2 = params.next();
        assertEquals("key2", param2.getKey());
        assertEquals("value2", param2.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void twoWithEmpty() {
        final Iterator<Map.Entry<String, String>> params =
                from("?&key1=value1&&key2=value2&");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key1", param1.getKey());
        assertEquals("value1", param1.getValue());
        final Map.Entry<String, String> param2 = params.next();
        assertEquals("key2", param2.getKey());
        assertEquals("value2", param2.getValue());
        assertFalse(params.hasNext());
    }

    @Test
    public void mixed() {
        final Iterator<Map.Entry<String, String>> params =
                from("?&key1=value1&&key2=&&key3&&key4=value4&&");
        final Map.Entry<String, String> param1 = params.next();
        assertEquals("key1", param1.getKey());
        assertEquals("value1", param1.getValue());
        final Map.Entry<String, String> param2 = params.next();
        assertEquals("key2", param2.getKey());
        assertEquals("", param2.getValue());
        final Map.Entry<String, String> param3 = params.next();
        assertEquals("key3", param3.getKey());
        assertEquals("", param3.getValue());
        final Map.Entry<String, String> param4 = params.next();
        assertEquals("key4", param4.getKey());
        assertEquals("value4", param4.getValue());
        assertFalse(params.hasNext());
    }
}

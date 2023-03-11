/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.serial;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class JsonTest implements Serializable {

    private long lng = Long.MIN_VALUE;
    private Object map = new HashMap<String, String>() {{
        put("someKey", "some \"Value\"");
    }};

    @Test
    public void basicTest() throws IOException {
        Object obj = Arrays.asList("abc", 1, 2.0, true, new JsonTest());
        Assert.assertEquals("[\"abc\",1,2.0,true,{\"lng\":\"-9223372036854775808\",\"map\":{\"someKey\":\"some \\\"Value\\\"\"}}]", Json.toJson(obj));
        TestObject object = new TestObject();
        object.name = "Maxim";
        Assert.assertEquals("{\"test_name\":\"Maxim\",\"date\":null}", Json.toJson(object));
    }

    @Test
    public void testDateParsing() throws IOException, ClassNotFoundException {
        Assert.assertEquals(0, Json.fromJson("{\"date\":\"0\"}", TestObject.class).date.getTime());
        Assert.assertEquals(123456789, Json.fromJson("{\"date\":123456789}", TestObject.class).date.getTime());
        Assert.assertEquals(-1678468117765L, Json.fromJson("{\"date\":\"-1678468117765\"}", TestObject.class).date.getTime());
        Assert.assertEquals(1678468117765L, Json.fromJson("{\"date\":\"1678468117765\"}", TestObject.class).date.getTime());
        Assert.assertEquals(1678561558935L, Json.fromJson("{\"date\":\"2023-03-11T19:05:58.935Z\"}", TestObject.class).date.getTime());
        Assert.assertEquals(1678561558935L, Json.fromJson("{\"date\":\"2023-03-11T20:05:58.935+01:00\"}", TestObject.class).date.getTime());
        Assert.assertEquals(1678561558000L, Json.fromJson("{\"date\":\"Sat, 11 Mar 2023 19:05:58 GMT\"}", TestObject.class).date.getTime());
        Assert.assertNull(Json.fromJson("{\"date\":\"\"}", TestObject.class).date);
        Assert.assertNull(Json.fromJson("{\"date\":\" \"}", TestObject.class).date);
    }

    public static class TestObject implements Serializable {
        @JsonName("test_name")
        public String name;
        public Date date;
    }
}

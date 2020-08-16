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

package one.nio.serial;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JsonReaderTest {
    private static final String sample = "[{\n" +
            "  \"created_at\": \"Thu Jun 22 21:00:00 +0000 2017\",\n" +
            "  \"id\": 877994604561387500,\n" +
            "  \"id_str\": \"877994604561387520\",\n" +
            "  \"text\": \"Creating a Grocery List Manager \\u0026 Display Items https://t.co/xFox12345 #Angular\",\n" +
            "  \"truncated\": false,\n" +
            "  \"entities\": {\n" +
            "    \"hashtags\": [{\n" +
            "      \"text\": \"Angular\",\n" +
            "      \"indices\": [103, 111]\n" +
            "    }],\n" +
            "    \"symbols\": [null],\n" +
            "    \"user_mentions\": [],\n" +
            "    \"urls\": [{\n" +
            "      \"url\": \"https://t.co/xFox12345\",\n" +
            "      \"expanded_url\": \"http://example.com/2sr60pf\",\n" +
            "      \"display_url\": \"example.com/2sr60pf\",\n" +
            "      \"indices\": [79, 102]\n" +
            "    }]\n" +
            "  },\n" +
            "  \"source\": \"<a href=\\\"http://example.com\\\" rel=\\\"nofollow\\\">Some link</a>\",\n" +
            "  \"user\": {\n" +
            "    \"id\": 772682964,\n" +
            "    \"id_str\": \"772682964\",\n" +
            "    \"name\": \"Example JavaScript\",\n" +
            "    \"screen_name\": \"ExampleJS\",\n" +
            "    \"location\": \"Melbourne, Australia\",\n" +
            "    \"description\": \"Keep up with JavaScript tutorials, tips, tricks and articles.\",\n" +
            "    \"url\": \"http://t.co/cCHxxxxx\",\n" +
            "    \"entities\": {\n" +
            "      \"url\": {\n" +
            "        \"urls\": [{\n" +
            "          \"url\": \"http://t.co/cCHxxxxx\",\n" +
            "          \"expanded_url\": \"http://example.com/javascript\",\n" +
            "          \"display_url\": \"example.com/javascript\",\n" +
            "          \"indices\": [0, 22]\n" +
            "        }]\n" +
            "      },\n" +
            "      \"description\": {\n" +
            "        \"urls\": []\n" +
            "      }\n" +
            "    },\n" +
            "    \"protected\": false,\n" +
            "    \"followers_count\": 2145,\n" +
            "    \"friends_count\": 18,\n" +
            "    \"listed_count\": 328,\n" +
            "    \"created_at\": \"Wed Aug 22 02:06:33 +0000 2012\",\n" +
            "    \"favourites_count\": 57,\n" +
            "    \"utc_offset\": 43200,\n" +
            "    \"time_zone\": \"Wellington\"\n" +
            "  }\n" +
            "}]";

    @Test
    public void jsonReaderWriter() throws IOException, ClassNotFoundException {
        JsonReader reader = new JsonReader(sample.getBytes());
        Object o1 = reader.readObject();
        String s1 = Json.toJson(o1);
        Object o2 = Json.fromJson(s1);
        String s2 = Json.toJson(o2);
        Assert.assertEquals(o1, o2);
        Assert.assertEquals(s1, s2);
    }

    @Test
    public void customClass() throws IOException, ClassNotFoundException {
        String s = Json.toJson(new Custom());
        System.out.println(s);
        JsonReader reader = new JsonReader(s.getBytes());
        Custom x = reader.readObject(Custom.class);
    }

    @Test
    public void nullableFields() throws IOException, ClassNotFoundException {
        String s = "{\"FB\": null, \"set\": []}";
        JsonReader reader = new JsonReader(s.getBytes());
        Custom x = reader.readObject(Custom.class);
        Assert.assertNull(x.FB);
        Assert.assertTrue(x.set instanceof Set && x.set.isEmpty());
    }

    static class Custom implements Serializable {
        int intValue;
        Long longValue = -77L;
        final String string = "zzz";
        Object[] FB = {"A", true};
        Set<String> set = Collections.emptySet();
        final Map<String, Integer> Ea = new HashMap<String, Integer>() {{
            put("one", 1);
            put("two", 2);
            put("three", 3);
        }};
    }
}

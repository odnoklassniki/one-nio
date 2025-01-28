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

package one.nio.serial;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

public class JsonTest implements Serializable {

    private long lng = Long.MIN_VALUE;
    private Object map = new HashMap<String, String>() {{
        put("someKey", "some \"Value\"");
    }};

    public static void main(String[] args) throws IOException {
        Object obj = Arrays.asList("abc", 1, 2.0, true, new JsonTest());
        System.out.println(Json.toJson(obj));

        TestObject object = new TestObject();
        object.name = "Maxim";
        System.out.println(Json.toJson(object));
    }

    public static class TestObject implements Serializable {
        @JsonName("test_name")
        public String name;
    }
}

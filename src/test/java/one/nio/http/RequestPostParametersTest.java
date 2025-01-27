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

package one.nio.http;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;


public class RequestPostParametersTest {
    private static final String EMPTY_VALUE = "";

    private static Request request(String body) {
        Request request = new Request(Request.METHOD_POST, "/", true);
        request.setBodyUtf8(body);
        return request;
    }

    @Test
    public void empty() {
        Map<String, String> params = request("").getPostParams();
        Assert.assertTrue(params.isEmpty());
    }

    @Test
    public void simple() {
        Map<String, String> params = request("key1=value1&key2=value2").getPostParams();
        Assert.assertEquals(2, params.size());
        Assert.assertEquals("value1", params.get("key1"));
        Assert.assertEquals("value2", params.get("key2"));
    }

    @Test
    public void encoded() {
        Map<String, String> params = request("key1=&encoded+key=value2&key3=a+b").getPostParams();

        Assert.assertEquals(3, params.size());
        Assert.assertEquals(EMPTY_VALUE, params.get("key1"));
        Assert.assertEquals("value2", params.get("encoded key"));
        Assert.assertEquals("a b", params.get("key3"));
    }

    @Test
    public void eqSignAndEmptyValAtTheEnd() {
        Map<String, String> params = request("key3=a+%3D+b&key2=value2&key1=").getPostParams();

        Assert.assertEquals(3, params.size());
        Assert.assertTrue(params.containsKey("key1"));
        Assert.assertEquals(EMPTY_VALUE, params.get("key1"));
        Assert.assertEquals("value2", params.get("key2"));
        Assert.assertEquals("a = b", params.get("key3"));
    }

    @Test
    public void filterBeginningWithEmptyPair() {
        Map<String, String> params = request("=&key2=value2&k3=&=").getPostParams();
        Assert.assertEquals(2, params.size());
        Assert.assertEquals("value2", params.get("key2"));
        Assert.assertEquals(EMPTY_VALUE, params.get("k3"));
    }

}

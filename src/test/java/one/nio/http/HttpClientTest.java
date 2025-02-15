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

import one.nio.net.ConnectionString;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        HttpClient client = new HttpClient(new ConnectionString(args[0]));
        String path = args[1];

        Response response = client.get(path);
        System.out.println("Status code: " + response.getStatus());
        System.out.println(response.toString());
        client.close();
    }
}

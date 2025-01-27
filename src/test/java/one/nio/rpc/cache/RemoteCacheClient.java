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

package one.nio.rpc.cache;

import one.nio.net.ConnectionString;
import one.nio.rpc.RpcClient;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class RemoteCacheClient {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            showUsage();
        }

        CacheService<Entity> remoteCacheClient = getCacheService(args[0]);
        String cmd = args[1];
        long id = Long.parseLong(args[2]);

        if (args.length == 3 && cmd.equals("get")) {
            Entity value = remoteCacheClient.get(id);
            System.out.println("get = " + value);
        } else if (args.length == 4 && cmd.equals("set")) {
            Entity value = new Entity(id, args[3]);
            remoteCacheClient.set(id, value);
            System.out.println("set = " + value);
        } else {
            showUsage();
        }
    }

    private static void showUsage() {
        System.out.println("Usage: java " + RemoteCacheClient.class.getName() + " <host> [get <id> | set <id> <name>]");
        System.exit(1);
    }

    @SuppressWarnings("unchecked")
    private static CacheService<Entity> getCacheService(String host) throws IOException {
        ConnectionString conn = new ConnectionString(host + ':' + CacheService.DEFAULT_PORT);
        RpcClient client = new RpcClient(conn);
        return (CacheService<Entity>) Proxy.newProxyInstance(
                CacheService.class.getClassLoader(), new Class[] { CacheService.class }, client);
    }
}

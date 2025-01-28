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

import one.nio.rpc.RpcServer;
import one.nio.server.ServerConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteCacheServer implements CacheService<Entity> {
    private Map<Long, Entity> cacheImpl;

    private RemoteCacheServer() {
        this.cacheImpl = new ConcurrentHashMap<>();
    }

    @Override
    public Entity get(long key) {
        return cacheImpl.get(key);
    }

    @Override
    public void set(long key, Entity value) {
        cacheImpl.put(key, value);
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.from("0.0.0.0:" + DEFAULT_PORT);
        RpcServer<CacheService> server = new RpcServer<CacheService>(config, new RemoteCacheServer());
        server.start();
    }
}

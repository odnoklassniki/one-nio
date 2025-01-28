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

import one.nio.cluster.ServiceProvider;
import one.nio.net.ConnectionString;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpProvider implements ServiceProvider {
    private final HttpClient client;
    private final AtomicBoolean available;
    private final AtomicInteger failures;

    public HttpProvider(ConnectionString conn) {
        this(new HttpClient(conn));
    }

    public HttpProvider(HttpClient client) {
        this.client = client;
        this.available = new AtomicBoolean(true);
        this.failures = new AtomicInteger();
    }

    public Response invoke(Request request) throws Exception {
        Response response = client.invoke(request);
        if (response.getStatus() >= 500) {
            throw new IOException(this + " call failed with status " + response.getHeaders()[0]);
        }
        return response;
    }

    public AtomicInteger getFailures() {
        return failures;
    }

    @Override
    public boolean available() {
        return available.get();
    }

    @Override
    public boolean check() throws Exception {
        Response response = client.head("/");
        if (response.getStatus() >= 500) {
            throw new IOException(this + " check failed with status " + response.getHeaders()[0]);
        }
        return true;
    }

    @Override
    public boolean enable() {
        if (available.compareAndSet(false, true)) {
            failures.set(0);
            return true;
        }
        return false;
    }

    @Override
    public boolean disable() {
        client.invalidateAll();
        return available.compareAndSet(true, false);
    }

    @Override
    public void close() {
        available.set(false);
        client.close();
    }

    @Override
    public String toString() {
        return "HttpProvider[" + client.name() + "]";
    }
}

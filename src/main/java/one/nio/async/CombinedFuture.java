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

package one.nio.async;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CombinedFuture<V> implements Future<List<V>>, Serializable {
    private final Future<V>[] futures;

    @SuppressWarnings("unchecked")
    public CombinedFuture(Future<V>... futures) {
        this.futures = futures;
    }

    @SuppressWarnings("unchecked")
    public CombinedFuture(Collection<Future> futures) {
        this.futures = futures.toArray(new Future[0]);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.cancel(mayInterruptIfRunning);
        }
        return result;
    }

    @Override
    public boolean isCancelled() {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.isCancelled();
        }
        return result;
    }

    @Override
    public boolean isDone() {
        boolean result = true;
        for (Future<V> future : futures) {
            result &= future.isDone();
        }
        return result;
    }

    @Override
    public List<V> get() throws InterruptedException, ExecutionException {
        ArrayList<V> result = new ArrayList<>(futures.length);
        for (Future<V> future : futures) {
            result.add(future.get());
        }
        return result;
    }

    @Override
    public List<V> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        ArrayList<V> result = new ArrayList<>(futures.length);
        for (Future<V> future : futures) {
            result.add(future.get(timeout, unit));
        }
        return result;
    }
}

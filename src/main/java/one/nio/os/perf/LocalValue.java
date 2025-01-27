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

package one.nio.os.perf;

public class LocalValue implements CounterValue {
    public static final LocalValue ZERO = new LocalValue(0, 0, 0);
    
    public final long value;
    public final long running;
    public final long enabled;

    public LocalValue(long value, long running, long enabled) {
        this.value = value;
        this.running = running;
        this.enabled = enabled;
    }

    @Override
    public double scalingFactor() {
        return running == enabled ? 1. : (double) enabled / running;
    }

    @Override
    public double runningFraction() {
        return running == enabled ? 1. : (double) running / enabled;
    }

    @Override
    public long normalized() {
        return enabled == running ? value : (long) (value * scalingFactor());
    }

    @Override
    public CounterValue sub(CounterValue prev) {
        return sub((LocalValue) prev);
    }

    public LocalValue sub(LocalValue v) {
        return new LocalValue(value - v.value, running - v.running, enabled - v.enabled);
    }
}

/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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

public class GlobalValue implements CounterValue {
    private final long[] buffer;

    GlobalValue(long[] buffer) {
        this.buffer = buffer;
    }

    @Override
    public double scalingFactor() {
        return 1. / runningFraction();
    }

    @Override
    public double runningFraction() {
        long enabled = 0;
        long running = 0;
        for (int i = 0; i < buffer.length; i += 3) {
            enabled += buffer[i + 1];
            running += buffer[i + 2];
        }
        return enabled == running ? 1. : 1. * running / enabled;
    }

    @Override
    public long normalized() {
        double total = 0;
        for (int i = 0; i < buffer.length; i += 3) {
            long value = buffer[i];
            long enabled = buffer[i + 1];
            long running = buffer[i + 2];
            if (enabled == running) {
                total += value;
            } else if (running > 0) {
                total += (double) value * enabled / running;
            }
        }
        return (long) total;
    }

    @Override
    public CounterValue sub(CounterValue prev) {
        return sub((GlobalValue) prev);
    }

    public GlobalValue sub(GlobalValue prev) {
        long[] buffer = this.buffer.clone();
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] -= prev.buffer[i];
        }
        return new GlobalValue(buffer);
    }
}

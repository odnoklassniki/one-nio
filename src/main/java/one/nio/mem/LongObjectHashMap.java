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

package one.nio.mem;

import java.util.Arrays;

public class LongObjectHashMap<T> extends LongHashSet {
    private static final long base = unsafe.arrayBaseOffset(Object[].class);
    private static final int shift = 31 - Integer.numberOfLeadingZeros(unsafe.arrayIndexScale(Object[].class));

    protected final Object[] values;

    @SuppressWarnings("unchecked")
    public LongObjectHashMap(int capacity) {
        super(capacity);
        this.values = new Object[this.capacity];
    }

    public T get(long key) {
        int index = getKey(key);
        return index >= 0 ? valueAt(index) : null;
    }

    public void put(long key, T value) {
        int index = putKey(key);
        setValueAt(index, value);
    }

    public boolean replace(long key, T oldValue, T newValue) {
        int index = getKey(key);
        return index >= 0 && unsafe.compareAndSwapObject(values, offset(index), oldValue, newValue);
    }

    public T replace(long key, T newValue) {
        int index = putKey(key);
        return replaceValueAt(index, newValue);
    }

    public T remove(long key) {
        int index = getKey(key);
        return index >= 0 ? replaceValueAt(index, null) : null;
    }

    @SuppressWarnings("unchecked")
    public final T valueAt(int index) {
        return (T) values[index];
    }

    public final void setValueAt(int index, T value) {
        values[index] = value;
    }

    @SuppressWarnings("unchecked")
    public final T replaceValueAt(int index, T newValue) {
        long offset = offset(index);
        for (;;) {
            Object oldValue = unsafe.getObjectVolatile(values, offset);
            if (unsafe.compareAndSwapObject(values, offset, oldValue, newValue)) {
                return (T) oldValue;
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        Arrays.fill(values, null);
    }

    private static long offset(int index) {
        return base + (((long) index) << shift);
    }
}

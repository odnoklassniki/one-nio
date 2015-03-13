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

package one.nio.serial;

class SerializationContext {
    private static final int INITIAL_CAPACITY = 64;

    private Object first;
    private Object[] keys;
    private int[] values;
    private int size;
    private int threshold;

    public int put(Object obj) {
        if (first == null) {
            first = obj;
            return -1;
        }

        Object[] keys = this.keys;
        if (keys == null) keys = init();
        int mask = keys.length - 1;

        int i = System.identityHashCode(obj) & mask;
        while (keys[i] != null) {
            if (keys[i] == obj) {
                return values[i];
            }
            i = (i + 1) & mask;
        }

        keys[i] = obj;
        values[i] = size;
        if (++size >= threshold) resize();
        return -1;
    }

    public int indexOf(Object obj) {
        if (first == null) {
            return Integer.MIN_VALUE;
        }

        Object[] keys = this.keys;
        if (keys == null) keys = init();
        int mask = keys.length - 1;

        int i = System.identityHashCode(obj) & mask;
        while (keys[i] != null) {
            if (keys[i] == obj) {
                return values[i];
            }
            i = (i + 1) & mask;
        }
        return ~i;
    }

    public void putAt(int index, Object obj) {
        if (index == Integer.MIN_VALUE) {
            first = obj;
        } else {
            keys[~index] = obj;
            values[~index] = size;
            if (++size >= threshold) resize();
        }
    }

    private Object[] init() {
        this.keys = new Object[INITIAL_CAPACITY];
        this.values = new int[INITIAL_CAPACITY];
        this.size = 1;
        this.threshold = INITIAL_CAPACITY * 2 / 3;

        keys[System.identityHashCode(first) & (INITIAL_CAPACITY - 1)] = first;
        return keys;
    }

    private void resize() {
        Object[] keys = this.keys;
        int[] values = this.values;

        int newLength = keys.length * 2;
        Object[] newKeys = new Object[newLength];
        int[] newValues = new int[newLength];
        int mask = newLength - 1;

        for (int i = 0; i < keys.length; i++) {
            Object obj = keys[i];
            if (obj != null) {
                int j = System.identityHashCode(obj) & mask;
                while (newKeys[j] != null) {
                    j = (j + 1) & mask;
                }
                newKeys[j] = obj;
                newValues[j] = values[i];
            }
        }

        this.keys = newKeys;
        this.values = newValues;
        this.threshold = newLength * 2 / 3;
    }
}

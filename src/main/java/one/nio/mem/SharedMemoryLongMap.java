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

import one.nio.util.Hash;

import java.io.IOException;

public class SharedMemoryLongMap<V> extends SharedMemoryMap<Long, V> {

    public SharedMemoryLongMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    public SharedMemoryLongMap(int capacity, String fileName, long fileSize, long expirationTime) throws IOException {
        super(capacity, fileName, fileSize, expirationTime);
    }

    @Override
    protected Long keyAt(long entry) {
        // Recover original key from a hashCode
        return Hash.twang_unmix(unsafe.getLong(entry + HASH_OFFSET));
    }

    @Override
    protected long hashCode(Long key) {
        // Shuffle bits in order to randomize buckets for close keys
        return Hash.twang_mix(key);
    }

    @Override
    protected boolean equalsAt(long entry, Long key) {
        // Equal hashCodes <=> equal keys
        return true;
    }
}

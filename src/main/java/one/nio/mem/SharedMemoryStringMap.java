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

public class SharedMemoryStringMap<V> extends SharedMemoryMap<String, V> {

    public SharedMemoryStringMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    public SharedMemoryStringMap(int capacity, String fileName, long fileSize, long expirationTime) throws IOException {
        super(capacity, fileName, fileSize, expirationTime);
    }

    @Override
    protected String keyAt(long entry) {
        int keyLength = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 33);
        long keyOffset = entry + HEADER_SIZE;
        char[] key = new char[keyLength];
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            key[i] = unsafe.getChar(keyOffset);
        }
        return new String(key);
    }

    @Override
    protected long hashCode(String key) {
        int stringHashCode = Hash.murmur3(key);
        return (long) key.length() << 33 | (stringHashCode & 0xffffffffL);
    }

    @Override
    protected boolean equalsAt(long entry, String key) {
        int keyLength = key.length();
        long keyOffset = entry + HEADER_SIZE;
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            if (unsafe.getChar(keyOffset) != key.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected long allocateEntry(String key, long hashCode, int size) {
        int keyLength = key.length();
        long entry = allocator.segmentFor(hashCode).malloc(HEADER_SIZE + keyLength * 2 + size);
        long keyOffset = entry + HEADER_SIZE;
        for (int i = 0; i < keyLength; i++, keyOffset += 2) {
            unsafe.putChar(keyOffset, key.charAt(i));
        }
        return entry;
    }

    @Override
    protected int headerSize(long entry) {
        int keySizeInBytes = (int) (unsafe.getLong(entry + HASH_OFFSET) >>> 32);
        return HEADER_SIZE + keySizeInBytes;
    }
}

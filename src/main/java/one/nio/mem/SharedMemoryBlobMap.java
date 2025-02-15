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

import java.io.IOException;

public class SharedMemoryBlobMap extends SharedMemoryLongMap<byte[]> {

    public SharedMemoryBlobMap(int capacity, String fileName, long fileSize) throws IOException {
        super(capacity, fileName, fileSize);
    }

    public SharedMemoryBlobMap(int capacity, String fileName, long fileSize, long expirationTime) throws IOException {
        super(capacity, fileName, fileSize, expirationTime);
    }

    @Override
    protected int sizeOf(byte[] value) {
        return value.length + 4;
    }

    @Override
    protected void setValueAt(long entry, byte[] value) {
        int length = value.length;
        unsafe.putInt(entry + HEADER_SIZE, length);
        unsafe.copyMemory(value, byteArrayOffset, null, entry + (HEADER_SIZE + 4), length);
    }

    @Override
    protected byte[] valueAt(long entry) {
        int length = unsafe.getInt(entry + HEADER_SIZE);
        byte[] value = new byte[length];
        unsafe.copyMemory(null, entry + (HEADER_SIZE + 4), value, byteArrayOffset, length);
        return value;
    }
}

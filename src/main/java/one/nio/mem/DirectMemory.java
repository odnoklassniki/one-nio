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

package one.nio.mem;

import one.nio.util.Cleaner;
import one.nio.util.JavaInternals;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static one.nio.util.JavaInternals.unsafe;

public final class DirectMemory {
    private static final long addressOffset = JavaInternals.fieldOffset(Buffer.class, "address");
    private static final long capacityOffset = JavaInternals.fieldOffset(Buffer.class, "capacity");

    static final ByteBuffer prototype = ByteBuffer.allocateDirect(0);

    // Too long Unsafe.setMemory may prevent JVM safepoint from happening
    private static final long MAX_SETMEMORY_BYTES = 2 * 1024 * 1024;

    public static long allocate(long size, Object holder) {
        final long address = unsafe.allocateMemory(size);
        new Cleaner(holder) {
            @Override
            public void clean() {
                unsafe.freeMemory(address);
            }
        };
        return address;
    }

    public static long allocateAndClear(long size, Object holder) {
        long address = allocate(size, holder);
        clear(address, size);
        return address;
    }

    public static long allocateDirty(long size) {
        return unsafe.allocateMemory(size);
    }

    public static long allocateRaw(long size) {
        long address = unsafe.allocateMemory(size);
        clear(address, size);
        return address;
    }

    public static void freeRaw(long address) {
        unsafe.freeMemory(address);
    }

    public static void clear(long address, long size) {
        for (; size > MAX_SETMEMORY_BYTES; size -= MAX_SETMEMORY_BYTES) {
            unsafe.setMemory(address, MAX_SETMEMORY_BYTES, (byte) 0);
            address += MAX_SETMEMORY_BYTES;
        }
        unsafe.setMemory(address, size, (byte) 0);
    }

    // Faster than Unsafe.setMemory for small arrays
    public static void clearSmall(long address, int size) {
        for (; size >= 8; size -= 8) {
            unsafe.putLong(address, 0);
            address += 8;
        }
        if ((size & 4) != 0) {
            unsafe.putInt(address, 0);
            address += 4;
        }
        if ((size & 2) != 0) {
            unsafe.putShort(address, (short) 0);
            address += 2;
        }
        if ((size & 1) != 0) {
            unsafe.putByte(address, (byte) 0);
        }
    }

    public static long getAddress(ByteBuffer buffer) {
        return unsafe.getLong(buffer, addressOffset);
    }

    public static ByteBuffer wrap(long address, int count) {
        ByteBuffer result = prototype.duplicate();
        unsafe.putLong(result, addressOffset, address);
        unsafe.putInt(result, capacityOffset, count);
        result.limit(count);
        return result;
    }

    public static boolean compare(Object obj1, long offset1, Object obj2, long offset2, int count) {
        for (; count >= 8; count -= 8) {
            if (unsafe.getLong(obj1, offset1) != unsafe.getLong(obj2, offset2)) return false;
            offset1 += 8;
            offset2 += 8;
        }
        if ((count & 4) != 0) {
            if (unsafe.getInt(obj1, offset1) != unsafe.getInt(obj2, offset2)) return false;
            offset1 += 4;
            offset2 += 4;
        }
        if ((count & 2) != 0) {
            if (unsafe.getShort(obj1, offset1) != unsafe.getShort(obj2, offset2)) return false;
            offset1 += 2;
            offset2 += 2;
        }
        return (count & 1) == 0 || unsafe.getByte(obj1, offset1) == unsafe.getByte(obj2, offset2);
    }

    // Similar to Unsafe.copyMemory, but better for small arrays
    public static void copy(Object from, long fromOffset, Object to, long toOffset, int count) {
        for (; count >= 8; count -= 8) {
            unsafe.putLong(to, toOffset, unsafe.getLong(from, fromOffset));
            fromOffset += 8;
            toOffset += 8;
        }
        if ((count & 4) != 0) {
            unsafe.putInt(to, toOffset, unsafe.getInt(from, fromOffset));
            fromOffset += 4;
            toOffset += 4;
        }
        if ((count & 2) != 0) {
            unsafe.putShort(to, toOffset, unsafe.getShort(from, fromOffset));
            fromOffset += 2;
            toOffset += 2;
        }
        if ((count & 1) != 0) {
            unsafe.putByte(to, toOffset, unsafe.getByte(from, fromOffset));
        }
    }
}

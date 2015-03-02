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

import one.nio.util.JavaInternals;

import sun.misc.Cleaner;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static one.nio.util.JavaInternals.unsafe;

public final class DirectMemory {
    private static final long addressOffset = JavaInternals.fieldOffset(Buffer.class, "address");
    private static final ByteBuffer prototype = createPrototype();

    public static long allocate(long size, Object holder) {
        final long address = unsafe.allocateMemory(size);
        Cleaner.create(holder, new Runnable() {
            @Override
            public void run() {
                unsafe.freeMemory(address);
            }
        });
        return address;
    }

    public static long allocateAndFill(long size, Object holder, byte filler) {
        long address = allocate(size, holder);
        unsafe.setMemory(address, size, filler);
        return address;
    }

    public static long allocateRaw(long size) {
        long address = unsafe.allocateMemory(size);
        unsafe.setMemory(address, size, (byte) 0);
        return address;
    }

    public static void freeRaw(long address) {
        unsafe.freeMemory(address);
    }

    public static void clear(long address, long length) {
        unsafe.setMemory(address, length, (byte) 0);
    }

    public static long getAddress(ByteBuffer buffer) {
        return unsafe.getLong(buffer, addressOffset);
    }

    public static ByteBuffer wrap(long address, int count) {
        ByteBuffer result = prototype.duplicate();
        unsafe.putLong(result, addressOffset, address);
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

    // Murmur3_x86_32 hash code
    public static int hash(Object obj, long offset, int count) {
        int h1 = 0xa9b4de21;
        int remain;

        for (remain = count; remain >= 4; remain -= 4) {
            int k1 = unsafe.getInt(obj, offset);
            offset += 4;

            k1 *= 0xcc9e2d51;
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= 0x1b873593;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        switch (remain) {
            case 3:
                k1 = (unsafe.getByte(obj, offset + 2) & 0xff) << 16;
                // fallthrough
            case 2:
                k1 |= (unsafe.getByte(obj, offset + 1) & 0xff) << 8;
                // fallthrough
            case 1:
                k1 |= unsafe.getByte(obj, offset) & 0xff;
                k1 *= 0xcc9e2d51;
                k1 = (k1 << 15) | (k1 >>> 17);
                k1 *= 0x1b873593;
                h1 ^= k1;
        }

        h1 ^= count;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }

    private static ByteBuffer createPrototype() {
        ByteBuffer result = ByteBuffer.allocateDirect(0);
        try {
            JavaInternals.getField(Buffer.class, "capacity").setInt(result, Integer.MAX_VALUE);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }
}

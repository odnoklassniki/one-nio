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

import java.io.Closeable;
import java.util.BitSet;

import static one.nio.util.JavaInternals.unsafe;

/**
 * An offheap implementation of a bit set. Especially useful for large bloom
 * filters, e.g. in Cassandra. This implementation is not meant to be modified
 * concurrently. However it can be read concurrently. Current implementation
 * supports only reversed byte order (i.e. Big endian on x86).
 *
 * @author Oleg Anastasyev
 * @see BitSet
 */
public class OffheapBitSet implements Closeable {
    // address of this bitset 0th element
    protected long baseAddr;
    // size of this bitset in words
    protected long size;

    public OffheapBitSet(long numBits) {
        this.size = bits2words(numBits);
        this.baseAddr = DirectMemory.allocateRaw(size * 8);
    }

    public OffheapBitSet(long address, long sizeBytes) {
        this.baseAddr = address;
        this.size = sizeBytes >>> 3;
    }

    /**
     * @param numBits a number of bits to hold
     * @return the number of 64 bit words it would take to hold numBits
     */
    public static long bits2words(long numBits) {
        return (((numBits - 1) >>> 6) + 1);
    }

    @Override
    public void close() {
        if (baseAddr != 0) {
            DirectMemory.freeRaw(baseAddr);
            baseAddr = 0;
        }
    }

    public long capacity() {
        return size << 6;
    }

    /**
     * Returns true or false for the specified bit index. The index should be
     * less than the capacity.
     *
     * @param index the bit index
     * @return the value of the bit with the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public boolean get(long index) {
        return unsafeGet(checkBounds(index));
    }

    /**
     * Same as {@link #get(long)} but does not check for index within capacity
     * bounds. This allows to make it few ticks faster in exchange to seg fault
     * possibility. Use when going out of capacity is ensured by other means
     * outside of this method
     *
     * @param index a bit index
     * @return the value of the bit with the specified index
     */
    public boolean unsafeGet(long index) {
        long word = index >> 6; // div 8 and round to long word
        long bitmask = 1L << index;

        return (unsafe.getLong(baseAddr + word * 8) & bitmask) != 0;
    }

    /**
     * Sets the bit at the specified index. The index should be less than the
     * capacity.
     *
     * @param index a bit index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void set(long index) {
        unsafeSet(checkBounds(index));
    }

    public void unsafeSet(long index) {
        long word = index >> 6; // div 8 and round to long word
        long bitmask = 1L << index;

        long wordAddr = baseAddr + word * 8;
        unsafe.putLong(wordAddr, unsafe.getLong(wordAddr) | bitmask);
    }

    /**
     * clears the bit. The index should be less than the capacity.
     *
     * @param index a bit index
     */
    public void clear(long index) {
        unsafeClear(checkBounds(index));
    }

    public void unsafeClear(long index) {
        long word = index >> 6; // div 8 and round to long word
        long bitmask = 1L << index;

        long wordAddr = baseAddr + word * 8;
        unsafe.putLong(wordAddr, unsafe.getLong(wordAddr) & ~bitmask);
    }

    public void clear() {
        DirectMemory.clear(baseAddr, size * 8);
    }

    protected long checkBounds(long index) {
        if (index < 0 || (index >> 6) >= size) {
            throw new IndexOutOfBoundsException(index + " is out of bounds");
        }
        return index;
    }
}

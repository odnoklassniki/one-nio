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

package one.nio.os.bpf;

import one.nio.os.Cpus;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class BpfMap extends BpfObj implements Closeable {
    public static final int CPUS = Cpus.COUNT;
    public static final int ARRAY_KEY_SIZE = 4;

    public final MapType type;
    public final int keySize;
    public final int valueSize;
    public final int totalValueSize;
    public final int maxEntries;
    public final int flags;

    BpfMap(MapType type, int id, String name, int keySize, int valueSize, int maxEntries, int flags, int fd) {
        super(id, name, fd);
        this.keySize = keySize;
        this.valueSize = valueSize;
        this.maxEntries = maxEntries;
        this.flags = flags;
        // see kernel/bpf/syscall.c:bpf_map_value_size
        this.totalValueSize = type.perCpu ? roundUp(valueSize) * CPUS : valueSize;
        this.type = type;
    }

    private static int roundUp(int valueSize) {
        return (valueSize + 7) & ~7;
    }

    public boolean get(byte[] key, byte[] result) throws IOException {
        return get(key, result, 0);
    }

    protected boolean get(byte[] key, byte[] result, int flags) throws IOException {
        checkKeyLength(key.length);
        checkTotalValueLength(result.length);
        return Bpf.mapLookup(fd(), key, result, flags);
    }

    public byte[] get(byte[] key) throws IOException {
        checkKeyLength(key.length);
        byte[] result = new byte[totalValueSize];
        boolean res = get(key, result);
        return res ? result : null;
    }

    public boolean put(byte[] key, byte[] value) throws IOException {
        return put(key, value, Bpf.BPF_ANY);
    }

    public boolean putIfAbsent(byte[] key, byte[] value) throws IOException {
        return put(key, value, Bpf.BPF_NOEXIST);
    }

    public boolean putIfPresent(byte[] key, byte[] value) throws IOException {
        return put(key, value, Bpf.BPF_EXIST);
    }

    protected boolean put(byte[] key, byte[] value, int flags) throws IOException {
        checkKeyLength(key.length);
        checkTotalValueLength(value.length);
        return Bpf.mapUpdate(fd(), key, value, flags);
    }

    public boolean remove(byte[] key) throws IOException {
        checkKeyLength(key.length);
        return Bpf.mapRemove(fd(), key);
    }

    public Iterable<byte[]> keys() {
        return KeysIterator::new;
    }

    public BpfMap synchronizedMap() {
        return new SynchronizedBpfMap(this);
    }

    public static BpfMap getPinned(String path) throws IOException {
        int fd = Bpf.objectGet(path);
        return getByFd(fd);
    }

    public static BpfMap getById(int id) throws IOException {
        int fd = Bpf.mapGetFdById(id);
        return getByFd(fd);
    }

    public static BpfMap getByFd(int fd) throws IOException {
        int[] res = new int[6];
        String name = Bpf.mapGetInfo(fd, res);
        MapType type = MapType.values()[res[0]];
        int id = res[1];
        int keySize = res[2];
        int valueSize = res[3];
        int maxEntries = res[4];
        int flags = res[5];
        return new BpfMap(type, id, name, keySize, valueSize, maxEntries, flags, fd);
    }

    public static BpfMap newMap(MapType type, int keySize, int valueSize, int maxEntries, String name, int flags) throws IOException {
        int fd = Bpf.mapCreate(type.ordinal(), keySize, valueSize, maxEntries, name, flags, 0);
        return getByFd(fd);
    }

    public static BpfMap newMapOfMaps(MapType type, int keySize, int maxEntries, String name, int flags, BpfMap innerMap) throws IOException {
        int fd = Bpf.mapCreate(type.ordinal(), keySize, 4, maxEntries, name, flags, innerMap.fd());
        return getByFd(fd);
    }

    public static BpfMap newPerfEventArray(String name, int flags) throws IOException {
        return newMap(MapType.PERF_EVENT_ARRAY, ARRAY_KEY_SIZE, 4, CPUS, name, flags);
    }

    private void checkKeyLength(int length) {
        if (keySize != length) {
            throw new IllegalArgumentException("Invalid key size");
        }
    }

    private void checkTotalValueLength(int length) {
        if (totalValueSize != length) {
            throw new IllegalArgumentException("Invalid value size");
        }
    }

    public static Iterable<Integer> getAllIds() {
        return () -> new IdsIterator(Bpf.OBJ_MAP);
    }

    public static byte[] bytes(int i) {
        return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(i).array();
    }

    public static byte[] bytes(long i) {
        return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(i).array();
    }

    public static ByteBuffer bytes(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.nativeOrder());
    }

    public static IntBuffer ints(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    public static LongBuffer longs(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.nativeOrder()).asLongBuffer();
    }

    private static class SynchronizedBpfMap extends BpfMap {
        public SynchronizedBpfMap(BpfMap map) {
            super(map.type, map.id, map.name, map.keySize, map.valueSize, map.maxEntries, map.flags, map.fd());
        }

        @Override
        protected boolean get(byte[] key, byte[] result, int flags) throws IOException {
            return super.get(key, result, flags | Bpf.BPF_F_LOCK);
        }

        @Override
        protected boolean put(byte[] key, byte[] value, int flags) throws IOException {
            return super.put(key, value, flags | Bpf.BPF_F_LOCK);
        }
    }

    class KeysIterator implements Iterator<byte[]> {
        byte[] next;
        boolean nextChecked;

        @Override
        public boolean hasNext() {
            if (!nextChecked) {
                byte[] buf = new byte[keySize];
                boolean hasNext = Bpf.mapGetNextKey(fd(), next, buf);
                next = hasNext ? buf : null;
                nextChecked = true;
            }
            return next != null;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            nextChecked = false;
            return next;
        }
    }
}

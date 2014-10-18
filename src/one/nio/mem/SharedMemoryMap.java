package one.nio.mem;

import one.nio.async.AsyncExecutor;
import one.nio.async.ParallelTask;
import one.nio.mgt.Management;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.Repository;
import one.nio.serial.SerializeStream;
import one.nio.serial.Serializer;
import one.nio.serial.SerializerCollector;
import one.nio.serial.SerializerNotFoundException;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SharedMemoryMap<K, V> extends OffheapMap<K, V> implements SharedMemoryMapMXBean {
    protected static final long SIGNATURE_CLEAR = 0xa1091e4c436d6873L;
    protected static final long SIGNATURE_DIRTY = 0xa1091f49446d6873L;

    protected static final long SIGNATURE_OFFSET   = 0;
    protected static final long TIMESTAMP_OFFSET   = 8;
    protected static final long CAPACITY_OFFSET    = 16;
    protected static final long BASE_OFFSET        = 24;
    protected static final long UID_OFFSET         = 32;
    protected static final long CUSTOM_SIZE_OFFSET = 40;
    protected static final long CUSTOM_DATA_OFFSET = 48;
    protected static final long MAP_OFFSET         = MB;

    protected static final int MAX_CUSTOM_DATA_SIZE = (int) (MAP_OFFSET - CUSTOM_DATA_OFFSET);

    protected final Serializer<V> serializer;
    protected final MappedFile mmap;
    protected final String name;
    protected MallocMT allocator;

    protected SharedMemoryMap(int capacity, String fileName, long fileSize) throws IOException {
        this(capacity, (Serializer<V>) null, fileName, fileSize);
    }

    protected SharedMemoryMap(int capacity, Class<V> valueType, String fileName, long fileSize) throws IOException {
        this(capacity, Repository.get(valueType), fileName, fileSize);
    }

    protected SharedMemoryMap(int capacity, Serializer<V> serializer, String fileName, long fileSize) throws IOException {
        super(capacity);
        this.serializer = serializer;
        if (fileName == null) {
            this.mmap = new MappedFile(fileSize);
            this.name = "anon." + Long.toHexString(mmap.getAddr());
        } else {
            this.mmap = new MappedFile(fileName, fileSize);
            this.name = fileName;
        }

        long mallocOffset = MAP_OFFSET + (long) this.capacity * 8;
        if (mmap.getSize() <= mallocOffset) {
            long minSize = (mallocOffset + (MB - 1)) / MB;
            throw new IllegalArgumentException("Minimum SharedMemoryMap size is " + minSize + " MB");
        }

        init();
        createAllocator(mmap.getAddr() + mallocOffset, mmap.getSize() - mallocOffset);
        loadSchema();

        Management.registerMXBean(this, "one.nio.mem:type=SharedMemoryMap,name=" + name);
    }

    @Override
    protected void closeInternal() {
        Management.unregisterMXBean("one.nio.mem:type=SharedMemoryMap,name=" + name);

        storeSchema();
        setHeader(TIMESTAMP_OFFSET, System.currentTimeMillis());
        setHeader(SIGNATURE_OFFSET, SIGNATURE_CLEAR);
        mmap.close();
        log.info("SharedMemoryMap gracefully closed");
    }

    private void init() {
        boolean needCleanup = false;
        if (getHeader(SIGNATURE_OFFSET) != SIGNATURE_CLEAR) {
            log.info("Initial cleanup of SharedMemoryMap...");
            needCleanup = true;
        } else if (getHeader(CAPACITY_OFFSET) != capacity) {
            log.info("SharedMemoryMap capacity has changed, performing cleanup...");
            needCleanup = true;
        }

        if (needCleanup) {
            unsafe.setMemory(mmap.getAddr(), mmap.getSize(), (byte) 0);
            setHeader(CAPACITY_OFFSET, capacity);
        }

        setHeader(SIGNATURE_OFFSET, SIGNATURE_DIRTY);
        this.mapBase = mmap.getAddr() + MAP_OFFSET;

        long oldBase = getHeader(BASE_OFFSET);
        if (oldBase != 0) {
            log.info("Relocating SharedMemoryMap...");
            relocate(mmap.getAddr() - oldBase);
        }
        setHeader(BASE_OFFSET, mmap.getAddr());
    }

    protected void relocate(long delta) {
        int count = 0;

        for (int i = 0; i < capacity; i++) {
            long currentPtr = mapBase + (long) i * 8;
            for (long entry; (entry = unsafe.getAddress(currentPtr)) != 0; currentPtr = entry + NEXT_OFFSET) {
                unsafe.putAddress(currentPtr, entry += delta);
                count++;
            }
        }

        this.count.set(count);
    }

    protected long getHeader(long offset) {
        return unsafe.getLong(mmap.getAddr() + offset);
    }

    protected void setHeader(long offset, long value) {
        unsafe.putLong(mmap.getAddr() + offset, value);
    }

    protected byte[] getCustomData() {
        byte[] data = new byte[(int) getHeader(CUSTOM_SIZE_OFFSET)];
        unsafe.copyMemory(null, mmap.getAddr() + CUSTOM_DATA_OFFSET, data, byteArrayOffset, data.length);
        return data;
    }

    protected void setCustomData(byte[] data) {
        if (data.length > MAX_CUSTOM_DATA_SIZE) {
            throw new IllegalArgumentException("Custom data too long");
        }
        setHeader(CUSTOM_SIZE_OFFSET, data.length);
        unsafe.copyMemory(data, byteArrayOffset, null, mmap.getAddr() + CUSTOM_DATA_OFFSET, data.length);
    }

    @Override
    protected long allocateEntry(K key, long hashCode, int size) {
        return allocator.segmentFor(hashCode).malloc(HEADER_SIZE + size);
    }

    @Override
    protected void destroyEntry(long entry) {
        allocator.free(entry);
    }

    @Override
    protected int sizeOf(long entry) {
        return allocator.allocatedSize(entry) - headerSize(entry);
    }

    @Override
    protected V valueAt(long entry) {
        try {
            long valueAddress = entry + headerSize(entry);
            return serializer.read(new DeserializeStream(valueAddress, Integer.MAX_VALUE));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void setValueAt(long entry, V value) {
        try {
            long valueAddress = entry + headerSize(entry);
            serializer.write(value, new SerializeStream(valueAddress, Integer.MAX_VALUE));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected int sizeOf(V value) {
        try {
            CalcSizeStream css = new CalcSizeStream();
            serializer.calcSize(value, css);
            return css.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int headerSize(long entry) {
        return HEADER_SIZE;
    }

    @Override
    public long getTotalMemory() {
        return allocator.getTotalMemory();
    }

    @Override
    public long getFreeMemory() {
        return allocator.getFreeMemory();
    }

    @Override
    public long getUsedMemory() {
        return allocator.getUsedMemory();
    }

    @Override
    public int entriesToClean() {
        long totalMemory = getTotalMemory();
        long freeMemory = getFreeMemory();
        long exceededMemory = (long) (totalMemory * cleanupThreshold) - freeMemory;
        if (exceededMemory > 0 && totalMemory > freeMemory) {
            return (int) (getCount() * exceededMemory / (totalMemory - freeMemory));
        }
        return 0;
    }

    protected void createAllocator(long startAddress, long totalMemory) {
        this.allocator = new MallocMT(startAddress, totalMemory);

        log.info("SharedMemoryMap initialized: capacity = " + getCount() + "/" + getCapacity()
                + ", memory = " + allocator.getUsedMemory() / MB + "/" + allocator.getTotalMemory() / MB + " MB");
    }

    protected void storeSchema() {
        if (serializer == null) return;

        log.info("Saving serialization schema...");

        final HashSet<Serializer> serializers = new HashSet<Serializer>();
        if (serializer.uid() >= 0) {
            serializers.add(serializer);
        }

        AsyncExecutor.fork(new ParallelTask() {
            @Override
            public void execute(int taskNum, int taskCount) throws IOException, ClassNotFoundException {
                SerializerCollector collector = new SerializerCollector(mmap.getAddr(), mmap.getSize());

                for (int i = taskNum; i < capacity; i += taskCount) {
                    long currentPtr = mapBase + (long) i * 8;
                    for (long entry; (entry = unsafe.getAddress(currentPtr)) != 0; currentPtr = entry + NEXT_OFFSET) {
                        collector.setOffset(entry + headerSize(entry));
                        serializer.read(collector);
                    }
                }

                synchronized (serializers) {
                    serializers.addAll(collector.serializers());
                }
            }
        });

        SerializeStream ss = new SerializeStream(mmap.getAddr() + CUSTOM_DATA_OFFSET, MAX_CUSTOM_DATA_SIZE);
        try {
            for (Serializer serializer : serializers) {
                ss.writeObject(serializer);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        setHeader(UID_OFFSET, serializer.uid());
        setHeader(CUSTOM_SIZE_OFFSET, ss.count());

        log.info("Stored " + serializers.size() + " serializers. Metadata size = " + ss.count());
    }

    protected void loadSchema() throws IOException {
        if (serializer == null) return;

        log.info("Loading serialization schema...");

        long metadataSize = getHeader(CUSTOM_SIZE_OFFSET);
        if (metadataSize < 0 || metadataSize > MAX_CUSTOM_DATA_SIZE) {
            throw new IllegalStateException("Invalid metadata size: " + metadataSize);
        }

        int count = 0;
        DeserializeStream ds = new DeserializeStream(mmap.getAddr() + CUSTOM_DATA_OFFSET, (int) metadataSize);
        while (ds.available() > 0) {
            try {
                Repository.provideSerializer((Serializer) ds.readObject());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            count++;
        }

        log.info("Loaded " + count + " serializers");

        long oldUid = getHeader(UID_OFFSET);
        if (oldUid != 0 && oldUid != serializer.uid()) {
            convert(getSerializer(oldUid));
        }
    }

    protected void convert(final Serializer<V> oldSerializer) {
        log.info("Main serializer mismatch. Will run in-memory conversion.");
        log.info("Old serializer:\n" + oldSerializer + "New serializer:\n" + serializer);

        long startTime = System.currentTimeMillis();
        long startFreeMemory = allocator.getFreeMemory();
        final AtomicInteger totalConverted = new AtomicInteger();

        AsyncExecutor.fork(allocator.segments(), new ParallelTask() {
            @Override
            public void execute(int taskNum, int taskCount) throws IOException, ClassNotFoundException {
                Malloc localAllocator = allocator.segment(taskNum);
                int converted = 0;

                for (int i = taskNum; i < capacity; i += taskCount) {
                    long currentPtr = mapBase + (long) i * 8;
                    for (long entry; (entry = unsafe.getAddress(currentPtr)) != 0; currentPtr = entry + NEXT_OFFSET) {
                        int headerSize = headerSize(entry);
                        V value = oldSerializer.read(new DeserializeStream(entry + headerSize, Integer.MAX_VALUE));

                        int oldSize = sizeOf(entry);
                        int newSize = sizeOf(value);
                        if (newSize > oldSize) {
                            long newEntry = localAllocator.malloc(headerSize + newSize);
                            unsafe.copyMemory(null, entry, null, newEntry, headerSize);
                            unsafe.putAddress(currentPtr, newEntry);
                            allocator.free(entry);
                            entry = newEntry;
                        }

                        serializer.write(value, new SerializeStream(entry + headerSize, Integer.MAX_VALUE));
                        converted++;
                    }
                }

                totalConverted.addAndGet(converted);
            }
        });

        long endFreeMemory = allocator.getFreeMemory();
        long endTime = System.currentTimeMillis();
        log.info("Converted " + totalConverted.get() + " objects in " + (endTime - startTime) + " ms. Memory delta = " +
                (endFreeMemory - startFreeMemory));
    }

    @SuppressWarnings("unchecked")
    private Serializer<V> getSerializer(long uid) throws SerializerNotFoundException {
        return Repository.requestSerializer(uid);
    }
}

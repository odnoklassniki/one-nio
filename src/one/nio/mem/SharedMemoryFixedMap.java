package one.nio.mem;

import java.io.IOException;

public abstract class SharedMemoryFixedMap<K, V> extends SharedMemoryMap<K, V> {
    protected static final long ALLOCATOR_SIZE_OFFSET = CUSTOM_SIZE_OFFSET;
    protected static final long ALLOCATOR_HEAD_OFFSET = CUSTOM_DATA_OFFSET;

    protected final int valueSize;
    protected final FixedSizeAllocator allocator;

    protected SharedMemoryFixedMap(String fileName, long fileSize, int valueSize) throws IOException {
        super((int) (fileSize / getEntrySize(valueSize) * 1.5), fileName, fileSize);
        this.valueSize = valueSize;
        this.allocator = createFixedSizeAllocator(getEntrySize(valueSize));
    }

    private static int getEntrySize(int valueSize) {
        return (valueSize + (HEADER_SIZE + 7)) & ~7;  // align to 8-byte boundary
    }

    private FixedSizeAllocator createFixedSizeAllocator(int entrySize) {
        long allocOffset = MAP_OFFSET + (long) this.capacity * 8;
        long allocBase = mmap.getAddr() + allocOffset;
        long allocSize = mmap.getSize() - allocOffset;

        long oldEntrySize = getHeader(ALLOCATOR_SIZE_OFFSET);
        if (oldEntrySize == 0) {
            return new FixedSizeAllocator(allocBase, allocSize, entrySize);
        } else if (oldEntrySize == entrySize) {
            return new FixedSizeAllocator(allocBase, allocSize, entrySize, getHeader(ALLOCATOR_HEAD_OFFSET));
        }

        throw new IllegalArgumentException("Entry size has changed from " + oldEntrySize + " to " + entrySize);
    }

    @Override
    protected void closeInternal() {
        setHeader(ALLOCATOR_SIZE_OFFSET, allocator.entrySize);
        setHeader(ALLOCATOR_HEAD_OFFSET, allocator.head);
        super.closeInternal();
    }

    @Override
    protected void createAllocator(long startAddress, long totalMemory) {
        // Not used
    }

    @Override
    protected void relocate(long delta) {
        super.relocate(delta);
        FixedSizeAllocator.relocate(mmap.getAddr() + ALLOCATOR_HEAD_OFFSET, delta);
    }

    @Override
    protected long allocateEntry(K key, long hashCode, int size) {
        return allocator.malloc();
    }

    @Override
    protected void destroyEntry(long entry) {
        allocator.free(entry);
    }

    @Override
    protected int sizeOf(long entry) {
        return 0;
    }

    @Override
    protected int sizeOf(V value) {
        return 0;
    }

    @Override
    public long getTotalMemory() {
        return allocator.totalMemory();
    }

    @Override
    public long getFreeMemory() {
        return getTotalMemory() - getUsedMemory();
    }

    @Override
    public long getUsedMemory() {
        return allocator.entrySize() * getCount();
    }
}

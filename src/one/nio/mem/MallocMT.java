package one.nio.mem;

import one.nio.util.Management;

import java.util.Random;

/**
 * MT-friendly version of Malloc.
 * Divides the whole memory space into 8 thread-local areas.
 */
public class MallocMT extends Malloc {
    static final int SEGMENT_COUNT = 8;
    static final int SEGMENT_MASK  = SEGMENT_COUNT - 1;

    private Malloc[] segments;
    private Random random;

    public MallocMT(long capacity) {
        super(capacity);
    }

    public MallocMT(long base, long capacity) {
        super(base, capacity);
    }

    public MallocMT(MappedFile mmap) {
        super(mmap);
    }

    public final int segments() {
        return segments.length;
    }

    public final Malloc segment(int index) {
        return segments[index];
    }

    @Override
    public long getFreeMemory() {
        long result = 0;
        for (Malloc segment : segments) {
            result += segment.freeMemory;
        }
        return result;
    }

    @Override
    public long malloc(int size) {
        int alignedSize = (Math.max(size, 16) + (HEADER_SIZE + 7)) & ~7;
        int bin = getBin(alignedSize);

        Malloc startSegment = segments[random.nextInt() & SEGMENT_MASK];
        Malloc segment = startSegment;

        do {
            if (segment.freeMemory >= alignedSize) {
                long address = segment.mallocImpl(bin, alignedSize);
                if (address != 0) {
                    return address;
                }
            }
        } while ((segment = segment.next) != startSegment);

        throw new OutOfMemoryException("Failed to allocate " + size + " bytes");
    }

    @Override
    public void free(long address) {
        Malloc segment = segments[unsafe.getInt(address - HEADER_SIZE + SIZE_OFFSET) & SEGMENT_MASK];
        segment.free(address);
    }

    @Override
    public void verify() {
        for (Malloc segment : segments) {
            segment.verify();
        }
    }

    @Override
    void init() {
        Management.registerMXBean(this, "type=MallocMT,base=" + Long.toHexString(base));

        long segmentSize = (capacity / SEGMENT_COUNT) & ~7;
        segments = new Malloc[SEGMENT_COUNT];

        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Malloc(base + i * segmentSize, segmentSize);
        }

        for (int i = 0; i < segments.length; i++) {
            segments[i].next = segments[(i + 5) & SEGMENT_MASK];
            segments[i].mask |= i;
        }

        random = new Random();
    }
}

package one.nio.mem;

import java.io.PrintStream;
import java.util.Arrays;

public class MallocAnalyzer extends MallocMT {
    private PrintStream out;

    public MallocAnalyzer(long base, long capacity, PrintStream out) {
        super(base, capacity);
        this.out = out;
    }

    public void info() {
        segmentInfo();
        chunkInfo();
    }
    
    public void segmentInfo() {
        out.println("=== Segment Info ===");
        out.println("Segment Total\t" + Long.toHexString(base) + "\t" +
                    getTotalMemory() + "\t" + getUsedMemory() + "\t" + getFreeMemory());
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            Malloc segment = segment(i);
            out.println("Segment " + i + "\t" + Long.toHexString(segment.base) + "\t" +
                        segment.getTotalMemory() + "\t" + segment.getUsedMemory() + "\t" + segment.getFreeMemory());
        }
        out.println();
    }

    public void chunkInfo() {
        BinStats[] usedStats = new BinStats[SEGMENT_COUNT];
        BinStats[] freeStats = new BinStats[SEGMENT_COUNT];

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            long start = segment(i).base + BIN_SPACE;
            long end = segment(i).base + segment(i).capacity - HEADER_SIZE * 2;
            BinStats used = usedStats[i] = new BinStats();
            BinStats free = freeStats[i] = new BinStats();

            for (int size; start < end; start += size) {
                int sizeField = unsafe.getInt(start + SIZE_OFFSET);
                size = sizeField & FREE_MASK;
                (sizeField == size ? free : used).addChunk(size);
            }
        }

        printChunkInfo("=== Used Info ===", usedStats);
        printChunkInfo("=== Free Info ===", freeStats);
    }

    private void printChunkInfo(String header, BinStats[] stats) {
        out.println(header);

        long globalCounter = 0;
        long globalTotal = 0;

        for (int bin = getBin(MIN_CHUNK); bin < BIN_COUNT; bin++) {
            out.print("Bin " + bin + "\t" + (binSize(bin) & 0xffffffffL));

            int counter = 0;
            long total = 0;
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                out.print("\t" + stats[i].counter[bin]);
                counter += stats[i].counter[bin];
                total += stats[i].total[bin];
            }

            globalCounter += counter;
            globalTotal += total;

            if (counter != 0) {
                out.println("\t" + counter + "\t" + total + "\t(" + total / counter + ")");
            } else {
                out.println();
            }
        }

        if (globalCounter != 0) {
            out.println("Total\t" + globalCounter + "\t" + globalTotal + "\t(" + globalTotal / globalCounter + ")");
        }
        out.println();
    }

    public static void main(String[] args) throws Exception {
        MappedFile mmap = new MappedFile(args[0], 0, MappedFile.MAP_PV);
        long base = mmap.getAddr();
        long size = mmap.getSize();

        if (unsafe.getLong(base) == 0xA0014B4F236D6873L || unsafe.getLong(base) == 0xA0015444236D6873L) {
            long offset = unsafe.getLong(base + 16);
            base += offset;
            size -= offset;
        } else if (unsafe.getInt(base) == 0x30667247 || unsafe.getInt(base) == 0x44667247) {
            long offset = (long) unsafe.getInt(base + 4) * 16 + 32;
            base += offset;
            size -= offset;
        }

        MallocAnalyzer ma = new MallocAnalyzer(base, size, System.out);
        ma.info();

        mmap.close();
    }

    static class BinStats {
        final int[] counter = new int[BIN_COUNT];
        final long[] total = new long[BIN_COUNT];
        final int[] min = new int[BIN_COUNT];
        final int[] max = new int[BIN_COUNT];

        BinStats() {
            Arrays.fill(min, Integer.MAX_VALUE);
        }

        void addChunk(int size) {
            int bin = getBin(size);
            size -= HEADER_SIZE;

            counter[bin]++;
            total[bin] += size;
            if (size < min[bin]) min[bin] = size;
            if (size > max[bin]) max[bin] = size;
        }
    }
}

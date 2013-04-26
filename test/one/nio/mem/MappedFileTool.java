package one.nio.mem;

import one.nio.util.JavaInternals;
import sun.misc.Unsafe;

public class MappedFileTool {
    private static final Unsafe unsafe = JavaInternals.getUnsafe();

    public static void main(String[] args) throws Exception {
        MappedFile mmap = new MappedFile(args[0], 0, MappedFile.MAP_RW);
        long base = mmap.getAddr();

        String cmd = args[1];
        long addr = base + Long.decode(args[2]);

        if ("getInt".equals(cmd)) {
            System.out.println(unsafe.getInt(addr));
        } else if ("getLong".equals("cmd")) {
            System.out.println(unsafe.getLong(addr));
        } else if ("putInt".equals(cmd)) {
            unsafe.putInt(addr, Integer.decode(args[3]));
            System.out.println("OK");
        } else if ("putLong".equals(cmd)) {
            unsafe.putLong(addr, Long.decode(args[3]));
            System.out.println("OK");
        } else {
            System.out.println("Unknown command");
        }

        mmap.close();
    }

}

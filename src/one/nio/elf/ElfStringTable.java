package one.nio.elf;

import java.nio.ByteBuffer;

public class ElfStringTable extends ElfSection {

    ElfStringTable(ElfReader reader, int offset) {
        super(reader, offset);
    }

    public String string(int index) {
        ByteBuffer buf = reader.buf;
        int pos = (int) offset + index;

        StringBuilder result = new StringBuilder();
        for (byte b; (b = buf.get(pos)) != 0; pos++) {
            result.append((char) b);
        }
        return result.toString();
    }
}

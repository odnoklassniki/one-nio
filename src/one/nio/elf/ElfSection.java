package one.nio.elf;

import java.nio.ByteBuffer;

public class ElfSection {
    public static final int SHT_NULL     = 0;
    public static final int SHT_PROGBITS = 1;
    public static final int SHT_SYMTAB   = 2;
    public static final int SHT_STRTAB   = 3;
    public static final int SHT_RELA     = 4;
    public static final int SHT_HASH     = 5;
    public static final int SHT_DYNAMIC  = 6;
    public static final int SHT_NOTE     = 7;
    public static final int SHT_NOBITS   = 8;
    public static final int SHT_REL      = 9;
    public static final int SHT_SHLIB    = 10;
    public static final int SHT_DYNSYM   = 11;
    public static final int SHT_LOPROC   = 0x70000000;
    public static final int SHT_HIPROC   = 0x7fffffff;
    public static final int SHT_LOUSER   = 0x80000000;
    public static final int SHT_HIUSER   = 0xffffffff;

    final ElfReader reader;
    final int nameIndex;
    final int type;
    final long flags;
    final long address;
    final long offset;
    final long size;
    final int linkIndex;
    final int info;
    final long align;
    final long entrySize;

    ElfSection(ElfReader reader, int offset) {
        ByteBuffer buf = reader.buf;
        this.reader = reader;
        this.nameIndex = buf.getInt(offset);
        this.type = buf.getInt(offset + 4);

        if (reader.elf64) {
            this.flags = buf.getLong(offset + 8);
            this.address = buf.getLong(offset + 16);
            this.offset = buf.getLong(offset + 24);
            this.size = buf.getLong(offset + 32);
            this.linkIndex = buf.getInt(offset + 40);
            this.info = buf.getInt(offset + 44);
            this.align = buf.getLong(offset + 48);
            this.entrySize = buf.getLong(offset + 56);
        } else {
            this.flags = buf.getInt(offset + 8) & 0xffffffffL;
            this.address = buf.getInt(offset + 12) & 0xffffffffL;
            this.offset = buf.getInt(offset + 16) & 0xffffffffL;
            this.size = buf.getInt(offset + 20) & 0xffffffffL;
            this.linkIndex = buf.getInt(offset + 24);
            this.info = buf.getInt(offset + 28);
            this.align = buf.getInt(offset + 32);
            this.entrySize = buf.getInt(offset + 36);
        }
    }

    public String name() {
        return reader.strtab == null ? null : reader.strtab.string(nameIndex);
    }

    public int type() {
        return type;
    }

    public long flags() {
        return flags;
    }

    public long address() {
        return address;
    }

    public long offset() {
        return offset;
    }

    public long size() {
        return size;
    }

    public ElfSection link() {
        return reader.sections[linkIndex];
    }

    public int info() {
        return info;
    }

    public long align() {
        return align;
    }

    public long entrySize() {
        return entrySize;
    }

    public int count() {
        return entrySize == 0 ? 0 : (int) (size / entrySize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + name() + ')';
    }

    static ElfSection read(ElfReader reader, int offset) {
        int type = reader.buf.getInt(offset + 4);
        switch (type) {
            case SHT_NULL:
                return null;
            case SHT_SYMTAB:
            case SHT_DYNSYM:
                return new ElfSymbolTable(reader, offset);
            case SHT_STRTAB:
                return new ElfStringTable(reader, offset);
            case SHT_RELA:
            case SHT_REL:
                return new ElfRelocationTable(reader, offset);
            default:
                return new ElfSection(reader, offset);
        }
    }
}

package one.nio.elf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class ElfReader {
    public static final short ET_NONE   = 0;
    public static final short ET_REL    = 1;
    public static final short ET_EXEC   = 2;
    public static final short ET_DYN    = 3;
    public static final short ET_CORE   = 4;
    public static final short ET_NUM    = 5;
    public static final short ET_LOOS   = (short) 0xfe00;
    public static final short ET_HIOS   = (short) 0xfeff;
    public static final short ET_LOPROC = (short) 0xff00;
    public static final short ET_HIPROC = (short) 0xffff;

    public static final short EM_NONE    = 0;
    public static final short EM_SPARC   = 2;
    public static final short EM_386     = 3;
    public static final short EM_PPC     = 20;
    public static final short EM_PPC64   = 21;
    public static final short EM_ARM     = 40;
    public static final short EM_SPARCV9 = 43;
    public static final short EM_IA_64   = 50;
    public static final short EM_X86_64  = 62;
    public static final short EM_AARCH64 = 183;

    public static final byte ELFOSABI_SYSV       = 0;
    public static final byte ELFOSABI_HPUX       = 1;
    public static final byte ELFOSABI_NETBSD     = 2;
    public static final byte ELFOSABI_GNU        = 3;
    public static final byte ELFOSABI_SOLARIS    = 6;
    public static final byte ELFOSABI_AIX        = 7;
    public static final byte ELFOSABI_IRIX       = 8;
    public static final byte ELFOSABI_FREEBSD    = 9;
    public static final byte ELFOSABI_TRU64      = 10;
    public static final byte ELFOSABI_MODESTO    = 11;
    public static final byte ELFOSABI_OPENBSD    = 12;
    public static final byte ELFOSABI_ARM_AEABI  = 64;
    public static final byte ELFOSABI_ARM        = 97;
    public static final byte ELFOSABI_STANDALONE = -1;

    final ByteBuffer buf;
    final boolean elf64;
    final byte abi;
    final byte abiVersion;
    final short type;
    final short machine;
    final int version;
    final int flags;
    final long entry;
    final ElfSection[] sections;
    final ElfStringTable strtab;

    public ElfReader(String fileName) throws IOException {
        this(new File(fileName));
    }

    public ElfReader(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            this.buf = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
        } finally {
            raf.close();
        }

        byte[] ident = new byte[16];
        buf.get(ident);
        if (ident[0] != 0x7f || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') {
            throw new ElfException("Invalid ELF signature");
        }

        switch (ident[4]) {
            case 1: this.elf64 = false; break;
            case 2: this.elf64 = true; break;
            default: throw new ElfException("Invalid ELF class");
        }

        switch (ident[5]) {
            case 1: buf.order(ByteOrder.LITTLE_ENDIAN); break;
            case 2: buf.order(ByteOrder.BIG_ENDIAN); break;
            default: throw new ElfException("Invalid ELF endian");
        }

        if (ident[6] != 1) {
            throw new ElfException("Invalid ELF version");
        }

        this.abi = ident[7];
        this.abiVersion = ident[8];

        this.type = buf.getShort(16);
        this.machine = buf.getShort(18);
        this.version = buf.getInt(20);

        if (elf64) {
            this.entry = buf.getLong(24);
            this.flags = buf.getInt(48);
            this.sections = readSections(buf.getInt(40), buf.getShort(58) & 0xffff, buf.getShort(60) & 0xffff);
            this.strtab = (ElfStringTable) sections[buf.getShort(62) & 0xffff];
        } else {
            this.entry = buf.getInt(24) & 0xffffffffL;
            this.flags = buf.getInt(36);
            this.sections = readSections(buf.getInt(32), buf.getShort(46) & 0xffff, buf.getShort(48) & 0xffff);
            this.strtab = (ElfStringTable) sections[buf.getShort(50) & 0xffff];
        }
    }

    private ElfSection[] readSections(int start, int entrySize, int entries) {
        ElfSection[] sections = new ElfSection[entries];
        for (int i = 0; i < entries; i++) {
            sections[i] = ElfSection.read(this, start + i * entrySize);
        }
        return sections;
    }

    public ElfSection[] sections() {
        return sections;
    }

    public ElfSection section(String name) {
        for (ElfSection section : sections) {
            if (section != null && name.equals(section.name())) {
                return section;
            }
        }
        return null;
    }

    public boolean elf64() {
        return elf64;
    }

    public ByteOrder endian() {
        return buf.order();
    }

    public byte abi() {
        return abi;
    }

    public byte abiVersion() {
        return abiVersion;
    }

    public short type() {
        return type;
    }

    public short machine() {
        return machine;
    }

    public int version() {
        return version;
    }

    public int flags() {
        return flags;
    }

    public long entry() {
        return entry;
    }
}

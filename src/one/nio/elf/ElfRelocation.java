package one.nio.elf;

import java.nio.ByteBuffer;

public class ElfRelocation {
    final ElfSymbolTable symtab;
    final long offset;
    final long info;
    final long addend;

    ElfRelocation(ElfReader reader, ElfRelocationTable rel, int offset) {
        ByteBuffer buf = reader.buf;
        this.symtab = (ElfSymbolTable) rel.link();

        if (reader.elf64) {
            this.offset = buf.getLong(offset);
            this.info = buf.getLong(offset + 8);
            this.addend = rel.entrySize >= 24 ? buf.getLong(offset + 16) : 0;
        } else {
            this.offset = buf.getInt(offset) & 0xffffffffL;
            this.info = buf.getInt(offset + 4);
            this.addend = rel.entrySize >= 12 ? buf.getInt(offset + 8) : 0;
        }
    }

    public long offset() {
        return offset;
    }

    public ElfSymbol symbol() {
        return symtab.symbol((int) (info >>> 32));
    }

    public int type() {
        return (int) info;
    }

    public long addend() {
        return addend;
    }

    @Override
    public String toString() {
        return symbol().name() + '(' + type() + ')' + addend;
    }
}

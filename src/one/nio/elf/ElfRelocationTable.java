package one.nio.elf;

import java.util.Iterator;

public class ElfRelocationTable extends ElfSection implements Iterable<ElfRelocation> {

    ElfRelocationTable(ElfReader reader, int offset) {
        super(reader, offset);
    }

    public ElfRelocation relocation(int index) {
        return new ElfRelocation(reader, this, (int) (offset + index * entrySize));
    }

    public ElfRelocation[] relocations() {
        ElfRelocation[] relocations = new ElfRelocation[count()];
        for (int i = 0; i < relocations.length; i++) {
            relocations[i] = relocation(i);
        }
        return relocations;
    }

    @Override
    public Iterator<ElfRelocation> iterator() {
        return new Iterator<ElfRelocation>() {
            final int count = count();
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < count;
            }

            @Override
            public ElfRelocation next() {
                return relocation(index++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

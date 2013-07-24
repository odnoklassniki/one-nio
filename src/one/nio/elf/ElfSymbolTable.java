package one.nio.elf;

import java.util.Iterator;

public class ElfSymbolTable extends ElfSection implements Iterable<ElfSymbol> {

    ElfSymbolTable(ElfReader reader, int offset) {
        super(reader, offset);
    }

    public ElfSymbol symbol(int index) {
        return new ElfSymbol(reader, (ElfStringTable) link(), (int) (offset + index * entrySize));
    }

    public ElfSymbol symbol(String name) {
        for (ElfSymbol symbol : this) {
            if (name.equals(symbol.name())) {
                return symbol;
            }
        }
        return null;
    }

    public ElfSymbol[] symbols() {
        ElfSymbol[] symbols = new ElfSymbol[count()];
        for (int i = 0; i < symbols.length; i++) {
            symbols[i] = symbol(i);
        }
        return symbols;
    }

    @Override
    public Iterator<ElfSymbol> iterator() {
        return new Iterator<ElfSymbol>() {
            private final int count = count();
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < count;
            }

            @Override
            public ElfSymbol next() {
                return symbol(index++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

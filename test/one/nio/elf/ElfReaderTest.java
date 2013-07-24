package one.nio.elf;

public class ElfReaderTest {

    public static void main(String[] args) throws Exception {
        ElfReader reader = new ElfReader(args[0]);

        System.out.println("Sections:");
        for (ElfSection section : reader.sections()) {
            System.out.println("  " + section);
        }

        System.out.println("Symbols:");
        ElfSymbolTable symtab = (ElfSymbolTable) reader.section(".dynsym");
        for (ElfSymbol symbol : symtab) {
            System.out.println("  " + symbol);
        }

        System.out.println("Relocations:");
        ElfRelocationTable reltab = (ElfRelocationTable) reader.section(".rel.plt");
        if (reltab == null) {
            reltab = (ElfRelocationTable) reader.section(".rela.plt");
        }
        for (ElfRelocation relocation : reltab) {
            System.out.println("  " + relocation);
        }
    }
}

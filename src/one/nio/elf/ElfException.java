package one.nio.elf;

import java.io.IOException;

public class ElfException extends IOException {

    public ElfException() {
    }

    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }

    public ElfException(Throwable cause) {
        super(cause);
    }
}

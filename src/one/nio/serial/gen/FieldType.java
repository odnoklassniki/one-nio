package one.nio.serial.gen;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

enum FieldType implements Opcodes {
    Object (Object.class,  0, new int[] { NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP, POP }),
    Int    (int.class,     4, new int[] { NOP, NOP, I2L, I2B, I2B, I2S, I2C, I2F, I2D, POP }),
    Long   (long.class,    8, new int[] { NOP, L2I, NOP, L2I | I2B << 8, L2I | I2B << 8, L2I | I2S << 8, L2I | I2C << 8, L2F, L2D, POP2}),
    Boolean(boolean.class, 1, new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D, POP }),
    Byte   (byte.class,    1, new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D, POP }),
    Short  (short.class,   2, new int[] { NOP, NOP, I2L, I2B, I2B, NOP, I2C, I2F, I2D, POP }),
    Char   (char.class,    2, new int[] { NOP, NOP, I2L, I2B, I2B, I2S, NOP, I2F, I2D, POP }),
    Float  (float.class,   4, new int[] { NOP, F2I, F2L, F2I | I2B << 8, F2I | I2B << 8, F2I | I2S << 8, F2I | I2C << 8, NOP, F2D, POP }),
    Double (double.class,  8, new int[] { NOP, D2I, D2L, D2I | I2B << 8, D2I | I2B << 8, D2I | I2S << 8, D2I | I2C << 8, D2F, NOP, POP2 }),
    Void   (void.class,    0, new int[] { ACONST_NULL, ICONST_0, LCONST_0, ICONST_0, ICONST_0, ICONST_0, ICONST_0, FCONST_0, DCONST_0, NOP });

    private static final FieldType[] VALUES = values();

    final Class cls;
    final String sig;
    final int dataSize;
    final int[] convertOpcodes;

    private FieldType(Class cls, int dataSize, int[] convertOpcodes) {
        this.cls = cls;
        this.sig = Type.getDescriptor(cls);
        this.dataSize = dataSize;
        this.convertOpcodes = convertOpcodes;
    }

    public String readMethod() {
        return "read" + name();
    }

    public String readSignature() {
        return "()" + sig;
    }

    public String writeMethod() {
        return this == Byte ? "write" : "write" + name();
    }

    public String writeSignature() {
        return this == Byte || this == Short || this == Char ? "(I)V" : "(" + sig + ")V";
    }

    public String appendSignature() {
        return this == Byte || this == Short || this == Char ? "(I)Ljava/lang/StringBuilder;" : "(" + sig + ")Ljava/lang/StringBuilder;";
    }

    public String putMethod() {
        return "put" + name();
    }

    public String putSignature() {
        return "(Ljava/lang/Object;J" + sig + ")V";
    }

    public int convertTo(FieldType target) {
        return convertOpcodes[target.ordinal()];
    }

    public static FieldType valueOf(Class cls) {
        if (cls.isPrimitive()) {
            for (FieldType value : VALUES) {
                if (value.cls == cls) {
                    return value;
                }
            }
        }
        return Object;
    }
}

package one.nio.serial.gen;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public enum FieldType implements Opcodes {
    Object (Object.class,  0, ACONST_NULL, new int[] { NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP }),
    Int    (int.class,     4, ICONST_0,    new int[] { NOP, NOP, I2L, I2B, I2B, I2S, I2C, I2F, I2D }),
    Long   (long.class,    8, LCONST_0,    new int[] { NOP, L2I, NOP, L2I | I2B << 8, L2I | I2B << 8, L2I | I2S << 8, L2I | I2C << 8, L2F, L2D}),
    Boolean(boolean.class, 1, ICONST_0,    new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D }),
    Byte   (byte.class,    1, ICONST_0,    new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D }),
    Short  (short.class,   2, ICONST_0,    new int[] { NOP, NOP, I2L, I2B, I2B, NOP, I2C, I2F, I2D }),
    Char   (char.class,    2, ICONST_0,    new int[] { NOP, NOP, I2L, I2B, I2B, I2S, NOP, I2F, I2D }),
    Float  (float.class,   4, FCONST_0,    new int[] { NOP, F2I, F2L, F2I | I2B << 8, F2I | I2B << 8, F2I | I2S << 8, F2I | I2C << 8, NOP, F2D }),
    Double (double.class,  8, DCONST_0,    new int[] { NOP, D2I, D2L, D2I | I2B << 8, D2I | I2B << 8, D2I | I2S << 8, D2I | I2C << 8, D2F, NOP });

    final Class cls;
    final String sig;
    final int dataSize;
    final int defaultOpcode;
    final int[] convertOpcodes;

    private FieldType(Class cls, int dataSize, int defaultOpcode, int[] convertOpcodes) {
        this.cls = cls;
        this.sig = Type.getDescriptor(cls);
        this.dataSize = dataSize;
        this.defaultOpcode = defaultOpcode;
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
            for (FieldType value : values()) {
                if (value.cls == cls) {
                    return value;
                }
            }
        }
        return Object;
    }
}

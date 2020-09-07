/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.serial.gen;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

enum FieldType implements Opcodes {
    Object (Object.class,  0,         0, new int[] { NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP, NOP, POP }),
    Int    (int.class,     T_INT,     4, new int[] { NOP, NOP, I2L, I2B, I2B, I2S, I2C, I2F, I2D, POP }),
    Long   (long.class,    T_LONG,    8, new int[] { NOP, L2I, NOP, L2I | I2B << 8, L2I | I2B << 8, L2I | I2S << 8, L2I | I2C << 8, L2F, L2D, POP2}),
    Boolean(boolean.class, T_BOOLEAN, 1, new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D, POP }),
    Byte   (byte.class,    T_BYTE,    1, new int[] { NOP, NOP, I2L, NOP, NOP, I2S, I2C, I2F, I2D, POP }),
    Short  (short.class,   T_SHORT,   2, new int[] { NOP, NOP, I2L, I2B, I2B, NOP, I2C, I2F, I2D, POP }),
    Char   (char.class,    T_CHAR,    2, new int[] { NOP, NOP, I2L, I2B, I2B, I2S, NOP, I2F, I2D, POP }),
    Float  (float.class,   T_FLOAT,   4, new int[] { NOP, F2I, F2L, F2I | I2B << 8, F2I | I2B << 8, F2I | I2S << 8, F2I | I2C << 8, NOP, F2D, POP }),
    Double (double.class,  T_DOUBLE,  8, new int[] { NOP, D2I, D2L, D2I | I2B << 8, D2I | I2B << 8, D2I | I2S << 8, D2I | I2C << 8, D2F, NOP, POP2 }),
    Void   (void.class,    0,         0, new int[] { ACONST_NULL, ICONST_0, LCONST_0, ICONST_0, ICONST_0, ICONST_0, ICONST_0, FCONST_0, DCONST_0, NOP });

    private static final FieldType[] VALUES = values();

    final Class cls;
    final String sig;
    final int bytecodeType;
    final int dataSize;
    final int[] convertOpcodes;

    FieldType(Class cls, int bytecodeType, int dataSize, int[] convertOpcodes) {
        this.cls = cls;
        this.sig = Type.getDescriptor(cls);
        this.bytecodeType = bytecodeType;
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
        return "(Ljava/lang/Object;" + sig + "J)V";
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

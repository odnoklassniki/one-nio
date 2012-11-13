package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class ClassSerializer extends Serializer<Class> {
    private static final Class[] PRIMITIVE_CLASSES = {
        int.class, long.class, boolean.class, byte.class, short.class, char.class, float.class, double.class, void.class
    };

    ClassSerializer() {
        super(Class.class);
    }

    @Override
    public void write(Class obj, ObjectOutput out) throws IOException {
        if (obj.isPrimitive()) {
            for (int i = 0; i < PRIMITIVE_CLASSES.length; i++) {
                if (obj == PRIMITIVE_CLASSES[i]) {
                    out.writeByte(i);
                    break;
                }
            }
        } else {
            out.writeByte(-1);
            out.writeUTF(obj.getName());
        }
    }

    @Override
    public Class read(ObjectInput in) throws IOException, ClassNotFoundException {
        int index = in.readByte();
        return index >= 0 ? PRIMITIVE_CLASSES[index] : Class.forName(in.readUTF());
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        if (in.readByte() < 0) {
            in.skipBytes(in.readUnsignedShort());
        }
    }
}

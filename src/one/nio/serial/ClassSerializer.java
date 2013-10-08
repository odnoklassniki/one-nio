package one.nio.serial;

import one.nio.util.Utf8;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class ClassSerializer extends Serializer<Class<?>> {

    ClassSerializer() {
        super(Class.class);
    }

    @Override
    public void calcSize(Class<?> obj, CalcSizeStream css) {
        if (obj.isPrimitive()) {
            css.count++;
        } else {
            int length = Utf8.length(obj.getName());
            Renamed renamed = obj.getAnnotation(Renamed.class);
            css.count += (renamed == null) ? 3 + length : 4 + length + Utf8.length(renamed.from());
        }
    }

    @Override
    public void write(Class<?> obj, ObjectOutput out) throws IOException {
        if (obj.isPrimitive()) {
            out.writeByte(primitiveIndex(obj));
        } else {
            out.writeByte(-1);
            out.writeUTF(classDescriptor(obj));
        }
    }

    @Override
    public Class<?> read(ObjectInput in) throws IOException, ClassNotFoundException {
        int index = in.readByte();
        return index >= 0 ? PRIMITIVE_CLASSES[index] : classByDescriptor(in.readUTF());
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        if (in.readByte() < 0) {
            in.skipBytes(in.readUnsignedShort());
        }
    }

    @Override
    public void toJson(Class<?> obj, StringBuilder builder) {
        builder.append('"').append(obj.getName()).append('"');
    }
}

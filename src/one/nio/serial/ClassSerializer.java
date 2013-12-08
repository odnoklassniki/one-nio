package one.nio.serial;

import one.nio.util.Utf8;

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
    public void write(Class<?> obj, DataStream out) throws IOException {
        if (obj.isPrimitive()) {
            out.writeByte(primitiveIndex(obj));
        } else {
            out.writeByte(-1);
            out.writeUTF(classDescriptor(obj));
        }
    }

    @Override
    public Class<?> read(DataStream in) throws IOException, ClassNotFoundException {
        int index = in.readByte();
        Class result = index >= 0 ? PRIMITIVE_CLASSES[index] : classByDescriptor(in.readUTF());
        in.register(result);
        return result;
    }

    @Override
    public void toJson(Class<?> obj, StringBuilder builder) {
        builder.append('"').append(obj.getName()).append('"');
    }
}

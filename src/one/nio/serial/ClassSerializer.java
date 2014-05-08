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
        TypeDescriptor.writeClass(out, obj);
    }

    @Override
    public Class<?> read(DataStream in) throws IOException, ClassNotFoundException {
        Class<?> result = TypeDescriptor.readClass(in);
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        if (in.readByte() < 0) {
            in.skipBytes(in.readUnsignedShort());
        }
    }

    @Override
    public void toJson(Class<?> obj, StringBuilder builder) {
        builder.append('"').append(obj.getName()).append('"');
    }
}

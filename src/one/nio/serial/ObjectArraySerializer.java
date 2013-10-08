package one.nio.serial;

import one.nio.util.Json;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.lang.reflect.Array;

public class ObjectArraySerializer extends Serializer<Object[]> {
    private Class componentType;

    ObjectArraySerializer(Class cls) {
        super(cls);
        this.componentType = cls.getComponentType();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.tryReadExternal(in, (Repository.stubOptions & Repository.ARRAY_STUBS) == 0);
        if (this.cls == null) {
            this.cls = Object[].class;
        }
        this.componentType = cls.getComponentType();
    }

    @Override
    public void calcSize(Object[] obj, CalcSizeStream css) throws IOException {
        css.count += 4;
        for (Object v : obj) {
            css.writeObject(v);
        }
    }

    @Override
    public void write(Object[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (Object v : obj) {
            out.writeObject(v);
        }
    }

    @Override
    public Object read(ObjectInput in) throws IOException, ClassNotFoundException {
        return Array.newInstance(componentType, in.readInt());
    }

    @Override
    public void fill(Object[] obj, ObjectInput in) throws IOException, ClassNotFoundException {
        for (int i = 0; i < obj.length; i++) {
            obj[i] = in.readObject();
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            in.readObject();
        }
    }

    @Override
    public void toJson(Object[] obj, StringBuilder builder) throws IOException {
        builder.append('[');
        if (obj.length > 0) {
            Json.appendObject(builder, obj[0]);
            for (int i = 1; i < obj.length; i++) {
                builder.append(',');
                Json.appendObject(builder, obj[i]);
            }
        }
        builder.append(']');
    }
}

package one.nio.serial;

import one.nio.serial.gen.StubGenerator;
import one.nio.util.Json;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;

public class MapSerializer extends Serializer<Map> {
    private Constructor constructor;

    MapSerializer(Class cls) {
        super(cls);
        this.constructor = findConstructor();
        this.constructor.setAccessible(true);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        String className = super.tryReadExternal(in, (Repository.stubOptions & Repository.MAP_STUBS) == 0);
        if (this.cls == null) {
            this.cls = StubGenerator.generateRegular(className, "java.util.HashMap", null);
            this.original = true;
        }

        this.constructor = findConstructor();
        this.constructor.setAccessible(true);
    }

    @Override
    public void calcSize(Map obj, CalcSizeStream css) throws IOException {
        css.count += 4;
        for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
            css.writeObject(e.getKey());
            css.writeObject(e.getValue());
        }
    }

    @Override
    public void write(Map obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.size());
        for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
            out.writeObject(e.getKey());
            out.writeObject(e.getValue());
        }
    }

    @Override
    public Object read(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fill(Map obj, ObjectInput in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            obj.put(in.readObject(), in.readObject());
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            in.readObject();
            in.readObject();
        }
    }

    @Override
    public void toJson(Map obj, StringBuilder builder) throws IOException {
        builder.append('{');
        boolean firstWritten = false;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
            if (firstWritten) builder.append(','); else firstWritten = true;
            Json.appendString(builder, entry.getKey().toString());
            builder.append(':');
            Json.appendObject(builder, entry.getValue());
        }
        builder.append('}');
    }

    @SuppressWarnings("unchecked")
    private Constructor findConstructor() {
        try {
            return cls.getConstructor();
        } catch (NoSuchMethodException e) {
            Class implementation = SortedMap.class.isAssignableFrom(cls) ? TreeMap.class : HashMap.class;
            try {
                return implementation.getDeclaredConstructor();
            } catch (NoSuchMethodException e1) {
                return null;
            }
        }
    }
}

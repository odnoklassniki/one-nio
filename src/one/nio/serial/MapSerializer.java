package one.nio.serial;

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
        super.readExternal(in);
        this.constructor = findConstructor();
        this.constructor.setAccessible(true);
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

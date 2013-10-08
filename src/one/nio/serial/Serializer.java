package one.nio.serial;

import one.nio.serial.gen.StubGenerator;
import one.nio.util.DigestStream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public abstract class Serializer<T> implements Externalizable {
    protected static final Class[] PRIMITIVE_CLASSES = {
            int.class,      // 0
            long.class,     // 1
            boolean.class,  // 2
            byte.class,     // 3
            short.class,    // 4
            char.class,     // 5
            float.class,    // 6
            double.class,   // 7
            void.class      // 8
    };

    protected Class cls;
    protected long uid;
    protected boolean original;

    protected Serializer(Class cls) {
        this.cls = cls;
        this.uid = generateLongUid();
        this.original = true;
    }

    public String uid() {
        return Long.toHexString(uid);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(classDescriptor(cls));
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.cls = classByDescriptor(in.readUTF());
        this.uid = in.readLong();
    }

    protected String tryReadExternal(ObjectInput in, boolean throwClassNotFound) throws IOException, ClassNotFoundException {
        String name = in.readUTF();
        this.uid = in.readLong();

        try {
            this.cls = classByDescriptor(name);
            return cls.getName();
        } catch (ClassNotFoundException e) {
            if (throwClassNotFound) throw e;
            int p = name.indexOf('|');
            return p >= 0 ? name.substring(0, p) : name;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append("---\n");
        builder.append("Class: ").append(cls.getName()).append('\n');
        builder.append("UID: ").append(uid()).append('\n');
        builder.append("Original: ").append(original).append('\n');
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Serializer) {
            Serializer other = (Serializer) obj;
            return cls == other.cls && uid == other.uid;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) uid ^ (int) (uid >>> 32) ^ cls.hashCode();
    }

    private long generateLongUid() {
        DigestStream ds = new DigestStream("MD5");
        try {
            ds.writeUTF(getClass().getName());
            writeExternal(ds);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return ds.digest();
    }

    public int getSize(T obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        calcSize(obj, css);
        return css.count;
    }

    public abstract void calcSize(T obj, CalcSizeStream css) throws IOException;
    public abstract void write(T obj, ObjectOutput out) throws IOException;
    public abstract Object read(ObjectInput in) throws IOException, ClassNotFoundException;
    public abstract void skip(ObjectInput in) throws IOException, ClassNotFoundException;
    public abstract void toJson(T obj, StringBuilder builder) throws IOException;

    public void fill(T obj, ObjectInput in) throws IOException, ClassNotFoundException {
        // Nothing to do here if read() completes creation of an object
    }

    protected static int primitiveIndex(Class<?> cls) {
        for (int i = 0; i < PRIMITIVE_CLASSES.length; i++) {
            if (cls == PRIMITIVE_CLASSES[i]) {
                return i;
            }
        }
        return -1;
    }

    protected static String classDescriptor(Class<?> cls) {
        Renamed renamed = cls.getAnnotation(Renamed.class);
        return renamed == null ? cls.getName() : cls.getName() + '|' + renamed.from();
    }

    protected static Class<?> classByDescriptor(String name) throws ClassNotFoundException {
        int p = name.indexOf('|');
        if (p >= 0) {
            try {
                return Class.forName(name.substring(0, p), true, StubGenerator.INSTANCE);
            } catch (ClassNotFoundException e) {
                // New class is missed, try old name
                name = name.substring(p + 1);
            }
        }

        Class renamedClass = Repository.renamedClasses.get(name);
        return renamedClass != null ? renamedClass : Class.forName(name, true, StubGenerator.INSTANCE);
    }
}

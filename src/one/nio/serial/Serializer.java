package one.nio.serial;

import one.nio.util.DigestStream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Serializer<T> implements Externalizable {
    protected Class cls;
    protected long uid;

    static final AtomicInteger serializersSent = new AtomicInteger();
    static final AtomicInteger serializersReceived = new AtomicInteger();

    protected Serializer(Class cls) {
        this.cls = cls;
        this.uid = generateLongUid();
    }

    protected Serializer(Class cls, long uid) {
        this.cls = cls;
        this.uid = uid;
    }

    public String uid() {
        return Long.toHexString(uid);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        serializersSent.incrementAndGet();

        writeInstanceClass(cls, out);
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        serializersReceived.incrementAndGet();

        this.cls = readInstanceClass(in);
        this.uid = in.readLong();
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append("---\n");
        builder.append("Class: ").append(cls.getName()).append('\n');
        builder.append("UID: ").append(uid()).append('\n');
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

    public void fill(T obj, ObjectInput in) throws IOException, ClassNotFoundException {
        // Nothing to do here if read() completes creation of an object
    }

    protected static void writeInstanceClass(Class<?> cls, ObjectOutput out) throws IOException {
        Renamed renamed = cls.getAnnotation(Renamed.class);
        out.writeUTF(renamed == null ? cls.getName() : cls.getName() + '|' + renamed.from());
    }

    protected static Class<?> readInstanceClass(ObjectInput in) throws IOException, ClassNotFoundException {
        String name = in.readUTF();

        int p = name.indexOf('|');
        if (p >= 0) {
            try {
                return Class.forName(name.substring(0, p));
            } catch (ClassNotFoundException e) {
                // New class is missed, try old name
                name = name.substring(p + 1);
            }
        }

        Class renamedClass = Repository.renamedClasses.get(name);
        return renamedClass != null ? renamedClass : Class.forName(name);
    }
}

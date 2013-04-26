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

        out.writeUTF(cls.getName());
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        serializersReceived.incrementAndGet();

        this.cls = Class.forName(in.readUTF());
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

    public abstract void write(T obj, ObjectOutput out) throws IOException;
    public abstract Object read(ObjectInput in) throws IOException, ClassNotFoundException;
    public abstract void skip(ObjectInput in) throws IOException, ClassNotFoundException;
    
    public void fill(T obj, ObjectInput in) throws IOException, ClassNotFoundException {
        // Nothing to do here if read() completes creation of an object
    }
}

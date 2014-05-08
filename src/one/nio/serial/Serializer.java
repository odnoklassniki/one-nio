package one.nio.serial;

import one.nio.util.DigestStream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Serializer<T> implements Externalizable {
    static final AtomicInteger unknownClasses = new AtomicInteger();

    protected String descriptor;
    protected long uid;
    protected Class cls;
    protected Origin origin;

    protected Serializer(Class cls) {
        this.descriptor = TypeDescriptor.classDescriptor(cls);
        this.cls = cls;
        this.origin = Origin.LOCAL;
    }

    public long uid() {
        return uid;
    }

    @SuppressWarnings("unchecked")
    public Class<T> cls() {
        return cls;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(descriptor);
        out.writeLong(uid);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        tryReadExternal(in, true);
    }

    protected final void tryReadExternal(ObjectInput in, boolean throwException) throws IOException, ClassNotFoundException {
        try {
            this.descriptor = in.readUTF();
            this.uid = in.readLong();
            this.cls = TypeDescriptor.resolve(descriptor);
            this.origin = Origin.EXTERNAL;
        } catch (ClassNotFoundException e) {
            if (throwException) throw e;
            Repository.log.warn("[" + Long.toHexString(uid) + "] Unknown local class: " + descriptor);
            unknownClasses.incrementAndGet();
            this.origin = Origin.GENERATED;
        }
    }

    protected final void generateUid() {
        if (this.uid == 0) {
            DigestStream ds = new DigestStream("MD5");
            try {
                ds.writeUTF(getClass().getName());
                writeExternal(ds);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            this.uid = ds.digest();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append("---\n");
        builder.append("Class: ").append(descriptor).append('\n');
        builder.append("UID: ").append(Long.toHexString(uid)).append('\n');
        builder.append("Origin: ").append(origin).append('\n');
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Serializer) {
            Serializer other = (Serializer) obj;
            return uid == other.uid && descriptor.equals(other.descriptor);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) uid ^ (int) (uid >>> 32);
    }

    public abstract void calcSize(T obj, CalcSizeStream css) throws IOException;
    public abstract void write(T obj, DataStream out) throws IOException;
    public abstract T read(DataStream in) throws IOException, ClassNotFoundException;
    public abstract void skip(DataStream in) throws IOException, ClassNotFoundException;
    public abstract void toJson(T obj, StringBuilder builder) throws IOException;

    public static byte[] serialize(Object obj) throws IOException {
        CalcSizeStream css = new CalcSizeStream();
        css.writeObject(obj);
        byte[] data = new byte[css.count];
        DataStream ds = css.hasCycles ? new SerializeStream(data) : new DataStream(data);
        ds.writeObject(obj);
        return data;
    }

    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        return new DeserializeStream(data).readObject();
    }
}

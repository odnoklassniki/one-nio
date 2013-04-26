package one.nio.serial;

import one.nio.util.ByteArrayStream;

import java.io.IOException;

public class SerializeStream extends ByteArrayStream {
    static final byte REF_NULL      = -1;
    static final byte REF_RECURSIVE = -2;

    protected SerializationContext context;

    public SerializeStream(byte[] input) {
        super(input);
        this.context = new SerializationContext();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            buf[count++] = REF_NULL;
        } else {
            int index = context.put(obj);
            if (index < 0) {
                Serializer serializer = Repository.get(obj.getClass());
                if (serializer.uid < 0) {
                    buf[count++] = (byte) serializer.uid;
                } else {
                    writeLong(serializer.uid);
                }
                serializer.write(obj, this);
            } else if (index <= 0xffff) {
                buf[count++] = REF_RECURSIVE;
                writeShort(index);
            } else {
                throw new IOException("Recursive reference overflow");
            }
        }
    }

    @Override
    public void close() {
        context = null;
    }
}

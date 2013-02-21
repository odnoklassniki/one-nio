package one.nio.serial;

import one.nio.util.ByteArrayStream;

import java.io.IOException;
import java.util.IdentityHashMap;

public class SerializeStream extends ByteArrayStream {
    static final byte REF_NULL      = -1;
    static final byte REF_RECURSIVE = -2;

    protected IdentityHashMap<Object, Integer> context;

    public SerializeStream(byte[] input) {
        super(input);
        this.context = new IdentityHashMap<Object, Integer>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            buf[count++] = REF_NULL;
        } else {
            Integer index = context.put(obj, context.size());
            if (index != null) {
                context.put(obj, index);
                buf[count++] = REF_RECURSIVE;
                writeShort(index);
            } else {
                Serializer serializer = Repository.get(obj.getClass());
                if (serializer.uid < 0) {
                    buf[count++] = (byte) serializer.uid;
                } else {
                    writeLong(serializer.uid);
                }
                serializer.write(obj, this);
            }
        }
    }

    @Override
    public void close() {
        context = null;
    }
}

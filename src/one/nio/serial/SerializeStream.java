package one.nio.serial;

import one.nio.util.DataStream;

import java.io.IOException;

public class SerializeStream extends DataStream {
    static final byte REF_NULL       = -1;
    static final byte REF_RECURSIVE  = -2;
    static final byte REF_RECURSIVE2 = -3;

    protected SerializationContext context;

    public SerializeStream(byte[] array) {
        super(array);
        this.context = new SerializationContext();
    }

    public SerializeStream(long address, int capacity) {
        super(address, capacity);
        this.context = new SerializationContext();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) throws IOException {
        if (obj == null) {
            writeByte(REF_NULL);
        } else {
            int index = context.put(obj);
            if (index < 0) {
                Serializer serializer = Repository.get(obj.getClass());
                if (serializer.uid < 0) {
                    writeByte((byte) serializer.uid);
                } else {
                    writeLong(serializer.uid);
                }
                serializer.write(obj, this);
            } else if (index <= 0xffff) {
                writeByte(REF_RECURSIVE);
                writeShort(index);
            } else {
                writeByte(REF_RECURSIVE2);
                writeInt(index);
            }
        }
    }

    @Override
    public void close() {
        context = null;
    }
}

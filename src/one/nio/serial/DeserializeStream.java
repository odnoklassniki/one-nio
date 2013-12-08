package one.nio.serial;

import java.io.IOException;
import java.util.Arrays;

public class DeserializeStream extends DataStream {
    static final int INITIAL_CAPACITY = 24;

    protected Object[] context;
    protected int contextSize;
    
    public DeserializeStream(byte[] array) {
        super(array);
        this.context = new Object[INITIAL_CAPACITY];
    }

    public DeserializeStream(long address, int capacity) {
        super(address, capacity);
        this.context = new Object[INITIAL_CAPACITY];
    }

    @Override
    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
        } else if (b == SerializeStream.REF_NULL) {
            return null;
        } else if (b == SerializeStream.REF_RECURSIVE) {
            return context[readUnsignedShort()];
        } else if (b == SerializeStream.REF_RECURSIVE2) {
            return context[readInt()];
        } else {
            serializer = Repository.requestBootstrapSerializer(b);
        }
        return serializer.read(this);
    }

    @Override
    public void close() {
        context = null;
    }

    @Override
    protected void register(Object obj) {
        if (contextSize >= context.length) {
            context = Arrays.copyOf(context, context.length * 2);
        }
        context[contextSize++] = obj;
    }
}

package one.nio.serial;

import one.nio.util.DataStream;

import java.io.IOException;
import java.util.ArrayList;

public class DeserializeStream extends DataStream {
    static final int INITIAL_CAPACITY = 24;

    protected ArrayList<Object> context;
    
    public DeserializeStream(byte[] array) {
        super(array);
        this.context = new ArrayList<Object>(INITIAL_CAPACITY);
    }

    public DeserializeStream(long address, int capacity) {
        super(address, capacity);
        this.context = new ArrayList<Object>(INITIAL_CAPACITY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
        } else if (b == SerializeStream.REF_NULL) {
            return null;
        } else if (b == SerializeStream.REF_RECURSIVE) {
            return context.get(readUnsignedShort());
        } else if (b == SerializeStream.REF_RECURSIVE2) {
            return context.get(readInt());
        } else {
            serializer = Repository.requestBootstrapSerializer(b);
        }
        Object obj = serializer.read(this);
        context.add(obj);
        serializer.fill(obj, this);
        return obj;
    }

    @Override
    public void close() {
        context = null;
    }
}

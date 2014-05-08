package one.nio.serial;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SerializerCollector extends DataStream {
    private final HashSet<Serializer> serializers = new HashSet<Serializer>();

    public SerializerCollector(byte[] array) {
        super(array, byteArrayOffset, array.length);
    }

    public SerializerCollector(long address, long length) {
        super(null, address, length);
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public Set<Serializer> serializers() {
        return serializers;
    }

    public long[] uids() {
        long[] result = new long[serializers.size()];
        int i = 0;
        for (Serializer serializer : serializers) {
            result[i++] = serializer.uid;
        }
        return result;
    }

    @Override
    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
            serializers.add(serializer);
        } else if (b == REF_NULL) {
            return null;
        } else if (b == REF_RECURSIVE) {
            offset += 2;
            return null;
        } else if (b == REF_RECURSIVE2) {
            offset += 4;
            return null;
        } else {
            serializer = Repository.requestBootstrapSerializer(b);
        }

        serializer.skip(this);
        return null;
    }
}

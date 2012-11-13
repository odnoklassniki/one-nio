package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class ByteArraySerializer extends Serializer<byte[]> {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    ByteArraySerializer() {
        super(byte[].class);
    }

    @Override
    public void write(byte[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        out.write(obj);
    }

    @Override
    public byte[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            byte[] result = new byte[length];
            in.readFully(result);
            return result;
        } else {
            return EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length);
        }
    }
}

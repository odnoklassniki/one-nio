package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class IntegerArraySerializer extends Serializer<int[]> {
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    IntegerArraySerializer() {
        super(int[].class);
    }

    @Override
    public void write(int[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (int v : obj) {
            out.writeInt(v);
        }
    }

    @Override
    public int[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            int[] result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readInt();
            }
            return result;
        } else {
            return EMPTY_INT_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length << 2);
        }
    }
}

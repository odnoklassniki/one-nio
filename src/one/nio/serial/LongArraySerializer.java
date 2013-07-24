package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class LongArraySerializer extends Serializer<long[]> {
    private static final long[] EMPTY_LONG_ARRAY = new long[0];

    LongArraySerializer() {
        super(long[].class);
    }

    @Override
    public void calcSize(long[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 8;
    }

    @Override
    public void write(long[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (long v : obj) {
            out.writeLong(v);
        }
    }

    @Override
    public long[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            long[] result = new long[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readLong();
            }
            return result;
        } else {
            return EMPTY_LONG_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length * 8);
        }
    }
}

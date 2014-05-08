package one.nio.serial;

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
    public void write(long[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (long v : obj) {
            out.writeLong(v);
        }
    }

    @Override
    public long[] read(DataStream in) throws IOException {
        long[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new long[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readLong();
            }
        } else {
            result = EMPTY_LONG_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 8);
    }

    @Override
    public void toJson(long[] obj, StringBuilder builder) {
        builder.append('[');
        if (obj.length > 0) {
            builder.append(obj[0]);
            for (int i = 1; i < obj.length; i++) {
                builder.append(',').append(obj[i]);
            }
        }
        builder.append(']');
    }
}

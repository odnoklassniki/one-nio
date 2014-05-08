package one.nio.serial;

import java.io.IOException;

class ShortArraySerializer extends Serializer<short[]> {
    private static final short[] EMPTY_SHORT_ARRAY = new short[0];

    ShortArraySerializer() {
        super(short[].class);
    }

    @Override
    public void calcSize(short[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 2;
    }

    @Override
    public void write(short[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (short v : obj) {
            out.writeShort(v);
        }
    }

    @Override
    public short[] read(DataStream in) throws IOException {
        short[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new short[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readShort();
            }
        } else {
            result = EMPTY_SHORT_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 2);
    }

    @Override
    public void toJson(short[] obj, StringBuilder builder) {
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

package one.nio.serial;

import java.io.IOException;

class IntegerArraySerializer extends Serializer<int[]> {
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    IntegerArraySerializer() {
        super(int[].class);
    }

    @Override
    public void calcSize(int[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 4;
    }

    @Override
    public void write(int[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (int v : obj) {
            out.writeInt(v);
        }
    }

    @Override
    public int[] read(DataStream in) throws IOException {
        int[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new int[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readInt();
            }
        } else {
            result = EMPTY_INT_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 4);
    }

    @Override
    public void toJson(int[] obj, StringBuilder builder) {
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

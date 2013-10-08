package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
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
    public void write(short[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (short v : obj) {
            out.writeShort(v);
        }
    }

    @Override
    public short[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            short[] result = new short[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readShort();
            }
            return result;
        } else {
            return EMPTY_SHORT_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length * 2);
        }
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

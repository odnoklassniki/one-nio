package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class BooleanArraySerializer extends Serializer<boolean[]> {
    private static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];

    BooleanArraySerializer() {
        super(boolean[].class);
    }

    @Override
    public void calcSize(boolean[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length;
    }

    @Override
    public void write(boolean[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (boolean v : obj) {
            out.writeBoolean(v);
        }
    }

    @Override
    public boolean[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            boolean[] result = new boolean[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readBoolean();
            }
            return result;
        } else {
            return EMPTY_BOOLEAN_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length);
        }
    }

    @Override
    public void toJson(boolean[] obj, StringBuilder builder) {
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

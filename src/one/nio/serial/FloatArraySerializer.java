package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class FloatArraySerializer extends Serializer<float[]> {
    private static final float[] EMPTY_FLOAT_ARRAY = new float[0];

    FloatArraySerializer() {
        super(float[].class);
    }

    @Override
    public void calcSize(float[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 4;
    }

    @Override
    public void write(float[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (float v : obj) {
            out.writeFloat(v);
        }
    }

    @Override
    public float[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readFloat();
            }
            return result;
        } else {
            return EMPTY_FLOAT_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length * 4);
        }
    }

    @Override
    public void toJson(float[] obj, StringBuilder builder) {
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

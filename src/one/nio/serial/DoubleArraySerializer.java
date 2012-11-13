package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class DoubleArraySerializer extends Serializer<double[]> {
    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    DoubleArraySerializer() {
        super(double[].class);
    }

    @Override
    public void write(double[] obj, ObjectOutput out) throws IOException {
        out.writeInt(obj.length);
        for (double v : obj) {
            out.writeDouble(v);
        }
    }

    @Override
    public double[] read(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            double[] result = new double[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readDouble();
            }
            return result;
        } else {
            return EMPTY_DOUBLE_ARRAY;
        }
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length << 3);
        }
    }
}

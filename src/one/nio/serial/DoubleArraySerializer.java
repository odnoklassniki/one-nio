package one.nio.serial;

import java.io.IOException;

class DoubleArraySerializer extends Serializer<double[]> {
    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    DoubleArraySerializer() {
        super(double[].class);
    }

    @Override
    public void calcSize(double[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 8;
    }

    @Override
    public void write(double[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (double v : obj) {
            out.writeDouble(v);
        }
    }

    @Override
    public double[] read(DataStream in) throws IOException {
        double[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new double[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readDouble();
            }
        } else {
            result = EMPTY_DOUBLE_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 8);
    }

    @Override
    public void toJson(double[] obj, StringBuilder builder) {
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

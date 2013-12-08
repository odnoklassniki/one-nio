package one.nio.serial;

import java.io.IOException;

class DoubleSerializer extends Serializer<Double> {

    DoubleSerializer() {
        super(Double.class);
    }

    @Override
    public void calcSize(Double obj, CalcSizeStream css) {
        css.count += 8;
    }

    @Override
    public void write(Double v, DataStream out) throws IOException {
        out.writeDouble(v);
    }

    @Override
    public Double read(DataStream in) throws IOException {
        Double result = in.readDouble();
        in.register(result);
        return result;
    }

    @Override
    public void toJson(Double obj, StringBuilder builder) {
        builder.append(obj.doubleValue());
    }
}

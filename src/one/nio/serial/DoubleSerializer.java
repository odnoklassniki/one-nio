package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
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
    public void write(Double v, ObjectOutput out) throws IOException {
        out.writeDouble(v);
    }

    @Override
    public Double read(ObjectInput in) throws IOException {
        return in.readDouble();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(8);
    }

    @Override
    public void toJson(Double obj, StringBuilder builder) {
        builder.append(obj.doubleValue());
    }
}

package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class FloatSerializer extends Serializer<Float> {

    FloatSerializer() {
        super(Float.class);
    }

    @Override
    public void calcSize(Float obj, CalcSizeStream css) {
        css.count += 4;
    }

    @Override
    public void write(Float v, ObjectOutput out) throws IOException {
        out.writeFloat(v);
    }

    @Override
    public Float read(ObjectInput in) throws IOException {
        return in.readFloat();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(4);
    }
}

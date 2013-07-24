package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class BooleanSerializer extends Serializer<Boolean> {

    BooleanSerializer() {
        super(Boolean.class);
    }

    @Override
    public void calcSize(Boolean obj, CalcSizeStream css) {
        css.count++;
    }

    @Override
    public void write(Boolean v, ObjectOutput out) throws IOException {
        out.writeBoolean(v);
    }

    @Override
    public Boolean read(ObjectInput in) throws IOException {
        return in.readByte() == 0 ? Boolean.FALSE : Boolean.TRUE;
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(1);
    }
}

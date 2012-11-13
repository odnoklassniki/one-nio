package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class ShortSerializer extends Serializer<Short> {

    ShortSerializer() {
        super(Short.class);
    }

    @Override
    public void write(Short v, ObjectOutput out) throws IOException {
        out.writeShort(v);
    }

    @Override
    public Short read(ObjectInput in) throws IOException {
        return in.readShort();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(2);
    }
}

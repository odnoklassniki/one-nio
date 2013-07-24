package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;

class ByteSerializer extends Serializer<Byte> {

    ByteSerializer() {
        super(Byte.class);
    }

    @Override
    public void calcSize(Byte obj, CalcSizeStream css) {
        css.count++;
    }

    @Override
    public void write(Byte v, ObjectOutput out) throws IOException {
       out.writeByte(v);
    }

    @Override
    public Byte read(ObjectInput in) throws IOException {
        return in.readByte();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(1);
    }
}

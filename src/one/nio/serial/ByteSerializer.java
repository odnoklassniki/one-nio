package one.nio.serial;

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
    public void write(Byte v, DataStream out) throws IOException {
       out.writeByte(v);
    }

    @Override
    public Byte read(DataStream in) throws IOException {
        Byte result = in.readByte();
        in.register(result);
        return result;
    }

    @Override
    public void toJson(Byte obj, StringBuilder builder) {
        builder.append(obj.byteValue());
    }
}

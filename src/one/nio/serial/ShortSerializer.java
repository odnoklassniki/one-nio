package one.nio.serial;

import java.io.IOException;

class ShortSerializer extends Serializer<Short> {

    ShortSerializer() {
        super(Short.class);
    }

    @Override
    public void calcSize(Short obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Short v, DataStream out) throws IOException {
        out.writeShort(v);
    }

    @Override
    public Short read(DataStream in) throws IOException {
        Short result = in.readShort();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Short obj, StringBuilder builder) {
        builder.append(obj.shortValue());
    }
}

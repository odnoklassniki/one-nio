package one.nio.serial;

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
    public void write(Float v, DataStream out) throws IOException {
        out.writeFloat(v);
    }

    @Override
    public Float read(DataStream in) throws IOException {
        Float result = in.readFloat();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(4);
    }

    @Override
    public void toJson(Float obj, StringBuilder builder) {
        builder.append(obj.floatValue());
    }
}

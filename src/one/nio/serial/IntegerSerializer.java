package one.nio.serial;

import java.io.IOException;

class IntegerSerializer extends Serializer<Integer> {

    IntegerSerializer() {
        super(Integer.class);
    }

    @Override
    public void calcSize(Integer obj, CalcSizeStream css) {
        css.count += 4;
    }

    @Override
    public void write(Integer v, DataStream out) throws IOException {
        out.writeInt(v);
    }

    @Override
    public Integer read(DataStream in) throws IOException {
        Integer result = in.readInt();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(4);
    }

    @Override
    public void toJson(Integer obj, StringBuilder builder) {
        builder.append(obj.intValue());
    }
}

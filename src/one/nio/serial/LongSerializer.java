package one.nio.serial;

import java.io.IOException;

class LongSerializer extends Serializer<Long> {

    LongSerializer() {
        super(Long.class);
    }

    @Override
    public void calcSize(Long obj, CalcSizeStream css) {
        css.count += 8;
    }

    @Override
    public void write(Long v, DataStream out) throws IOException {
        out.writeLong(v);
    }

    @Override
    public Long read(DataStream in) throws IOException {
        Long result = in.readLong();
        in.register(result);
        return result;
    }

    @Override
    public void toJson(Long obj, StringBuilder builder) {
        builder.append(obj.longValue());
    }
}

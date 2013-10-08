package one.nio.serial;

import java.io.ObjectInput;
import java.io.ObjectOutput;
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
    public void write(Long v, ObjectOutput out) throws IOException {
        out.writeLong(v);
    }

    @Override
    public Long read(ObjectInput in) throws IOException {
        return in.readLong();
    }

    @Override
    public void skip(ObjectInput in) throws IOException {
        in.skipBytes(8);
    }

    @Override
    public void toJson(Long obj, StringBuilder builder) {
        builder.append(obj.longValue());
    }
}

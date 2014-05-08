package one.nio.serial;

import java.io.IOException;
import java.util.Date;

class DateSerializer extends Serializer<Date> {

    DateSerializer() {
        super(Date.class);
    }

    @Override
    public void calcSize(Date obj, CalcSizeStream css) {
        css.count += 8;
    }

    @Override
    public void write(Date obj, DataStream out) throws IOException {
        out.writeLong(obj.getTime());
    }

    @Override
    public Date read(DataStream in) throws IOException {
        Date result = new Date(in.readLong());
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(8);
    }

    @Override
    public void toJson(Date obj, StringBuilder builder) {
        builder.append(obj.getTime());
    }
}

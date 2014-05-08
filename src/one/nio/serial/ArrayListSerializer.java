package one.nio.serial;

import java.io.IOException;
import java.util.ArrayList;

class ArrayListSerializer extends Serializer<ArrayList> {

    ArrayListSerializer() {
        super(ArrayList.class);
    }

    @Override
    public void calcSize(ArrayList obj, CalcSizeStream css) throws IOException {
        int length = obj.size();
        css.count += 4;
        for (int i = 0; i < length; i++) {
            css.writeObject(obj.get(i));
        }
    }

    @Override
    public void write(ArrayList obj, DataStream out) throws IOException {
        int length = obj.size();
        out.writeInt(length);
        for (int i = 0; i < length; i++) {
            out.writeObject(obj.get(i));
        }
    }

    @Override
    public ArrayList read(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        ArrayList<Object> result = new ArrayList<Object>(length);
        in.register(result);
        for (int i = 0; i < length; i++) {
            result.add(in.readObject());
        }
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        for (int i = 0; i < length; i++) {
            in.readObject();
        }
    }

    @Override
    public void toJson(ArrayList obj, StringBuilder builder) throws IOException {
        builder.append('[');
        int length = obj.size();
        if (length > 0) {
            Json.appendObject(builder, obj.get(0));
            for (int i = 1; i < length; i++) {
                builder.append(',');
                Json.appendObject(builder, obj.get(i));
            }
        }
        builder.append(']');
    }
}

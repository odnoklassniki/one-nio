package one.nio.serial;

import java.io.IOException;
import java.util.HashMap;

class HashMapSerializer extends MapSerializer {

    HashMapSerializer() {
        super(HashMap.class);
    }

    @Override
    public HashMap read(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        HashMap<Object, Object> result = new HashMap<Object, Object>(length * 3 / 2, 0.75f);
        in.register(result);
        for (int i = 0; i < length; i++) {
            result.put(in.readObject(), in.readObject());
        }
        return result;
    }
}

package one.nio.serial;

import one.nio.util.JavaInternals;

import java.io.IOException;
import java.io.NotSerializableException;
import java.lang.reflect.Field;
import java.util.BitSet;

class BitSetSerializer extends Serializer<BitSet> {
    private static final Field wordsField = JavaInternals.getField(BitSet.class, "words");
    private static final Field wordsInUseField = JavaInternals.getField(BitSet.class, "wordsInUse");

    BitSetSerializer() {
        super(BitSet.class);
    }

    @Override
    public void calcSize(BitSet obj, CalcSizeStream css) {
        try {
            css.count += 4 + wordsInUseField.getInt(obj) * 8;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void write(BitSet obj, DataStream out) throws IOException {
        try {
            long[] words = (long[]) wordsField.get(obj);
            int wordsInUse = wordsInUseField.getInt(obj);
            out.writeInt(wordsInUse);
            for (int i = 0; i < wordsInUse; i++) {
                out.writeLong(words[i]);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public BitSet read(DataStream in) throws IOException {
        int wordsInUse = in.readInt();
        BitSet result = new BitSet(wordsInUse << 6);
        try {
            long[] words = (long[]) wordsField.get(result);
            wordsInUseField.set(result, wordsInUse);
            for (int i = 0; i < wordsInUse; i++) {
                words[i] = in.readLong();
            }
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        }
        in.register(result);
        return result;
    }

    @Override
    public void toJson(BitSet obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }
}

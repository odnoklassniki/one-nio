/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 8);
    }

    @Override
    public void toJson(BitSet obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(cls.getName());
    }
}

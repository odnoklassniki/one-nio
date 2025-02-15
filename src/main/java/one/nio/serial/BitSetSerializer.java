/*
 * Copyright 2025 VK
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

import one.nio.util.Base64;

import java.io.IOException;
import java.nio.LongBuffer;
import java.util.BitSet;

class BitSetSerializer extends Serializer<BitSet> {

    BitSetSerializer() {
        super(BitSet.class);
    }

    @Override
    public void calcSize(BitSet obj, CalcSizeStream css) {
        int wordCount = (obj.length() + 63) / 64;
        css.count += 4 + wordCount * 8;
    }

    @Override
    public void write(BitSet obj, DataStream out) throws IOException {
        long[] words = obj.toLongArray();
        out.writeInt(words.length);
        for (long word : words) {
            out.writeLong(word);
        }
    }

    @Override
    public BitSet read(DataStream in) throws IOException {
        int wordCount = in.readInt();
        LongBuffer buf = in.byteBuffer(wordCount * 8).asLongBuffer();
        BitSet result = BitSet.valueOf(buf);
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 8);
    }

    @Override
    public void toJson(BitSet obj, StringBuilder builder) {
        Json.appendBinary(builder, obj.toByteArray());
    }

    @Override
    public BitSet fromJson(JsonReader in) throws IOException {
        return BitSet.valueOf(in.readBinary());
    }

    @Override
    public BitSet fromString(String s) {
        return BitSet.valueOf(Base64.decodeFromChars(s.toCharArray()));
    }
}

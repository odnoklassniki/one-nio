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

import java.io.IOException;

class CharacterArraySerializer extends Serializer<char[]> {
    private static final char[] EMPTY_CHAR_ARRAY = new char[0];

    CharacterArraySerializer() {
        super(char[].class);
    }

    @Override
    public void calcSize(char[] obj, CalcSizeStream css) {
        css.count += 4 + obj.length * 2;
    }

    @Override
    public void write(char[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (char v : obj) {
            out.writeChar(v);
        }
    }

    @Override
    public char[] read(DataStream in) throws IOException {
        char[] result;
        int length = in.readInt();
        if (length > 0) {
            result = new char[length];
            for (int i = 0; i < length; i++) {
                result[i] = in.readChar();
            }
        } else {
            result = EMPTY_CHAR_ARRAY;
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(in.readInt() * 2);
    }

    @Override
    public void toJson(char[] obj, StringBuilder builder) {
        Json.appendChars(builder, obj);
    }

    @Override
    public char[] fromJson(JsonReader in) throws IOException {
        return in.readString().toCharArray();
    }
}

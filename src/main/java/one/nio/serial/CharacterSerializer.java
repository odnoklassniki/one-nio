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

import java.io.IOException;

class CharacterSerializer extends Serializer<Character> {

    CharacterSerializer() {
        super(Character.class);
    }

    @Override
    public void calcSize(Character obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Character v, DataStream out) throws IOException {
        out.writeChar(v);
    }

    @Override
    public Character read(DataStream in) throws IOException {
        Character result = in.readChar();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Character v, StringBuilder builder) {
        Json.appendChar(builder, v);
    }

    @Override
    public Character fromJson(JsonReader in) throws IOException {
        return in.readChar();
    }

    @Override
    public Character fromString(String s) {
        return s.charAt(0);
    }
}

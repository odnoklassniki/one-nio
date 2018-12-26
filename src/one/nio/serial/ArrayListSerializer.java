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
        ArrayList<Object> result = new ArrayList<>(length);
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

    @Override
    public ArrayList fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return in.readArray();
    }
}

/*
 * Copyright 2019 Odnoklassniki Ltd, Mail.Ru Group
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

import java.io.Closeable;
import java.io.IOException;

/**
 * Asymmetric serializer that writes a preserialized object as byte[],
 * but reads an original object
 */
class SerializedWrapperSerializer extends Serializer<Object> {

    SerializedWrapperSerializer() {
        super(SerializedWrapper.class);
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws IOException {
        css.add(getSerialized(obj).length);
    }

    @Override
    public void write(Object obj, DataStream out) throws IOException {
        out.write(getSerialized(obj));
    }

    @Override
    public Object read(DataStream in) throws IOException, ClassNotFoundException {
        Object result;
        try (Closeable blankScope = in.newScope()) {
            result = in.readObject();
        }
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        try (Closeable blankScope = in.newScope()) {
            in.readObject();
        }
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws IOException {
        Json.appendBinary(builder, getSerialized(obj));
    }

    @Override
    public Object fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return Serializer.deserialize(in.readBinary());
    }

    private static byte[] getSerialized(Object obj) {
        return ((SerializedWrapper) obj).getSerialized();
    }
}

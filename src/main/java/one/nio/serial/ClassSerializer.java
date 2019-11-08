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

import one.nio.gen.BytecodeGenerator;
import one.nio.util.Utf8;

import java.io.IOException;

class ClassSerializer extends Serializer<Class<?>> {

    ClassSerializer() {
        super(Class.class);
    }

    @Override
    public void calcSize(Class<?> obj, CalcSizeStream css) {
        if (obj.isPrimitive()) {
            css.count++;
        } else {
            int length = Utf8.length(obj.getName());
            Renamed renamed = obj.getAnnotation(Renamed.class);
            css.count += (renamed == null) ? 3 + length : 4 + length + Utf8.length(renamed.from());
        }
    }

    @Override
    public void write(Class<?> obj, DataStream out) throws IOException {
        TypeDescriptor.writeClass(out, obj);
    }

    @Override
    public Class<?> read(DataStream in) throws IOException, ClassNotFoundException {
        Class<?> result = TypeDescriptor.readClass(in);
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        if (in.readByte() < 0) {
            in.skipBytes(in.readUnsignedShort());
        }
    }

    @Override
    public void toJson(Class<?> obj, StringBuilder builder) {
        builder.append('"').append(obj.getName()).append('"');
    }

    @Override
    public Class<?> fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        return fromString(in.readString());
    }

    @Override
    public Class<?> fromString(String s) throws ClassNotFoundException {
        switch (s) {
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "short":
                return short.class;
            case "char":
                return char.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "void":
                return void.class;
        }
        return Class.forName(s, false, BytecodeGenerator.INSTANCE);
    }
}

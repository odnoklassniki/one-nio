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

import java.io.ObjectInput;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class ObjectArraySerializer extends Serializer<Object[]> {
    private Class componentType;

    ObjectArraySerializer(Class cls) {
        super(cls);
        this.componentType = cls.getComponentType();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.ARRAY_STUBS) == 0) throw e;
            // Cannot generate the right array class because the element's serializer is not known yet
            this.cls = Object[].class;
            this.origin = Origin.EXTERNAL;
        }

        this.componentType = cls.getComponentType();
    }

    @Override
    public void calcSize(Object[] obj, CalcSizeStream css) throws IOException {
        css.count += 4;
        for (Object v : obj) {
            css.writeObject(v);
        }
    }

    @Override
    public void write(Object[] obj, DataStream out) throws IOException {
        out.writeInt(obj.length);
        for (Object v : obj) {
            out.writeObject(v);
        }
    }

    @Override
    public Object[] read(DataStream in) throws IOException, ClassNotFoundException {
        Object[] result = (Object[]) Array.newInstance(componentType, in.readInt());
        in.register(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = in.readObject();
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
    public void toJson(Object[] obj, StringBuilder builder) throws IOException {
        builder.append('[');
        if (obj.length > 0) {
            Json.appendObject(builder, obj[0]);
            for (int i = 1; i < obj.length; i++) {
                builder.append(',');
                Json.appendObject(builder, obj[i]);
            }
        }
        builder.append(']');
    }

    @Override
    public Object[] fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        ArrayList<Object> list = in.readArray();
        return list.toArray((Object[]) Array.newInstance(componentType, list.size()));
    }
}

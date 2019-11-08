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

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;

import static one.nio.util.JavaInternals.unsafe;

public class ExternalizableSerializer extends Serializer<Externalizable> {

    ExternalizableSerializer(Class cls) {
        super(cls);
    }

    @Override
    public void calcSize(Externalizable obj, CalcSizeStream css) throws IOException {
        obj.writeExternal(css);
    }

    @Override
    public void write(Externalizable obj, DataStream out) throws IOException {
        obj.writeExternal(out);
    }

    @Override
    public Externalizable read(DataStream in) throws IOException, ClassNotFoundException {
        Externalizable result;
        try {
            result = (Externalizable) unsafe.allocateInstance(cls);
            in.register(result);
        } catch (InstantiationException e) {
            throw new IOException(e);
        }

        result.readExternal(in);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        read(in);
    }

    @Override
    public void toJson(Externalizable obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Externalizable fromJson(JsonReader in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }
}

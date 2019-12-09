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

import static one.nio.util.JavaInternals.unsafe;

class SerializerSerializer extends ExternalizableSerializer {

    SerializerSerializer(Class cls) {
        super(cls);
    }

    @Override
    public Serializer read(DataStream in) throws IOException, ClassNotFoundException {
        String descriptor = in.readUTF();
        long uid = in.readLong();

        Serializer serializer = Repository.uidMap.get(uid);
        if (serializer != null) {
            if (!descriptor.equals(serializer.descriptor)) {
                throw new IllegalStateException("UID collision (" + Long.toHexString(uid) + "): " +
                        descriptor + " overwrites " + serializer.descriptor);
            }
            in.register(serializer);
            serializer.skipExternal(in);
            return serializer;
        }

        try {
            serializer = (Serializer) unsafe.allocateInstance(cls);
            serializer.descriptor = descriptor;
            serializer.uid = uid;
        } catch (InstantiationException e) {
            throw new IOException(e);
        }

        in.register(serializer);
        serializer.readExternal(in);
        return serializer;
    }

    @Override
    public void toJson(Externalizable obj, StringBuilder builder) {
        ((Serializer) obj).toJson(builder);
    }
}

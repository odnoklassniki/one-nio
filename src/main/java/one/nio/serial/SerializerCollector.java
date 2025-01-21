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
import java.util.HashSet;
import java.util.Set;

public class SerializerCollector extends DataStream {
    private final HashSet<Serializer> serializers = new HashSet<>();

    public SerializerCollector(byte[] array) {
        super(array);
    }

    public SerializerCollector(long address, long length) {
        super(null, address, length);
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public Set<Serializer> serializers() {
        return serializers;
    }

    public long[] uids() {
        long[] result = new long[serializers.size()];
        int i = 0;
        for (Serializer serializer : serializers) {
            result[i++] = serializer.uid;
        }
        return result;
    }

    @Override
    public Object readObject() throws IOException, ClassNotFoundException {
        Serializer serializer;
        byte b = readByte();
        if (b >= 0) {
            offset--;
            serializer = Repository.requestSerializer(readLong());
            serializers.add(serializer);
        } else if (b <= FIRST_BOOT_UID) {
            serializer = Repository.requestBootstrapSerializer(b);
        } else if (b == REF_NULL) {
            return null;
        } else if (b == REF_RECURSIVE) {
            offset += 2;
            return null;
        } else if (b == REF_RECURSIVE2) {
            offset += 4;
            return null;
        } else {
            return readRef(b);
        }

        serializer.skip(this);
        return null;
    }
}

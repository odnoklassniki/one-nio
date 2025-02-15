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

import one.nio.util.Hash;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A wrapper for a preserialized object. Helps to avoid double serialization.
 * The wrapper automatically unpacks the original object during deserialization.
 */
public class SerializedWrapper<T> implements Serializable {
    private final byte[] serialized;

    public SerializedWrapper(byte[] serialized) {
        this.serialized = serialized;
    }

    public byte[] getSerialized() {
        return serialized;
    }

    @Override
    public int hashCode() {
        return Hash.xxhash(serialized, 0, serialized.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SerializedWrapper)) {
            return false;
        }

        return Arrays.equals(serialized, ((SerializedWrapper) obj).serialized);
    }

    public static <T> SerializedWrapper<T> wrap(T object) throws IOException {
        return new SerializedWrapper<>(Serializer.serialize(object));
    }

    @SuppressWarnings("unchecked")
    public T unwrap() throws IOException, ClassNotFoundException {
        return (T) Serializer.deserialize(serialized);
    }
}

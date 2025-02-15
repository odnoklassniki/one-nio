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

import java.io.NotSerializableException;

class InvalidSerializer extends Serializer {

    InvalidSerializer(Class cls) {
        super(cls);
    }

    InvalidSerializer(String descriptor) {
        super(Object.class);
        this.descriptor = descriptor;
        this.origin = Origin.EXTERNAL;
    }

    @Override
    public void calcSize(Object obj, CalcSizeStream css) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public void write(Object obj, DataStream out) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Object read(DataStream in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public void skip(DataStream in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public void toJson(Object obj, StringBuilder builder) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Object fromJson(JsonReader in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Object fromString(String s) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }
}

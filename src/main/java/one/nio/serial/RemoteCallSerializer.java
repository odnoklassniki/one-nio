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

import one.nio.rpc.RemoteCall;

import java.io.IOException;
import java.io.NotSerializableException;

class RemoteCallSerializer extends Serializer<RemoteCall> {

    RemoteCallSerializer() {
        super(RemoteCall.class);
    }

    @Override
    public void calcSize(RemoteCall obj, CalcSizeStream css) throws IOException {
        MethodSerializer serializer = obj.serializer();
        Object[] args = obj.args();

        css.count += 8;
        for (int i = 0; i < serializer.argCount; i++) {
            css.writeObject(args[i]);
        }
    }

    @Override
    public void write(RemoteCall obj, DataStream out) throws IOException {
        MethodSerializer serializer = obj.serializer();
        Object[] args = obj.args();

        out.writeLong(serializer.uid);
        for (int i = 0; i < serializer.argCount; i++) {
            out.writeObject(args[i]);
        }
    }

    @Override
    public RemoteCall read(DataStream in) throws IOException, ClassNotFoundException {
        MethodSerializer serializer = (MethodSerializer) Repository.requestSerializer(in.readLong());
        Object[] args = new Object[serializer.argCount];

        RemoteCall result = new RemoteCall(serializer, args);
        in.register(result);
        for (int i = 0; i < args.length; i++) {
            args[i] = in.readObject();
        }
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        MethodSerializer serializer = (MethodSerializer) Repository.requestSerializer(in.readLong());
        for (int i = 0; i < serializer.argCount; i++) {
            in.readObject();
        }
    }

    @Override
    public void toJson(RemoteCall obj, StringBuilder builder) throws IOException {
        builder.append("{\"method\":\"").append(obj.method()).append("\",\"args\":[");
        Object[] args = obj.args();
        if (args.length > 0) {
            Json.appendObject(builder, args[0]);
            for (int i = 1; i < args.length; i++) {
                builder.append(',');
                Json.appendObject(builder, args[i]);
            }
        }
        builder.append("]}");
    }

    @Override
    public RemoteCall fromJson(JsonReader in) throws NotSerializableException {
        throw new NotSerializableException(descriptor);
    }
}

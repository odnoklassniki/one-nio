/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.rpc.stream;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

public interface RpcStream extends BaseStream, ObjectInput, ObjectOutput {
    void read(ByteBuffer buf) throws IOException;
    void write(ByteBuffer buf) throws IOException;

    static RpcStream create(StreamHandler<RpcStream> handler) {
        return new StreamProxy<>(handler);
    }
}

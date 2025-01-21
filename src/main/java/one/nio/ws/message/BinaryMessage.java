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

package one.nio.ws.message;

import one.nio.util.Hex;
import one.nio.util.SimpleName;
import one.nio.ws.frame.Opcode;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class BinaryMessage extends Message<byte[]> {

    public BinaryMessage(byte[] payload) {
        this(Opcode.BINARY, payload);
    }

    protected BinaryMessage(Opcode opcode, byte[] payload) {
        super(opcode, payload);
    }

    @Override
    public byte[] payload() {
        return payload;
    }

    @Override
    public String toString() {
        return SimpleName.of(getClass()) + "<" + Hex.toHex(payload) + ">";
    }
}

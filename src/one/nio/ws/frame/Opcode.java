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

package one.nio.ws.frame;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public enum Opcode {
    CONTINUATION(0x00),
    TEXT(0x01),
    BINARY(0x02),
    CLOSE(0x08),
    PING(0x09),
    PONG(0x0A);

    private static final Opcode[] VALUES;
    static {
        VALUES = new Opcode[11];
        for (Opcode opcode : Opcode.values()) {
            if (VALUES[opcode.value] != null) {
                throw new IllegalArgumentException("Opcode " + opcode.value + " already used.");
            }
            VALUES[opcode.value] = opcode;
        }
    }

    public final byte value;

    Opcode(int value) {
        this.value = (byte) value;
    }

    public boolean isControl() {
        return (value & 0x08) > 0;
    }

    public boolean isContinuation() {
        return this == CONTINUATION;
    }

    public static Opcode valueOf(int value) {
        return VALUES[value];
    }
}

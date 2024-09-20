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

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class Frame {
    private final boolean fin;
    private final Opcode opcode;
    private int rsv;
    private int payloadLength;
    private byte[] mask;
    private byte[] payload;

    public Frame(boolean fin, Opcode opcode, int rsv, int payloadLength) {
        this.fin = fin;
        this.opcode = opcode;
        this.rsv = rsv;
        this.payloadLength = payloadLength;
    }

    public Frame(Opcode opcode, byte[] payload) {
        this.fin = true;
        this.rsv = 0;
        this.opcode = opcode;
        this.payload = payload;
        this.payloadLength = payload.length;
    }

    public boolean isFin() {
        return fin;
    }

    public int getRsv() {
        return rsv;
    }

    public void setRsv(int rsv) {
        this.rsv = rsv;
    }

    public Opcode getOpcode() {
        return opcode;
    }

    public boolean isControl() {
        return opcode.isControl();
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public byte[] getMask() {
        return mask;
    }

    public void setMask(byte[] mask) {
        this.mask = mask;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
        this.payloadLength = payload.length;
    }

    public void unmask() {
        if (mask == null) {
            return;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(payload);
        final int intMask = ByteBuffer.wrap(mask).getInt();
        while (buffer.remaining() >= 4) {
            int pos = buffer.position();
            buffer.putInt(pos, buffer.getInt() ^ intMask);
        }
        while (buffer.hasRemaining()) {
            int pos = buffer.position();
            buffer.put(pos, (byte) (buffer.get() ^ mask[pos % 4]));
        }
        setPayload(buffer.array());
        this.mask = null;
    }
}

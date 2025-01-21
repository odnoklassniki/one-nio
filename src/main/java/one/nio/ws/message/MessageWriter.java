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

import java.io.IOException;
import java.util.List;

import one.nio.net.Session;
import one.nio.ws.extension.Extension;
import one.nio.ws.frame.Frame;
import one.nio.ws.frame.FrameWriter;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class MessageWriter {
    private final FrameWriter writer;
    private final List<Extension> extensions;

    public MessageWriter(Session session, List<Extension> extensions) {
        this.writer = new FrameWriter(session);
        this.extensions = extensions;
    }

    public void write(Message<?> message) throws IOException {
        Frame frame = new Frame(message.opcode(), message.payload());
        for (Extension extension : extensions) {
            extension.transformOutput(frame);
        }
        writer.write(frame);
    }
}

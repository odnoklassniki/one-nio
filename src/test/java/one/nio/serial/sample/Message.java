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

package one.nio.serial.sample;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class Message implements Serializable {
    public final long id;
    public final long timestamp;
    public final Long author;
    public final String text;
    public final List<Attachment> attachments;

    public Message(long id, Long author, String text, List<Attachment> attachments) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.author = author;
        this.text = text;
        this.attachments = attachments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return id == message.id &&
                timestamp == message.timestamp &&
                Objects.equals(author, message.author) &&
                Objects.equals(text, message.text) &&
                Objects.equals(attachments, message.attachments);
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}

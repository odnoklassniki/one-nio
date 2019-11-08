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

package one.nio.serial.sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Sample {

    public static Chat createChat() {
        Long user1 = 11L;
        Long user2 = 22L;
        return new Chat(1, new long[]{user1, user2}, "Chat title", "http://path.to/icon.png", Arrays.asList(
                new Message(111, user1, "First message", Collections.<Attachment>emptyList()),
                new Message(222, user1, "Second message", Collections.<Attachment>singletonList(
                        new Attachment(Attachment.Type.PHOTO, 999)
                )),
                new Message(333, user2, "Reply", Collections.<Attachment>emptyList()),
                new Message(444, user1, "Reply to reply", Arrays.asList(
                        new Attachment(Attachment.Type.PHOTO, 888),
                        new Attachment(Attachment.Type.VIDEO, 777)
                                .with("property1", "value")
                                .with("property2", true)
                                .with("", 0)
                )),
                new Message(555, null, "value", new ArrayList<Attachment>())
        ));
    }
}

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

import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public class SerializerRegistrationAfterRemove {

    static class User implements Serializable {
        String name;

        public User(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    @Test
    public void test() throws IOException, ClassNotFoundException {
        Serializer<User> userSerializer = Repository.get(User.class);
        Repository.removeSerializer(userSerializer.uid);
        User data = new User("test");
        Utils.checkSerialize(data);
    }
}

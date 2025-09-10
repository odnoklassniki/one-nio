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

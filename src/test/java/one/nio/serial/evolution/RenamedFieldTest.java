package one.nio.serial.evolution;

import one.nio.serial.AbstractEvolutionTest;
import one.nio.serial.Renamed;
import one.nio.serial.Utils;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public class RenamedFieldTest extends AbstractEvolutionTest {

    static class UserOld implements Serializable {
        private String name_old;
        private final String name_final_old;
        private long id_old;
        private final long id_final_old;

        public UserOld(String name_final, long id, String name, long idFinal) {
            this.id_old = id;
            id_final_old = idFinal;
            this.name_old = name;
            this.name_final_old = name_final;
        }
    }

    static class UserNew implements Serializable {
        @Renamed(from="name_old")
        private String name;

        @Renamed(from="name_final_old")
        private final String name_final;

        @Renamed(from="id_old")
        private long id;

        @Renamed(from="id_final_old")
        private final long id_final;

        public UserNew(String name_final, long id, String name, long idFinal) {
            this.id = id;
            id_final = idFinal;
            this.name = name;
            this.name_final = name_final;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserNew userNew = (UserNew) o;
            return id == userNew.id && id_final == userNew.id_final && Objects.equals(name, userNew.name) && Objects.equals(name_final, userNew.name_final);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, name_final, id, id_final);
        }
    }

    @Test
    public void testActualData() throws IOException, ClassNotFoundException {
        UserNew user = new UserNew("final", 1L, "name", -1L);
        Utils.checkSerialize(user);
    }

    @Test
    public void testOldData() throws IOException, ClassNotFoundException {
        UserOld userOld = new UserOld("final", 1L, "name", -1L);
        UserNew userNew = new UserNew("final", 1L, "name", -1L);
        doTest(userOld, userNew);
    }
}

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

package one.nio.serial.evolution;

import one.nio.serial.AbstractEvolutionTest;
import one.nio.serial.evolution.sample.Base;
import one.nio.serial.evolution.sample.Derived;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public class BaseDerivedChangeTest extends AbstractEvolutionTest {

    static class UserDerived implements Serializable {
        private Derived info;

        public UserDerived(Derived info) {
            this.info = info;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserDerived userOld = (UserDerived) o;
            return Objects.equals(info, userOld.info);
        }

        @Override
        public int hashCode() {
            return Objects.hash(info);
        }
    }

    static class UserBase implements Serializable {
        private Base info;

        public UserBase(Base info) {
            this.info = info;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserBase userNew = (UserBase) o;
            return Objects.equals(info, userNew.info);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(info);
        }
    }

    @Test
    public void testDerivedToBaseClass() throws IOException, ClassNotFoundException {
        UserDerived userOld = new UserDerived(new Derived("myname", 1));
        UserBase userNew = new UserBase(new Derived("myname", 1));
        doTest(userOld, userNew);
    }

    @Test
    public void testBase2DerivedOK() throws IOException, ClassNotFoundException {
        UserBase userOld = new UserBase(new Derived("myname", 1));
        UserDerived userNew = new UserDerived(new Derived("myname", 1));
        doTest(userOld, userNew);
    }

    @Test(expected = ClassCastException.class)
    public void testBase2DerivedFail() throws IOException, ClassNotFoundException {
        UserBase userOld = new UserBase(new Base("myname"));
        UserDerived userNew = new UserDerived(new Derived("should fail", 1));
        doTest(userOld, userNew);
    }

    static class UserFinalDerived implements Serializable {
        private final Derived info;

        public UserFinalDerived(Derived info) {
            this.info = info;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserFinalDerived userOld = (UserFinalDerived) o;
            return Objects.equals(info, userOld.info);
        }

        @Override
        public int hashCode() {
            return Objects.hash(info);
        }
    }

    static class UserFinalBase implements Serializable {
        private final Base info;

        public UserFinalBase(Base info) {
            this.info = info;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserFinalBase userNew = (UserFinalBase) o;
            return Objects.equals(info, userNew.info);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(info);
        }
    }

    @Test
    public void testFinalDerivedToBaseClass() throws IOException, ClassNotFoundException {
        UserFinalDerived userOld = new UserFinalDerived(new Derived("myname", 1));
        UserFinalBase userNew = new UserFinalBase(new Derived("myname", 1));
        doTest(userOld, userNew);
    }

    @Test
    public void testFinalBase2DerivedOK() throws IOException, ClassNotFoundException {
        UserFinalBase userOld = new UserFinalBase(new Derived("myname", 1));
        UserFinalDerived userNew = new UserFinalDerived(new Derived("myname", 1));
        doTest(userOld, userNew);
    }

    @Test(expected = ClassCastException.class)
    public void testFinalBase2DerivedFail() throws IOException, ClassNotFoundException {
        UserFinalBase userOld = new UserFinalBase(new Base("myname"));
        UserFinalDerived userNew = new UserFinalDerived(new Derived("should fail", 1));
        doTest(userOld, userNew);
    }
}

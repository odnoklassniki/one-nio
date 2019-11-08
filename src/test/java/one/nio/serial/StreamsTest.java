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

package one.nio.serial;

import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;

import static org.junit.Assert.assertEquals;

public class StreamsTest {

    @Test
    public void testReadPlace() throws IOException, ClassNotFoundException {
        Place place = new Place("blah", "", "");

        Serializer<Place> serializer = Repository.get(Place.class);
        CalcSizeStream css = new CalcSizeStream();
        serializer.calcSize(place, css);
        int length = css.count();

        byte[] buf = new byte[length];
        SerializeStream out = new SerializeStream(buf);
        serializer.write(place, out);
        DeserializeStream in = new DeserializeStream(buf);
        Place place2 = serializer.read(in);

        assertEquals(place, place2);
    }

    private static class Place implements Serializable {
        private String name;
        private String altName;
        private String intName;

        public Place(String name, String altName, String intName) {
            this.name = name;
            this.altName = altName;
            this.intName = intName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Place place = (Place) o;

            if (intName != null ? !intName.equals(place.intName) : place.intName != null) {
                return false;
            }
            if (name != null ? !name.equals(place.name) : place.name != null) {
                return false;
            }
            if (altName != null ? !altName.equals(place.altName) : place.altName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (altName != null ? altName.hashCode() : 0);
            result = 31 * result + (intName != null ? intName.hashCode() : 0);
            return result;
        }
    }
}

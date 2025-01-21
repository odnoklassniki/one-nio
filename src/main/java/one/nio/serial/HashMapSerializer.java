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

import java.io.IOException;
import java.util.HashMap;

class HashMapSerializer extends MapSerializer {

    HashMapSerializer() {
        super(HashMap.class);
    }

    @Override
    public HashMap read(DataStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        HashMap<Object, Object> result = new HashMap<>(length * 3 / 2, 0.75f);
        in.register(result);
        for (int i = 0; i < length; i++) {
            result.put(in.readObject(), in.readObject());
        }
        return result;
    }
}

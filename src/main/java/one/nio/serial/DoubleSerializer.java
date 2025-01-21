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

class DoubleSerializer extends Serializer<Double> {

    DoubleSerializer() {
        super(Double.class);
    }

    @Override
    public void calcSize(Double obj, CalcSizeStream css) {
        css.count += 8;
    }

    @Override
    public void write(Double v, DataStream out) throws IOException {
        out.writeDouble(v);
    }

    @Override
    public Double read(DataStream in) throws IOException {
        Double result = in.readDouble();
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(8);
    }

    @Override
    public void toJson(Double obj, StringBuilder builder) {
        builder.append(obj.doubleValue());
    }

    @Override
    public Double fromJson(JsonReader in) throws IOException {
        return in.readDouble();
    }

    @Override
    public Double fromString(String s) {
        return Double.valueOf(s);
    }
}

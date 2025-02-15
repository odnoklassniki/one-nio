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

package one.nio.serial.gen;

import one.nio.serial.CalcSizeStream;
import one.nio.serial.DataStream;
import one.nio.serial.JsonReader;

import java.io.IOException;

public interface Delegate {
    void calcSize(Object obj, CalcSizeStream css) throws IOException;
    void write(Object obj, DataStream out) throws IOException;
    Object read(DataStream in) throws IOException, ClassNotFoundException;
    void skip(DataStream in) throws IOException, ClassNotFoundException;
    void toJson(Object obj, StringBuilder builder) throws IOException;
    Object fromJson(JsonReader in) throws IOException, ClassNotFoundException;
}

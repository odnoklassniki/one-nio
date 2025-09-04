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

package one.nio.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;

/**
 * Convenience methods for looking up VarHandles.
 */
public class VarHandlesReflection {

    public static VarHandle forField(Class<?> declaringClass, String fieldName, Class<?> fieldType) {
        try {
            Lookup lookup = MethodHandles.lookup();
            return lookup.findVarHandle(declaringClass, fieldName, fieldType);
        } catch (Exception e) {
            try {
                return MethodHandlesReflection.privateLookup.findVarHandle(declaringClass, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                return null;
            }
        }
    }
}

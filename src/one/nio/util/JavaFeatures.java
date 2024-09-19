/*
 * Copyright 2020 Odnoklassniki Ltd, Mail.Ru Group
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class JavaFeatures {
    private static final MethodHandle onSpinWait = getOnSpinWait();
    private static final MethodHandle isRecord = getIsRecord();

    private static MethodHandle getOnSpinWait() {
        try {
            return MethodHandles.publicLookup().findStatic(
                    Thread.class, "onSpinWait", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static MethodHandle getIsRecord() {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    Class.class, "isRecord", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Calls Thread.onSpinWait() since Java 9; does nothing otherwise
     */
    public static void onSpinWait() {
        if (onSpinWait != null) {
            try {
                onSpinWait.invokeExact();
            } catch (Throwable e) {
                // Never happens
            }
        }
    }

    /**
     * Calls Class.isRecord() since Java 14 preview
     *
     * @param cls a class object
     * @return the result of the Class.isRecord() method invoked. It is always false, if the version of the JVM Runtime is less than 14
     */
    public static boolean isRecord(Class<?> cls) {
        if (isRecord != null) {
            try {
                return (boolean) isRecord.invokeExact(cls);
            } catch (Throwable e) {
                // Never happens
            }
        }
        return false;
    }
}

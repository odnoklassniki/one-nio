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

public class SpinWait {
    private static final MethodHandle onSpinWait = getOnSpinWait();

    private static MethodHandle getOnSpinWait() {
        try {
            return MethodHandles.publicLookup().findStatic(
                    Thread.class, "onSpinWait", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            // fall through
        }

        // Older JDK: no Thread.onSpinWait method
        try {
            return MethodHandles.lookup().findStatic(
                    SpinWait.class, "onSpinWait", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Should not happen");
        }
    }

    private static void onSpinWait() {
        // Fallback implementation
    }

    public static void pause() {
        try {
            onSpinWait.invokeExact();
        } catch (Throwable e) {
            // Never happens
        }
    }
}

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

import one.nio.os.NativeLibrary;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Allows to get a subset of available Class' fields or methods,
 * when the standard getDeclaredFields() / getDeclaredMethods()
 * fail with NoClassDefFoundError due to some missing class file.
 */
public class NativeReflection {
    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED && initJVMTI();

    private static native boolean initJVMTI();

    public static native Field[] getFields(Class<?> cls, boolean includeStatic);
    public static native Method[] getMethods(Class<?> cls, boolean includeStatic);

    public static native void openModules();
}

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

package one.nio.serial;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintStream;
import java.io.PrintWriter;

public class AsmUtils {
    static class MyVisitor extends ClassVisitor {
        protected MyVisitor(int api) {
            super(api);
        }
    }

    public static Type OBJECT_TYPE = Type.getType(Object.class);

    public static void printify(byte[] classData, PrintStream out) {
        ClassReader classReader = new ClassReader(classData);
        PrintWriter printWriter = new PrintWriter(out);
        classReader.accept(new TraceClassVisitor(printWriter), ClassReader.SKIP_DEBUG);
    }

    public static void verifyBytecode(byte[] classData) {
        ClassReader classReader = new ClassReader(classData);
        classReader.accept(new CheckClassAdapter(new MyVisitor(Opcodes.ASM9)), ClassReader.SKIP_DEBUG);
    }
}

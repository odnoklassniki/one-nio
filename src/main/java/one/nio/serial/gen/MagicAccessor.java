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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MagicAccessor {
    
    public static byte[] magicAccessorBridge() {
        String superClass;
        if (useSerializationConstructorAccessor()) {
            superClass = "jdk/internal/reflect/SerializationConstructorAccessorImpl";
        } else {
            superClass = "jdk/internal/reflect/MagicAccessorImpl";
        }

        return generateClass("jdk/internal/reflect/MagicAccessorBridge", superClass);
    }

    public static byte[] sunMagicAccessor() {
        return generateClass(DelegateGenerator.MAGIC_CLASS, "jdk/internal/reflect/MagicAccessorBridge");
    }

    private static boolean useSerializationConstructorAccessor() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = Integer.parseInt(javaVersion.substring(0, javaVersion.indexOf(".")));
        return majorVersion >= 22;
    }

    private static byte[] generateClass(String name, String superClass) {
        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, superClass, new String[]{});
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitEnd();
        cv.visitEnd();
        return cv.toByteArray();
    }

}

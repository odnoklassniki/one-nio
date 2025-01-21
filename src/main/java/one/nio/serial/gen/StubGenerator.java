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

package one.nio.serial.gen;

import one.nio.gen.BytecodeGenerator;
import one.nio.serial.FieldDescriptor;
import one.nio.serial.Repository;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class StubGenerator extends BytecodeGenerator {
    public static final AtomicInteger stubClasses = new AtomicInteger();

    public static Class<?> generateRegular(String className, String superName, FieldDescriptor[] fds) {
        String internalClassName = getStubName(className);

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL, internalClassName, null, superName,
                new String[] { "java/io/Serializable" });

        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        if (fds != null) {
            HashSet<String> generatedFields = new HashSet<>();
            for (FieldDescriptor fd : fds) {
                String name = fd.name();
                String oldName = null;

                int p = name.indexOf('|');
                if (p >= 0) {
                    oldName = name.substring(p + 1);
                    name = name.substring(0, p);
                }

                String type = Type.getDescriptor(fd.type().resolve());
                if (!generatedFields.add(name + ':' + type)) {
                    Repository.log.warn("Skipping duplicate field: " + className + '.' + name);
                    continue;
                }

                FieldVisitor fv = cv.visitField(ACC_PRIVATE, name, type, null, null);
                if (oldName != null) {
                    AnnotationVisitor av = fv.visitAnnotation("Lone/nio/serial/Renamed;", true);
                    av.visit("from", oldName);
                    av.visitEnd();
                }
                fv.visitEnd();
            }
        }

        cv.visitEnd();
        return INSTANCE.defineClassIfNotExists(internalClassName.replace('/', '.'), cv.toByteArray());
    }

    public static Class<?> generateEnum(String className, String[] constants) {
        String internalClassName = getStubName(className);

        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(V1_6, ACC_PUBLIC | ACC_FINAL | ACC_ENUM, internalClassName, null, "java/lang/Enum", null);

        String classDesc = 'L' + internalClassName + ';';
        for (String c : constants) {
            cv.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, c, classDesc, null, null).visitEnd();
        }

        String arrayDesc = '[' + classDesc;
        cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "$VALUES", arrayDesc, null, null).visitEnd();

        MethodVisitor mv = cv.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        emitInt(mv, constants.length);
        mv.visitTypeInsn(ANEWARRAY, internalClassName);

        for (int i = 0; i < constants.length; i++) {
            mv.visitInsn(DUP);
            emitInt(mv, i);
            mv.visitTypeInsn(NEW, internalClassName);
            mv.visitInsn(DUP);
            mv.visitLdcInsn(constants[i]);
            emitInt(mv, i);
            mv.visitMethodInsn(INVOKESPECIAL, internalClassName, "<init>", "(Ljava/lang/String;I)V", false);
            mv.visitInsn(DUP);
            mv.visitFieldInsn(PUTSTATIC, internalClassName, constants[i], classDesc);
            mv.visitInsn(AASTORE);
        }

        mv.visitFieldInsn(PUTSTATIC, internalClassName, "$VALUES", arrayDesc);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "()" + arrayDesc, null, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, internalClassName, "$VALUES", arrayDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + classDesc, null, null);
        mv.visitCode();
        mv.visitLdcInsn(Type.getObjectType(internalClassName));
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        mv.visitTypeInsn(CHECKCAST, internalClassName);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cv.visitEnd();
        return INSTANCE.defineClassIfNotExists(internalClassName.replace('/', '.'), cv.toByteArray());
    }

    private static String getStubName(String className) {
        Repository.log.warn("Generating stub for class " + className);
        stubClasses.incrementAndGet();
        return "sun/reflect/" + className;
    }
}

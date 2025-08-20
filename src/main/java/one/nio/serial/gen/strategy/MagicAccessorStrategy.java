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

package one.nio.serial.gen.strategy;


import one.nio.serial.FieldDescriptor;
import one.nio.serial.SerializeWith;
import one.nio.serial.gen.FieldType;
import one.nio.serial.gen.MagicAccessor;
import one.nio.util.JavaInternals;
import one.nio.util.MethodHandlesReflection;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.function.Consumer;

import static one.nio.gen.BytecodeGenerator.*;
import static one.nio.util.JavaInternals.unsafe;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

public final class MagicAccessorStrategy extends GenerationStrategy {

    public static final String MAGIC_CLASS = "sun/reflect/MagicAccessorImpl";

    // In JDK 9+ there is no more sun.reflect.MagicAccessorImpl class.
    // Instead there is package private jdk.internal.reflect.MagicAccessorImpl, which is not visible
    // by application classes. We abuse ClassLoaded private API to inject a publicly visible bridge
    // using the bootstrap ClassLoader.
    //   ¯\_(ツ)_/¯
    static {
        if (JavaInternals.hasModules()) {
            try {
                Method m = JavaInternals.getMethod(ClassLoader.class, "defineClass1", ClassLoader.class, String.class,
                        byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
                if (m == null) {
                    throw new NoSuchMethodException("ClassLoader.defineClass1");
                }

                // public jdk.internal.reflect.MagicAccessorBridge extends jdk.internal.reflect.MagicAccessorImpl
                defineBootstrapClass(m, MagicAccessor.magicAccessorBridge());
                // public sun.reflect.MagicAccessorImpl extends jdk.internal.reflect.MagicAccessorBridge
                defineBootstrapClass(m, MagicAccessor.sunMagicAccessor());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // Defines a new class by the bootstrap ClassLoader
    private static void defineBootstrapClass(Method m, byte[] code) throws ReflectiveOperationException {
        m.invoke(null, null, null, code, 0, code.length, null, null);
    }


    @Override
    public String getBaseClassName() {
        return MAGIC_CLASS;
    }

    @Override
    public void generateStatics(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields) {

    }

    @Override
    public void emitWriteObjectCall(MethodVisitor mv, Class clazz, MethodHandleInfo methodType) {
        emitInvoke(mv, methodType);
    }

    @Override
    public void emitReadObjectCall(MethodVisitor mv, Class clazz, MethodHandleInfo methodType) {
        emitInvoke(mv, methodType);

    }

    @Override
    public void emitReadSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName) {
        SerializeWith serializeWith = field.getAnnotation(SerializeWith.class);
        if (serializeWith != null && !serializeWith.getter().isEmpty()) {
            try {
                MethodHandleInfo m = MethodHandlesReflection.findInstanceMethodOrThrow(field.getDeclaringClass(), serializeWith.getter(), MethodType.methodType(field.getType()));
                emitInvoke(mv, m);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Getter method not found", e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Incompatible getter method", e);
            }
        } else {
            emitGetField(mv, field);
        }
    }

    @Override
    public void emitWriteSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName) {
        SerializeWith serializeWith = field.getAnnotation(SerializeWith.class);
        if (serializeWith != null && !serializeWith.setter().isEmpty()) {
            try {
                MethodHandleInfo m = MethodHandlesReflection.findInstanceMethodOrThrow(field.getDeclaringClass(), serializeWith.setter(), MethodType.methodType(void.class, field.getType()));
                emitInvoke(mv, m);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Setter method not found", e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Incompatible setter method", e);
            }
        } else if (Modifier.isFinal(field.getModifiers())) {
            FieldType dstType = FieldType.valueOf(field.getType());
            mv.visitLdcInsn(unsafe.objectFieldOffset(field));
            mv.visitMethodInsn(INVOKESTATIC, "one/nio/util/JavaInternals", dstType.putMethod(), dstType.putSignature(), false);
        } else {
            emitPutField(mv, field);
        }
    }

    @Override
    public void emitRecordConstructorCall(MethodVisitor mv, Class clazz, String className, Constructor constuctor, Consumer<MethodVisitor> argGenerator) {
        mv.visitInsn(DUP);

        argGenerator.accept(mv);
        String holder = Type.getInternalName(constuctor.getDeclaringClass());
        String sig = Type.getConstructorDescriptor(constuctor);
        mv.visitMethodInsn(INVOKESPECIAL, holder, "<init>", sig, false);
    }
}

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

import one.nio.serial.FieldDescriptor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import static one.nio.serial.gen.HandlesStrategy.loadPrimitiveType;
import static org.objectweb.asm.Opcodes.CHECKCAST;

public abstract class GenerationStrategy {

    public abstract String getBaseClassName();

    public abstract void generateStatics(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields);

    public abstract void emitReadSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName);

    public abstract void emitWriteSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName);

    public abstract void emitWriteObjectCall(MethodVisitor mv, String className, MethodHandleInfo methodType);

    public abstract void emitReadObjectCall(MethodVisitor mv, String className, MethodHandleInfo methodType);

    public abstract void emitRecordConstructorCall(MethodVisitor mv, Class clazz, String className, Constructor constuctor, Consumer<MethodVisitor> argGenerator);

    public void generateCast(MethodVisitor mv, Class dst) {
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(dst));
    }

    public void loadClassSafe(MethodVisitor mv, Class clazz) {
        if (clazz.isPrimitive()) {
            loadPrimitiveType(mv, clazz);
        } else {
            mv.visitLdcInsn(Type.getType(clazz));;
        }
    }

}

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
import one.nio.serial.Repository;
import one.nio.util.JavaVersion;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.CHECKCAST;

public abstract class GenerationStrategy {

    private static final String STRATEGY_OPTION = "one.nio.serial.gen.mode";

    private static final String OLD_MODE = "magic_accessor";

    private static final String NEW_MODE = "method_handles";

    public abstract String getBaseClassName();

    public abstract void generateStatics(ClassWriter cv, Class cls, String className, FieldDescriptor[] fds, FieldDescriptor[] defaultFields);

    public abstract void emitReadSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName);

    public abstract void emitWriteSerialField(MethodVisitor mv, Class clazz, Field field, String serializerClassName);

    public abstract void emitWriteObjectCall(MethodVisitor mv, Class clazz, MethodHandleInfo methodType);

    public abstract void emitReadObjectCall(MethodVisitor mv, Class clazz, MethodHandleInfo methodType);

    public abstract void emitRecordConstructorCall(MethodVisitor mv, Class clazz, String className, Constructor constuctor, Consumer<MethodVisitor> argGenerator);


    public static GenerationStrategy createStrategy() {
        String option = System.getProperty(STRATEGY_OPTION);
        GenerationStrategy strategy = null;

        if (option == null) {
            option = OLD_MODE;
        }

        Logger logger = Repository.log;

        if (OLD_MODE.equalsIgnoreCase(option) && JavaVersion.isJava24Plus()) {
            String msg = "One-nio 2.x supports JDK 24+ only in experimental mode, which can be enable by setting the `one.nio.serial.gen.strategy=handles` environment variable. Please refer to the documentation for additional details.";
            logger.warn(msg);
            throw new RuntimeException(msg);
        }

        if (NEW_MODE.equalsIgnoreCase(option) && JavaVersion.isJava8()) {
            String msg = "One-nio doesn't support the `one.nio.serial.gen.strategy=handles` mode with JDK 8. Please use JDK 9 or higher, or remove the `one.nio.serial.gen.strategy` environment variable.";
            logger.warn(msg);
            throw new RuntimeException(msg);
        }

        if (!NEW_MODE.equalsIgnoreCase(option) && !OLD_MODE.equalsIgnoreCase(option)) {
            String msg = "Unknown value for `one.nio.serial.gen.mode` option: '" + option + "'. Supported values are '" + OLD_MODE + "' and '" + NEW_MODE + "'.";
            logger.warn(msg);
            throw new RuntimeException(msg);
        }

        logger.info("One-nio uses `" + option + "` strategy for class generation.");
        if (NEW_MODE.equalsIgnoreCase(option)) {
            return new HandlesStrategy();
        } else {
            return new MagicAccessorStrategy();
        }
    }

    public void generateCast(MethodVisitor mv, Class dst) {
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(dst));
    }

    public void loadClassSafe(MethodVisitor mv, Class clazz) {
        mv.visitLdcInsn(Type.getType(clazz));;
    }

}

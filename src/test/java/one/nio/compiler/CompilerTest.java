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

package one.nio.compiler;

import one.nio.gen.BytecodeGenerator;

public class CompilerTest {

    public static void main(String[] args) throws Exception {
        String code =
                "package one.nio.compiler;\n" +
                "public class GeneratedRunnable implements Runnable {\n" +
                "    public void run() {\n" +
                "        System.out.println(\"Hello \" + getClass().getClassLoader());\n" +
                "    }\n" +
                "}\n";

        long startMemory = Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        byte[] classData = new Javac(CompilerTest.class.getClassLoader()).compile(code);

        long compilationTime = System.currentTimeMillis();

        Runnable runnable = new BytecodeGenerator().instantiate(classData, Runnable.class);

        long loadingTime = System.currentTimeMillis();
        long endMemory = Runtime.getRuntime().freeMemory();

        runnable.run();

        System.out.println("Compilation time = " + (compilationTime - startTime) +
                ", loading time = " + (loadingTime - compilationTime) +
                ", memory = " + (startMemory - endMemory) / 1024 + " KB");
    }
}

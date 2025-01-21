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

package one.nio.os.perf;

public final class SampleType {
    public static final int IP = 0x1;
    public static final int TID = 0x2;
    public static final int TIME = 0x4;
    public static final int ADDR = 0x8;
    public static final int READ = 0x10;
    public static final int CALLCHAIN = 0x20;
    public static final int ID = 0x40;
    public static final int CPU = 0x80;
    public static final int PERIOD = 0x100;
    public static final int STREAM_ID = 0x200;
    public static final int RAW = 0x400;
    public static final int BRANCH_STACK = 0x800;
    public static final int REGS_USER = 0x1000;
    public static final int STACK_USER = 0x2000;
    public static final int WEIGHT = 0x4000;
    public static final int DATA_SRC = 0x8000;
    public static final int IDENTIFIER = 0x10000;
    public static final int TRANSACTION = 0x20000;
    public static final int REGS_INTR = 0x40000;
    public static final int PHYS_ADD = 0x80000;
}

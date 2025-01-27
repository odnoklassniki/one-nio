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

package one.nio.os;

public class BatchThread extends Thread {

    public BatchThread() {
    }

    public BatchThread(Runnable target) {
        super(target);
    }

    public BatchThread(String name) {
        super(name);
    }

    public BatchThread(Runnable target, String name) {
        super(target, name);
    }

    @Override
    public void run() {
        adjustPriority();
        super.run();
    }

    public static void adjustPriority() {
        if (Proc.IS_SUPPORTED) {
            Proc.sched_setscheduler(0, Proc.SCHED_BATCH);
            Proc.setpriority(0, 19);
        }
    }
}

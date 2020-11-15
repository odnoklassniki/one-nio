/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.server;

import one.nio.os.BatchThread;
import one.nio.os.IdleThread;
import one.nio.os.Proc;

public class PayloadThread extends Thread {
    protected Object payload;
    private int schedulingPolicy;

    public PayloadThread(Runnable target) {
        super(target);
    }

    public PayloadThread(String name) {
        super(name);
    }

    public PayloadThread(Runnable target, String name) {
        super(target, name);
    }

    public final Object payload() {
        return payload;
    }

    public final void setPayload(Object payload) {
        this.payload = payload;
    }

    public final int schedulingPolicy() {
        return schedulingPolicy;
    }

    public final void setSchedulingPolicy(int schedulingPolicy) {
        this.schedulingPolicy = schedulingPolicy;
    }

    public void adjustPriority() {
        if (schedulingPolicy == Proc.SCHED_BATCH) {
            BatchThread.adjustPriority();
        } else if (schedulingPolicy == Proc.SCHED_IDLE) {
            IdleThread.adjustPriority();
        }
    }

    @Override
    public void run() {
        adjustPriority();
        super.run();
    }

    public static PayloadThread current() {
        return (PayloadThread) Thread.currentThread();
    }
}

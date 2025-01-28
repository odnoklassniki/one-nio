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

package one.nio.server;

import one.nio.os.SchedulingPolicy;

public class PayloadThread extends Thread {
    protected Object payload;
    protected SchedulingPolicy schedulingPolicy;

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

    public final SchedulingPolicy schedulingPolicy() {
        return schedulingPolicy;
    }

    public final void setSchedulingPolicy(SchedulingPolicy schedulingPolicy) {
        this.schedulingPolicy = schedulingPolicy;
    }

    @Override
    public void run() {
        if (schedulingPolicy != null) {
            schedulingPolicy.apply();
        }
        super.run();
    }

    public static PayloadThread current() {
        return (PayloadThread) Thread.currentThread();
    }
}

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

package one.nio.mgt;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;

public class ThreadDumperImpl {
    private static final String PID;

    static {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        PID = vmName.substring(0, vmName.indexOf('@'));
    }

    public static void dump(OutputStream out) throws IOException {
        HotSpotVirtualMachine vm;
        try {
            vm = (HotSpotVirtualMachine) VirtualMachine.attach(PID);
        } catch (AttachNotSupportedException e) {
            throw new IOException(e);
        }

        try {
            if (out == null) {
                vm.localDataDump();
            } else {
                try (InputStream in = vm.remoteDataDump()) {
                    copy(in, out);
                }
            }
        } finally {
            vm.detach();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        for (int bytes; (bytes = in.read(buf)) > 0; ) {
            out.write(buf, 0, bytes);
        }
        out.flush();
    }
}

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
                InputStream in = vm.remoteDataDump();
                try {
                    copy(in, out);
                } finally {
                    in.close();
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

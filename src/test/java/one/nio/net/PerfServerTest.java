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

package one.nio.net;

import one.nio.mem.DirectMemory;
import one.nio.os.Mem;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class PerfServerTest {

    private static ByteBuffer wrap(byte[] buf) {
        return ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());
    }

    public static void main(String[] args) throws Exception {
        String sockPath = args.length > 0 ? args[0] : "/tmp/one-nio.sock";
        String mapPath = args.length > 1 ? args[1] : "/tmp/one-nio-map.tmp";
        long mapSize = args.length > 2 ? Long.parseLong(args[2]) : 16 * 1024 * 1024;

        RandomAccessFile raf = new RandomAccessFile(mapPath, "rw");
        raf.setLength(mapSize);
        int fd = Mem.getFD(raf.getFD());

        MappedByteBuffer map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, mapSize);
        long mapAddr = DirectMemory.getAddress(map);

        Socket srv = Socket.bindUnix(new File(sockPath), 1);
        byte[] request = new byte[4];
        byte[] response = new byte[20];

        while (true) {
            Socket s = srv.accept();
            byte[] cred = s.getOption(Socket.SOL_SOCKET, Socket.SO_PEERCRED);
            if (cred == null) {
                throw new IOException("Cannot get peer pid");
            }
            int clientPid = wrap(cred).getInt(0);

            int bytesRead = s.read(request, 0, request.length);
            if (bytesRead != 4 || wrap(request).getInt(0) != 2) {
                throw new IOException("Invalid request");
            }

            ByteBuffer buf = wrap(response);
            buf.putInt(2).putInt(0).putLong(mapSize).putInt(0);

            s.sendMsg(new Msg(response).withFd(fd), 0);
            s.close();
        }
    }
}

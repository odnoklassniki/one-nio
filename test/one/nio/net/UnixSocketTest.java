/*
 * Copyright 2021 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.os.Proc;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.*;

public class UnixSocketTest {

    @Test
    public void testSendDescriptors() throws IOException {
        File sockpath = new File("/tmp/one-nio-test.sock");
        sockpath.delete();

        Socket server = Socket.bindUnix(sockpath, 1);
        new Thread(() -> acceptorThread(server)).start();

        Socket client = Socket.connectUnix(sockpath);
        Msg msg = new Msg("hello".getBytes()).withFd(2, 1, 0);

        int bytes = client.sendMsg(msg, 0);
        assertEquals(5, bytes);

        client.close();
    }

    private void acceptorThread(Socket server) {
        try {
            Socket s = server.accept();

            byte[] cred = s.getOption(Socket.SOL_SOCKET, Socket.SO_PEERCRED);
            int clientPid = ByteBuffer.wrap(cred, 0, 4).order(ByteOrder.nativeOrder()).getInt();
            assertEquals(Proc.getpid(), clientPid);

            Msg msg = new Msg(100);
            int bytes = s.recvMsg(msg, 0);

            assertEquals(5, bytes);
            assertEquals("hello", new String(msg.data(), 0, 5));
            assertEquals(Msg.SCM_RIGHTS, msg.cmsgType());
            assertEquals(3,  msg.cmsgData().length);

            s.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        new UnixSocketTest().testSendDescriptors();
    }
}

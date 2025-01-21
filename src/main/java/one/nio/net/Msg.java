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

import java.util.Arrays;

public final class Msg {
    public static final int SCM_RIGHTS = 1;
    public static final int SCM_CREDENTIALS = 2;

    private byte[] data;
    private int cmsgType;
    private int[] cmsgData;

    public Msg(int capacity) {
        this.data = new byte[capacity];
    }

    public Msg(byte[] data) {
        this.data = data;
    }

    public byte[] data() {
        return data;
    }

    public int cmsgType() {
        return cmsgType;
    }

    public int[] cmsgData() {
        return cmsgData;
    }

    public Msg withCmsg(int type, int... data) {
        if (cmsgType != 0) {
            throw new IllegalStateException("cmsg already set");
        }
        cmsgType = type;
        cmsgData = data;
        return this;
    }

    public Msg withFd(int fd) {
        return withCmsg(SCM_RIGHTS, fd);
    }

    public Msg withFd(int... fds) {
        return withCmsg(SCM_RIGHTS, fds);
    }

    public Msg withCred(int pid, int uid, int gid) {
        return withCmsg(SCM_CREDENTIALS, pid, uid, gid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Msg(").append(data == null ? 0 : data.length);
        if (cmsgType != 0) {
            sb.append(cmsgType == SCM_RIGHTS ? " + SCM_RIGHTS" : " + SCM_CREDENTIALS");
            sb.append(Arrays.toString(cmsgData));
        }
        return sb.append(')').toString();
    }
}

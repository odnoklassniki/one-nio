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

package one.nio.os;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public final class User {
    private static final Log log = LogFactory.getLog(NativeLibrary.class);

    public static final boolean IS_SUPPORTED = NativeLibrary.IS_SUPPORTED;

    public static final String PASSWD = "/etc/passwd";
    public static final String GROUP  = "/etc/group";

    public static final int U_NAME      = 0;
    public static final int U_PASSWORD  = 1;
    public static final int U_UID       = 2;
    public static final int U_GID       = 3;
    public static final int U_GECOS     = 4;
    public static final int U_DIRECTORY = 5;
    public static final int U_SHELL     = 6;

    public static final int G_NAME      = 0;
    public static final int G_PASSWORD  = 1;
    public static final int G_GID       = 2;
    public static final int G_USER_LIST = 3;

    public static native int setuid(int uid);
    public static native int setgid(int gid);

    public static String[] findUser(String user) {
        return find(PASSWD, user);
    }

    public static int findUid(String user) {
        String[] userInfo = findUser(user);
        if (userInfo != null) {
            try {
                return Integer.parseInt(userInfo[U_UID]);
            } catch (RuntimeException e) {
                log.warn("Cannot find uid for " + user, e);
            }
        }
        return -1;
    }

    public static String[] findGroup(String group) {
        return find(GROUP, group);
    }

    public static int findGid(String group) {
        String[] groupInfo = findGroup(group);
        if (groupInfo != null) {
            try {
                return Integer.parseInt(groupInfo[G_GID]);
            } catch (RuntimeException e) {
                log.warn("Cannot find gid for " + group, e);
            }
        }
        return -1;
    }

    private static String[] find(String file, String account) {
        String searchString = account + ":";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                for (String s; (s = reader.readLine()) != null; ) {
                    if (s.startsWith(searchString)) {
                        return s.split(":");
                    }
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            log.warn("Cannot read " + file, e);
        }
        return null;
    }
}

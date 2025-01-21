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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class User {
    private static final Logger log = LoggerFactory.getLogger(NativeLibrary.class);

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
    public static native int setgroups(int[] gids);
    public static native int chown(String fileName, int uid, int gid);

    public static String[] findUser(String user) {
        return find(PASSWD, user);
    }

    public static int findUid(String user) {
        String[] userInfo = findUser(user);
        return userInfo != null ? Integer.parseInt(userInfo[U_UID]) : -1;
    }

    public static String[] findGroup(String group) {
        return find(GROUP, group);
    }

    public static int findGid(String group) {
        String[] groupInfo = findGroup(group);
        return groupInfo != null ? Integer.parseInt(groupInfo[G_GID]) : -1;
    }

    public static List<String[]> findSupplementaryGroups(String user) {
        List<String[]> groupInfos = new ArrayList<>();
        Pattern userPattern = Pattern.compile("\\b\\Q" + user + "\\E\\b");
        try (BufferedReader reader = new BufferedReader(new FileReader(GROUP))) {
            for (String s; (s = reader.readLine()) != null; ) {
                String[] groupInfo = s.split(":");
                if (groupInfo.length > G_USER_LIST && userPattern.matcher(groupInfo[G_USER_LIST]).find()) {
                    groupInfos.add(groupInfo);
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read " + GROUP, e);
        }
        return groupInfos;
    }

    public static int[] findSupplementaryGids(String user) {
        List<String[]> groupInfos = findSupplementaryGroups(user);
        int[] gids = new int[groupInfos.size()];
        for (int i = 0; i < gids.length; i++) {
            gids[i] = Integer.parseInt(groupInfos.get(i)[G_GID]);
        }
        return gids;
    }

    private static String[] find(String file, String account) {
        String searchString = account + ':';
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String s; (s = reader.readLine()) != null; ) {
                if (s.startsWith(searchString)) {
                    return s.split(":");
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read {}", file, e);
        }
        return null;
    }
}

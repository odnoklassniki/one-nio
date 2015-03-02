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

package one.nio.serial;

import java.io.IOException;

public class Json {

    public static void appendChar(StringBuilder builder, char c) {
        builder.append('"');
        if (c == '"' || c == '\\') {
            builder.append('\\');
        }
        builder.append(c).append('"');
    }

    public static void appendChars(StringBuilder builder, char[] obj) {
        builder.append('"');
        int from = 0;
        for (int i = 0; i < obj.length; i++) {
            if (obj[i] == '"' || obj[i] == '\\') {
                builder.append(obj, from, i - from).append('\\');
                from = i;
            }
        }
        builder.append(obj, from, obj.length - from).append('"');
    }

    public static void appendString(StringBuilder builder, String s) {
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                appendChars(builder, s.toCharArray());
                return;
            }
        }
        builder.append('"').append(s).append('"');
    }

    @SuppressWarnings("unchecked")
    public static void appendObject(StringBuilder builder, Object obj) throws IOException {
        if (obj == null) {
            builder.append("null");
        } else {
            Serializer serializer = Repository.get(obj.getClass());
            serializer.toJson(obj, builder);
        }
    }

    @SuppressWarnings("unchecked")
    public static String toJson(Object obj) throws IOException {
        if (obj == null) {
            return "null";
        } else {
            StringBuilder builder = new StringBuilder(240);
            Serializer serializer = Repository.get(obj.getClass());
            serializer.toJson(obj, builder);
            return builder.toString();
        }
    }
}

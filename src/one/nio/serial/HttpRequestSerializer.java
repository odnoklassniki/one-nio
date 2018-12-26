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

package one.nio.serial;

import one.nio.http.Request;

import java.io.IOException;
import java.io.NotSerializableException;

public class HttpRequestSerializer extends Serializer<Request> {

    HttpRequestSerializer() {
        super(Request.class);
    }

    @Override
    public void calcSize(Request obj, CalcSizeStream css) throws IOException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public void write(Request obj, DataStream out) throws IOException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Request read(DataStream in) throws IOException, ClassNotFoundException {
        String requestLine = in.readLine();
        int method = selectMethod(requestLine);

        int uriFrom = requestLine.indexOf(' ') + 1;
        int uriTo = requestLine.lastIndexOf(' ');
        String uri = uriFrom <= uriTo ?  requestLine.substring(uriFrom, uriTo) : requestLine.substring(uriFrom);
        boolean http11 = requestLine.endsWith("/1.1");

        Request request = new Request(method, uri, http11);
        for (String header; !(header = in.readLine()).isEmpty(); ) {
            request.addHeader(header);
        }

        String contentLength = request.getHeader("content-length: ");
        if (contentLength != null) {
            if (Integer.parseInt(contentLength) != in.available()) {
                throw new IOException("Content-Length mismatch");
            }
            byte[] body = new byte[in.available()];
            in.readFully(body);
            request.setBody(body);
        }

        return request;
    }

    @Override
    public void skip(DataStream in) throws IOException, ClassNotFoundException {
        // Skip request line
        in.readLine();

        // Skip headers
        int contentLength = 0;
        for (String header; !(header = in.readLine()).isEmpty(); ) {
            if (header.regionMatches(true, 0, "content-length: ", 0, 16)) {
                contentLength = Integer.parseInt(header.substring(16));
            }
        }

        // Skip body
        in.skipBytes(contentLength);
    }

    @Override
    public void toJson(Request obj, StringBuilder builder) throws IOException {
        throw new NotSerializableException(descriptor);
    }

    @Override
    public Request fromJson(JsonReader in) throws IOException, ClassNotFoundException {
        throw new NotSerializableException(descriptor);
    }

    private static int selectMethod(String requestLine) throws IOException {
        // The first byte was overwritten with Serializer's UID by RpcSession
        if (requestLine.startsWith("ET ")) {
            return Request.METHOD_GET;
        } else if (requestLine.startsWith("OST ")) {
            return Request.METHOD_POST;
        } else if (requestLine.startsWith("EAD ")) {
            return Request.METHOD_HEAD;
        }
        throw new IOException("Invalid HTTP method");
    }
}

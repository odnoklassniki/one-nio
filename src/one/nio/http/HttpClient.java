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

package one.nio.http;

import one.nio.net.SslClientContextFactory;
import one.nio.net.ConnectionString;
import one.nio.net.HttpProxy;
import one.nio.net.Socket;
import one.nio.net.SocketClosedException;
import one.nio.pool.PoolException;
import one.nio.pool.SocketPool;
import one.nio.util.Utf8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class HttpClient extends SocketPool {
    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    protected String[] permanentHeaders;
    protected int bufferSize;

    public HttpClient(ConnectionString conn) {
        this(conn,
                "Host: " + conn.getHost(),
                conn.getBooleanParam("keepalive", true) ? "Connection: Keep-Alive" : "Connection: close");
    }

    public HttpClient(ConnectionString conn, String... permanentHeaders) {
        super(conn);
        this.permanentHeaders = permanentHeaders;
    }

    @Override
    protected void setProperties(ConnectionString conn) {
        boolean https = "https".equals(conn.getProtocol());
        if (https) {
            sslContext = SslClientContextFactory.create();
        }
        if (port == 0) {
            port = https ? 443 : 80;
        }

        String proxyAddr = conn.getStringParam("proxy");
        if (proxyAddr != null) {
            int p = proxyAddr.lastIndexOf(':');
            if (p >= 0) {
                String proxyHost = proxyAddr.substring(0, p);
                int proxyPort = Integer.parseInt(proxyAddr.substring(p + 1));
                setProxy(new HttpProxy(proxyHost, proxyPort));
            } else {
                setProxy(new HttpProxy(proxyAddr, 3128));
            }
        }

        bufferSize = conn.getIntParam("bufferSize", 8000);
    }

    public Response invoke(Request request) throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(request, readTimeout);
    }

    public Response invoke(Request request, int timeout) throws InterruptedException, PoolException, IOException, HttpException {
        int method = request.getMethod();
        byte[] rawRequest = request.toBytes();
        ResponseReader responseReader;

        Socket socket = borrowObject();
        boolean keepAlive = false;
        try {
            try {
                socket.setTimeout(timeout == 0 ? readTimeout : timeout);
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new ResponseReader(socket, bufferSize);
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                // Stale connection? Retry on a fresh socket
                destroyObject(socket);
                socket = createObject();
                socket.writeFully(rawRequest, 0, rawRequest.length);
                responseReader = new ResponseReader(socket, bufferSize);
            }

            Response response = responseReader.readResponse(method);
            keepAlive = !"close".equalsIgnoreCase(response.getHeader("Connection:"));
            return response;
        } finally {
            if (keepAlive) {
                returnObject(socket);
            } else {
                invalidateObject(socket);
            }
        }
    }

    public Response get(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_GET, uri, headers));
    }

    public EventSourceResponse openEvents(String uri, String... headers)
                    throws InterruptedException, PoolException, IOException, HttpException {
        return openEvents( createRequest( Request.METHOD_GET, uri, headers ), readTimeout );
    }

    public Response delete(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_DELETE, uri, headers));
    }

    public Response post(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_POST, uri, headers));
    }

    public Response post(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_POST, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response put(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_PUT, uri, headers));
    }

    public Response put(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_PUT, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response patch(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_PATCH, uri, headers));
    }

    public Response patch(String uri, byte[] body, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        Request request = createRequest(Request.METHOD_PATCH, uri, headers);
        if (body != null) {
            request.addHeader("Content-Length: " + body.length);
            request.setBody(body);
        }
        return invoke(request);
    }

    public Response head(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_HEAD, uri, headers));
    }

    public Response options(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_OPTIONS, uri, headers));
    }

    public Response trace(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_TRACE, uri, headers));
    }

    public Response connect(String uri, String... headers)
            throws InterruptedException, PoolException, IOException, HttpException {
        return invoke(createRequest(Request.METHOD_CONNECT, uri, headers));
    }

    @SuppressWarnings ( "resource")
    public EventSourceResponse openEvents( Request request, int timeout ) throws InterruptedException, PoolException, IOException, HttpException
    {
        request.addHeader( "Accept: text/event-stream" );

        int method = request.getMethod();
        byte[] rawRequest = request.toBytes();
        ServerSentEventsReader sseReader;

        Socket socket = borrowObject();
        boolean open = false;

        try {
            try {
                socket.setTimeout( timeout == 0 ? readTimeout : timeout );
                socket.writeFully( rawRequest, 0, rawRequest.length );
                sseReader = new ServerSentEventsReader( socket, bufferSize );
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                // Stale connection? Retry on a fresh socket
                destroyObject(socket);
                socket = createObject();
                socket.setTimeout( timeout == 0 ? readTimeout : timeout );
                socket.writeFully(rawRequest, 0, rawRequest.length);
                sseReader = new ServerSentEventsReader( socket, bufferSize );
            }

            EventSourceResponse response = sseReader.readResponse(method);
            open = true;
            return response;
        } finally {
            if (!open) {
                invalidateObject(socket);
            }
        }
    }

    public EventSourceResponse reopenEvents( Request request, String lastId, int timeout ) throws InterruptedException, PoolException, IOException, HttpException
    {
        request.addHeader( "Last-Event-ID: " + lastId );

        return openEvents( request, timeout );
    }

    public Request createRequest(int method, String uri, String... headers) {
        Request request = new Request(method, uri, true);
        for (String header : permanentHeaders) {
            request.addHeader(header);
        }
        for (String header : headers) {
            request.addHeader(header);
        }
        return request;
    }

    static class ResponseReader {
        Socket socket;
        byte[] buf;
        int length;
        int pos;

        ResponseReader(Socket socket, int bufferSize) throws IOException {
            this.socket = socket;
            this.buf = new byte[bufferSize];
            this.length = socket.read(buf, 0, bufferSize, 0);
        }

        Response readResponse(int method) throws IOException, HttpException {
            Response response = new Response( readResultCode() );
            readResponseHeaders( response );
            readResponseBody( method, response );
            return response;
        }

        String readResultCode() throws IOException, HttpException
        {
            String responseHeader = readLine();
            if (responseHeader.length() <= 9) {
                throw new HttpException("Invalid response header: " + responseHeader);
            }
            return responseHeader.substring(9);
        }

        void readResponseHeaders(Response response) throws IOException, HttpException
        {
            for (String header; !(header = readLine()).isEmpty(); ) {
                response.addHeader(header);
            }
        }

        void readResponseBody( int method, Response response ) throws IOException, HttpException
        {
            if (method != Request.METHOD_HEAD && mayHaveBody(response.getStatus())) {
                if ("chunked".equalsIgnoreCase(response.getHeader("Transfer-Encoding:"))) {
                    response.setBody(readChunkedBody());
                } else {
                    String contentLength = response.getHeader("Content-Length:");
                    if (contentLength != null) {
                        response.setBody(readBody(Integer.parseInt(contentLength)));
                    } else if ("close".equalsIgnoreCase(response.getHeader("Connection:"))) {
                        response.setBody(readBodyUntilClose());
                    } else {
                        log.debug("Content-Length unspecified: {}", response);
                        throw new HttpException("Content-Length unspecified");
                    }
                }
            }
        }

        String readLine() throws IOException, HttpException {
            byte[] buf = this.buf;
            int pos = this.pos;
            int lineStart = pos;

            do {
                if (pos == length) {
                    if (pos >= buf.length) {
                        throw new HttpException("Line too long");
                    }
                    length += socket.read(buf, pos, buf.length - pos, 0);
                }
            } while (buf[pos++] != '\n');

            this.pos = pos;
            return Utf8.read(buf, lineStart, pos - lineStart - 2);
        }

        byte[] readChunkedBody() throws IOException, HttpException {
            ArrayList<byte[]> chunks = new ArrayList<>(4);

            while (true) {
                int chunkSize = Integer.parseInt(readLine(), 16);
                if (chunkSize == 0) {
                    readLine();
                    break;
                }

                byte[] chunk = new byte[chunkSize];
                chunks.add(chunk);

                int contentBytes = length - pos;
                if (contentBytes < chunkSize) {
                    System.arraycopy(buf, pos, chunk, 0, contentBytes);
                    socket.readFully(chunk, contentBytes, chunkSize - contentBytes);
                    pos = 0;
                    length = 0;
                } else {
                    System.arraycopy(buf, pos, chunk, 0, chunkSize);
                    pos += chunkSize;
                    if (pos + 128 >= buf.length) {
                        System.arraycopy(buf, pos, buf, 0, length -= pos);
                        pos = 0;
                    }
                }

                readLine();
            }

            return mergeChunks(chunks);
        }

        byte[] readBody(int contentLength) throws IOException {
            byte[] body = new byte[contentLength];
            int contentBytes = length - pos;
            System.arraycopy(buf, pos, body, 0, contentBytes);
            if (contentBytes < body.length) {
                socket.readFully(body, contentBytes, body.length - contentBytes);
            }
            return body;
        }

        byte[] readBodyUntilClose() throws IOException {
            ArrayList<byte[]> chunks = new ArrayList<>(4);

            if (pos < length) {
                chunks.add(Arrays.copyOfRange(buf, pos, length));
            }

            try {
                for (int bytes; (bytes = socket.read(buf, 0, buf.length)) >= 0; ) {
                    chunks.add(Arrays.copyOf(buf, bytes));
                }
            } catch (SocketClosedException e) {
                // expected
            }

            return mergeChunks(chunks);
        }

        byte[] mergeChunks(List<byte[]> chunks) {
            if (chunks.size() == 1) {
                return chunks.get(0);
            }

            int totalBytes = 0;
            for (byte[] chunk : chunks) {
                totalBytes += chunk.length;
            }

            byte[] result = new byte[totalBytes];
            int position = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, position, chunk.length);
                position += chunk.length;
            }
            return result;
        }

        private static boolean mayHaveBody(int status) {
            return status >= 200 && status != 204 && status != 304;
        }
    }

    class ChunkedLineReader extends ResponseReader implements Iterator<String>, Closeable {

        private byte[] ch;
        private int chPos, chLen;

        private boolean hasNext;


        ChunkedLineReader( Socket socket, int bufferSize ) throws IOException
        {
            super( socket, bufferSize );
            this.ch = buf;
            this.chPos = 0;
            this.chLen = 0;
            this.hasNext = true;
        }

        private boolean nextChunk() throws IOException, HttpException {

            // the very first chunk header is written without empty line at the start, like:
            //      999\n\r
            // all subsequent chunk headers start with empty line, like:
            //      \n\r999\n\r
            String l = readLine();
            int chunkSize = Integer.parseInt( l.isEmpty() ? readLine() : l, 16 );
            if (chunkSize == 0) {
                readLine();
                this.chPos = 0;
                this.chLen = 0;
                this.hasNext = false;
                return false;
            }

            if ( chunkSize > ch.length ) {
                // initially ch points to buf and reallocates to separate only if chunk size is greater than buf
                ch = new byte[ chunkSizeFor( chunkSize ) ];
            }

            int contentBytes = length - pos;
            if (contentBytes < chunkSize) {
                System.arraycopy(buf, pos, ch, 0, contentBytes);
                socket.readFully(ch, contentBytes, chunkSize - contentBytes);
                pos = 0;
                length = 0;
                chPos = 0;
            } else {
                if ( ch != buf ) {
                    System.arraycopy(buf, pos, ch, 0, chunkSize);
                    chPos = 0;
                } else {
                    chPos = pos;
                }
                pos += chunkSize;
            }
            chLen = chunkSize;

            return true;

        }

        private int chunkSizeFor( int cap )
        {
            int n = -1 >>> Integer.numberOfLeadingZeros( cap - 1 );
            return n + 1;
        }

        @Override
        public boolean hasNext()
        {
            return hasNext;
        }

        @Override
        public String next()
        {
            try {
                return readChunkedLine();
            } catch ( IOException | HttpException e ) {
                log.debug("Event stream is closed by server");
                close();
            }

            return null;
        }

        private String readChunkedLine() throws IOException, HttpException {
            // whole line is found within current chunk
            int end = findLineEnd( ch, chPos, chLen );
            if ( end >= 0 ) {
                int lineLen = end - chPos;
                String line = Utf8.read( ch, chPos, lineLen );
                lineLen++; // skip over \n
                chLen -= lineLen;
                chPos += lineLen;
                return line;
            }

            ArrayList<byte[]> chunks = new ArrayList<>();
            int lineLen = 0;

            do {
                chunks.add(Arrays.copyOfRange(ch, this.chPos, this.chPos + this.chLen));
                lineLen += this.chLen;
                this.ch = this.buf;
                this.chPos = 0;
                this.chLen = 0;

                if ( !nextChunk() ) {
                    // end of stream detected
                    end = 0;
                    break;
                }

                end = findLineEnd( ch, chPos, chLen );
            } while ( end < 0 );

            lineLen += Math.max( end - chPos, 0 );
            if ( lineLen == 0 )
                return "";

            byte[] lineBytes = new byte[ lineLen ];
            int linePos = 0;
            for ( byte[] b : chunks ) {
                System.arraycopy( b, 0, lineBytes, linePos, b.length );
                linePos += b.length;
            }

            // ch has last piece of line, if end > 0 || lineLen > linePos
            if ( end > 0 ) {
                System.arraycopy( ch, this.chPos, lineBytes, linePos, end - this.chPos );
                linePos += end - this.chPos;
                chLen -= end - chPos + 1;
                chPos = end + 1; // skip over \n
            }

            assert linePos == lineLen;

            String line = Utf8.read( lineBytes, 0, lineBytes.length );
            return line;
        }

        private int findLineEnd( byte[] b, int start, int len ) {
            int end = start + len;
            for ( ; start < end && b[start] != '\n'; start++ ) ;

            return start >= end ? -1 : start ;
        }

        @Override
        public void close()
        {
            if ( socket == null )
                return;

            invalidateObject(socket);
            this.hasNext = false;
            this.socket = null;
        }

    }

    class ServerSentEventsReader extends ChunkedLineReader implements EventSource<String> {

        private boolean keepAlive;

        ServerSentEventsReader( Socket socket, int bufferSize ) throws IOException
        {
            super( socket, bufferSize );
        }

        EventSourceResponse readResponse(int method) throws IOException, HttpException {
            EventSourceResponse response = new EventSourceResponse( readResultCode() );
            readResponseHeaders( response );

            if ( response.getHeader( "Content-Type: text/event-stream" ) == null ) {
                try {
                    readResponseBody( method, response );
                    keepAlive = !"close".equalsIgnoreCase(response.getHeader("Connection:"));
                    return response;
                } finally {
                    close();
                }
            }

            if ( !"chunked".equalsIgnoreCase( response.getHeader( "Transfer-Encoding:" ) ) ) {
                throw new UnsupportedOperationException( "Only chunked transfer encoding is supported for text/event-stream" );
            }

            response.setEventSource( this );

            return response;
        }

        @Override
        public Event<String> poll( )
        {
            if ( !hasNext() )
                return null;

            String line = next();
            return line == null || line.isEmpty() ? null : readEvent( line );

        }

        private EventImpl readEvent( String line )
        {
            EventImpl eimpl = new EventImpl();

            StringBuilder databuf = new StringBuilder( line.length() );
            String field=":"; // impossible value
            try {
                do {
                    int cpos = line.indexOf( ':' );
                    String f;
                    if ( cpos == 0 ) {
                        // comment. sometimes used alone as keep alive messages
                        f="";
                        cpos++;
                    } else if ( cpos < 0 ) {
                        // no colon - whole line is field name as per spec
                        f = line;
                        cpos = line.length();
                    } else {
                        // field name separated from data by colon with optional
                        // single space char after colon, like field-name: data
                        f = line.substring( 0, cpos );
                        cpos++;
                        if ( cpos < line.length() && line.charAt( cpos )==' ')
                            cpos++;
                    }

                    if ( !field.equals( f ) ) {

                        eimpl.with( field, databuf );

                        field = f;
                        databuf.setLength( 0 );
                    } else {
                        // multiple lines of the same field name concatenate data with newline
                        // a:b
                        // a:c
                        // a="b\nc"
                        databuf.append('\n');
                    }

                    databuf.append( line, cpos, line.length() );

                    line = next();
                    if (line == null) {
                        // EOF
                        return null;
                    }
                } while ( !line.isEmpty() );

                if ( databuf.length() > 0 )
                    eimpl.with( field, databuf );

            } catch ( RuntimeException e ) {
                log.error( "Cannot parse line: {}", line, e );
                throw e;
            }

            log.debug( "Read event from stream: {}", eimpl );

            return eimpl;
        }

        @Override
        public void close()
        {
            if ( socket != null && keepAlive) {
                returnObject(socket);
                socket = null;
            } else {
                super.close();
            }
        }

    }

    static class EventImpl implements EventSource.Event<String> {

        private String id, name, data, comment;

        @Override
        public String name()
        {
            return name;
        }

        @Override
        public String id()
        {
            return id;
        }

        @Override
        public String data()
        {
            return data;
        }

        @Override
        public String comment()
        {
            return comment;
        }

        boolean with( String field, StringBuilder databuf ) {
            switch ( field ) {
            case "id":
                id = databuf.toString();
                break;
            case "event":
                name = databuf.toString();
                break;
            case "data":
                data = databuf.toString();
                break;
            case "":
                comment = databuf.toString();
                break;
            default:
                return false;
            }
            return true;
        }

        public boolean isEmpty() {
            return id == null && name == null && data == null;
        }

        @Override
        public String toString()
        {
            return isEmpty() ? "empty" : name + ":" + id;
        }
    }
}

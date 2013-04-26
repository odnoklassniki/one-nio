package one.nio.rpc;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.SerializeStream;
import one.nio.serial.SerializerNotFoundException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;

public class RpcSession extends Session {
    private static final Log log = LogFactory.getLog(RpcSession.class);
    private static final int BUFFER_SIZE = 8000;

    protected final RpcServer server;
    private byte[] buffer;
    private int bytesRead;
    private int requestSize;

    public RpcSession(Socket socket, RpcServer server) {
        super(socket);
        this.server = server;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void processRead(byte[] unusedBuffer) throws Exception {
        byte[] buffer = this.buffer;
        int bytesRead = this.bytesRead;
        int requestSize = this.requestSize;

        // Read 4-bytes header
        if (requestSize == 0) {
            bytesRead += socket.read(buffer, bytesRead, 4 - bytesRead);
            if (bytesRead < 4) {
                this.bytesRead = bytesRead;
                return;
            }
            bytesRead = 0;

            if (buffer[0] != 0) {
                throw new IOException("Invalid request or request too large");
            }

            requestSize = this.requestSize = (buffer[1] & 0xff) << 16 | (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff);
            if (requestSize > buffer.length) {
                buffer = this.buffer = new byte[requestSize];
            }
        }

        // Read request
        bytesRead += socket.read(buffer, bytesRead, requestSize - bytesRead);
        if (bytesRead < requestSize) {
            this.bytesRead = bytesRead;
            return;
        }

        // Request is complete - deserialize it
        this.bytesRead = 0;
        this.requestSize = 0;

        final Object request;
        try {
            request = new DeserializeStream(buffer).readObject();
        } catch (SerializerNotFoundException e) {
            writeResponse(e);
            return;
        } finally {
            if (requestSize > BUFFER_SIZE) {
                this.buffer = new byte[BUFFER_SIZE];
            }
        }

        // Perform the invocation
        if (server.getWorkersUsed()) {
            try {
                server.asyncExecute(new AsyncRequest(request));
                server.incRequestsProcessed();
            } catch (RejectedExecutionException e) {
                handleRejectedExecution(e, request);
                server.incRequestsRejected();
            }
        } else {
            writeResponse(server.invoke(request));
            server.incRequestsProcessed();
        }
    }

    protected void writeResponse(Object response) throws IOException {
        CalcSizeStream calcSizeStream = new CalcSizeStream();
        calcSizeStream.writeObject(response);
        int size = calcSizeStream.count();
        byte[] buffer = new byte[size + 4];

        SerializeStream ss = new SerializeStream(buffer);
        ss.writeInt(size);
        ss.writeObject(response);

        super.write(buffer, 0, buffer.length, false);
    }

    protected void handleRejectedExecution(RejectedExecutionException e, Object request) throws IOException {
        writeResponse(e);
        if (log.isWarnEnabled()) {
            log.warn("RejectedExecutionException for request: " + request);
        }
    }

    private class AsyncRequest implements Runnable {
        private final Object request;

        AsyncRequest(Object request) {
            this.request = request;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try {
                writeResponse(server.invoke(request));
            } catch (SocketException e) {
                if (server.isRunning() && log.isDebugEnabled()) {
                    log.debug("Connection closed: " + clientIp());
                }
                close();
            } catch (Throwable e) {
                if (server.isRunning()) {
                    log.error("Cannot process session from " + clientIp(), e);
                }
                close();
            }
        }
    }
}

package one.nio.rpc;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.serial.CalcSizeStream;
import one.nio.serial.DeserializeStream;
import one.nio.serial.SerializeStream;
import one.nio.serial.SerializerNotFoundException;

import java.io.IOException;

public class RpcSession extends Session {
    private static final int BUFFER_SIZE = 8000;

    private final RpcService service;
    private byte[] buffer;
    private int bytesRead;
    private int requestSize;

    public RpcSession(Socket socket, RpcService service) {
        super(socket);
        this.service = service;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
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

        // Request is complete - proceed with the invocation
        this.bytesRead = 0;
        this.requestSize = 0;
        processRequest(buffer);
        if (requestSize > BUFFER_SIZE) {
            this.buffer = new byte[BUFFER_SIZE];
        }
    }

    @SuppressWarnings("unchecked")
    private void processRequest(byte[] buffer) throws Exception {
        Object response;
        try {
            Object request = new DeserializeStream(buffer).readObject();
            response = service.invoke(request);
        } catch (SerializerNotFoundException e) {
            response = e;
        }

        CalcSizeStream calcSizeStream = new CalcSizeStream();
        calcSizeStream.writeObject(response);
        int size = calcSizeStream.count();
        buffer = new byte[size + 4];

        SerializeStream ss = new SerializeStream(buffer);
        ss.writeInt(size);
        ss.writeObject(response);

        super.write(buffer, 0, buffer.length, false);
    }
}

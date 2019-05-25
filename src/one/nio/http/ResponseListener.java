package one.nio.http;

@FunctionalInterface
public interface ResponseListener {
    void onDone(int bytesSent, int totalToSend, Exception problem);
}

package one.nio.net;

import one.nio.os.NativeLibrary;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

public abstract class Selector implements Iterable<Session>, Closeable {
    public abstract int size();
    public abstract void close();
    public abstract void register(Session session);
    public abstract void unregister(Session session);
    public abstract void listen(Session session, int events);
    public abstract Iterator<Session> iterator();
    public abstract Iterator<Session> select();

    public static Selector create() throws IOException {
        return NativeLibrary.IS_SUPPORTED ? new NativeSelector() : new JavaSelector();
    }
}

package one.nio.cluster;

public interface ServiceProvider {
    boolean available();
    boolean check() throws Exception;
    void enable();
    void disable();
    void close();
}

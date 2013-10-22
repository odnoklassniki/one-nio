package one.nio.cluster;

public interface ServiceProvider {
    boolean available();
    boolean check() throws Exception;
    boolean enable();
    boolean disable();
    void close();
}

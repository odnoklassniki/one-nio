package one.nio.cluster;

public interface Cluster<T extends ServiceProvider> {
    T getProvider() throws ServiceUnavailableException;
    void enableProvider(T provider);
    void disableProvider(T provider);
}

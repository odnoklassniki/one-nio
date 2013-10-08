package one.nio.cluster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class WeightCluster<T extends ServiceProvider> implements Cluster<T> {
    protected static final Log log = LogFactory.getLog(WeightCluster.class);

    protected final Random random = new Random();
    protected final HashMap<T, Integer> providers = new HashMap<T, Integer>();
    protected Timer monitorTimer;
    protected long monitorTimeout;
    protected volatile ProviderSelector providerSelector;

    public WeightCluster() {
        this.monitorTimer = new Timer("Cluster monitor", true);
        this.monitorTimeout = 1000L;
        rebuildProviderSelector();
    }

    public synchronized void close() {
        monitorTimer.cancel();
        for (ServiceProvider provider : providers.keySet()) {
            provider.close();
        }
    }

    public long getMonitorTimeout() {
        return monitorTimeout;
    }

    public void setMonitorTimeout(long monitorTimeout) {
        this.monitorTimeout = monitorTimeout;
    }

    @Override
    public T getProvider() throws ServiceUnavailableException {
        return providerSelector.select();
    }

    @Override
    public void enableProvider(T provider) {
        provider.enable();
        rebuildProviderSelector();
    }

    @Override
    public void disableProvider(T provider) {
        provider.disable();
        rebuildProviderSelector();
        monitorTimer.schedule(new MonitoringTask(provider), monitorTimeout, monitorTimeout);
    }

    public synchronized void addProvider(T provider, int weight) {
        Integer oldWeight = providers.put(provider, weight);
        if (oldWeight == null || oldWeight != weight) {
            rebuildProviderSelector();
        }
    }

    public synchronized void addProviders(Map<T, Integer> newProviders) {
        providers.putAll(newProviders);
        rebuildProviderSelector();
    }

    public synchronized void removeProvider(T provider) {
        if (providers.remove(provider) != null) {
            rebuildProviderSelector();
        }
    }

    public synchronized void removeProviders(Collection<T> oldProviders) {
        providers.keySet().removeAll(oldProviders);
        rebuildProviderSelector();
    }

    public synchronized Integer getWeight(T provider) {
        return providers.get(provider);
    }

    protected synchronized void rebuildProviderSelector() {
        this.providerSelector = new ProviderSelector(providers);
    }

    public class ProviderSelector {
        public final T[] providers;
        public final int[] weights;
        public final int weightRange;

        @SuppressWarnings("unchecked")
        public ProviderSelector(Map<T, Integer> providers) {
            int size = providers.size();
            this.providers = (T[]) new ServiceProvider[size];
            this.weights = new int[size];

            int weightRange = 0;
            int index = 0;
            for (Map.Entry<T, Integer> entry : providers.entrySet()) {
                T provider = entry.getKey();
                if (provider.available()) weightRange += entry.getValue();
                this.providers[index] = provider;
                this.weights[index] = weightRange;
                index++;
            }
            this.weightRange = weightRange;
        }

        public T select() throws ServiceUnavailableException {
            if (weightRange > 0) {
                int w = random.nextInt(weightRange);
                int low = 0;
                int high = weights.length - 1;
                while (low < high) {
                    int med = (low + high) >>> 1;
                    if (w < weights[med]) {
                        high = med;
                    } else {
                        low = med + 1;
                    }
                }
                return providers[low];
            }
            throw new ServiceUnavailableException("No providers available");
        }
    }

    public class MonitoringTask extends TimerTask {
        public final T provider;

        public MonitoringTask(T provider) {
            this.provider = provider;
        }

        @Override
        public void run() {
            try {
                if (getWeight(provider) == null) {
                    cancel();  // the provider has gone - stop monitoring
                    return;
                }
                if (!provider.available() && provider.check()) {
                    enableProvider(provider);
                }
                if (provider.available()) {
                    cancel();
                }
            } catch (Exception e) {
                log.warn("Provider [" + provider + "] is not available", e);
            }
        }
    }
}

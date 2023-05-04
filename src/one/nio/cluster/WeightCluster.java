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

package one.nio.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

public class WeightCluster<T extends ServiceProvider> implements Cluster<T> {
    protected static final Logger log = LoggerFactory.getLogger(WeightCluster.class);

    protected final HashMap<T, Integer> providers = new HashMap<>();
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
        if (provider.enable()) {
            log.info("Enabled {}", provider);
            rebuildProviderSelector();
        }
    }

    @Override
    public void disableProvider(T provider) {
        if (provider.disable()) {
            log.info("Disabled {}", provider);
            rebuildProviderSelector();
            monitorTimer.schedule(new MonitoringTask(provider), monitorTimeout, monitorTimeout);
        }
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

    public synchronized List<T> replaceProviders(Map<T, Integer> newProviders) {
        ArrayList<T> oldProviders = new ArrayList<T>(providers.keySet());
        providers.clear();
        providers.putAll(newProviders);
        rebuildProviderSelector();
        return oldProviders;
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
                int w = ThreadLocalRandom.current().nextInt(weightRange);
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
            } catch (Throwable e) {
                log.warn("{} is not available", provider, e);
            }
        }
    }
}

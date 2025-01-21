/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
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

import one.nio.cluster.ServiceUnavailableException;
import one.nio.cluster.WeightCluster;
import one.nio.net.ConnectionString;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpCluster extends WeightCluster<HttpProvider> {
    protected volatile int retries = 3;
    protected volatile int maxFailures = 5;
    protected volatile boolean logTimeouts;

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public void setLogTimeouts(boolean logTimeouts) {
        this.logTimeouts = logTimeouts;
    }

    public void configure(String configuration) {
        Map<HttpProvider, Integer> newProviders = createProviders(configuration);
        List<HttpProvider> oldProviders = replaceProviders(newProviders);
        for (HttpProvider provider : oldProviders) {
            provider.close();
        }
    }

    protected Map<HttpProvider, Integer> createProviders(String configuration) {
        HashMap<HttpProvider, Integer> providers = new HashMap<>();
        for (StringTokenizer st = new StringTokenizer(configuration); st.hasMoreTokens(); ) {
            HttpProvider provider = createProvider(st.nextToken());
            int weight = Integer.parseInt(st.nextToken());
            providers.put(provider, weight);
        }
        return providers;
    }

    protected HttpProvider createProvider(String provider) {
        return new HttpProvider(new ConnectionString(provider));
    }

    public Response invoke(Request request) throws ServiceUnavailableException {
        log.trace("{}", request);

        final int retries = this.retries;
        for (int i = 0; i < retries; i++) {
            HttpProvider provider = getProvider();
            try {
                Response response = provider.invoke(request);
                provider.getFailures().set(0);
                return response;
            } catch (Exception e) {
                if (provider.getFailures().incrementAndGet() >= maxFailures) {
                    disableProvider(provider);
                }
                if ((e instanceof SocketTimeoutException || e.getCause() instanceof SocketTimeoutException)
                        && !(log.isTraceEnabled() || logTimeouts)) {
                    log.debug("{} timed out", provider);
                } else {
                    log.warn("{} invocation failed {}", provider, request.getURI(), e);
                }
            }
        }

        throw new ServiceUnavailableException("Cluster invocation failed");
    }
}

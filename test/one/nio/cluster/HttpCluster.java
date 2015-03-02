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

import one.nio.http.Request;
import one.nio.net.ConnectionString;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpCluster extends WeightCluster<HttpProvider> {
    private static final Log log = LogFactory.getLog(HttpCluster.class);
    private static final int RETRIES = 3;

    protected synchronized List<HttpProvider> replaceProviders(Map<HttpProvider, Integer> newProviders) {
        ArrayList<HttpProvider> oldProviders = new ArrayList<HttpProvider>(providers.keySet());
        providers.clear();
        providers.putAll(newProviders);
        rebuildProviderSelector();
        return oldProviders;
    }

    public void configure(String configuration) throws IOException {
        HashMap<HttpProvider, Integer> newProviders = new HashMap<HttpProvider, Integer>();
        for (StringTokenizer st = new StringTokenizer(configuration); st.hasMoreElements(); ) {
            String host = st.nextToken();
            int weight = Integer.parseInt(st.nextToken());
            newProviders.put(new HttpProvider(new ConnectionString(host)), weight);
        }

        List<HttpProvider> oldProviders = replaceProviders(newProviders);
        for (HttpProvider provider : oldProviders) {
            provider.close();
        }
    }

    public String invoke(Request request) throws ServiceUnavailableException {
        for (int i = 0; i < RETRIES; i++) {
            HttpProvider provider = getProvider();
            try {
                return provider.invoke(request);
            } catch (Exception e) {
                disableProvider(provider);
                log.warn(provider + " invocation failed", e);
            }
        }
        throw new ServiceUnavailableException("Cluster invocation failed after " + RETRIES + " retries");
    }
}

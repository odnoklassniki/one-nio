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

package one.nio.server;

import one.nio.config.Config;
import one.nio.config.Converter;
import one.nio.net.ConnectionString;
import one.nio.net.SslConfig;

@Config
public class ServerConfig {
    public AcceptorConfig[] acceptors;
    public int selectors;
    public boolean affinity;
    public int minWorkers;
    public int maxWorkers;
    @Converter(method = "time")
    public int queueTime;
    @Converter(method = "time")
    public int keepAlive;
    public int threadPriority = Thread.NORM_PRIORITY;

    public ServerConfig() {
    }

    private ServerConfig(ConnectionString conn) {
        AcceptorConfig ac = new AcceptorConfig();
        ac.address = conn.getHost();
        ac.port = conn.getPort();
        ac.recvBuf = conn.getIntParam("recvBuf", 0);
        ac.sendBuf = conn.getIntParam("sendBuf", 0);
        ac.tos = conn.getIntParam("tos", 0);
        ac.backlog = conn.getIntParam("backlog", 128);
        if ("ssl".equals(conn.getProtocol())) {
            ac.ssl = SslConfig.from(System.getProperties());
        }

        this.acceptors = new AcceptorConfig[]{ac};
        this.selectors = conn.getIntParam("selectors", 0);
        this.minWorkers = conn.getIntParam("minWorkers", 0);
        this.maxWorkers = conn.getIntParam("maxWorkers", 0);
        this.queueTime = conn.getIntParam("queueTime", 0) / 1000;
        this.threadPriority = conn.getIntParam("threadPriority", Thread.NORM_PRIORITY);
    }

    // Do not use for new servers! Use ConfigParser instead
    public static ServerConfig from(String conn) {
        return new ServerConfig(new ConnectionString(conn));
    }

    // Do not use for new servers! Use ConfigParser instead
    public static ServerConfig from(ConnectionString conn) {
        return new ServerConfig(conn);
    }
}

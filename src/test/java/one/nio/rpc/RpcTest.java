/*
 * Copyright 2018 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.rpc;

import one.nio.config.ConfigParser;
import one.nio.net.ConnectionString;
import one.nio.net.SocketUtil;
import one.nio.rpc.stream.BidiStream;
import one.nio.serial.sample.Message;
import one.nio.serial.sample.Sample;
import one.nio.server.ServerConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RpcTest {
    private static RpcServer<TestService> server;
    private static TestService client;

    @BeforeClass
    public static void setup() throws Exception {
        int availablePort = SocketUtil.getFreePort();
        ServerConfig config = ConfigParser.parse("acceptors:\n - port: " + availablePort, ServerConfig.class);
        server = new RpcServer<>(config, new TestServiceImpl());
        server.start();

        client = (TestService) Proxy.newProxyInstance(
                RpcTest.class.getClassLoader(),
                new Class[]{TestService.class},
                new RpcClient(new ConnectionString("127.0.0.1:" + availablePort)));
    }

    @AfterClass
    public static void destroy() {
        server.stop();
    }

    @Test
    public void testSimpleMethod() {
        Set<Long> ids = new HashSet<>(Arrays.asList(111L, 222L, 333L, 444L, 555L));
        Map<Long, Message> messages = client.getMessagesByIds(ids);
        assertEquals(5, messages.size());
        assertEquals("Second message", messages.get(222L).text);
        assertEquals(2, messages.get(444L).attachments.size());
    }

    @Test
    public void testNumberStream() throws Exception {
        Stats stats = testNumberStream(1);
        assertEquals(-100, stats.min);
        assertEquals(300, stats.max);
        assertEquals(BigDecimal.valueOf(100), stats.avg);
        assertEquals(BigInteger.valueOf(400), stats.sum);

        stats = testNumberStream(10000);
        assertEquals(-100, stats.min);
        assertEquals(300, stats.max);
        assertEquals(BigDecimal.valueOf(100), stats.avg);
        assertEquals(BigInteger.valueOf(4000000), stats.sum);
    }

    private Stats testNumberStream(int iterations) throws Exception {
        try (BidiStream<Number, Stats> stream = client.openNumberStream()) {
            for (int i = 0; i < iterations; i++) {
                stream.send(200L);
                stream.send(-100);
                stream.send(BigInteger.ZERO);
                stream.send(300.0);
            }
            return stream.sendAndGet(null);
        }
    }


    interface TestService {
        Map<Long, Message> getMessagesByIds(Set<Long> ids);
        BidiStream<Number, Stats> openNumberStream();
    }

    static class TestServiceImpl implements TestService {

        @Override
        public Map<Long, Message> getMessagesByIds(Set<Long> ids) {
            Map<Long, Message> map = new HashMap<>();
            for (Message message : Sample.createChat().messages) {
                if (ids.contains(message.id)) {
                    map.put(message.id, message);
                }
            }
            return map;
        }

        @Override
        public BidiStream<Number, Stats> openNumberStream() {
            return BidiStream.create(stream -> {
                BigInteger sum = BigInteger.ZERO;
                long count = 0;
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;

                for (Number number; (number = stream.receive()) != null; ) {
                    long n = number.longValue();
                    sum = sum.add(BigInteger.valueOf(n));
                    count++;
                    min = Math.min(min, n);
                    max = Math.max(max, n);
                }

                BigDecimal avg = new BigDecimal(sum).divide(BigDecimal.valueOf(count), 0);
                stream.send(new Stats(sum, avg, min, max));
            });
        }
    }

    static class Stats implements Serializable {
        final BigInteger sum;
        final BigDecimal avg;
        final long min;
        final long max;

        Stats(BigInteger sum, BigDecimal avg, long min, long max) {
            this.sum = sum;
            this.avg = avg;
            this.min = min;
            this.max = max;
        }
    }
}

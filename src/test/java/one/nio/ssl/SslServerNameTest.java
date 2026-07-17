/*
 * Copyright 2026 VK
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

package one.nio.ssl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import one.nio.config.ConfigParser;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.net.SocketUtil;
import one.nio.net.SslOption;
import one.nio.server.Server;
import one.nio.server.ServerConfig;
import one.nio.util.Utf8;

public class SslServerNameTest {

    private static final String SNI_HOST = "sni-test.example.com";
    private static final String NO_SNI = "<none>";

    static String startupConfigTemplate = "\n" +
        "acceptors:\n" +
        " - port: %d\n" +
        "   ssl:\n" +
        "     applicationProtocols: http/1.1\n" +
        "     protocols:            TLSv1.3\n" +
        "     certFile:             %s\n" +
        "     privateKeyFile:       %s\n";

    private static String cert;
    private static String privKey;

    private int port;
    private Server server;
    private SSLSocket socket;

    @BeforeClass
    public static void beforeClass() {
        Properties systemProps = System.getProperties();
        String truststorePath = SslServerNameTest.class.getClassLoader().getResource("ssl/trustore.jks").getFile();
        systemProps.put("javax.net.ssl.trustStore", truststorePath);
        systemProps.put("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperties(systemProps);
        cert = SslServerNameTest.class.getClassLoader().getResource("ssl/certificate.crt").getFile();
        privKey = SslServerNameTest.class.getClassLoader().getResource("ssl/certificate.key").getFile();

        System.setProperty("jdk.tls.namedGroups", "secp521r1,secp256r1");
    }

    @Before
    public void beforeMethod() throws IOException {
        Assume.assumeTrue(Socket.USE_NATIVE_SOCKET);

        port = SocketUtil.getFreePort();
        ServerConfig config = ConfigParser.parse(String.format(startupConfigTemplate, port, cert, privKey), ServerConfig.class);
        server = new ServerNameEchoServer(config);
        server.start();
    }

    @AfterClass
    public static void tearDownClass() {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("jdk.tls.namedGroups");
    }

    @After
    public void tearDown() throws Exception {
        if (socket != null) socket.close();
        if (server != null) server.stop();
    }

    @Test
    public void serverNameReported() throws Exception {
        Assert.assertEquals(SNI_HOST, connectAndQueryServerName(SNI_HOST));
    }

    private String connectAndQueryServerName(String sniHost) throws IOException {
        socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket("127.0.0.1", port);
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        SSLParameters params = socket.getSSLParameters();
        params.setServerNames(sniHost == null ? Collections.emptyList()
                : Collections.singletonList(new SNIHostName(sniHost)));
        socket.setSSLParameters(params);
        socket.startHandshake();

        socket.getOutputStream().write('?');
        socket.getOutputStream().flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        return in.readLine();
    }

    private static class ServerNameEchoServer extends Server {
        ServerNameEchoServer(ServerConfig config) throws IOException {
            super(config);
        }

        @Override
        public Session createSession(Socket socket) {
            return new Session(socket) {
                @Override
                protected void processRead(byte[] buffer) throws Exception {
                    if (read(buffer, 0, buffer.length) > 0) {
                        String name = socket().getSslOption(SslOption.SERVER_NAME);
                        byte[] response = Utf8.toBytes((name == null ? NO_SNI : name) + "\n");
                        write(response, 0, response.length);
                    }
                }
            };
        }
    }
}

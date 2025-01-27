/*
 * Copyright 2025 VK
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

import java.io.IOException;
import java.util.Properties;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import one.nio.config.ConfigParser;
import one.nio.server.Server;
import one.nio.server.ServerConfig;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TLSCurveTest {

    private static int port = 7443;


    private static String clientAllowedCurves = "secp521r1,secp256r1";

    static String startupConfigTemplate = "\n" +
        "acceptors:\n" +
        " - port: " + port +"\n" +
        "   ssl:\n" +
        "     applicationProtocols: http/1.1\n" +
        "     protocols:            TLSv1.3\n" +
        "     certFile:             %s\n" +
        "     privateKeyFile:       %s\n";

    static String curveConfigTemplate = "\n" +
        "     curve:                %s\n";

    private Server server;

    private static String cert;
    private static String privKey;
    SSLSocket socket;

    @BeforeClass
    public static void beforeClass() {
        Properties systemProps = System.getProperties();
        String truststorePath = TLSCurveTest.class.getClassLoader().getResource("ssl/trustore.jks").getFile();
        systemProps.put("javax.net.ssl.trustStore", truststorePath);
        systemProps.put("javax.net.ssl.trustStorePassword","changeit");
        System.setProperties(systemProps);
        cert = TLSCurveTest.class.getClassLoader().getResource("ssl/certificate.crt").getFile();
        privKey = TLSCurveTest.class.getClassLoader().getResource("ssl/certificate.key").getFile();

        // set allowed curves list for the client once
        // changing jdk.tls.namedGroups after first call SSLSocketFactory.getDefault() won't take effect
        System.setProperty("jdk.tls.namedGroups", clientAllowedCurves);
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

    private ServerConfig getServerConfig(String curve) {
        String curveConfigPart = curve == null ? "" : String.format(curveConfigTemplate, curve);
        return ConfigParser.parse(String.format(startupConfigTemplate, cert, privKey) + curveConfigPart,
            ServerConfig.class);
    }

    private void setupServer(String curve) throws IOException {
        ServerConfig config = getServerConfig(curve);
        server = new Server(config);
        server.start();
    }

    private void tryHandshake() throws IOException {
        socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket("127.0.0.1", port);
        socket.setEnabledProtocols(new String[] {"TLSv1.3"});
        socket.startHandshake();
    }

    /**
     * Both client and server support secp521r1 - successful handshake.
     */
    @Test
    public void secp521r1() throws Exception {
        setupServer("secp521r1");
        tryHandshake();
    }

    /**
     * Both client and server support prime256v1 - successful handshake.
     *
     * Name of the curve depends on standards organization:
     *   server curve (openssl impl.) - uses ANSI X9.62 and SECG names;
     *   client curve (java impl.) - uses SECG names only,
     * Curve name prime256v1 (ANSI) is alias of the curve secp256r1 (SECG).
     */
    @Test
    public void prime256v1() throws Exception {
        setupServer("prime256v1");
        tryHandshake();
    }

    /**
     * Both client and server support intersecting sets of curves - successful handshake.
     */
    @Test
    public void secp256k1_secp521r1() throws Exception {
        setupServer("secp256k1:secp521r1");
        tryHandshake();
    }

    /**
     *  Both client and server support not intersecting sets of curves - handshake fails.
     */
    @Test(expected = javax.net.ssl.SSLHandshakeException.class)
    public void client_server_curve_mismatch() throws Exception {
        setupServer("secp384r1");
        tryHandshake();
    }

    /**
     * A curve is not specified in the server config - successful handshake.
     * Using auto selection server curve
     */
    @Test
    public void no_server_curve_specified() throws Exception {
        setupServer(null);
        tryHandshake();
    }

    /**
     * A curve specified in the server config not supported by linked openssl build - server fails startup.
     */
    @Test(expected = javax.net.ssl.SSLException.class)
    public void bad_curve_name() throws Exception {
        setupServer("prime256v1:BAD_CURVE:secp521r1");
        tryHandshake();
    }

    /**
     * The server started up with a curve not supported by the client - handshake fails.
     * Then reconfigure the server to supported one - successful handshake.
     */
    @Test
    public void reconfigure_server_curve() throws Exception {
        setupServer("secp384r1");  // a curve not supported by the client
        try {
            tryHandshake();
            Assert.fail("First handshake didn't fail");
        } catch (javax.net.ssl.SSLHandshakeException e) {
        }
        ServerConfig newConfig = getServerConfig("prime256v1");  // the curve supported by the client
        server.reconfigure(newConfig);
        tryHandshake();
    }

}

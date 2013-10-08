package one.nio.http;

import one.nio.net.ConnectionString;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        HttpClient client = new HttpClient(new ConnectionString(args[0]));
        String path = args[1];

        Response response = client.get(path);
        System.out.println("Status code: " + response.getStatus());
        System.out.println(response.toString());
        client.close();
    }
}

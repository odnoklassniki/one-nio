package one.nio.ws.extension;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class ExtensionRequest {
    private final String name;
    private final Map<String, String> parameters;

    public ExtensionRequest(String name) {
        this.name = name;
        this.parameters = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void addParameter(String name, String value) {
        this.parameters.put(name, value);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }
}

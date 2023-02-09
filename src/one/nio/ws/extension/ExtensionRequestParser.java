package one.nio.ws.extension;

import java.util.ArrayList;
import java.util.List;

/**
 *  The relevant ABNF for the Sec-WebSocket-Extensions is as follows:
 *      extension-list = 1#extension
 *      extension = extension-token *( ";" extension-param )
 *      extension-token = registered-token
 *      registered-token = token
 *      extension-param = token [ "=" (token | quoted-string) ]
 *          ; When using the quoted-string syntax variant, the value
 *          ; after quoted-string unescaping MUST conform to the
 *          ; 'token' ABNF.
 *
 *  The limiting of parameter values to tokens or "quoted tokens" makes
 *  the parsing of the header significantly simpler and allows a number
 *  of short-cuts to be taken.
 *
 * @author <a href="mailto:vadim.yelisseyev@gmail.com">Vadim Yelisseyev</a>
 */
public class ExtensionRequestParser {

    public static List<ExtensionRequest> parse(String header) {
        final List<ExtensionRequest> result = new ArrayList<>();

        // Step one, split the header into individual extensions using ',' as a separator
        for (String unparsedExtension : header.split(",")) {
            // Step two, split the extension into the registered name and parameter/value pairs
            final String[] unparsedParameters = unparsedExtension.split(";");
            final ExtensionRequest request = new ExtensionRequest(unparsedParameters[0].trim());

            for (int i = 1; i < unparsedParameters.length; i++) {
                int equalsPos = unparsedParameters[i].indexOf('=');
                String name;
                String value;
                if (equalsPos == -1) {
                    name = unparsedParameters[i].trim();
                    value = null;
                } else {
                    name = unparsedParameters[i].substring(0, equalsPos).trim();
                    value = unparsedParameters[i].substring(equalsPos + 1).trim();
                    int len = value.length();
                    if (len > 1) {
                        if (value.charAt(0) == '\"' && value.charAt(len - 1) == '\"') {
                            value = value.substring(1, value.length() - 1);
                        }
                    }
                }

                // Make sure value doesn't contain any of the delimiters since that would indicate something went wrong
                if (containsDelims(name) || containsDelims(value)) {
                    throw new IllegalArgumentException("An illegal extension parameter was specified with name [" + name + "] and value [" + value + "]");
                }

                request.addParameter(name, value);
            }

            result.add(request);
        }

        return result;
    }

    private static boolean containsDelims(String input) {
        if (input == null || input.length() == 0) {
            return false;
        }
        for (int i = 0; i < input.length(); i++) {
            switch (input.charAt(i)) {
                case ',':
                case ';':
                case '\"':
                case '=':
                    return true;
                default:
                    // NO_OP
            }
        }
        return false;
    }

}

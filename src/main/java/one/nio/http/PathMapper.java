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

package one.nio.http;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Finds a RequestHandler by the given @Path and HTTP method.
 * Uses an embedded HashMap for performance reasons.
 */
public class PathMapper extends HashMap<String, RequestHandler[]> {

    // Add a new mapping
    public void add(String path, int[] methods, RequestHandler handler) {
        RequestHandler[] handlersByMethod = super.computeIfAbsent(path, p -> new RequestHandler[1]);
        if (methods == null) {
            handlersByMethod[0] = handler;
        } else {
            for (int method : methods) {
                if (method <= 0 || method >= Request.NUMBER_OF_METHODS) {
                    throw new IllegalArgumentException("Invalid RequestMethod " + method + " for path " + path);
                }
                if (method >= handlersByMethod.length) {
                    handlersByMethod = Arrays.copyOf(handlersByMethod, method + 1);
                    super.put(path, handlersByMethod);
                }
                handlersByMethod[method] = handler;
            }
        }
    }

    // Return an existing handler for this HTTP request or null if not found
    public RequestHandler find(String path, int method) {
        RequestHandler[] handlersByMethod = super.get(path);
        if (handlersByMethod == null) {
            return null;
        }

        // First, try to find a handler with @RequestMethod annotation
        if (method > 0 && method < handlersByMethod.length && handlersByMethod[method] != null) {
            return handlersByMethod[method];
        }
        // Otherwise return the universal handler for all methods
        return handlersByMethod[0];
    }
}

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

package one.nio.http;

import one.nio.util.URLEncoder;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * {@link Iterator} over {@code String} {@code key[=[value]]} query parameters in order skipping empty parameters
 *
 * @author incubos
 */
final class QueryParameterIterator implements Iterator<Map.Entry<String, String>> {
    private final String query;
    private int cur; // Always points at the current parameter or -1 (EOF)

    QueryParameterIterator(String query) {
        assert query != null;

        this.query = query;
        this.cur = 0;

        advance();
    }

    @Override
    public boolean hasNext() {
        return cur >= 0;
    }

    @Override
    public Map.Entry<String, String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Beyond query parameters");
        }

        // Extract parameter
        final int next = query.indexOf('&', cur);
        final String parameter;
        if (next < 0) {
            // Last parameter
            parameter = query.substring(cur);
            cur = -1;
        } else {
            // Advance
            parameter = query.substring(cur, next);
            cur = next;
            advance();
        }

        // Parse {@code key[=[value]]} parameter
        final int q = parameter.indexOf('=');
        if (q < 0) {
            return new QueryParameter(parameter, "");
        } else {
            return new QueryParameter(parameter.substring(0, q), parameter.substring(q + 1));
        }
    }

    // Skip '&'s and position at the next parameter or stops
    private void advance() {
        assert cur >= 0;

        // Skip '&'s
        while (cur < query.length() && query.charAt(cur) == '&') {
            cur++;
        }

        // Check the end
        if (cur == query.length()) {
            cur = -1;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Query parameter removal is not supported");
    }

    // Query parameter with lazily URL decoded value without memoization
    private static final class QueryParameter implements Map.Entry<String, String> {
        private final String key;
        private final String rawValue;

        QueryParameter(String key, String rawValue) {
            assert key != null && !key.isEmpty();
            assert rawValue != null;

            this.key = key;
            this.rawValue = rawValue;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return URLEncoder.decode(rawValue);
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }
}

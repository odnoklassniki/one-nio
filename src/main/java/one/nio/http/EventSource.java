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

import java.io.Closeable;
import java.io.IOException;

/**
 * A source of server side emitted events
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events">HTML Standard: 9.2 Server-sent events</a>
 * @see HttpClient#openEvents(Request, int)
 */
public interface EventSource<D> extends Closeable
{
    /**
     * Waits for the next SSE and returns an event. The method can block for a long time ( determined by server ).
     * It is essential to check for null and {@link Event#isEmpty()} before processing
     *
     * @return the next event from the stream or null, if stream was closed by either party
     * @throws IOException an I/O exception occurred
     * @throws HttpException an incorrect HTTP request received
     */
    Event<D> poll( ) throws IOException, HttpException;
    
    /**
     * A single Server Sent Event, received from peer.
     */
    interface Event<D>
    {
        /**
         * No name, id and data in event ( only comment )
         *
         * @return true, if the event has no name, id and data. false, otherwise
         */
        boolean isEmpty();
        
        /**
         * an SSE event name
         *
         * @return the event name
         */
        String name();

        /**
         * an SSE event id. this can be used to request events starting from specified
         *
         * @return the event id
         * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#the-last-event-id-header">HTML Standard: 9.2.4 The `Last-Event-ID` header</a>
         */
        String id();

        /**
         * an SSE "data" line.
         *
         * @return the event data
         */
        D data();
        

        /**
         * an SSE comment concatenated
         *
         * @return the event comment
         */
        String comment();

    }

}

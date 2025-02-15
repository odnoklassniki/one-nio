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

import java.io.IOException;

/**
 * A Response, which can poll for server emitted events.
 * Unlike regular {@link Response} this object must be close'd to
 * prevent resource leak.
 * <p>
 * The usage flow is as follows:
 * <ol>
 * <li>Call {@link HttpClient#openEvents(Request, int)}</li>
 * <li>Inspect the result code, if it is not OK process the error</li>
 * <li>Inspect the content-type, it must be text/event-stream; if it is not - process the response body - there will be no events</li>
 * <li>while ( ( event = poll() ) != null ) process( event )</li>
 * <li>call {@link #close()}</li>
 * <li>call {@link HttpClient#reopenEvents(Request, String, int)} with last processed {@link Event#id()} and go to p.2</li>
 * </ol>
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events">HTML Standard: 9.2 Server-sent events</a>
 * @see HttpClient#openEvents(Request, int)
 */
public class EventSourceResponse extends Response implements EventSource<String>
{
    private EventSource<String> eventSource;

    public EventSourceResponse( String resultCode )
    {
        super( resultCode );
    }

    @Override
    public Event<String> poll() throws IOException, HttpException
    {
        return eventSource == null ? null : eventSource.poll();
    }
    
    void setEventSource( EventSource<String> es ) {
        this.eventSource = es;
    }
    
    @Override
    public void close() throws IOException
    {
        if ( eventSource != null ) {
            eventSource.close();
            eventSource = null;
        }
    }
}

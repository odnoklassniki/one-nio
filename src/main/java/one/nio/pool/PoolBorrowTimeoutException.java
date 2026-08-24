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

package one.nio.pool;

/**
 * Dedicated exception for timeout on getting socket from socket pool.
 * <p>
 * This one to distinguish case "pool has no free sockets" vs other issues
 * like "connection issues" or "pool is already closed"
 */
public class PoolBorrowTimeoutException extends PoolException {
    public PoolBorrowTimeoutException() {
    }

    public PoolBorrowTimeoutException(String message) {
        super(message);
    }

    public PoolBorrowTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public PoolBorrowTimeoutException(Throwable cause) {
        super(cause);
    }
}

1.1.0
  * `ConfigParser` better handles scalars, collections and generics
  * `Malloc` compatibility with older format
  * `SerializeWith(getter, setter)` and `SerialOptions` annotations
  * SSL key passphrase and `SOL_SSL` socket options
  * `Socket.read()` with flags
  * Fixed `EINTR` during connect
  * Fixed `readFully`/`writeFully` on `JavaSocket`
  * `RpcClient` invoke with timeout
  * Server threads now extend `PayloadThread`
  * JDK 9,10,11 support
  * Iterator over all nonempty HTTP Request query parameters
  * Return query parameters as an iterable
  * Handle `Throwable` instead of `Exception` in `AsyncExecutor`
  * YAML parser fixes
  * Java 8 minimum requirement
  * HTTP and SOCKS proxy support
  * Bridge to `setns()` syscall
  * Batch/Idle thread scheduling priorities
  * FIFO/LIFO pools
  * Socket I/O improvements: `ByteChannel` API, `accept4()` support
  * JSON serialization/deserialization
  * HTTP `@RequestMethod` annotation
  * Object Streaming API
  * RPC streaming and HTTP-RPC support

1.0.2
  * YAML parser now supports references and extended array/list declaration
  * ByteBuffer API for LZ4 compression/decompression
  * HttpServerConfig
  * Native UDP sockets
  * TOS and reusePort attributes for TCP sockets
  * Compatibility layer for different versions of OpenSSL. Support for multiple server certificates.
  * Better type migration. Default fields improvement. Concurrency issues.
  * Virtual hosts for HttpServer
  * Fixed LZ4 native wrapper (JVM crash)
  * Default connectTimeout != readTimeout
  * Load libssl.so dynamically (to support both OpenSSL 1.0.x and 1.1.0)
  * Fixed MappedFileTool argument parsing
  * Minor one.nio.mem and concurrency fixes

1.0.1
  * Support for HTTP PUT/DELETE/TRACE/CONNECT/PATCH (#16)
  * Include libonenio.so into Maven build
  * Interpret IllegalArgumentException as "400 Bad request"
  * Support for HTTP request body

1.0.0
  * Initial release

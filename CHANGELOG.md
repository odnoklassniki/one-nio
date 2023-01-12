1.6.1
 * openssl3 support
 * BigDecimal deserialization

1.6.0
 * java.time serialization
 * Arena Allocator
 * PaddedRWLock to avoid false sharing
 * Handle EINTR in socket read/write
 * Enum serialization fixes
 * Fixed global perf counters in presence of offline CPUs
 * HttpClient should support responses without Content-Length / Transfer-Encoding
 * Gracefully close sessions when stopping Server

1.5.0
 * JDK 17 support. Workaround `setAccessible` restrictions
 * New BPF map types
 * `SOCK_SEQPACKET` support
 * `sendMsg`/`recvMsg` for passing descriptors over a UNIX domain socket
 * Serializer generator now tolerates missing class files
 * Fixed serialization of private Records
 * Optimize `Utf8.read` for Compact Strings
 * Customizable strategy for reusing OffheapMap entries
 * Allow custom reference tags in serialization streams
 * Fixed overflow bugs in `ObjectInput(Output)Channel`
 * A method to open all JDK modules
 * `Json.fromJson` with a given Class or Type
 * A few new methods in HTTP API 
 * Migrated to ASM 9
 * Reduced size of libonenio binary

1.4.0
 * eBPF and extended perf_events support
 * HttpClient fixes
 * Reflection and serialization now works for classes with missing field types
 * Improved serialization performance
 * OffheapMap API enhancements; compatibility with newer JDKs
 * Configure scheduling policy for thread pools
 * Fixed `byte[]` <-> `long` conversion

1.3.2
 * Optimized serialization of empty collections
 * `schedulingPolicy` option for NIO server threads
 * Fixed JavaSocket timeouts on Mac and Windows

1.3.1
 * Serialization of Java Records
 * DigestStream fixes

1.3.0
 * SSL improvements: RDRAND, PEER_CERTIFICATE_CHAIN, autoupdate certificates
 * Extended Perf Events API, including PEBS
 * Garbage free thread safe date utilities; high performance date formatter
 * Serialization: collection type evolution; better JSON support
 * PROXY protocol support for RPC server
 * Configurable `DigestStream`
 * `Thread.onSpinWait` bridge
 * Custom collection converters for `ConfigParser`
 * HttpClient fixes
 * Utility class to invoke HotSpot diagnostic commands
 * `CustomThreadFactory` for creating named/daemon/batch threads
 * `RpcStream` memory optimizations
 * `setAffinity`/`getAffinity` for machines with more than 64 CPUs
 * AF_UNIX socket support. Refactored InetAddress handling
 * systemd-notify API

1.2.1
  * Enable SO_KEEPALIVE on a ServerSocket by default
  * Fixed server graceful shutdown
  * Fixed Socket tests failing on older kernels

1.2.0
  * Updated SSL API. Client certificate inspection support
  * `SerializedWrapper` for transfering pre-serialized objects
  * Http server returns "400 Bad Request" if fails to parse parameters
  * Getters for all socket options
  * Synchronous `Server.start`
  * Java wrappers for `setpriority`/`getpriority` (thread niceness)
  * `HttpClient` invoke with timeout
  * Support for connected UDP Java socket
  * Java API for perf events on Linux
  * Serialization bugfixes
  * `ConnectionString` recognizes well-known ports
  * `ConfigParser` enhancements
  * Fixed compatibility issues with JDK 11. Get rid of `tools.jar` dependency

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

[![one-nio](https://maven-badges.herokuapp.com/maven-central/ru.odnoklassniki/one-nio/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ru.odnoklassniki/one-nio) [![Javadocs](http://www.javadoc.io/badge/ru.odnoklassniki/one-nio.svg)](http://www.javadoc.io/doc/ru.odnoklassniki/one-nio)

# one-nio

**one-nio** is a library for building high-performance Java servers.
It exposes OS capabilities and selected JDK internal APIs to help high-load applications get the most out of the underlying system.

## Key Features

- **Optimized Native Sockets**: A low-level socket I/O API with its own native library, providing wrappers for Linux-specific primitives like `epoll_wait()` and `sendfile()`.
- **Off-Heap Memory Management**: APIs for managing gigabytes of RAM beyond the Java heap, including `DirectMemory`, `MappedFile`, and high-performance off-heap hash tables.
- **Fast and Compact Serialization**: A serialization mechanism that's significantly faster and more compact than standard Java serialization, with built-in schema evolution and dynamic serializer generation.
- **High-Performance RPC**: An RPC client/server technology that is an order of magnitude faster than Java RMI, leveraging `one-nio`'s fast I/O and serialization.
- **Extended Functionality**: Includes a simple, high-performance HTTP server, a general-purpose TCP server, and utility functions for asynchronous execution, resource pooling, and JMX management.
- **Access to System Internals**: Java accessors for perf events and eBPF on supported platforms.

(Detailed package description)[https://github.com/odnoklassniki/one-nio/wiki/Package-description]

## Table of contents

- [Serialization](#serialization)
  - [Serialization features](#serialization-features-and-limitations)
  - [Quick start](#quick-start-example)
  - [Serializer generation modes](#serializer-generation-modes)
- [More on the wiki](#more-on-the-wiki)

## Serialization
**one-nio** provides a fast and compact serialization mechanism designed for high-throughput services and long-term schema evolution. It supports:

- High Performance: Efficient for both primitives and object graphs.
- Backward/Forward Compatibility: Handles common refactorings like adding/removing/reordering fields, compatible type changes, and safe renames via the @Renamed annotation.
- Practical Tooling: Includes JMX statistics and a bytecode dump option to help diagnose version conflicts.

### Serialization features and limitations

- Supports `Serializable` and `Externalizable` interfaces:
  - `readObject`/`writeObject` are invoked when present; they may only call `defaultReadObject`/`defaultWriteObject` on the data stream.
  - `Externalizable.readExternal`/`writeExternal` are fully supported.
- Collections and maps are serialized by contents. Types without a public no‑arg constructor fall back to sensible defaults (e.g., `ArrayList`/`LinkedList`, `HashSet`/`TreeSet`, `HashMap`/`TreeMap`) chosen by implemented interfaces.
- Constructors are not invoked during deserialization (except for collections/maps as noted above).
- `serialVersionUID` is ignored by one-nio (safe to omit). The UID is derived from instance field names, types, and the field order.
- Schema evolution is built in:
  - Adding/removing/reordering fields is compatible.
  - Safe renames via `@Renamed` are supported for classes and fields.
  - Certain type changes are auto-converted (primitive↔primitive, wrapper↔primitive, `Number`↔`Number`, up/downcasts within a hierarchy, and via `valueOf()`/instance methods when present). If no rule applies, the field falls back to a default (`0`/`null`).
- Enums are supported with compact 2‑byte encoding.
- Wrapper types add ~1 byte of overhead compared to primitive serialization.
- Known JDK type caveats: java.sql.Date and java.sql.Time have only transient fields, so use java.util.Date or java.sql.Timestamp.
- Avoid anonymous/local classes in serialized state (the engine warns: "Trying to serialize anonymous class").

Additional details: see the [Serialization FAQ](https://github.com/odnoklassniki/one-nio/wiki/Serialization-FAQ)

### Quick start example

A minimal example showing how to serialize and deserialize a simple object with a transient field:

```java
import one.nio.serial.Serializer;
import java.io.Serializable;

public class Example {
    static class User implements Serializable {
        String name;
        int age;
        transient String password; // will not be serialized

        User(String name, int age, String password) {
            this.name = name;
            this.age = age;
            this.password = password;
        }

        public String toString() {
          return "User{" +
                  "name='" + name + '\'' +
                  ", age=" + age +
                  ", password='" + password + '\'' +
                  '}';
        }
    }

    public static void main(String[] args) throws Exception {
        User alice = new User("Alice", 30, "secret");

        // Serialize to compact byte[]
        byte[] data = Serializer.serialize(alice);

        // Deserialize back
        User copy = (User) Serializer.deserialize(data);

        System.out.println(copy);
    }
}
```

### Serializer generation modes

**one-nio** generates bytecode for serializers at runtime. Since version 2.2.0, it supports two generation modes:

- magic_accessor (legacy, default on JDK ≤ 23): Uses the old MagicAccessor-based approach that relied on JDK internals.
- method_handles (new experimental mode, supported on JDK 9+): Uses MethodHandles/VarHandles instead of legacy JDK internal APIs. Required for JDK 24+.

The mode can be configured with the `one.nio.serial.gen.mode` system property:

- -Done.nio.serial.gen.mode=magic_accessor
- -Done.nio.serial.gen.mode=method_handles


## More on the wiki
https://github.com/odnoklassniki/one-nio/wiki

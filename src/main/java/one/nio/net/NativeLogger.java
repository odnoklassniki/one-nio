package one.nio.net;

import org.slf4j.Logger;

public interface NativeLogger {
    Logger logger();
    default void log(String level, String message) {
        switch (level) {
            case "TRACE":
                logger().trace(message);
                break;
            case "DEBUG":
                logger().debug(message);
                break;
            case "INFO":
                logger().info(message);
                break;
            case "WARN":
                logger().warn(message);
                break;
            case "ERROR":
                logger().error(message);
                break;
            default:
                throw new IllegalArgumentException("Unknown log level: " + level);
        }
    }
}

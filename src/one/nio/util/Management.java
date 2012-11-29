package one.nio.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class Management {
    private static final Log log = LogFactory.getLog(Management.class);

    public static void registerMXBean(Object object, String id) {
        String name = object.getClass().getPackage().getName() + ':' + id;
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(object, new ObjectName(name));
        } catch (Exception e) {
            log.error("Cannot register MXBean " + name, e);
        }
    }
}

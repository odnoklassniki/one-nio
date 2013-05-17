package one.nio.mgt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.Set;

public class Management {
    private static final Log log = LogFactory.getLog(Management.class);

    public static void registerMXBean(Object object, String name) {
        try {
            StandardMBean mb = new StandardMBean(object, null, true);
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();

            ObjectName objectName = new ObjectName(name);
            if (beanServer.isRegistered(objectName)) {
                beanServer.unregisterMBean(objectName);
            }
            beanServer.registerMBean(mb, objectName);
        } catch (Exception e) {
            log.error("Cannot register MXBean " + name, e);
        }
    }

    public static Object getAttribute(String name, String attribute) throws JMException {
        ObjectName objName = resolveName(name);
        return ManagementFactory.getPlatformMBeanServer().getAttribute(objName, attribute);
    }

    public static Object[] getAttributes(String name, String[] attributes) throws JMException {
        ObjectName objName = resolveName(name);
        AttributeList list = ManagementFactory.getPlatformMBeanServer().getAttributes(objName, attributes);

        Object[] values = new Object[attributes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = ((Attribute) list.get(i)).getValue();
        }
        return  values;
    }

    private static ObjectName resolveName(String name) throws JMException {
        ObjectName objName = new ObjectName(name);
        if (name.indexOf('*') < 0 && name.indexOf('?') < 0) {
            return objName;
        }

        Set<ObjectName> objNames = ManagementFactory.getPlatformMBeanServer().queryNames(objName, null);
        if (objNames.isEmpty()) {
            throw new InstanceNotFoundException(name);
        }
        return objNames.iterator().next();
    }
}

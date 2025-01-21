/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
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

package one.nio.mgt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;

public class Management {
    private static final Logger log = LoggerFactory.getLogger(Management.class);

    static {
        try {
            // After registering this magic instance, new HotSpot internal MBeans appear:
            // HotspotRuntimeMBean, getHotspotMemoryMBean etc.
            ManagementFactory.getPlatformMBeanServer().createMBean("sun.management.HotspotInternal", null);
        } catch (Exception e) {
            log.warn("Cannot register HotspotInternal", e);
        }
    }

    public static void registerMXBean(Object object, String name) {
        registerMXBean(object, null, name);
    }

    public static <T> void registerMXBean(T object, Class<T> mxbeanInterface, String name) {
        try {
            StandardMBean mb = new StandardMBean(object, mxbeanInterface, true);
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();

            ObjectName objectName = new ObjectName(name);
            if (beanServer.isRegistered(objectName)) {
                beanServer.unregisterMBean(objectName);
            }
            beanServer.registerMBean(mb, objectName);
        } catch (Exception e) {
            log.error("Cannot register MXBean {}", name, e);
        }
    }

    public static void unregisterMXBean(String name) {
        try {
            MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(name);
            if (beanServer.isRegistered(objectName)) {
                beanServer.unregisterMBean(objectName);
            }
        } catch (Exception e) {
            log.error("Cannot unregister MXBean {}", name, e);
        }
    }

    public static Object getAttribute(String name, String attribute) throws JMException {
        return getAttribute(new ObjectName(name), attribute);
    }

    public static Object getAttribute(ObjectName objName, String attribute) throws JMException {
        return ManagementFactory.getPlatformMBeanServer().getAttribute(objName, attribute);
    }

    public static Object[] getAttributes(String name, String... attributes) throws JMException {
        return getAttributes(new ObjectName(name), attributes);
    }

    public static Object[] getAttributes(ObjectName objName, String... attributes) throws JMException {
        AttributeList list = ManagementFactory.getPlatformMBeanServer().getAttributes(objName, attributes);
        Object[] values = new Object[list.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = ((Attribute) list.get(i)).getValue();
        }
        return values;
    }

    public static Set<ObjectName> resolvePattern(String name) throws JMException {
        ObjectName objName = new ObjectName(name);
        if (name.indexOf('*') < 0 && name.indexOf('?') < 0) {
            return Collections.singleton(objName);
        }

        Set<ObjectName> objNames = ManagementFactory.getPlatformMBeanServer().queryNames(objName, null);
        if (objNames.isEmpty()) {
            throw new InstanceNotFoundException(name);
        }
        return objNames;
    }
}

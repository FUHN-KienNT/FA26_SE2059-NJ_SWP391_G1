package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import jakarta.servlet.ServletContextEvent;

class AssignmentDatabaseLifecycleTest {
    @Test void loadsDriverWhenTomcatInitializedJdbcBeforeWebapp() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        DriverManager.getDrivers();
        URL[] urls = {
            DBConnection.class.getProtectionDomain().getCodeSource().getLocation(),
            ServletContextEvent.class.getProtectionDomain().getCodeSource().getLocation(),
            org.postgresql.Driver.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var db = loader.loadClass(DBConnection.class.getName());
            var connect = db.getMethod("getConnection");
            // Upstream DBConnection now also registers the driver. Both initialization
            // paths must coexist with an isolated Tomcat webapp classloader.
            var eventType = loader.loadClass(ServletContextEvent.class.getName());
            var lifecycle = loader.loadClass(AssignmentDatabaseLifecycle.class.getName());
            var listener = lifecycle.getConstructor().newInstance();
            lifecycle.getMethod("contextInitialized", eventType).invoke(listener, new Object[]{null});
            try (Connection c = (Connection) connect.invoke(null); var s = c.createStatement();
                 var r = s.executeQuery("SELECT 1")) {
                assertTrue(r.next()); assertEquals(1, r.getInt(1));
            } finally {
                lifecycle.getMethod("contextDestroyed", eventType).invoke(listener, new Object[]{null});
            }
        }
    }
}

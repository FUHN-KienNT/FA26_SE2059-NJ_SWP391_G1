package vn.edu.fpt.ams.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Load the JDBC driver inside the webapp, even if Tomcat initialized DriverManager first. */
@WebListener
public class AssignmentDatabaseLifecycle implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event) {
        try {
            Class.forName("org.postgresql.Driver", true, getClass().getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("PostgreSQL JDBC driver is missing from WEB-INF/lib.", ex);
        }
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            var driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() != getClass().getClassLoader()) continue;
            try { DriverManager.deregisterDriver(driver); }
            catch (SQLException ex) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "Could not release webapp JDBC driver", ex);
            }
        }
    }
}

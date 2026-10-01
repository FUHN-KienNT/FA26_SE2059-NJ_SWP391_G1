package vn.edu.fpt.ams.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {
    private DBConnection() {}
    public static Connection getConnection() throws SQLException {
        Properties p = new Properties();
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) { throw new SQLException("Cannot read database configuration", e); }
        String url = value("DB_URL", p.getProperty("db.url"));
        if (url == null) {
            String host = value("DB_HOST", p.getProperty("db.host"));
            String port = value("DB_PORT", p.getProperty("db.port"));
            String name = value("DB_NAME", p.getProperty("db.name"));
            if (host != null && port != null && name != null) {
                url = "jdbc:postgresql://" + host + ":" + port + "/" + name;
            }
        }
        String user = value("DB_USER", value("DB_USERNAME", p.getProperty("db.username")));
        String password = value("DB_PASSWORD", p.getProperty("db.password"));
        if (url == null || user == null || password == null) {
            throw new SQLException("Set DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD (or DB_URL/DB_USERNAME) or a local application.properties");
        }
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("PostgreSQL JDBC driver is not available", ex);
        }
        return DriverManager.getConnection(url, user, password);
    }
    private static String value(String key, String fallback) {
        String env = System.getenv(key);
        return env != null && !env.isBlank() ? env : fallback;
    }
}

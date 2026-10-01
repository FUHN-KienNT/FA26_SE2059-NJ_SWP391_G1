package vn.edu.fpt.ams.util;

import java.util.Properties;

public final class AppConfig {
  private AppConfig() {}

  public static String get(String env, String key, String fallback) {
    String v = System.getenv(env);
    if (v != null && !v.isBlank()) return v;
    Properties p = new Properties();
    try (var in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
      if (in != null) p.load(in);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot read configuration", e);
    }
    return p.getProperty(key, fallback);
  }

  public static String required(String env, String key) {
    String v = get(env, key, "");
    if (v.isBlank() || v.equals("CHANGE_ME"))
      throw new IllegalStateException("Configure " + env + " before using this integration");
    return v;
  }
}

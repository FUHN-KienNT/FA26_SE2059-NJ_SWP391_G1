package vn.edu.fpt.ams.util;

import java.util.Locale;

/** The answer editor accepts self-contained diagrams, never includes or macros. */
public final class PlantUmlSource {
    public static final int MAX_LENGTH = 20_000, MAX_LINES = 300;

    private PlantUmlSource() {}

    public static String normalize(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "Mã PlantUML cần có nội dung và tối đa 20.000 ký tự.");

        String value = source.replace("\r\n", "\n").replace('\r', '\n').strip();

        if (value.lines().count() > MAX_LINES)
            throw new IllegalArgumentException("Mã PlantUML tối đa 300 dòng.");

        if (value.startsWith("@startuml\n") && value.endsWith("\n@enduml"))
            value =
                    value.substring("@startuml".length(), value.length() - "@enduml".length())
                            .strip();

        String lower = value.toLowerCase(Locale.ROOT);

        if (value.isBlank()
                || value.indexOf('@') >= 0
                || value.indexOf('!') >= 0
                || value.indexOf('%') >= 0
                || value.indexOf('\0') >= 0
                || lower.contains("<img")
                || lower.contains("<image")
                || lower.contains("[[")
                || lower.contains("url")
                || lower.contains("sprite")
                || lower.contains("<svg")
                || lower.contains("<&")
                || lower.contains("<$"))
            throw new IllegalArgumentException(
                    "Chỉ dùng mã sơ đồ độc lập; không dùng tệp, liên kết, ảnh ngoài hoặc lệnh tiền xử lý.");

        String normalized = "@startuml\n" + value + "\n@enduml";

        if (normalized.length() > MAX_LENGTH || normalized.lines().count() > MAX_LINES)
            throw new IllegalArgumentException(
                    "Mã PlantUML vượt giới hạn 20.000 ký tự hoặc 300 dòng.");

        return normalized;
    }
}

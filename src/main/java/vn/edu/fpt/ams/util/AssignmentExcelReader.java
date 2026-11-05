package vn.edu.fpt.ams.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Bounded Excel question import. Cell formulas are rejected and never evaluated. */
final class AssignmentExcelReader {
    static final List<String> HEADERS =
            List.of(
                    "Câu số",
                    "Nội dung câu hỏi",
                    "Điểm câu hỏi",
                    "Yêu cầu số",
                    "Nội dung yêu cầu",
                    "Điểm yêu cầu",
                    "Hình thức trả lời");
    private static final int MAX_ROWS =
            AssignmentRules.MAX_TASKS * AssignmentRules.MAX_REQUIREMENTS;
    private static final int MAX_EXPANDED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;

    private AssignmentExcelReader() {}

    static JsonArray read(String filename, byte[] bytes) {
        if (bytes == null
                || bytes.length == 0
                || bytes.length > AssignmentDocumentReader.MAX_FILE_BYTES)
            throw invalid("File Excel phải có nội dung và không vượt 2 MB.");

        boolean xlsx = filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");

        if (xlsx) checkExpandedSize(bytes);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if ((xlsx && !(workbook instanceof XSSFWorkbook))
                    || (!xlsx && !(workbook instanceof HSSFWorkbook)))
                throw invalid(
                        "Nội dung file không khớp đuôi .xlsx hoặc .xls. Hãy lưu lại bằng Excel rồi thử lại.");

            Sheet sheet = workbook.getSheet("Cau hoi");

            if (sheet == null && workbook.getNumberOfSheets() > 0) sheet = workbook.getSheetAt(0);

            if (sheet == null) throw invalid("File Excel chưa có trang tính câu hỏi.");

            if (sheet.getLastRowNum() > MAX_ROWS)
                throw invalid("Trang câu hỏi được tối đa 2.000 dòng yêu cầu và một dòng tiêu đề.");

            String[] header = values(sheet.getRow(0), 1);

            for (int column = 0; column < HEADERS.size(); column++)
                if (!HEADERS.get(column).equalsIgnoreCase(header[column]))
                    throw invalid(
                            "Tiêu đề cột "
                                    + (column + 1)
                                    + " phải là “"
                                    + HEADERS.get(column)
                                    + "”. Hãy dùng file Excel mẫu.");

            JsonArray tasks = new JsonArray();
            JsonObject current = null;
            int textChars = 0;

            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                int row = index + 1;
                String[] cells = values(sheet.getRow(index), row);
                boolean empty = true;

                for (String cell : cells) {
                    textChars += cell.length();

                    if (!cell.isBlank()) empty = false;
                }

                if (textChars > AssignmentDocumentReader.MAX_TEXT_CHARS)
                    throw invalid("Nội dung Excel vượt 100.000 ký tự.");

                if (empty) continue;

                int question = integer(cells[0], AssignmentRules.MAX_TASKS, row, "Câu số");
                int requirement =
                        integer(cells[3], AssignmentRules.MAX_REQUIREMENTS, row, "Yêu cầu số");
                String title = required(cells[1], 200, row, "Nội dung câu hỏi");
                BigDecimal points = points(cells[2], row, "Điểm câu hỏi");

                if (question == tasks.size() + 1) {
                    if (requirement != 1)
                        throw invalid("Dòng " + row + ": yêu cầu của câu mới phải bắt đầu từ 1.");

                    current = new JsonObject();
                    current.addProperty("task_order", question);
                    current.addProperty("title", title);
                    current.addProperty("description", "");
                    current.addProperty("maximum_points", points);
                    current.add("requirements", new JsonArray());
                    tasks.add(current);
                } else if (current == null || question != tasks.size()) {
                    throw invalid(
                            "Dòng "
                                    + row
                                    + ": số câu phải liên tiếp từ 1, các yêu cầu của cùng một câu nằm cạnh nhau.");
                } else if (!current.get("title").getAsString().equals(title)
                        || current.get("maximum_points").getAsBigDecimal().compareTo(points) != 0) {
                    throw invalid(
                            "Dòng "
                                    + row
                                    + ": nội dung và điểm câu hỏi phải giống nhau ở các dòng của cùng một câu.");
                }

                JsonArray requirements = current.getAsJsonArray("requirements");

                if (requirement != requirements.size() + 1)
                    throw invalid(
                            "Dòng " + row + ": số yêu cầu phải liên tiếp từ 1 trong từng câu.");

                JsonObject child = new JsonObject();

                child.addProperty("requirement_order", requirement);
                child.addProperty("title", required(cells[4], 500, row, "Nội dung yêu cầu"));
                child.addProperty("maximum_points", points(cells[5], row, "Điểm yêu cầu"));
                child.addProperty("answer_type", answerType(cells[6], row));
                requirements.add(child);
            }

            return EssayQuestions.normalize(tasks, null);
        } catch (EncryptedDocumentException ex) {
            throw invalid("Không hỗ trợ file Excel được mã hóa hoặc yêu cầu mật khẩu.");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalid("Không đọc được file Excel. Hãy kiểm tra định dạng và dùng file mẫu.");
        }
    }

    private static String[] values(Row source, int row) {
        String[] result = new String[HEADERS.size()];

        if (source != null && source.getLastCellNum() > 64)
            throw invalid("Dòng " + row + ": trang câu hỏi chỉ sử dụng 7 cột trong file mẫu.");

        for (int column = 0; column < result.length; column++)
            result[column] = value(source == null ? null : source.getCell(column), row, column);

        if (source != null)
            for (int column = HEADERS.size(); column < source.getLastCellNum(); column++)
                if (!value(source.getCell(column), row, column).isBlank())
                    throw invalid("Dòng " + row + ": không thêm dữ liệu ngoài 7 cột của file mẫu.");

        return result;
    }

    private static String value(Cell cell, int row, int column) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";

        String text;

        switch (cell.getCellType()) {
            case STRING -> text = cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell))
                    throw invalid(
                            "Dòng " + row + ", cột " + (column + 1) + ": không nhập ngày tháng.");

                text =
                        BigDecimal.valueOf(cell.getNumericCellValue())
                                .stripTrailingZeros()
                                .toPlainString();
            }
            case FORMULA ->
                    throw invalid(
                            "Dòng "
                                    + row
                                    + ", cột "
                                    + (column + 1)
                                    + ": hãy nhập giá trị trực tiếp, không dùng công thức Excel.");
            default ->
                    throw invalid(
                            "Dòng " + row + ", cột " + (column + 1) + ": dữ liệu ô không hợp lệ.");
        }

        return Normalizer.normalize(AssignmentRules.normalizeLines(text), Normalizer.Form.NFC)
                .strip();
    }

    private static int integer(String text, int maximum, int row, String name) {
        try {
            if (!text.matches("[0-9]{1,4}")) throw new NumberFormatException();

            int value = Integer.parseInt(text);

            if (value < 1 || value > maximum) throw new NumberFormatException();

            return value;
        } catch (NumberFormatException ex) {
            throw invalid(
                    "Dòng " + row + ": “" + name + "” phải là số nguyên từ 1 đến " + maximum + ".");
        }
    }

    private static BigDecimal points(String text, int row, String name) {
        try {
            String normalized = text.replace(',', '.');

            if (!normalized.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))
                throw new NumberFormatException();

            BigDecimal value = new BigDecimal(normalized);

            if (!AssignmentRules.validPoints(value)) throw new NumberFormatException();

            return value.setScale(2);
        } catch (NumberFormatException ex) {
            throw invalid(
                    "Dòng "
                            + row
                            + ": “"
                            + name
                            + "” phải từ 0.01 đến 100, tối đa 2 chữ số thập phân.");
        }
    }

    private static String required(String text, int maximum, int row, String name) {
        if (text.isBlank() || AssignmentRules.length(text) > maximum)
            throw invalid(
                    "Dòng " + row + ": “" + name + "” bắt buộc, tối đa " + maximum + " ký tự.");

        return text;
    }

    private static String answerType(String text, int row) {
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "TEXT", "VĂN BẢN" -> "TEXT";
            case "DIAGRAM", "SƠ ĐỒ" -> "DIAGRAM";
            case "BOTH", "VĂN BẢN VÀ SƠ ĐỒ" -> "BOTH";
            default ->
                    throw invalid(
                            "Dòng " + row + ": hình thức trả lời phải là TEXT, DIAGRAM hoặc BOTH.");
        };
    }

    private static void checkExpandedSize(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int total = 0, count = 0;
            byte[] buffer = new byte[8192];

            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++count > MAX_ENTRIES) throw invalid("File Excel có quá nhiều thành phần.");

                if (entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"))
                    throw invalid(
                            "Không hỗ trợ file Excel chứa macro. Hãy lưu file .xlsx không có macro.");

                for (int length; (length = zip.read(buffer)) != -1; ) {
                    total += length;

                    if (total > MAX_EXPANDED_BYTES)
                        throw invalid("File Excel giải nén vượt giới hạn 16 MB.");
                }

                zip.closeEntry();
            }

            if (count == 0) throw invalid("File .xlsx không hợp lệ hoặc đã được mã hóa.");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalid("Không đọc được cấu trúc file .xlsx.");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

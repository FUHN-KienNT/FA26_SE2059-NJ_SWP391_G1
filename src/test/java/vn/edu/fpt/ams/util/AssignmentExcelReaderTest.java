package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.crypt.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AssignmentExcelReaderTest {
    private final AssignmentDocumentReader reader = new AssignmentDocumentReader();

    @Test
    void importsRealXlsxAndXlsWithMultipleRequirementsAndUnicode() throws Exception {
        for (boolean xlsx : List.of(true, false))
            try (Workbook workbook = workbook(xlsx)) {
                Sheet sheet = valid(workbook, "Cau hoi");

                sheet.getRow(1)
                        .getCell(1)
                        .setCellValue(
                                Normalizer.normalize(
                                        "Phân tích hệ thống quản lý bài tập.",
                                        Normalizer.Form.NFD));
                sheet.getRow(2).getCell(1).setCellValue("Phân tích hệ thống quản lý bài tập.");
                String text =
                        reader.read(xlsx ? "questions.XLSX" : "questions.XLS", bytes(workbook));
                var tasks = EssayQuestions.normalize(reader.parseQuestions(text), BigDecimal.TEN);

                assertEquals(2, tasks.size());
                assertEquals(
                        "Phân tích hệ thống quản lý bài tập.",
                        tasks.get(0).getAsJsonObject().get("title").getAsString());
                var children = tasks.get(0).getAsJsonObject().getAsJsonArray("requirements");

                assertEquals(2, children.size());
                assertEquals(
                        "Mô tả kiến trúc tổng quan.",
                        children.get(0).getAsJsonObject().get("title").getAsString());
                assertEquals(
                        "DIAGRAM",
                        children.get(1).getAsJsonObject().get("answer_type").getAsString());
                assertEquals(
                        "BOTH",
                        tasks.get(1)
                                .getAsJsonObject()
                                .getAsJsonArray("requirements")
                                .get(0)
                                .getAsJsonObject()
                                .get("answer_type")
                                .getAsString());
                assertEquals(
                        new BigDecimal("5.00"),
                        tasks.get(0).getAsJsonObject().get("maximum_points").getAsBigDecimal());
            }
    }

    @Test
    void choosesNamedQuestionSheetAndSupportsFirstSheetFallback() throws Exception {
        try (Workbook workbook = workbook(true)) {
            workbook.createSheet("Huong dan")
                    .createRow(0)
                    .createCell(0)
                    .setCellValue("Chỉ sửa dữ liệu ở trang Cau hoi.");
            valid(workbook, "Cau hoi");
            assertEquals(
                    2,
                    reader.parseQuestions(reader.read("questions.xlsx", bytes(workbook))).size());
        }

        try (Workbook workbook = workbook(false)) {
            Sheet sheet = valid(workbook, "Questions");

            // Numeric text and Vietnamese answer-type aliases are accepted without evaluating
            // formulas.
            sheet.getRow(1).getCell(2).setCellValue("5,00");
            sheet.getRow(1).getCell(6).setCellValue("Văn bản");
            sheet.getRow(2).getCell(6).setCellValue("Sơ đồ");
            sheet.getRow(3).getCell(6).setCellValue("Văn bản và sơ đồ");
            assertEquals(
                    2, reader.parseQuestions(reader.read("questions.xls", bytes(workbook))).size());
        }
    }

    @Test
    void rejectsFormulasEvenWithCachedOrUnusedColumnValues() throws Exception {
        for (boolean xlsx : List.of(true, false)) {
            for (int column : List.of(1, 2, 7))
                try (Workbook workbook = workbook(xlsx)) {
                    Sheet sheet = valid(workbook, "Cau hoi");

                    sheet.getRow(1).createCell(column).setCellFormula("2+3");
                    var failure =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            reader.read(
                                                    xlsx ? "questions.xlsx" : "questions.xls",
                                                    bytes(workbook)));

                    assertTrue(failure.getMessage().contains("công thức"));
                }
        }
    }

    @Test
    void rejectsHeadersOrdersMissingDataAndParentDisagreements() throws Exception {
        List<Consumer<Sheet>> cases =
                List.of(
                        sheet -> sheet.getRow(0).getCell(0).setCellValue("Question"),
                        sheet -> sheet.getRow(1).getCell(0).setCellValue(2),
                        sheet -> sheet.getRow(1).getCell(3).setCellValue(2),
                        sheet -> sheet.getRow(2).getCell(3).setCellValue(1),
                        sheet -> sheet.getRow(2).getCell(1).setCellValue("Đề khác."),
                        sheet -> sheet.getRow(2).getCell(2).setCellValue(4),
                        sheet -> sheet.getRow(1).getCell(4).setBlank(),
                        sheet -> sheet.getRow(1).getCell(6).setCellValue("FILE"),
                        sheet -> sheet.getRow(1).getCell(0).setCellValue(1.5),
                        sheet -> sheet.getRow(1).getCell(5).setCellValue(0),
                        sheet -> sheet.getRow(1).getCell(5).setCellValue(.001),
                        sheet -> sheet.getRow(1).getCell(5).setCellValue(101),
                        sheet -> sheet.getRow(1).getCell(2).setCellValue("5+5"),
                        sheet -> sheet.getRow(1).getCell(1).setCellValue("a".repeat(201)),
                        sheet -> sheet.getRow(1).getCell(4).setCellValue("a".repeat(501)),
                        sheet -> sheet.getRow(1).getCell(5).setCellValue(4));

        for (Consumer<Sheet> mutation : cases)
            try (Workbook workbook = workbook(true)) {
                mutation.accept(valid(workbook, "Cau hoi"));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read("questions.xlsx", bytes(workbook)));
            }
    }

    @Test
    void rejectsRowColumnAndQuestionRequirementLimits() throws Exception {
        for (Consumer<Sheet> mutation :
                List.<Consumer<Sheet>>of(
                        sheet -> sheet.createRow(2001),
                        sheet -> sheet.getRow(1).createCell(64),
                        sheet -> sheet.getRow(1).getCell(0).setCellValue(201),
                        sheet -> sheet.getRow(1).getCell(3).setCellValue(11))) {
            try (Workbook workbook = workbook(false)) {
                mutation.accept(valid(workbook, "Cau hoi"));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> reader.read("questions.xls", bytes(workbook)));
            }
        }
    }

    @Test
    void ignoresBlankRowsButRejectsEmptyWorkbookAndWrongExtension() throws Exception {
        try (Workbook workbook = workbook(true)) {
            valid(workbook, "Cau hoi").createRow(8);
            byte[] data = bytes(workbook);

            assertEquals(2, reader.parseQuestions(reader.read("questions.xlsx", data)).size());
            assertThrows(IllegalArgumentException.class, () -> reader.read("questions.xls", data));
        }

        try (Workbook workbook = workbook(false)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.read("questions.xls", bytes(workbook)));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("questions.xlsx", new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsEncryptedWorkbookAndZipExpansionBeforeParsing() throws Exception {
        try (Workbook workbook = workbook(true);
                POIFSFileSystem encrypted = new POIFSFileSystem()) {
            valid(workbook, "Cau hoi");
            Encryptor encryptor = new EncryptionInfo(EncryptionMode.agile).getEncryptor();

            encryptor.confirmPassword("synthetic-test-password");

            try (OutputStream output = encryptor.getDataStream(encrypted)) {
                workbook.write(output);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            encrypted.writeFilesystem(bytes);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.read("questions.xlsx", bytes.toByteArray()));
        }

        byte[] bomb = zip("xl/worksheets/sheet1.xml", new byte[16 * 1024 * 1024 + 1]);

        assertTrue(bomb.length < AssignmentDocumentReader.MAX_FILE_BYTES);
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> reader.read("bomb.xlsx", bomb))
                        .getMessage()
                        .contains("giải nén"));
        byte[] macro = zip("xl/vbaProject.bin", new byte[] {1});

        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> reader.read("macro.xlsx", macro))
                        .getMessage()
                        .contains("macro"));
    }

    @Test
    void appliesCanonicalTextLimitToLargeExcelContent() throws Exception {
        try (Workbook workbook = workbook(true)) {
            Sheet sheet = workbook.createSheet("Cau hoi");

            header(sheet);

            for (int question = 1; question <= 200; question++)
                row(
                        sheet,
                        question,
                        question,
                        "Q" + question,
                        1,
                        1,
                        "Nội dung yêu cầu. ".repeat(25),
                        1,
                        "TEXT");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.read("large.xlsx", bytes(workbook)));
        }
    }

    private static Workbook workbook(boolean xlsx) {
        return xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
    }

    private static Sheet valid(Workbook workbook, String name) {
        Sheet sheet = workbook.createSheet(name);

        header(sheet);
        row(
                sheet,
                1,
                1,
                "Phân tích hệ thống quản lý bài tập.",
                5,
                1,
                "Mô tả kiến trúc tổng quan.",
                3,
                "TEXT");
        row(
                sheet,
                2,
                1,
                "Phân tích hệ thống quản lý bài tập.",
                5,
                2,
                "Vẽ sơ đồ ca sử dụng.",
                2,
                "DIAGRAM");
        row(
                sheet,
                3,
                2,
                "Thiết kế giải pháp.",
                5,
                1,
                "Giải thích thiết kế và vẽ sơ đồ lớp.",
                5,
                "BOTH");

        return sheet;
    }

    private static void header(Sheet sheet) {
        Row row = sheet.createRow(0);

        for (int column = 0; column < AssignmentExcelReader.HEADERS.size(); column++)
            row.createCell(column).setCellValue(AssignmentExcelReader.HEADERS.get(column));
    }

    private static void row(
            Sheet sheet,
            int index,
            int question,
            String title,
            double points,
            int requirement,
            String prompt,
            double childPoints,
            String type) {
        Row row = sheet.createRow(index);
        Object[] values = {question, title, points, requirement, prompt, childPoints, type};

        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);

            if (values[column] instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(values[column].toString());
        }
    }

    private static byte[] bytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        workbook.write(output);

        return output.toByteArray();
    }

    private static byte[] zip(String name, byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(bytes);
            zip.closeEntry();
        }

        return output.toByteArray();
    }
}

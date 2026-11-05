package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssignmentDocumentReaderTest {
    private final AssignmentDocumentReader reader = new AssignmentDocumentReader();
    @TempDir Path temp;

    @Test
    void downloadableTemplateImportsAllScoresAndResponseTypes() {
        var tasks =
                EssayQuestions.normalize(
                        reader.parseQuestions(AssignmentDocumentReader.template()), BigDecimal.TEN);

        assertEquals(2, tasks.size());
        var children = tasks.get(0).getAsJsonObject().getAsJsonArray("requirements");

        assertEquals("DIAGRAM", children.get(1).getAsJsonObject().get("answer_type").getAsString());
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

    @Test
    void readsUtf8AndCanonicalJsonWithoutGuessingOrPartialImport() {
        String text = "\uFEFF" + AssignmentDocumentReader.template().replace("\n", "\r\n");
        String read = reader.read("questions.TXT", text.getBytes(StandardCharsets.UTF_8));

        assertFalse(read.contains("\r"));
        assertFalse(read.contains("\uFEFF"));
        var parsed = reader.parseQuestions(read);

        assertEquals(2, reader.parseQuestions("{\"tasks\":" + parsed + "}").size());
        assertEquals(2, reader.parseQuestions(parsed.toString()).size());

        for (String malformed :
                new String[] {
                    "Câu 1 (5 điểm): Câu hỏi",
                    "Câu 2 (5 điểm): Câu hỏi\nYêu cầu 1 (5 điểm): Trả lời",
                    "Câu 1 (5 điểm): Câu hỏi\nYêu cầu 2 (5 điểm): Trả lời",
                    AssignmentDocumentReader.template() + "\nKhông theo cấu trúc",
                    "{tasks: []}",
                    "{\"tasks\":[]}"
                })
            assertThrows(IllegalArgumentException.class, () -> reader.parseQuestions(malformed));
    }

    @Test
    void refusesUnsupportedMalformedOversizeAndTextBeyondLimit() {
        assertThrows(IllegalArgumentException.class, () -> reader.read("bad.exe", new byte[] {1}));
        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("bad.txt", new byte[] {(byte) 0xff}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reader.read(
                                "large.txt",
                                new byte[AssignmentDocumentReader.MAX_FILE_BYTES + 1]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reader.read(
                                "large.txt",
                                "a"
                                        .repeat(AssignmentDocumentReader.MAX_TEXT_CHARS + 1)
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> reader.read("empty.txt", new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("fake.docx", new byte[] {1, 2, 3}));
        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read("fake.pdf", new byte[] {1, 2, 3}));
    }

    @Test
    void extractsWordRunsAndParagraphsFromTemporaryDocx() throws Exception {
        String xml =
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                        + "<w:p><w:r><w:t>Câu 1 (5 điểm): </w:t></w:r><w:r><w:t>Câu hỏi</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:t>Yêu cầu 1 (5 điểm) [BOTH]: Trả lời và vẽ</w:t></w:r></w:p></w:body></w:document>";
        Path path = temp.resolve("questions.docx");

        Files.write(path, zip("word/document.xml", xml.getBytes(StandardCharsets.UTF_8)));
        var tasks =
                reader.parseQuestions(
                        reader.read(path.getFileName().toString(), Files.readAllBytes(path)));

        assertEquals(1, tasks.size());
        assertEquals("Câu hỏi", tasks.get(0).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void wordRejectsExternalEntitiesAndDecompressionBombs() throws Exception {
        String xml =
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///this-must-never-be-read'>]>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:document>";

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reader.read(
                                "xxe.docx",
                                zip("word/document.xml", xml.getBytes(StandardCharsets.UTF_8))));
        byte[] bomb = zip("word/document.xml", new byte[8 * 1024 * 1024 + 1]);

        assertTrue(bomb.length < AssignmentDocumentReader.MAX_FILE_BYTES);
        assertThrows(IllegalArgumentException.class, () -> reader.read("bomb.docx", bomb));
    }

    @Test
    void extractsRealPdfAndRejectsImageOnlyOrOverFortyPages() throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();

            pdf.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Question 1 (5 points): Analyze requirements");
                stream.newLineAtOffset(0, -20);
                stream.showText("Requirement 1 (5 points) [TEXT]: Explain actors");
                stream.endText();
            }

            Path path = temp.resolve("questions.pdf");

            pdf.save(path.toFile());
            assertEquals(
                    1,
                    reader.parseQuestions(reader.read("questions.pdf", Files.readAllBytes(path)))
                            .size());
        }

        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            pdf.save(bytes);
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> reader.read("scan.pdf", bytes.toByteArray()))
                            .getMessage()
                            .contains("OCR"));

            for (int i = 1; i < 41; i++) pdf.addPage(new PDPage());

            ByteArrayOutputStream many = new ByteArrayOutputStream();

            pdf.save(many);
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> reader.read("long.pdf", many.toByteArray()))
                            .getMessage()
                            .contains("40"));
        }
    }

    private static byte[] zip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }

        return bytes.toByteArray();
    }
}

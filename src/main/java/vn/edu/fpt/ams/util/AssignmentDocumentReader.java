package vn.edu.fpt.ams.util;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, text-only extraction. Files are never written to disk or executed. */
public class AssignmentDocumentReader {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 100_000;
    public static final int MAX_PDF_PAGES = 40;
    private static final int MAX_ZIP_BYTES = 16 * 1024 * 1024;
    private static final Pattern QUESTION = Pattern.compile(
            "^(?:Câu|Cau|Question)\\s+(\\d+)\\s*\\((\\d+(?:[.,]\\d{1,2})?)\\s*(?:điểm|diem|points?)\\)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REQUIREMENT = Pattern.compile(
            "^(?:Yêu cầu|Yeu cau|Requirement)\\s+(\\d+)\\s*\\((\\d+(?:[.,]\\d{1,2})?)\\s*(?:điểm|diem|points?)\\)\\s*(?:\\[(TEXT|DIAGRAM|BOTH)\\])?\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public String read(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw invalid("Tệp tải lên rỗng.");
        if (bytes.length > MAX_FILE_BYTES) throw invalid("Mỗi tệp được tối đa 2 MB.");
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (name.endsWith(".txt")) text = utf8(bytes);
            else if (name.endsWith(".docx")) text = docx(bytes);
            else if (name.endsWith(".pdf")) text = pdf(bytes);
            else if (name.endsWith(".xlsx") || name.endsWith(".xls")) text = AssignmentExcelReader.read(name, bytes).toString();
            else throw invalid("Chỉ hỗ trợ tệp .txt, .docx, .pdf có văn bản, .xlsx và .xls.");
            text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
            if (text.length() > MAX_TEXT_CHARS) throw invalid("Văn bản trong tệp quá dài (tối đa 100.000 ký tự).");
            if (text.isBlank()) throw invalid("Không đọc được văn bản. PDF quét ảnh cần được chuyển sang văn bản trước; hệ thống không dùng OCR.");
            return text;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw invalid("Không đọc được tệp. Hãy dùng tệp không mã hóa, đúng định dạng và thử lại."); }
    }

    public JsonArray parseQuestions(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS)
            throw invalid("Nội dung nhập không hợp lệ hoặc quá dài.");
        String clean = text.strip();
        if (clean.startsWith("[") || clean.startsWith("{")) {
            try {
                JsonElement root = JSON.fromJson(clean, JsonElement.class);
                if (root.isJsonObject()) root = root.getAsJsonObject().get("tasks");
                if (root == null || !root.isJsonArray()) throw new IllegalArgumentException();
                if (root.getAsJsonArray().isEmpty()) throw new IllegalArgumentException();
                return root.getAsJsonArray();
            } catch (RuntimeException ex) { throw invalid("JSON phải là danh sách câu hỏi hoặc đối tượng có mảng tasks hợp lệ."); }
        }
        JsonArray tasks = new JsonArray();
        JsonObject current = null;
        int expectedQuestion = 1, expectedRequirement = 1, lineNumber = 0;
        for (String raw : clean.split("\\R")) {
            lineNumber++;
            String line = raw.strip();
            if (line.isBlank()) continue;
            Matcher q = QUESTION.matcher(line), r = REQUIREMENT.matcher(line);
            if (q.matches()) {
                if (!q.group(1).equals(Integer.toString(expectedQuestion++)))
                    throw invalid("Số thứ tự câu hỏi phải liên tiếp từ 1 (dòng " + lineNumber + ").");
                if (current != null && current.getAsJsonArray("requirements").isEmpty())
                    throw invalid("Mỗi câu hỏi phải có ít nhất một yêu cầu.");
                current = new JsonObject();
                current.addProperty("task_order", tasks.size() + 1);
                current.addProperty("title", q.group(3).strip());
                current.addProperty("description", "");
                current.addProperty("maximum_points", q.group(2).replace(',', '.'));
                current.add("requirements", new JsonArray());
                tasks.add(current); expectedRequirement = 1;
                if (tasks.size() > AssignmentRules.MAX_TASKS) throw invalid("Chỉ được nhập tối đa 200 câu hỏi.");
            } else if (r.matches() && current != null) {
                if (!r.group(1).equals(Integer.toString(expectedRequirement++)))
                    throw invalid("Số thứ tự yêu cầu phải liên tiếp từ 1 trong từng câu (dòng " + lineNumber + ").");
                JsonArray requirements = current.getAsJsonArray("requirements");
                JsonObject item = new JsonObject();
                item.addProperty("requirement_order", requirements.size() + 1);
                item.addProperty("title", r.group(4).strip());
                item.addProperty("maximum_points", r.group(2).replace(',', '.'));
                item.addProperty("answer_type", r.group(3) == null ? "TEXT" : r.group(3).toUpperCase(Locale.ROOT));
                requirements.add(item);
                if (requirements.size() > AssignmentRules.MAX_REQUIREMENTS) throw invalid("Mỗi câu hỏi được tối đa 10 yêu cầu.");
            } else throw invalid("Không nhận dạng được dòng " + lineNumber + ". Hãy dùng mẫu: Câu 1 (5 điểm): ... và Yêu cầu 1 (3 điểm) [TEXT]: ...; mỗi mục trên một dòng.");
        }
        if (tasks.isEmpty() || current.getAsJsonArray("requirements").isEmpty())
            throw invalid("Tệp cần có câu hỏi và ít nhất một yêu cầu cho mỗi câu. Hãy tải mẫu nhập.");
        return tasks;
    }

    public static String template() {
        return "Câu 1 (5 điểm): Phân tích yêu cầu của hệ thống quản lý thư viện.\n"
                + "Yêu cầu 1 (3 điểm) [TEXT]: Mô tả các tác nhân và chức năng chính.\n"
                + "Yêu cầu 2 (2 điểm) [DIAGRAM]: Vẽ sơ đồ ca sử dụng (use case).\n\n"
                + "Câu 2 (5 điểm): Thiết kế giải pháp quản lý mượn sách.\n"
                + "Yêu cầu 1 (5 điểm) [BOTH]: Giải thích thiết kế và vẽ sơ đồ lớp.\n";
    }

    private static String utf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static String docx(byte[] bytes) throws Exception {
        byte[] document = null; int entries = 0, total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > 256) throw invalid("Tệp Word có quá nhiều thành phần.");
                ByteArrayOutputStream body = "word/document.xml".equals(entry.getName()) ? new ByteArrayOutputStream() : null;
                byte[] chunk = new byte[8192]; int length, entryBytes = 0;
                while ((length = zip.read(chunk)) != -1) {
                    total += length; entryBytes += length;
                    if (total > MAX_ZIP_BYTES || entryBytes > 8 * 1024 * 1024)
                        throw invalid("Tệp Word giải nén vượt giới hạn an toàn.");
                    if (body != null) {
                        if (entryBytes > 4 * 1024 * 1024) throw invalid("Nội dung Word quá dài.");
                        body.write(chunk, 0, length);
                    }
                }
                if (body != null) {
                    if (document != null) throw invalid("Tệp Word có cấu trúc không hợp lệ.");
                    document = body.toByteArray();
                }
                zip.closeEntry();
            }
        }
        if (document == null) throw invalid("Tệp .docx thiếu nội dung văn bản.");
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true); factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var parser = factory.newSAXParser();
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        StringBuilder text = new StringBuilder();
        parser.parse(new ByteArrayInputStream(document), new DefaultHandler() {
            private boolean inText;
            @Override public void startElement(String uri, String local, String name, Attributes attributes) {
                if (word(uri) && "t".equals(local)) inText = true;
                if (word(uri) && ("br".equals(local) || "cr".equals(local))) text.append('\n');
                if (word(uri) && "tab".equals(local)) text.append(' ');
            }
            @Override public void characters(char[] ch, int start, int length) {
                if (inText) text.append(ch, start, length);
                if (text.length() > MAX_TEXT_CHARS) throw invalid("Văn bản Word vượt 100.000 ký tự.");
            }
            @Override public void endElement(String uri, String local, String name) {
                if (word(uri) && "t".equals(local)) inText = false;
                if (word(uri) && "p".equals(local)) text.append('\n');
                if (text.length() > MAX_TEXT_CHARS) throw invalid("Văn bản Word vượt 100.000 ký tự.");
            }
        });
        return text.toString();
    }

    private static boolean word(String uri) {
        return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(uri)
                || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(uri);
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw invalid("Không hỗ trợ PDF được mã hóa.");
            if (document.getNumberOfPages() > MAX_PDF_PAGES) throw invalid("PDF được tối đa 40 trang.");
            StringBuilder text = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.writeText(document, new Writer() {
                @Override public void write(char[] chars, int off, int len) {
                    if (text.length() + len > MAX_TEXT_CHARS) throw invalid("Văn bản PDF vượt 100.000 ký tự.");
                    text.append(chars, off, len);
                }
                @Override public void flush() { }
                @Override public void close() { }
            });
            return text.toString();
        }
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

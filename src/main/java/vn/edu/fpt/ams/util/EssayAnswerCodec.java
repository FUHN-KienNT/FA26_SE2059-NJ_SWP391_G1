package vn.edu.fpt.ams.util;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, explicitly keyed essay answers. Diagram markup is stored, never rendered as HTML. */
public final class EssayAnswerCodec {
    public static final String FORMAT="ams-essay-v2";
    public static final int MAX_PAYLOAD=1_500_000, MAX_IMAGE=262_144, MAX_XML=150_000;
    public record Answer(int taskOrder,int requirementOrder,String text,String xml,String image) { }
    public record Content(String json,Map<String,Answer> answers) {
        public Content { answers=Map.copyOf(answers); }
    }
    private EssayAnswerCodec() { }
    public static String key(int task,int requirement) { return task+":"+requirement; }

    public static Content normalize(String taskJson,String input) {
        var tasks=EssayQuestions.read(taskJson);
        Map<String,EssayQuestions.Requirement> allowed=new LinkedHashMap<>();
        for(var task:tasks)for(var r:task.requirements())allowed.put(key(task.order(),r.order()),r);
        Map<String,Answer> answers=new LinkedHashMap<>();
        if(input!=null && !input.isBlank()) {
            if(input.getBytes(StandardCharsets.UTF_8).length>MAX_PAYLOAD)throw bad("Bài làm vượt giới hạn dung lượng 1.5 MB.");
            JsonElement parsed=strict(input);
            if(!parsed.isJsonObject())throw bad("Định dạng bài tự luận không hợp lệ.");
            var root=parsed.getAsJsonObject();
            if(!FORMAT.equals(string(root,"format")))throw bad("Bài làm cũ chưa hỗ trợ chỉnh sửa theo từng yêu cầu. Nội dung gốc được giữ nguyên.");
            var rows=root.get("answers");
            if(rows==null || !rows.isJsonArray() || rows.getAsJsonArray().size()>allowed.size())throw bad("Danh sách câu trả lời không hợp lệ.");
            for(var element:rows.getAsJsonArray()) {
                if(!element.isJsonObject())throw bad("Câu trả lời không hợp lệ.");
                var row=element.getAsJsonObject();int task=positive(row,"task_order"), requirement=positive(row,"requirement_order");
                String key=key(task,requirement);var expected=allowed.get(key);
                if(expected==null || answers.containsKey(key))throw bad("Câu trả lời bị trùng hoặc không thuộc đề bài của lượt làm này.");
                String text=string(row,"text"),xml=string(row,"diagram_xml"),image=string(row,"diagram_image");
                if(text.codePointCount(0,text.length())>10_000)throw bad("Mỗi câu trả lời tối đa 10.000 ký tự.");
                if("TEXT".equals(expected.answerType()) && (!xml.isBlank()||!image.isBlank()))throw bad("Yêu cầu này chỉ nhận câu trả lời bằng chữ.");
                if("DIAGRAM".equals(expected.answerType()) && !text.isBlank())throw bad("Yêu cầu này chỉ nhận sơ đồ.");
                if(xml.isBlank()!=image.isBlank())throw bad("Sơ đồ phải có cả dữ liệu chỉnh sửa và ảnh xem trước.");
                if(!xml.isBlank()) { validateXml(xml); validateImage(image); }
                answers.put(key,new Answer(task,requirement,text,xml,image));
            }
        }
        JsonArray rows=new JsonArray();
        for(var task:tasks)for(var r:task.requirements()) {
            String k=key(task.order(),r.order());
            var answer=answers.computeIfAbsent(k,x->new Answer(task.order(),r.order(),"","",""));
            JsonObject row=new JsonObject();row.addProperty("task_order",answer.taskOrder());row.addProperty("requirement_order",answer.requirementOrder());
            row.addProperty("text",answer.text());row.addProperty("diagram_xml",answer.xml());row.addProperty("diagram_image",answer.image());rows.add(row);
        }
        JsonObject root=new JsonObject();root.addProperty("format",FORMAT);root.add("answers",rows);
        String json=root.toString();if(json.getBytes(StandardCharsets.UTF_8).length>MAX_PAYLOAD)throw bad("Bài làm vượt giới hạn dung lượng 1.5 MB.");
        return new Content(json,answers);
    }

    public static void validateImage(String image) {
        String prefix="data:image/png;base64,";
        if(!image.startsWith(prefix)||image.length()>MAX_IMAGE*4/3+prefix.length()+8)throw bad("Ảnh sơ đồ phải là PNG và không quá 256 KB.");
        try {
            byte[] bytes=Base64.getDecoder().decode(image.substring(prefix.length()));
            if(bytes.length>MAX_IMAGE||bytes.length<24||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw bad("Ảnh sơ đồ PNG không hợp lệ.");
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw bad("Ảnh sơ đồ PNG không hợp lệ.");
                var reader=readers.next();try {
                    reader.setInput(stream,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(!"png".equalsIgnoreCase(reader.getFormatName())||w<1||h<1||w>4096||h>4096||(long)w*h>8_000_000)throw bad("Ảnh sơ đồ quá lớn, hãy thu nhỏ trước khi lưu.");
                    if(reader.read(0)==null)throw bad("Ảnh sơ đồ PNG không hợp lệ.");
                } finally {reader.dispose();}
            }
        } catch(IOException|IllegalArgumentException ex) {throw bad("Ảnh sơ đồ PNG không hợp lệ hoặc vượt giới hạn dung lượng.");}
    }
    public static void validateXml(String xml) {
        if(xml.length()>MAX_XML)throw bad("Dữ liệu sơ đồ quá lớn.");
        try {
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            var builder=factory.newDocumentBuilder();builder.setErrorHandler(new DefaultHandler());
            var doc=builder.parse(new InputSource(new StringReader(xml)));String root=doc.getDocumentElement().getTagName();
            if(!Set.of("mxfile","mxGraphModel").contains(root))throw bad("Dữ liệu sơ đồ draw.io không hợp lệ.");
        } catch(Exception ex) {throw bad("Dữ liệu sơ đồ draw.io không hợp lệ.");}
    }
    private static int positive(JsonObject row,String key) {
        var v=row.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!v.getAsString().matches("[1-9][0-9]{0,3}"))throw bad("Tham chiếu câu trả lời không hợp lệ.");
        return v.getAsInt();
    }
    private static String string(JsonObject row,String key) {
        var value=row.get(key);if(value==null||value.isJsonNull())return "";
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw bad("Nội dung câu trả lời không hợp lệ.");
        return value.getAsString();
    }
    private static JsonElement strict(String input) {
        try(var reader=new JsonReader(new StringReader(input))) {
            reader.setStrictness(Strictness.STRICT);names(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw bad("JSON bài làm không hợp lệ.");
            return JsonParser.parseString(input);
        } catch(IOException|RuntimeException ex) {throw bad("JSON bài làm không hợp lệ hoặc chứa trường trùng.");}
    }
    private static void names(JsonReader r,int depth)throws IOException {
        if(depth>16)throw bad("Bài làm lồng quá nhiều cấp.");
        switch(r.peek()) {
            case BEGIN_OBJECT->{r.beginObject();Set<String> names=new HashSet<>();while(r.hasNext()){if(!names.add(r.nextName()))throw bad("Trường bị trùng.");names(r,depth+1);}r.endObject();}
            case BEGIN_ARRAY->{r.beginArray();while(r.hasNext())names(r,depth+1);r.endArray();}
            case STRING,NUMBER->r.nextString();case BOOLEAN->r.nextBoolean();case NULL->r.nextNull();default->throw bad("JSON không hợp lệ.");
        }
    }
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}

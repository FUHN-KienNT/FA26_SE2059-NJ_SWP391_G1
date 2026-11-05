package vn.edu.fpt.ams.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.*;
import vn.edu.fpt.ams.util.EssayAnswerCodec;
import vn.edu.fpt.ams.util.PlantUmlSource;

/** A bounded child JVM isolates renderer CPU/memory and SANDBOX blocks file/network access. */
public class PlantUmlRenderer {
    private static final Semaphore SLOTS = new Semaphore(2);
    public String render(String source) {
        String normalized = PlantUmlSource.normalize(source);
        if (!SLOTS.tryAcquire()) throw new IllegalStateException("Trình vẽ đang bận. Vui lòng thử lại sau vài giây.");
        Process process = null;
        ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "plantuml-output"); thread.setDaemon(true); return thread;
        });
        try {
            Path jar = Path.of(net.sourceforge.plantuml.SourceStringReader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            ProcessBuilder builder = new ProcessBuilder(java, "-Xmx128m", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                    "-DPLANTUML_SECURITY_PROFILE=SANDBOX", "-DPLANTUML_LIMIT_SIZE=4096", "-jar", jar.toString(),
                    "-pipe", "-tpng", "-charset", "UTF-8", "-nometadata", "-failfast2");
            // Rendering must not inherit application credentials or JVM override variables.
            String systemRoot = builder.environment().get("SystemRoot");
            builder.environment().clear();
            if (systemRoot != null) { builder.environment().put("SystemRoot", systemRoot); builder.environment().put("WINDIR", systemRoot); }
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start(); final Process child = process;
            Future<byte[]> output = reader.submit(() -> {
                try (InputStream stream = child.getInputStream()) {
                    byte[] bytes = stream.readNBytes(EssayAnswerCodec.MAX_IMAGE + 1);
                    if (bytes.length > EssayAnswerCodec.MAX_IMAGE) {
                        child.destroyForcibly(); throw new IllegalArgumentException("Sơ đồ quá lớn. Hãy giảm nội dung trước khi lưu.");
                    }
                    return bytes;
                }
            });
            try (OutputStream input = process.getOutputStream()) {
                input.write(normalized.replace("@startuml\n", "@startuml\n!pragma layout smetana\n").getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(15, TimeUnit.SECONDS))
                throw new IllegalArgumentException("Sơ đồ quá phức tạp hoặc tạo ảnh quá lâu. Hãy rút gọn rồi thử lại.");
            byte[] bytes = output.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IllegalArgumentException("Mã PlantUML chưa đúng cú pháp. Hãy kiểm tra và tạo lại ảnh xem trước.");
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            EssayAnswerCodec.validateImage(image);
            return image;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Đã dừng tạo sơ đồ. Vui lòng thử lại.", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IllegalArgumentException) throw (IllegalArgumentException) ex.getCause();
            throw new IllegalStateException("Chưa tạo được ảnh sơ đồ. Vui lòng thử lại.", ex);
        } catch (Exception ex) { throw new IllegalStateException("Trình vẽ PlantUML tạm thời không khả dụng.", ex); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); reader.shutdownNow(); SLOTS.release(); }
    }
}

package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import vn.edu.fpt.ams.util.EssayAnswerCodec;

class PlantUmlRendererTest {
    @Test
    @Timeout(60)
    void sequenceClassAndUseCaseRenderIntoRealBoundedPngWithoutGraphvizInstallation()
            throws Exception {
        var renderer = new PlantUmlRenderer();

        for (String body :
                List.of(
                        "actor SinhVien\nSinhVien -> HeThong : Nộp bài\nHeThong --> SinhVien : Đã nhận",
                        "class Student {\n  +submit()\n}\nclass Assignment\nStudent --> Assignment",
                        "actor \"Sinh viên\" as SV\nusecase \"Nộp bài\" as Submit\nSV --> Submit")) {
            String image = renderer.render("@startuml\n" + body + "\n@enduml");

            EssayAnswerCodec.validateImage(image);
            byte[] bytes =
                    Base64.getDecoder().decode(image.substring("data:image/png;base64,".length()));
            var decoded = ImageIO.read(new ByteArrayInputStream(bytes));

            assertNotNull(decoded);
            assertTrue(decoded.getWidth() > 20);
            assertTrue(decoded.getHeight() > 20);
        }
    }

    @Test
    @Timeout(25)
    void syntaxFailureDoesNotReturnAnErrorPictureAsStudentWork() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlantUmlRenderer()
                                .render(
                                        "@startuml\nthis is not valid plantuml syntax ???\n@enduml"));
    }

    @Test
    void externalIncludesAreRejectedBeforeStartingARender() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlantUmlRenderer()
                                .render(
                                        "@startuml\n!include https://example.test/external\n@enduml"));
    }
}

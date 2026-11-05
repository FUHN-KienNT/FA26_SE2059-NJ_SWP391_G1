package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlSourceTest {
    @Test void plainBodyAndOneWrappedDiagramPreserveVietnameseText() {
        String body = "SinhVien -> HeThong : Nộp bài\nHeThong --> SinhVien : Đã nhận";
        String normalized = PlantUmlSource.normalize(body);
        assertTrue(normalized.contains(body));
        assertEquals(normalized, PlantUmlSource.normalize(normalized));
        assertTrue(PlantUmlSource.normalize("@startuml\n" + body + "\n@enduml").contains(body));
    }

    @Test void sourceCannotLoadExternalContentOrExecutePreprocessorFunctions() {
        for (String body : List.of("!include https://example.test/diagram", "!include /etc/passwd",
                "!include <C4/C4_Container>", "!pragma layout dot", "!$secret = %getenv(\"PATH\")",
                "title %load_json(\"https://example.test\")", "A : <img:https://example.test/image.png>",
                "@startditaa\n+---+\n@endditaa", "@startuml\nA -> B\n@enduml\n@startuml\nC -> D\n@enduml"))
            assertThrows(IllegalArgumentException.class, () -> PlantUmlSource.normalize(body), body);
    }

    @Test void oversizedEmptyAndExcessiveLineInputsAreRejected() {
        for (String source : List.of("", " \r\n ", "@startuml\n@enduml", "A".repeat(20_001), "A -> B\n".repeat(301)))
            assertThrows(IllegalArgumentException.class, () -> PlantUmlSource.normalize(source));
    }
}

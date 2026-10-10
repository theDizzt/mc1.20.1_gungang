package dev.gungang.acoustics.client;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponTextTest {
    @Test void jsonNewlinesAndEmptyParagraphsArePreserved() {
        String text=JsonParser.parseString("{\"description\":\"첫 줄\\n\\n둘째 줄\\r\\n끝\\n\"}").getAsJsonObject().get("description").getAsString();
        assertEquals(List.of("첫 줄","","둘째 줄","끝",""),WeaponText.lines(text));
    }
    @Test void doubleEscapedNewlinesAndWindowsLineEndingsAreHandled() {
        assertEquals(List.of("A","B","C","D"),WeaponText.lines("A\\nB\\r\\nC\rD"));
        assertEquals(List.of(""),WeaponText.lines(""));
        assertEquals(List.of("일반 설명"),WeaponText.lines("일반 설명"));
    }
}

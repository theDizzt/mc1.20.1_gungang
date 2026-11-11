package dev.gungang.acoustics.client;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every Korean literal the client passes to Lang.tr / Lang.format has an English entry in hud_en.json. */
class HudEnglishTableTest {
    private static final Pattern CALL = Pattern.compile("Lang\\.(?:tr|format)\\((\"(?:[^\"\\\\]|\\\\.)*\")");
    private static final Pattern HANGUL = Pattern.compile("[\\uac00-\\ud7a3\\u3131-\\u318e]");

    @Test void everyTranslatedLiteralHasAnEnglishEntry() throws Exception {
        var table = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/gungang_acoustics/lang/hud_en.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var missing = new TreeSet<String>();
        try (var files = Files.list(Path.of("src/main/java/dev/gungang/acoustics/client"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = JsonParser.parseString(matcher.group(1)).getAsString().strip();
                    if (HANGUL.matcher(key).find() && !table.has(key)) missing.add(file.getFileName() + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "hud_en.json has no entry for:\n" + String.join("\n", missing));
        for (var entry : table.entrySet())
            assertFalse(HANGUL.matcher(entry.getValue().getAsString()).find(), "Korean left in the English value of " + entry.getKey());
    }
}

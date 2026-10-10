package dev.gungang.acoustics.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFontsTest {
    @TempDir Path directory;

    @Test void bundledFontsCoverLatinAndKorean() throws Exception {
        var latin = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, Path.of("font/Galmuri11-Bold.ttf").toFile());
        var korean = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, Path.of("font/Galmuri11-Bold.ttf").toFile());
        assertTrue(latin.canDisplay('A'));
        assertTrue(latin.canDisplay('0'));
        assertTrue(korean.canDisplay('한'));
        assertTrue(korean.canDisplay('글'));
    }

    @Test void emptyFolderUsesBundledFontAndCreatesDirectory() throws IOException {
        Path fonts = directory.resolve("fonts");
        assertNull(ExternalFonts.load(fonts, ""));
        assertTrue(Files.isDirectory(fonts));
    }
    @Test void migratesLegacyFontsWithoutOverwritingOrDeleting() throws IOException {
        Files.createDirectories(directory.resolve("fonts"));
        Files.writeString(directory.resolve("fonts/a.ttf"), "legacy");
        Path target = directory.resolve("gungang/fonts");
        Files.createDirectories(target);
        Files.writeString(target.resolve("a.ttf"), "new");
        Files.writeString(directory.resolve("fonts/b.ttf"), "copy");
        assertEquals(target, ExternalFonts.prepareDirectory(directory));
        assertEquals("new", Files.readString(target.resolve("a.ttf")));
        assertEquals("copy", Files.readString(target.resolve("b.ttf")));
        assertTrue(Files.exists(directory.resolve("fonts/b.ttf")));
    }

    @Test void loadsRealTtfWithUnicodeFilenameAndAutomaticSelection() throws IOException {
        byte[] expected = Files.readAllBytes(Path.of("font/Galmuri11-Bold.ttf"));
        Files.write(directory.resolve("한글 폰트.TTF"), expected);
        assertArrayEquals(expected, ExternalFonts.load(directory, ""));
        assertArrayEquals(expected, ExternalFonts.load(directory, "한글 폰트.TTF"));
        Files.writeString(directory.resolve("0-invalid.ttf"), "not a font");
        assertThrows(IOException.class, () -> ExternalFonts.load(directory, ""));
        assertArrayEquals(expected, ExternalFonts.load(directory, "한글 폰트.TTF"));
    }

    @Test void rejectsPathsMissingFilesAndCorruptFonts() throws IOException {
        for (String name : new String[]{"../outside.ttf", "C:\\font.ttf", "sub/font.ttf", "font.otf"}) {
            assertThrows(IllegalArgumentException.class, () -> ExternalFonts.filename(name));
        }
        assertThrows(IOException.class, () -> ExternalFonts.load(directory, "missing.ttf"));
        Files.writeString(directory.resolve("broken.ttf"), "invalid");
        assertThrows(IOException.class, () -> ExternalFonts.load(directory, "broken.ttf"));
    }
}

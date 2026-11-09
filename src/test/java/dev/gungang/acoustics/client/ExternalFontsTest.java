package dev.gungang.acoustics.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFontsTest {
    @TempDir Path directory;

    @Test void gungangPresetMatchesHangulToLatinCapitalHeight() throws Exception {
        var gungang = Path.of("font/gungang_hangul.ttf");
        var font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, gungang.toFile());
        for (char glyph : "한글가힣AZ09".toCharArray()) assertTrue(font.canDisplay(glyph));
        double size = dev.gungang.acoustics.FontPreset.GUNGANG.size;
        double capital = inkHeight(gungang, "A", size);
        assertEquals(7.0, capital, .01); // one GUNGANG pixel per GUI pixel
        for (String glyph : new String[]{"한", "글", "가"}) assertEquals(capital, inkHeight(gungang, glyph, size), .01);
    }

    /** Minecraft's STB pixel-height scale uses the hhea ascent minus descent. */
    private static double inkHeight(Path path, String text, double size) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        var data = java.nio.ByteBuffer.wrap(bytes);
        int units=0, height=0;
        for (int i=0; i<Short.toUnsignedInt(data.getShort(4)); i++) {
            int table=12+16*i, offset=data.getInt(table+8);
            String tag=new String(bytes,table,4,java.nio.charset.StandardCharsets.US_ASCII);
            if (tag.equals("head")) units=Short.toUnsignedInt(data.getShort(offset+18));
            if (tag.equals("hhea")) height=data.getShort(offset+4)-data.getShort(offset+6);
        }
        assertTrue(units>0 && height>0);
        var font=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,path.toFile()).deriveFont((float)units);
        var context=new java.awt.font.FontRenderContext(null,false,false);
        return font.createGlyphVector(context,text).getVisualBounds().getHeight()*size/height;
    }

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

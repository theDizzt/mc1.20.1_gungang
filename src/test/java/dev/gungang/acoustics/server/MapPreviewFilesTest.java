package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
class MapPreviewFilesTest {
    @TempDir Path temp;
    @Test void rejectsPathsAndOversizedImages()throws Exception {
        assertTrue(MapPreviewFiles.validName("iljang.png"));assertTrue(MapPreviewFiles.validName("사찰.png"));
        for(String invalid:new String[]{"../secret.png","C:\\image.png","a/b.png","bad.jpg"})assertFalse(MapPreviewFiles.validName(invalid));
        byte[] bytes=new byte[33];ByteBuffer.wrap(bytes).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(2049).putInt(10);
        assertThrows(java.io.IOException.class,()->MapPreviewFiles.dimensions(bytes));
        assertThrows(java.io.IOException.class,()->MapPreviewFiles.read(temp,"../secret.png"));
    }
    @Test void readsPngAndKeepsExistingMapsCompatible()throws Exception {
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII=");
        Files.write(temp.resolve("iljang.png"),png);assertArrayEquals(png,MapPreviewFiles.read(temp,"iljang.png"));
        assertArrayEquals(new int[]{1,1},MapPreviewFiles.dimensions(png));
        var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();json.remove("previewImage");
        assertEquals("",ServerFiles.GSON.fromJson(json,ArenaMap.class).validate().previewImage());
        json.addProperty("previewImage","iljang.png");assertEquals("iljang.png",ServerFiles.GSON.fromJson(json,ArenaMap.class).validate().previewImage());
    }
}

package dev.gungang.acoustics.client;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimerFontTest {
    @Test void timerAtlasCentersEveryGlyphIncludingOneAndColon() throws Exception {
        var atlas=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/gungang_acoustics/textures/gui/timer.png").toFile());
        assertEquals(330,atlas.getWidth()); assertEquals(48,atlas.getHeight());
        for(int glyph=0;glyph<11;glyph++) {
            int left=30,right=-1;
            for(int y=0;y<48;y++) for(int x=0;x<30;x++) {
                int pixel=atlas.getRGB(glyph*30+x,y);
                if((pixel>>>24)>128&&(pixel&0xFFFFFF)!=0) { left=Math.min(left,x);right=Math.max(right,x); }
            }
            assertTrue(right>=left,"Missing glyph "+glyph);
            assertEquals(14.5,(left+right)/2.0,1.0,"Off-center glyph "+glyph);
            assertTrue(left>0&&right<29,"Clipped glyph "+glyph);
        }
    }
    @Test void bundledTimerDigitsAreMonospaced() throws Exception {
        var font=Font.createFont(Font.TRUETYPE_FONT,Path.of("font/advanced_pixel_lcd-7.ttf").toFile()).deriveFont(18f);
        var context=new FontRenderContext(null,true,true);
        double width=font.getStringBounds("0",context).getWidth();
        for(char digit='0';digit<='9';digit++) {
            assertTrue(font.canDisplay(digit));
            assertEquals(width,font.getStringBounds(String.valueOf(digit),context).getWidth(),0.001);
        }
        assertTrue(font.canDisplay(':'));
    }
}

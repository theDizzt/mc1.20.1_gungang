package dev.gungang.acoustics.client;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimerFontTest {
    @Test void timerAtlasCentersEveryGlyphIncludingOneAndColon() throws Exception {
        var atlas=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/gungang_acoustics/textures/gui/timer.png").toFile());
        int w=atlas.getWidth()/11,h=atlas.getHeight();
        assertEquals(w*11,atlas.getWidth()); assertEquals(w*8,h*5); assertTrue(w>=60,"Timer atlas resolution");
        for(int glyph=0;glyph<11;glyph++) {
            int left=w,right=-1;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int pixel=atlas.getRGB(glyph*w+x,y);
                if((pixel>>>24)>128&&(pixel&0xFFFFFF)!=0) { left=Math.min(left,x);right=Math.max(right,x); }
            }
            assertTrue(right>=left,"Missing glyph "+glyph);
            assertEquals((w-1)/2.0,(left+right)/2.0,w/30.0,"Off-center glyph "+glyph);
            assertTrue(left>0&&right<w-1,"Clipped glyph "+glyph);
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

package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** LCD font baked into optically centered cells; no TTF bearings or scissor clipping. */
final class MatchTimerHud {
    private static final ResourceLocation ATLAS = new ResourceLocation(GungangAcoustics.ID,"textures/gui/timer.png");
    static void draw(GuiGraphics g,String text,int y) {
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,y,0);g.pose().scale(.8f,.8f,1);
        drawAt(g,text,0,0);g.pose().popPose();
    }
    static void drawAt(GuiGraphics g,String text,float centerX,int y) {
        g.pose().pushPose();
        g.pose().translate(Math.round(centerX-text.length()*5),y,0);
        g.pose().scale(1/3f,1/3f,1);
        // Soft drop shadow one GUI pixel down-right (3 atlas pixels) replaces the old backing box.
        RenderSystem.enableBlend();
        g.setColor(0,0,0,.6f);
        for(int i=0;i<text.length();i++) {
            int glyph="0123456789:".indexOf(text.charAt(i));
            if(glyph>=0)g.blit(ATLAS,i*30+3,3,glyph*30,0,30,48,330,48);
        }
        for(int row=0;row<48;row+=3) {
            float t=Math.max(0,Math.min(1,(row-6)/36f));
            g.setColor((180-125*t)/255f,(255-60*t)/255f,(170-125*t)/255f,1);
            for(int i=0;i<text.length();i++) {
                int glyph="0123456789:".indexOf(text.charAt(i));
                if(glyph>=0)g.blit(ATLAS,i*30,row,glyph*30,row,30,3,330,48);
            }
        }
        g.setColor(1,1,1,1);
        g.pose().popPose();
    }
    private MatchTimerHud() { }
}

package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MatchReport;
import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Match clock. Default: GUNGANG-font display with a glow, HUD brackets and a per-digit change animation.
 * The {@code lcdTimer} HUD option restores the LCD font baked into optically centered cells.
 */
final class MatchTimerHud {
    private static final ResourceLocation ATLAS = new ResourceLocation(GungangAcoustics.ID,"textures/gui/timer.png");
    /** Final-countdown pop: each new number starts this much larger and settles over POP_SECONDS. */
    private static final float POP_SCALE=.6f,POP_SECONDS=.35f;
    /** Opacity of the unlit "8" segments behind each digit. */
    private static final float GHOST=.13f;
    /** Atlas pixels per 1/3 HUD pixel: cells are 30*S x 48*S, sampled with linear filtering (timer.png.mcmeta). */
    private static final int S=2,CW=30*S,CH=48*S,AW=CW*11;
    private static int lastSecond=-1;
    private static long changedAt;

    /** Bomb rounds always count down; other modes report it via {@code countdown}. */
    static boolean finalCountdown(MatchSnapshot state) {
        return (state.countdown()||state.bomb()!=null)&&state.seconds()>=0&&state.seconds()<=10;
    }
    static void draw(GuiGraphics g,MatchSnapshot state,int y) {
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,y,0);g.pose().scale(.8f,.8f,1);
        drawAt(g,state,0,0);g.pose().popPose();
    }
    /** Normal mm:ss clock, or a bare "10, 9, 8 ..." in warm colours with a scale-down pop per second. */
    static void drawAt(GuiGraphics g,MatchSnapshot state,float centerX,int y) {drawAt(g,state,centerX,y,false);}
    /** {@code brackets}: corner brackets around the GUNGANG-font clock; the LCD clock ignores it. */
    static void drawAt(GuiGraphics g,MatchSnapshot state,float centerX,int y,boolean brackets) {
        if(!AcousticConfig.LCD_TIMER.get()){FutureTimer.draw(g,state,centerX,y,brackets);return;}
        int second=state.seconds();long now=System.nanoTime();
        if(second!=lastSecond){lastSecond=second;changedAt=now;}
        float elapsed=(now-changedAt)/1e9f;
        boolean countdown=finalCountdown(state);
        if(!countdown) {
            // Colon stays lit for the first half of each second, so the blink follows the clock.
            glyphs(g,MatchReport.time(second),Math.round(centerX-MatchReport.time(second).length()*5),y,false,elapsed<.5f?1:.3f);
            return;
        }
        float t=Math.min(1,elapsed/POP_SECONDS),settle=1-(1-t)*(1-t)*(1-t);
        float scale=1+POP_SCALE*(1-settle);
        String text=String.valueOf(second);
        // Scale about the glyph centre (cells are 10 wide, 16 tall) so the number shrinks in place.
        g.pose().pushPose();
        g.pose().translate(centerX,y+8,0);g.pose().scale(scale,scale,1);
        glyphs(g,text,-text.length()*5,-8,true,1);
        g.pose().popPose();
    }
    static void drawAt(GuiGraphics g,String text,float centerX,int y) {
        if(!AcousticConfig.LCD_TIMER.get()){FutureTimer.draw(g,text,centerX,y);return;}
        glyphs(g,text,Math.round(centerX-text.length()*5),y,false,1);
    }
    private static void glyphs(GuiGraphics g,String text,float x,float y,boolean warm,float colon) {
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        g.pose().scale(1f/(3*S),1f/(3*S),1);
        // Soft drop shadow one GUI pixel down-right (3*S atlas pixels) replaces the old backing box.
        RenderSystem.enableBlend();
        for(int i=0;i<text.length();i++) {
            int glyph="0123456789:".indexOf(text.charAt(i));
            g.setColor(0,0,0,.6f*(glyph==10?colon:1));
            if(glyph>=0)g.blit(ATLAS,i*CW+shift(glyph)+3*S,3*S,glyph*CW,0,CW,CH,AW,CH);
        }
        // Unlit LCD segments: a faint "8" (or colon) behind every cell, like a real seven-segment display.
        for(int i=0;i<text.length();i++) {
            int glyph="0123456789:".indexOf(text.charAt(i));if(glyph<0)continue;
            g.setColor(1,1,1,GHOST);
            g.blit(ATLAS,i*CW,0,(glyph==10?10:8)*CW,0,CW,CH,AW,CH);
        }
        // The atlas outline is black, so tinting only recolours the glyph body.
        for(int row=0;row<CH;row+=S) {
            float t=Math.max(0,Math.min(1,(row-6*S)/(36f*S)));
            float r,gr,b;
            if(warm){r=1;gr=(235-115*t)/255f;b=(110-90*t)/255f;} // yellow top -> orange bottom
            else{r=(255-59*t)/255f;gr=(255-49*t)/255f;b=(255-37*t)/255f;} // white top -> cool grey bottom
            for(int i=0;i<text.length();i++) {
                int glyph="0123456789:".indexOf(text.charAt(i));
                g.setColor(r,gr,b,glyph==10?colon:1);
                if(glyph>=0)g.blit(ATLAS,i*CW+shift(glyph),row,glyph*CW,row,CW,S,AW,CH);
            }
        }
        g.setColor(1,1,1,1);
        g.pose().popPose();
    }
    /** The "1" sits centred in its atlas cell (columns 8-20); a real LCD lights the right-hand segments,
     *  so move it onto the "8"'s right edge (column 27). Atlas pixels. */
    private static int shift(int glyph){return glyph==1?7*S:0;}
    private MatchTimerHud() { }
}

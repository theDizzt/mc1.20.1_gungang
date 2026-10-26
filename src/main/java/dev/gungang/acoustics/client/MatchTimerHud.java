package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MatchReport;
import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** LCD font baked into optically centered cells; no TTF bearings or scissor clipping. */
final class MatchTimerHud {
    private static final ResourceLocation ATLAS = new ResourceLocation(GungangAcoustics.ID,"textures/gui/timer.png");
    /** Final-countdown pop: each new number starts this much larger and settles over POP_SECONDS. */
    private static final float POP_SCALE=.6f,POP_SECONDS=.35f;
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
    static void drawAt(GuiGraphics g,MatchSnapshot state,float centerX,int y) {
        int second=state.seconds();long now=System.nanoTime();
        if(second!=lastSecond){lastSecond=second;changedAt=now;}
        float elapsed=(now-changedAt)/1e9f;
        boolean countdown=finalCountdown(state);
        capsule(g,centerX,y,countdown);
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
        glyphs(g,text,Math.round(centerX-text.length()*5),y,false,1);
    }
    /** Fixed "00:00"-wide plate behind the digits so the frame never jumps when the countdown starts. */
    private static void capsule(GuiGraphics g,float centerX,int y,boolean warm) {
        int cx=Math.round(centerX),x1=cx-30,x2=cx+30,y1=y-3,y2=y+19;
        g.fill(x1-1,y1-1,x2+1,y2+1,DeathmatchHud.EDGE);g.fill(x1,y1,x2,y2,DeathmatchHud.SURFACE);
        g.fill(x1,y1,x2,y1+1,0x14FFFFFF);
        g.fill(x1,y2-1,x2,y2,warm?0xFFFF9A3C:TacticalStyle.GOLD);
        TacticalStyle.corner(g,x2,y1,3,warm?0xFFFF9A3C:TacticalStyle.GOLD);
    }
    private static void glyphs(GuiGraphics g,String text,float x,float y,boolean warm,float colon) {
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        g.pose().scale(1/3f,1/3f,1);
        // Soft drop shadow one GUI pixel down-right (3 atlas pixels) replaces the old backing box.
        RenderSystem.enableBlend();
        for(int i=0;i<text.length();i++) {
            int glyph="0123456789:".indexOf(text.charAt(i));
            g.setColor(0,0,0,.6f*(glyph==10?colon:1));
            if(glyph>=0)g.blit(ATLAS,i*30+3,3,glyph*30,0,30,48,330,48);
        }
        // The atlas outline is black, so tinting only recolours the glyph body.
        for(int row=0;row<48;row+=3) {
            float t=Math.max(0,Math.min(1,(row-6)/36f));
            float r,gr,b;
            if(warm){r=1;gr=(235-115*t)/255f;b=(110-90*t)/255f;} // yellow top -> orange bottom
            else{r=(255-59*t)/255f;gr=(255-49*t)/255f;b=(255-37*t)/255f;} // white top -> cool grey bottom
            for(int i=0;i<text.length();i++) {
                int glyph="0123456789:".indexOf(text.charAt(i));
                g.setColor(r,gr,b,glyph==10?colon:1);
                if(glyph>=0)g.blit(ATLAS,i*30,row,glyph*30,row,30,3,330,48);
            }
        }
        g.setColor(1,1,1,1);
        g.pose().popPose();
    }
    private MatchTimerHud() { }
}

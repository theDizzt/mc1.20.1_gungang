package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel-stepped white frame, recessed clock and scores below the inner wing tips. */
final class MatchRibbonHud {
    static final int HEIGHT=47;
    static float scale(GuiGraphics g){return .75f*Math.min(1f,(g.guiWidth()-16)/360f);}
    static void outlined(GuiGraphics g,String text,int cx,int y,float size,int color){outlined(g,text,cx,y,size,color,0xFF000000);}
    /** Centred text with a one-pixel outline of {@code outline} on all eight sides. */
    static void outlined(GuiGraphics g,String text,int cx,int y,float size,int color,int outline){
        var font=Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(cx,y,0);g.pose().scale(size,size,1);
        int x=-font.width(text)/2;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
            if(dx!=0||dy!=0)g.drawString(font,text,x+dx,dy,outline,false);
        g.drawString(font,text,x,0,color,false);g.pose().popPose();
    }
    /** Soft drop shadow under the ribbon's lines and bars. */
    static final int SHADOW=0x66000000;
    /** Score bar easing per side; reset when the goal changes (a new match or mode). */
    private static final BarTween[] BARS={new BarTween(.55,.25),new BarTween(.55,.25)};
    private static final int[] GOALS={-1,-1};
    static void segments(GuiGraphics g,boolean left,int count,java.util.function.IntPredicate lit,int color){
        int start=left?-179:44,end=left?-44:179;
        g.fill(start,6,end,11,0xFF141B23);
        for(int i=0;i<count;i++){
            int x1=start+i*(end-start)/count,x2=start+(i+1)*(end-start)/count;
            g.fill(x1,6,Math.max(x1+1,x2-1),11,lit.test(i)?color|0xFF000000:0xFF454B52);
        }
    }
    static void frame(GuiGraphics g){
        // Integer stair steps keep the shoulder pixel-shaped; no dark outline behind the white line.
        // Shadow pass first so it never covers the line itself.
        for(int x=-180;x<180;x++)g.fill(x+1,frameY(x)+1,x+2,frameY(x)+2,SHADOW);
        g.fill(-179,7,-178,12,SHADOW);g.fill(180,7,181,12,SHADOW);
        for(int x=-180;x<180;x++)g.fill(x,frameY(x),x+1,frameY(x)+1,0xFFE0E5E9);
        g.fill(-180,6,-179,11,0xFFD6DDE2);g.fill(179,6,180,11,0xFFD6DDE2);
    }
    private static int frameY(int x){return 3+Math.round(17*Math.max(0,Math.min(1,(44-Math.abs(x))/17f)));}
    static void wing(GuiGraphics g,boolean left,int color,int value,int goal,String label,String note){
        // The track meets the outer white end cap on both sides.
        int x1=left?-179:44,x2=left?-44:179;
        float fraction=Math.max(0,Math.min(1,value/(float)Math.max(1,goal)));
        int side=left?0:1;var bar=BARS[side];
        if(GOALS[side]!=goal){GOALS[side]=goal;bar.reset();}
        float shown=bar.update(fraction);
        int width=x2-x1,fill=Math.round(width*shown),rgb=color|0xFF000000;
        g.fill(x1+1,7,x2+1,12,SHADOW);
        g.fill(x1,6,x2,11,0xB0141B23);
        if(fill>0){
            // Bars grow from the inner tips outward (left wing fills leftward).
            int start=left?x2-fill:x1,end=left?x2:x1+fill;
            g.fill(start,6,end,11,rgb);g.fill(start,6,end,7,0x50FFFFFF);
            float flash=bar.flash();
            if(flash>0){
                // Points just gained glow white; points lost leave a fading ghost.
                int a=Math.round(width*bar.flashLow()),b=Math.round(width*bar.flashHigh());
                int lo=left?x2-b:x1+a,hi=left?x2-a:x1+b;
                if(hi>lo)g.fill(lo,6,hi,11,Math.round((bar.rising()?190:140)*flash)<<24|0xFFFFFF);
            }
            double since=bar.since();
            if(bar.rising()&&since>=0&&since<.7){
                // A sheen runs along the bar toward its leading edge after a score.
                float t=(float)(since/.7);int pos=Math.round((fill+14)*t)-14;
                int a=Math.max(0,pos),b=Math.min(fill,pos+8);
                if(b>a)g.fill(left?x2-b:x1+a,6,left?x2-a:x1+b,11,Math.round(150*(1-t))<<24|0xFFFFFF);
            }
            g.fill(left?start:end-1,6,left?start+1:end,11,0xFFDCE5EC);
        }
        var font=Minecraft.getInstance().font;
        // Reserve the inner 30px for the score so longer names cannot overlap it.
        TeamEmblems.draw(g,left?"ALPHA":"BRAVO",left?-180:160,14,20);
        DeathmatchHud.smallMarquee(g,label,left?-156:156,16,.85f,rgb,left,90);
        DeathmatchHud.smallMarquee(g,note,left?-156:156,27,.8f,DeathmatchHud.MUTED,left,104);
        String score=String.valueOf(value);
        DeathmatchHud.score(g,left?0:1,value,score,left?-53:53,22,Math.min(2f,28f/Math.max(1,HudText.numberWidth(score))),true);
    }
    private MatchRibbonHud(){}
}

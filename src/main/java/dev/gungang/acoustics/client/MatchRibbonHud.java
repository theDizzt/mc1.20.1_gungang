package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel-stepped white frame, recessed clock and scores below the inner wing tips. */
final class MatchRibbonHud {
    static final int HEIGHT=47;
    static float scale(GuiGraphics g){return .75f*Math.min(1f,(g.guiWidth()-16)/360f);}
    static void outlined(GuiGraphics g,String text,int cx,int y,float size,int color){
        var font=Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(cx,y,0);g.pose().scale(size,size,1);
        int x=-font.width(text)/2;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
            if(dx!=0||dy!=0)g.drawString(font,text,x+dx,dy,0xFF000000,false);
        g.drawString(font,text,x,0,color,false);g.pose().popPose();
    }
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
        for(int x=-180;x<180;x++) {
            float t=Math.max(0,Math.min(1,(44-Math.abs(x))/17f));
            int y=3+Math.round(17*t);
            g.fill(x,y,x+1,y+1,0xFFE0E5E9);
        }
        g.fill(-180,6,-179,11,0xFFD6DDE2);g.fill(179,6,180,11,0xFFD6DDE2);
    }
    static void wing(GuiGraphics g,boolean left,int color,int value,int goal,String label,String note){
        // The track meets the outer white end cap on both sides.
        int x1=left?-179:44,x2=left?-44:179;
        float fraction=Math.max(0,Math.min(1,value/(float)Math.max(1,goal)));
        int fill=Math.round((x2-x1)*fraction),rgb=color|0xFF000000;
        g.fill(x1,6,x2,11,0xB0141B23);
        if(fill>0){
            int start=left?x2-fill:x1,end=left?x2:x1+fill;
            g.fill(start,6,end,11,rgb);g.fill(start,6,end,7,0x50FFFFFF);
            g.fill(left?start:end-1,6,left?start+1:end,11,0xFFDCE5EC);
        }
        var font=Minecraft.getInstance().font;
        // Reserve the inner 30px for the score so longer names cannot overlap it.
        DeathmatchHud.small(g,font.plainSubstrByWidth(label,110),left?-166:166,16,.85f,rgb,false,left);
        DeathmatchHud.small(g,font.plainSubstrByWidth(note,118),left?-166:166,27,.8f,DeathmatchHud.MUTED,false,left);
        String score=String.valueOf(value);
        DeathmatchHud.score(g,left?0:1,value,score,left?-53:53,22,Math.min(2f,28f/Math.max(1,HudText.numberWidth(score))),true);
    }
    private MatchRibbonHud(){}
}

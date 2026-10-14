package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Flat score strip: team plates either side of a free-standing, shadowed match clock. */
final class DeathmatchHud {
    static final int SURFACE=0x9E0B121C,EDGE=0xB42A3A50,MUTED=0xFF9AAABD;
    static int draw(GuiGraphics g,MatchSnapshot state) {
        int top=Math.max(2,BossBarHud.bottom+1);
        float scale=Math.min(.8f,(g.guiWidth()-12)/232f);
        var options=state.options()==null?TeamOptions.defaults():state.options();
        var bomb=state.bomb();int alpha=state.alphaScore(),bravo=state.bravoScore();
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        team(g,true,"ALPHA",options.color("ALPHA"),alpha,alpha>bravo,bomb==null?null:"ALPHA".equals(bomb.attacker()));
        team(g,false,"BRAVO",options.color("BRAVO"),bravo,bravo>alpha,bomb==null?null:"BRAVO".equals(bomb.attacker()));
        if(bomb!=null){
            small(g,"ROUND "+bomb.round()+" / "+bomb.totalRounds(),0,2,.75f,0xFFFFFFFF,true);
            MatchTimerHud.drawAt(g,MatchReport.time(state.seconds()),0,11);
        }else MatchTimerHud.drawAt(g,MatchReport.time(state.seconds()),0,7);
        g.pose().popPose();
        return top+Math.round(32*scale);
    }
    /** Attacking is null outside bomb rounds; otherwise it picks the ATK/DEF tag. */
    private static void team(GuiGraphics g,boolean left,String name,int color,int score,boolean leading,Boolean attacking) {
        int inner=36,outer=116,y1=3,y2=27,x1=left?-outer:inner,x2=left?-inner:outer;
        plate(g,x1,y1,x2,y2,color,left,leading);
        var font=Minecraft.getInstance().font;String text=String.valueOf(score);
        float size=Math.min(2f,36f/Math.max(1,font.width(text)));int width=Math.round(font.width(text)*size);
        score(g,left?0:1,score,text,(left?x2-8-width:x1+8)+width/2f,y1+12,size,leading);
        int edge=left?x1+7:x2-7;
        small(g,name,edge,y1+5,.75f,0xFFFFFFFF,false,left);
        if(attacking!=null){
            String tag=attacking?"ATK":"DEF";int tagColor=attacking?0xFFFF8A65:0xFF70CDBF,w=Math.round(font.width(tag)*.6f)+6;
            int tx=left?edge:edge-w;
            g.fill(tx,y1+14,tx+w,y1+21,(tagColor&0xFFFFFF)|0x40000000);g.fill(tx,y1+14,tx+1,y1+21,tagColor);
            small(g,tag,tx+3,y1+15,.6f,tagColor,false,true);
        }
    }
    /** Draws a score scaled about its centre so the pop grows in place; popping scores flash white. */
    static void score(GuiGraphics g,int team,int score,String text,float cx,float cy,float size,boolean bright) {
        var font=Minecraft.getInstance().font;float pop=ScorePop.scale(team,score);
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(size*pop,size*pop,1);
        g.drawString(font,text,-font.width(text)/2,-4,bright||ScorePop.popping(team)?0xFFFFFFFF:0xFFC4CEDA,false);
        g.pose().popPose();
    }
    /** Dark plate whose team tint fades from the centre outward, with a solid underline. */
    static void plate(GuiGraphics g,int x1,int y1,int x2,int y2,int color,boolean fadeLeft,boolean bright) {
        int rgb=color&0xFFFFFF,width=x2-x1,peak=bright?0x88:0x4C;
        g.fill(x1-1,y1-1,x2+1,y2+1,EDGE);g.fill(x1,y1,x2,y2,SURFACE);
        for(int i=0;i<width;i+=2) {
            float t=(fadeLeft?i:width-1-i)/(float)width;
            int a=Math.round(peak*t*t);if(a>0)g.fill(x1+i,y1,Math.min(x2,x1+i+2),y2,a<<24|rgb);
        }
        g.fill(x1,y1,x2,y1+1,0x14FFFFFF);
        g.fill(x1,y2-2,x2,y2,color|0xFF000000);
        g.fill(fadeLeft?x1:x2-2,y1,fadeLeft?x1+2:x2,y2,color|0xFF000000);
    }
    static void small(GuiGraphics g,String text,int x,int y,float size,int color,boolean centered) { small(g,text,x,y,size,color,centered,true); }
    /** Scaled label; leftAnchor=false right-aligns it to x. */
    static void small(GuiGraphics g,String text,int x,int y,float size,int color,boolean centered,boolean leftAnchor) {
        var font=Minecraft.getInstance().font;float w=font.width(text)*size;
        float sx=centered?x-w/2:leftAnchor?x:x-w;
        g.pose().pushPose();g.pose().translate(sx,y,0);g.pose().scale(size,size,1);
        g.drawString(font,text,0,0,color,true);g.pose().popPose();
    }
    private DeathmatchHud() { }
}

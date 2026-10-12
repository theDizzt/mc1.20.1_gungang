package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class DeathmatchHud {
    static int draw(GuiGraphics g,MatchSnapshot state) {
        int top=Math.max(2,BossBarHud.bottom+1);
        float scale=Math.min(.8f,(g.guiWidth()-12)/224f);
        var options=state.options()==null?TeamOptions.defaults():state.options();
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        panel(g,true,options.color("ALPHA"),state.alphaScore());
        panel(g,false,options.color("BRAVO"),state.bravoScore());
        MatchTimerHud.drawAt(g,MatchReport.time(state.seconds()),0,7);
        g.pose().popPose();
        return top+Math.round(32*scale);
    }
    private static void panel(GuiGraphics g,boolean left,int color,int score) {
        for(int y=0;y<30;y++) {
            int offset=y/2,a=47-offset,b=110-offset;
            if(y==0||y==29)span(g,left,a,b,y,0xFF465264);
            else {
                span(g,left,a,a+1,y,0xFF465264);span(g,left,b-1,b,y,0xFF465264);
                span(g,left,a+1,b-1,y,(color&0xFFFFFF)|0xDE000000);
            }
        }
        var font=Minecraft.getInstance().font;String text=String.valueOf(score);
        float scale=Math.min(1.7f,48f/Math.max(1,font.width(text)));
        g.pose().pushPose();g.pose().translate(left?-71:71,15-font.lineHeight*scale/2,0);g.pose().scale(scale,scale,1);
        g.drawString(font,text,-font.width(text)/2,0,0xFFFFFFFF,true);g.pose().popPose();
    }
    private static void span(GuiGraphics g,boolean left,int a,int b,int y,int color) {
        g.fill(left?-b:a,y,left?-a:b,y+1,color);
    }
    private DeathmatchHud() { }
}

package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.MatchSnapshot;
import dev.gungang.acoustics.server.TeamOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Two flat score plates and a radial objective meter, independent of vanilla boss bars. */
final class ControlPointHud {
    static int draw(GuiGraphics g, MatchSnapshot state) {
        var point=state.point();
        int top=Math.max(2,BossBarHud.bottom+1);
        if(point==null) return top;
        if("NORMANDY".equals(point.mode())) return normandy(g,state,top);
        var options=state.options()==null?TeamOptions.defaults():state.options();
        int alpha=options.color("ALPHA"),bravo=options.color("BRAVO");
        int tint=point.progress()>0?alpha:point.progress()<0?bravo:0xFFE2E7EF;
        float scale=Math.min(.8f,(g.guiWidth()-12)/208f);
        g.pose().pushPose();
        g.pose().translate(g.guiWidth()/2f,top,0);
        g.pose().scale(scale,scale,1);
        wing(g,true,alpha,state.alphaScore(),point.goal(),point.owner()>0);
        wing(g,false,bravo,state.bravoScore(),point.goal(),point.owner()<0);
        ring(g,0,22,22,Math.abs(point.progress()),tint,point.contested());
        g.pose().pushPose(); g.pose().translate(0,18,0); g.pose().scale(0.9f,0.9f,1);
        centered(g,Math.round(Math.abs(point.progress()))+"%",0,0,0xFFFFFFFF);
        g.pose().popPose();
        if(point.contested()) { g.fill(-6,46,6,48,0xC8FFD36A); }
        g.pose().popPose();
        return top+Math.round(48*scale);
    }
    private static int normandy(GuiGraphics g,MatchSnapshot state,int top) {
        var point=state.point();
        int active=Math.max(0,Math.min(3,point.completed()));
        int[] colors={0xFF2455FF,0xFF28D83B,0xFFFF8000,0xFFFF3333};
        // Reserve a fixed central clock; each pair grows outwards from it.
        int width=2*(36+44+6+28);
        float scale=Math.min(.8f,(g.guiWidth()-12)/(float)(width+2));
        g.pose().pushPose();
        g.pose().translate(g.guiWidth()/2f,top,0);
        g.pose().scale(scale,scale,1);
        int left=-36-(active<2?44+28:56)-6;
        for(int i=0;i<4;i++) {
            if(i==2)left=36;
            boolean current=i==active;
            int radius=current?22:14,x=left+radius;
            float progress=i<point.completed()?100:current?Math.abs(point.progress()):0;
            ring(g,x,22,radius,progress,colors[i],current&&point.contested());
            centered(g,String.valueOf((char)('A'+i)),x,current?10:18,0xFFFFFFFF);
            if(current) {
                g.pose().pushPose();g.pose().translate(x,25,0);g.pose().scale(0.9f,0.9f,1);
                centered(g,Math.round(progress)+"%",0,0,0xFFFFFFFF);g.pose().popPose();
                if(point.contested())g.fill(x-6,46,x+6,48,0xC8FFD36A);
            }
            left+=radius*2+6;
        }
        MatchTimerHud.drawAt(g,state,0,14);
        g.pose().popPose();
        return top+Math.round(48*scale);
    }
    private static void ring(GuiGraphics g,int cx,int cy,int size,float progress,int tint,boolean contested) {
        boolean large=size==22;
        int band=large?4:3,outer=size,inner=size-band,disc=inner-2;
        float fraction=Math.max(0,Math.min(1,progress/100f));
        int rgb=tint&0xFFFFFF,track=contested?0x70FFD36A:0x38FFFFFF;
        // Glass disc, a transparent gap, then a translucent track with the opaque arc on top.
        // Runs of equal colour per scanline keep the fill count low.
        for(int y=-size;y<=size;y++) {
            int run=-size,previous=0;
            for(int x=-size;x<=size+1;x++) {
                double radius=Math.sqrt(x*x+y*y);
                int color=0;
                if(radius<=disc) color=radius>disc-1?0x30FFFFFF:0x9E0B121C;
                else if(radius>=inner&&radius<=outer) {
                    double angle=(Math.atan2(x,-y)+Math.PI*2)%(Math.PI*2);
                    color=angle/(Math.PI*2)<fraction?(radius>outer-1?(rgb|0xB4000000):(rgb|0xFF000000)):track;
                }
                if(color!=previous) {
                    if(previous!=0) g.fill(cx+run,cy+y,cx+x,cy+y+1,previous);
                    run=x; previous=color;
                }
            }
        }
        if(fraction>0&&fraction<1) {
            // Bright tip marks the leading edge of the arc.
            double angle=fraction*Math.PI*2,mid=(inner+outer)/2.0;
            int tx=cx+(int)Math.round(Math.sin(angle)*mid),ty=cy-(int)Math.round(Math.cos(angle)*mid),r=large?2:1;
            g.fill(tx-r,ty-r,tx+r,ty+r,0xFFFFFFFF);
        }
    }
    private static void wing(GuiGraphics g,boolean left,int color,int score,int goal,boolean owner) {
        float fraction=Math.max(0,Math.min(1,score/(float)Math.max(1,goal)));
        int inner=27,outer=98,y1=10,y2=34,x1=left?-outer:inner,x2=left?-inner:outer;
        DeathmatchHud.plate(g,x1,y1,x2,y2,color,left,owner);
        // Score progress replaces the plate underline: dim track, team-coloured fill from the centre.
        int fill=Math.round((x2-x1)*fraction);
        g.fill(x1,y2-2,x2,y2,0xFF2A3A50);
        g.fill(left?x2-fill:x1,y2-2,left?x2:x1+fill,y2,color|0xFF000000);
        var font=Minecraft.getInstance().font;String text=String.valueOf(score);
        float size=Math.min(1.7f,30f/Math.max(1,font.width(text)));int width=Math.round(font.width(text)*size);
        DeathmatchHud.score(g,left?0:1,score,text,(left?x2-7-width:x1+7)+width/2f,y1+11,size,owner);
        int edge=left?x1+7:x2-7;
        DeathmatchHud.small(g,left?"ALPHA":"BRAVO",edge,y1+5,.7f,0xFFFFFFFF,false,left);
        DeathmatchHud.small(g,"/ "+goal,edge,y1+13,.6f,DeathmatchHud.MUTED,false,left);
    }
    private static void centered(GuiGraphics g,String text,int x,int y,int color) {
        var font=Minecraft.getInstance().font;
        g.drawString(font,text,x-font.width(text)/2,y,color,true);
    }
    private ControlPointHud() { }
}

package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.MatchSnapshot;
import dev.gungang.acoustics.server.TeamOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Two score wings and a radial objective meter, independent of vanilla boss bars. */
final class ControlPointHud {
    static int draw(GuiGraphics g, MatchSnapshot state) {
        var point=state.point();
        int top=Math.max(2,BossBarHud.bottom+1);
        if(point==null) return top;
        if("NORMANDY".equals(point.mode())) return normandy(g,point,top);
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
        if(point.contested()) { g.fill(-4,46,4,48,0xFFFFD36A); }
        g.pose().popPose();
        return top+Math.round(48*scale);
    }
    private static int normandy(GuiGraphics g,MatchSnapshot.Capture point,int top) {
        int active=Math.max(0,Math.min(3,point.completed()));
        int[] colors={0xFF2455FF,0xFF28D83B,0xFFFF8000,0xFFFF3333};
        // One 44px objective, three 28px objectives, with 6px gaps; center the whole row.
        int width=44+3*28+3*6;
        float scale=Math.min(.8f,(g.guiWidth()-12)/(float)(width+2));
        g.pose().pushPose();
        g.pose().translate(g.guiWidth()/2f,top,0);
        g.pose().scale(scale,scale,1);
        int left=-width/2;
        for(int i=0;i<4;i++) {
            boolean current=i==active;
            int radius=current?22:14,x=left+radius;
            float progress=i<point.completed()?100:current?Math.abs(point.progress()):0;
            ring(g,x,22,radius,progress,colors[i],current&&point.contested());
            centered(g,String.valueOf((char)('A'+i)),x,current?10:18,0xFFFFFFFF);
            if(current) {
                g.pose().pushPose();g.pose().translate(x,25,0);g.pose().scale(0.9f,0.9f,1);
                centered(g,Math.round(progress)+"%",0,0,0xFFFFFFFF);g.pose().popPose();
                if(point.contested())g.fill(x-4,46,x+4,48,0xFFFFD36A);
            }
            left+=radius*2+6;
        }
        g.pose().popPose();
        return top+Math.round(48*scale);
    }
    private static void ring(GuiGraphics g,int cx,int cy,int size,float progress,int tint,boolean contested) {
        int thickness=size==22?4:2;
        int inner=size-thickness-2;
        float fraction=Math.max(0,Math.min(1,progress/100f));
        // Clockwise progress starts at twelve o'clock; the dark center stays readable.
        for(int y=-size;y<=size;y++) {
            int run=-size,previous=0;
            for(int x=-size;x<=size+1;x++) {
                double radius=Math.sqrt(x*x+y*y);
                int color=radius>size?0:radius>=inner?0xFF465264:0xEB101A27;
                if(radius>=inner+1&&radius<size-1) {
                    double angle=(Math.atan2(x,-y)+Math.PI*2)%(Math.PI*2);
                    color=angle/(Math.PI*2)<fraction?tint:contested?0xFFFFD36A:0xFF465264;
                }
                if(color!=previous) {
                    if(previous!=0) g.fill(cx+run,cy+y,cx+x,cy+y+1,previous);
                    run=x; previous=color;
                }
            }
        }
    }
    private static void wing(GuiGraphics g,boolean left,int color,int score,int goal,boolean owner) {
        float fraction=Math.max(0,Math.min(1,score/(float)Math.max(1,goal)));
        int fillEnd=24+Math.round(72*fraction);
        for(int y=8;y<=36;y++) {
            int dy=y-22;
            int inner=(int)Math.ceil(Math.sqrt(24*24-dy*dy));
            int outer=96-Math.abs(dy);
            // Disjoint spans preserve the existing translucent background without layering it.
            if(y==8||y==36) span(g,left,inner,outer,y,0xFF465264);
            else {
                int a=inner+1,b=outer-2,filled=Math.max(a,Math.min(b,fillEnd));
                span(g,left,inner,a,y,0xFF465264);
                span(g,left,b,outer,y,0xFF465264);
                span(g,left,a,filled,y,(color&0xFFFFFF)|0xDE000000);
                span(g,left,filled,b,y,0xDE101A27);
            }
            if(owner) span(g,left,outer+3,outer+6,y,color);
        }
        g.pose().pushPose(); g.pose().translate(left?-57:57,16,0); g.pose().scale(1.5f,1.5f,1);
        centered(g,String.valueOf(score),0,0,0xFFFFFFFF);
        g.pose().popPose();
    }
    private static void span(GuiGraphics g,boolean left,int from,int to,int y,int color) {
        if(to<=from)return;
        g.fill(left?-to:from,y,left?-from:to,y+1,color);
    }
    private static void centered(GuiGraphics g,String text,int x,int y,int color) {
        var font=Minecraft.getInstance().font;
        g.drawString(font,text,x-font.width(text)/2,y,color,true);
    }
    private ControlPointHud() { }
}

package dev.gungang.acoustics.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared charcoal, ivory and brass surfaces. Team and warning colours remain semantic. */
final class TacticalStyle {
    static final int INK=0xFFE9E5D8,MUTED=0xFFAAA99E,DIM=0xFF74756E;
    static final int GOLD=0xFFD0B75E,PANEL=0xFF141511,EDGE=0xFF55564C,GREEN=0xFF70B58E;
    static final int TRACK=0xFF23241D,HAIRLINE=0x6655564C;
    static void frame(GuiGraphics g,int x,int y,int w,int h,int color){
        if(w<2||h<2)return;
        g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);
        g.fill(x,y+1,x+1,y+h-1,color);g.fill(x+w-1,y+1,x+w,y+h-1,color);
    }
    static void corner(GuiGraphics g,int right,int top,int size,int color){
        for(int i=0;i<size;i++)g.fill(right-size+i,top+i,right,top+i+1,color);
    }
    static void tile(GuiGraphics g,int x,int y,int w,int h,int background,int edge){
        g.fill(x,y,x+w,y+h,background);frame(g,x,y,w,h,edge);
    }
    /** Fill with the four corner pixels cut, which reads as a softly rounded rectangle at GUI scale. */
    static void round(GuiGraphics g,int x,int y,int w,int h,int color){
        if(w<3||h<3){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+1,y,x+w-1,y+h,color);g.fill(x,y+1,x+1,y+h-1,color);g.fill(x+w-1,y+1,x+w,y+h-1,color);
    }
    static void roundFrame(GuiGraphics g,int x,int y,int w,int h,int color){
        if(w<3||h<3)return;
        g.fill(x+1,y,x+w-1,y+1,color);g.fill(x+1,y+h-1,x+w-1,y+h,color);
        g.fill(x,y+1,x+1,y+h-1,color);g.fill(x+w-1,y+1,x+w,y+h-1,color);
    }
    /** Two-step drop shadow offset down and right; the overlap darkens toward the surface. */
    static void shadow(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x+1,y+2,x+w+1,y+h+2,0x26000000);g.fill(x+2,y+3,x+w+2,y+h+3,0x1A000000);
    }
    /** Raised surface: shadow, rounded edge, vertical gradient body and a faint top highlight. */
    static void surface(GuiGraphics g,int x,int y,int w,int h,int top,int bottom,int edge){
        shadow(g,x,y,w,h);round(g,x,y,w,h,edge);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,top,bottom);
        g.fill(x+2,y+1,x+w-2,y+2,0x12FFFFFF);
    }
    /** Header band: hairline accent with a heavier leading segment, a tint fading into {@code base}, then a divider. */
    static void band(GuiGraphics g,int x1,int y1,int x2,int h,int color,int base){
        g.fill(x1,y1,x2,y1+1,color);g.fill(x1,y1+1,x1+Math.max(48,(x2-x1)/4),y1+2,color);
        if(h<=3)return;
        g.fillGradient(x1,y1+2,x2,y1+h,ArenaScreen.blend(base,color,.14f),base);
        g.fill(x1,y1+h,x2,y1+h+1,HAIRLINE);
    }
    /** L-shaped sight brackets at the four corners. */
    static void brackets(GuiGraphics g,int x,int y,int w,int h,int size,int color){
        int c=Math.max(2,Math.min(size,Math.min(w,h)/4));
        g.fill(x,y,x+c,y+1,color);g.fill(x,y,x+1,y+c,color);g.fill(x+w-c,y,x+w,y+1,color);g.fill(x+w-1,y,x+w,y+c,color);
        g.fill(x,y+h-1,x+c,y+h,color);g.fill(x,y+h-c,x+1,y+h,color);g.fill(x+w-c,y+h-1,x+w,y+h,color);g.fill(x+w-1,y+h-c,x+w,y+h,color);
    }
    /** Faint layout grid for backdrops. */
    static void grid(GuiGraphics g,int x,int y,int w,int h,int step,int color){
        for(int gx=x+step;gx<x+w;gx+=step)g.fill(gx,y,gx+1,y+h,color);
        for(int gy=y+step;gy<y+h;gy+=step)g.fill(x,gy,x+w,gy+1,color);
    }
    /** Rounded track with a lit fill and faint 10px segments. */
    static void meter(GuiGraphics g,int x,int y,int w,int h,float fraction,int color){
        round(g,x,y,w,h,TRACK);
        int fill=Math.round(w*Math.max(0,Math.min(1,fraction)));
        if(fill<2)return;
        round(g,x,y,fill,h,ArenaScreen.blend(color,0xFF000000,.18f));
        if(h>=4)g.fill(x+1,y,x+fill-1,y+1,ArenaScreen.blend(color,0xFFFFFFFF,.3f));
        g.fill(x+fill-1,y,x+fill,y+h,ArenaScreen.blend(color,0xFFFFFFFF,.45f));
        for(int tick=x+10;tick<x+fill-2;tick+=10)g.fill(tick,y+1,tick+1,y+h-1,0x30000000);
    }
    /**
     * The gold progress gauge shared by the XP bar and the weapon stat bars: a see-through track with a soft outline, a
     * deep-to-bright gold gradient fill with a top highlight and a darker bottom line, a sheen sweeping across it every
     * four seconds, notches every 10% (a short mark above the half), and the part between {@code fill} and
     * {@code gained} in white (XP just earned, before the gold catches up). {@code head} draws a bright edge at the fill.
     */
    static void goldGauge(GuiGraphics g,int left,int top,int width,int height,float fill,float gained,boolean head){
        if(width<4||height<2)return;
        int right=left+width,bottom=top+height;
        fill=Math.max(0,Math.min(1,fill));gained=Math.max(fill,Math.min(1,gained));
        g.fill(left-1,top-1,right+1,bottom+1,0x50000000);
        g.fill(left,top,right,bottom,0x7023241D);
        int gold=left+Math.round(width*fill),white=left+Math.round(width*gained);
        if(white>gold)g.fill(gold,top,white,bottom,0xE0FFFFFF);
        if(gold>left){
            for(int px=left;px<gold;px++)g.fill(px,top,px+1,bottom,ArenaScreen.blend(0xFF9C7A2A,0xFFF2D27A,(px-left)/(float)Math.max(1,width)));
            g.fill(left,top,gold,top+1,0x70FFFFFF);
            if(height>=4)g.fill(left,bottom-1,gold,bottom,0x40000000);
            float phase=(net.minecraft.Util.getMillis()%4000)/1400f;
            if(phase<1){
                int c=left+Math.round((width+16)*phase)-8;
                for(int px=Math.max(left,c-6);px<Math.min(gold,c+6);px++){
                    int a=Math.round(70*(1-Math.abs(px-c)/6f));
                    if(a>0)g.fill(px,top,px+1,bottom,a<<24|0xFFFFFF);
                }
            }
            if(head&&gold<right)g.fill(gold-1,top-1,gold,bottom+1,0xFFFFFFFF);
        }
        for(int i=1;i<10;i++){
            int nx=left+width*i/10;
            g.fill(nx,top,nx+1,bottom,0x55000000);
            if(i==5)g.fill(nx,top-2,nx+1,top-1,0xA0FFFFFF);
        }
    }
    private TacticalStyle(){}
}

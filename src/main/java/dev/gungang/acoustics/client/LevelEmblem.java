package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Level badge: the tens bracket picks the outline shape, the last digit picks the colour, and the number sits inside. */
public final class LevelEmblem {
    public static final String[] SHAPES={"정사각형","원","삼각형","오각별","육각형","마름모","사각별","태양","왕관","은하수"};
    public static final String[] COLORS={"무지개","검정","빨강","주황","노랑","연두","청록","파랑","보라","흰색"};
    private static final int[] FILL={0,0xFF1C1C1C,0xFFE53935,0xFFFF8A1F,0xFFFFD83A,0xFF86DB4F,0xFF19BFAE,0xFF3A7BFF,0xFF9B5CFF,0xFFF4F4F4};
    private static final int TEXTURE=48,SAMPLES=4;
    private static final Map<Integer,ResourceLocation> CACHE=new HashMap<>();
    private static Method fontSet;
    private static Field glyphLeft,glyphRight;
    private static boolean noGlyphs;
    private static final double[][] TRIANGLE={{0,-.95},{.98,.8},{-.98,.8}};
    private static final double[][] PENTAGRAM=star(5,.98,.5),FOUR_STAR=star(4,.98,.48),SUN_RAYS=star(12,.98,.62);
    private static final double[][] HEXAGON=star(3,.93,.93);
    private static final double[][] CROWN={{-.86,.72},{-.86,-.5},{-.43,0},{0,-.75},{.43,0},{.86,-.5},{.86,.72}};

    public static int shape(int level) {return Math.min(9,(Mth.clamp(level,1,91)-1)/10);}
    public static int digit(int level) {return Mth.clamp(level,1,91)%10;}

    public static void draw(GuiGraphics g,int level,int x,int y,int size) {
        level=Mth.clamp(level,1,91);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        g.blit(texture(level),x,y,size,size,0,0,TEXTURE,TEXTURE,TEXTURE,TEXTURE);
        var font=Minecraft.getInstance().font;String text=String.valueOf(level);
        // Centre the inked digits (7 rows tall), not the advance box with its trailing letter gap.
        float[] ink=ink(font,text);float inkWidth=Math.max(1,ink[1]-ink[0]);
        float scale=Math.min(size/16f,size*.64f/inkWidth);
        // Triangle and crown have their widest room below the middle; other shapes are centred.
        float lift=switch(shape(level)){case 2->.15f;case 8->.13f;default->0;};
        g.pose().pushPose();
        g.pose().translate(x+size/2f-(ink[0]+inkWidth/2f)*scale,y+size*(.5f+lift)-3.5f*scale,0);
        g.pose().scale(scale,scale,1);
        for(int[] o:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})g.drawString(font,text,o[0],o[1],0xD0000000,false);
        g.drawString(font,text,0,0,0xFFFFFFFF,false);
        g.pose().popPose();
    }

    /**
     * Horizontal ink span {left, right} from the baked glyphs, so a TTF "1" set to the left of its
     * advance (Galmuri) still centres. Falls back to the advance width minus the 1px letter gap.
     */
    private static float[] ink(Font font,String text) {
        if(!noGlyphs)try {
            if(fontSet==null) {
                fontSet=ObfuscationReflectionHelper.findMethod(Font.class,"m_92863_",ResourceLocation.class);
                glyphLeft=ObfuscationReflectionHelper.findField(BakedGlyph.class,"f_95205_");
                glyphRight=ObfuscationReflectionHelper.findField(BakedGlyph.class,"f_95206_");
            }
            var set=(FontSet)fontSet.invoke(font,Style.DEFAULT_FONT);
            float pen=0,left=Float.MAX_VALUE,right=-Float.MAX_VALUE;
            for(int i=0;i<text.length();i++) {
                int c=text.charAt(i);var glyph=set.getGlyph(c);
                left=Math.min(left,pen+glyphLeft.getFloat(glyph));right=Math.max(right,pen+glyphRight.getFloat(glyph));
                pen+=set.getGlyphInfo(c,false).getAdvance(false);
            }
            if(left<right)return new float[]{left,right};
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            noGlyphs=true;
        }
        return new float[]{0,Math.max(1,font.width(text)-1)};
    }

    private static ResourceLocation texture(int level) {
        return CACHE.computeIfAbsent(level,l->{
            var texture=new DynamicTexture(render(l));
            var id=Minecraft.getInstance().getTextureManager().register("gungang_emblem_"+l,texture);
            texture.setFilter(true,false);return id;
        });
    }

    private static NativeImage render(int level) {
        int shape=shape(level),digit=digit(level);var image=new NativeImage(TEXTURE,TEXTURE,true);
        for(int py=0;py<TEXTURE;py++)for(int px=0;px<TEXTURE;px++) {
            double a=0,r=0,gr=0,b=0;
            for(int sy=0;sy<SAMPLES;sy++)for(int sx=0;sx<SAMPLES;sx++) {
                double x=(px+(sx+.5)/SAMPLES)/TEXTURE*2-1,y=(py+(sy+.5)/SAMPLES)/TEXTURE*2-1;
                if(!inside(shape,x,y))continue;
                boolean border=!inside(shape,x/.84,y/.84);
                int c=shape==9?galaxy(x,y,border,px,py):color(digit,x,y,border);
                a++;r+=c>>16&255;gr+=c>>8&255;b+=c&255;
            }
            if(a==0){image.setPixelRGBA(px,py,0);continue;}
            int alpha=(int)Math.round(a*255/(SAMPLES*SAMPLES));
            image.setPixelRGBA(px,py,alpha<<24|(int)(b/a)<<16|(int)(gr/a)<<8|(int)(r/a));
        }
        return image;
    }

    private static int color(int digit,double x,double y,boolean border) {
        int fill=digit==0?0xFF000000|Mth.hsvToRgb((float)Mth.positiveModulo((x+y)/4+.5,1),.78f,1):FILL[digit];
        if(border)return digit==1?0xFF6A6A6A:digit==9?0xFF9C9C9C:ArenaScreen.blend(fill,0xFF000000,.42f);
        // Soft top-down sheen so flat fills read as a badge rather than a sticker.
        return ArenaScreen.blend(fill,0xFFFFFFFF,(float)(.2*(1-(y+1)/2)));
    }

    private static int galaxy(double x,double y,boolean border,int px,int py) {
        if(border)return ArenaScreen.blend(0xFFC9B8FF,0xFF6FD8FF,(float)((x+1)/2));
        double r=Math.sqrt(x*x+y*y)/.8,theta=Math.atan2(y,x);
        double arm=Math.pow(.5+.5*Math.cos(2*(theta+4.4*r)),3)*Math.max(0,1-r*.95);
        int c=ArenaScreen.blend(0xFF0A0722,ArenaScreen.blend(0xFF9A63FF,0xFF5AD8FF,(float)Math.min(1,r)),(float)Math.min(1,arm*1.6));
        c=ArenaScreen.blend(c,0xFFFFF2D2,(float)Math.exp(-r*r/.035));
        int hash=(px*73856093^py*19349663)&1023;
        return hash<14&&r>.25?ArenaScreen.blend(c,0xFFFFFFFF,.85f):c;
    }

    private static boolean inside(int shape,double x,double y) {
        return switch(shape) {
            case 0->{double dx=Math.max(Math.abs(x)-.7,0),dy=Math.max(Math.abs(y)-.7,0);yield dx*dx+dy*dy<=.0225&&Math.abs(x)<=.85&&Math.abs(y)<=.85;}
            case 1->x*x+y*y<=.81;
            case 2->polygon(TRIANGLE,x,y);
            case 3->polygon(PENTAGRAM,x,y);
            case 4->polygon(HEXAGON,x,y);
            case 5->Math.abs(x)+Math.abs(y)<=.97;
            case 6->polygon(FOUR_STAR,x,y);
            case 7->x*x+y*y<=.36||polygon(SUN_RAYS,x,y);
            case 8->polygon(CROWN,x,y)||dot(x,y,-.86,-.6)||dot(x,y,0,-.85)||dot(x,y,.86,-.6);
            default->x*x+y*y<=.9;
        };
    }
    private static boolean dot(double x,double y,double cx,double cy) {return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=.0144;}
    private static boolean polygon(double[][] points,double x,double y) {
        boolean in=false;
        for(int i=0,j=points.length-1;i<points.length;j=i++) {
            double xi=points[i][0],yi=points[i][1],xj=points[j][0],yj=points[j][1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)in=!in;
        }
        return in;
    }
    /** Alternating outer/inner vertices, first point straight up. Equal radii give a regular 2n-gon. */
    private static double[][] star(int points,double outer,double inner) {
        var result=new double[points*2][];
        for(int i=0;i<points*2;i++) {
            double angle=-Math.PI/2+Math.PI*i/points,radius=i%2==0?outer:inner;
            result[i]=new double[]{Math.cos(angle)*radius,Math.sin(angle)*radius};
        }
        return result;
    }
    private LevelEmblem() { }
}

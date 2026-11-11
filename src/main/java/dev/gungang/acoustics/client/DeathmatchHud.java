package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Flat score strip: team plates either side of a free-standing, shadowed match clock. */
final class DeathmatchHud {
    static final int SURFACE=0xB0141511,EDGE=0xB455564C,MUTED=TacticalStyle.MUTED;
    static int draw(GuiGraphics g,MatchSnapshot state) {
        int top=Math.max(2,BossBarHud.bottom+1);
        if(state.bomb()==null) {
            float scale=MatchRibbonHud.scale(g);
            g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
            MatchRibbonHud.frame(g);
            if(FreeForAllHud.is(state))FreeForAllHud.top(g,state);
            else {
                var colors=state.options()==null?TeamOptions.defaults():state.options();
                int goal=state.point()==null?0:state.point().goal();
                MatchRibbonHud.wing(g,true,colors.color("ALPHA"),state.alphaScore(),goal,"ALPHA",goal>0?Lang.tr("목표 ")+goal:"");
                MatchRibbonHud.wing(g,false,colors.color("BRAVO"),state.bravoScore(),goal,"BRAVO",goal>0?Lang.tr("목표 ")+goal:"");
            }
            MatchTimerHud.drawAt(g,state,0,3);
            g.pose().popPose();
            return top+Math.round(MatchRibbonHud.HEIGHT*scale);
        }
        float scale=MatchRibbonHud.scale(g);
        var options=state.options()==null?TeamOptions.defaults():state.options();
        var bomb=state.bomb();
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        MatchRibbonHud.frame(g);
        for(boolean left:new boolean[]{true,false}) {
            String name=left?"ALPHA":"BRAVO";
            int color=options.color(name),score=left?state.alphaScore():state.bravoScore();
            MatchRibbonHud.wing(g,left,color,score,1,name+" · "+(name.equals(bomb.attacker())?"ATK":"DEF"),alive(state,name));
            int start=left?-179:44,end=left?-44:179;
            g.fill(start,6,end,11,0xFF141B23);
            var members=state.players().stream().filter(r->name.equals(r.team())).toList();
            var dead=bomb.deadPlayers()==null?java.util.List.<String>of():bomb.deadPlayers();
            for(int i=0;i<members.size();i++) {
                int x1=start+i*(end-start)/members.size(),x2=start+(i+1)*(end-start)/members.size();
                g.fill(x1,6,Math.max(x1+1,x2-1),11,dead.contains(members.get(i).id())?0xFF454B52:color|0xFF000000);
            }
        }
        MatchTimerHud.drawAt(g,state,0,3);
        small(g,"ROUND "+bomb.round()+" / "+bomb.totalRounds(),0,25,.8f,0xFFFFFFFF,true);
        g.pose().popPose();return top+Math.round(MatchRibbonHud.HEIGHT*scale);
    }
    /** Bomb round survivors of a team, e.g. "생존 3/5"; the dead watch until the next round. */
    private static String alive(MatchSnapshot state,String team) {
        var dead=state.bomb().deadPlayers()==null?java.util.List.<String>of():state.bomb().deadPlayers();
        var members=state.players().stream().filter(r->team.equals(r.team())).toList();
        long left=members.stream().filter(r->!dead.contains(r.id())).count();
        return Lang.tr("생존 ")+left+"/"+members.size();
    }
    /** Attacking is null outside bomb rounds; otherwise it picks the ATK/DEF tag. Note sits beside the tag (bomb survivors). */
    static void team(GuiGraphics g,boolean left,String name,int color,int score,boolean leading,Boolean attacking,String note) {
        int inner=36,outer=116,y1=3,y2=27,x1=left?-outer:inner,x2=left?-inner:outer;
        plate(g,x1,y1,x2,y2,color,left,leading);
        var font=Minecraft.getInstance().font;String text=String.valueOf(score);int numberWidth=HudText.numberWidth(text);
        float size=Math.min(2f,36f/Math.max(1,numberWidth));int width=Math.round(numberWidth*size);
        score(g,left?0:1,score,text,(left?x2-8-width:x1+8)+width/2f,y1+12,size,leading);
        int edge=left?x1+7:x2-7;
        small(g,name,edge,y1+5,.75f,0xFFFFFFFF,false,left);
        if(attacking!=null){
            String tag=attacking?"ATK":"DEF";int tagColor=attacking?0xFFFF8A65:0xFF4D9FFF,w=Math.round(font.width(tag)*.6f)+6;
            int tx=left?edge:edge-w;
            g.fill(tx,y1+14,tx+w,y1+21,(tagColor&0xFFFFFF)|0x40000000);g.fill(tx,y1+14,tx+1,y1+21,tagColor);
            small(g,tag,tx+3,y1+15,.6f,tagColor,false,true);
            if(!note.isEmpty())small(g,note,left?tx+w+3:tx-3,y1+15,.6f,note.startsWith(Lang.tr("생존 ")+"0/")?0xFFFF6B6B:0xFFFFFFFF,false,left);
        }
    }
    /** Draws a score scaled about its centre so the pop grows in place; popping scores flash white. */
    static void score(GuiGraphics g,int team,int score,String text,float cx,float cy,float size,boolean bright) {
        var font=Minecraft.getInstance().font;float pop=ScorePop.scale(team,score);
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(size*pop,size*pop,1);
        // Centre the inked digits: 7 rows tall, advance minus the trailing 1px letter gap.
        g.pose().translate(-(HudText.numberWidth(text)-1)/2f,-3.5f,0);
        HudText.drawNumber(g,HudText.number(text),0,0,bright||ScorePop.popping(team)?0xFFFFFFFF:0xFFC4CEDA);
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
        if(bright)TacticalStyle.corner(g,x2,y1,4,color|0xFF000000);
    }
    static void small(GuiGraphics g,String text,int x,int y,float size,int color,boolean centered) { small(g,text,x,y,size,color,centered,true); }
    /** {@link #small} (not centred) in a box {@code width} text pixels wide, scrolling when longer (Marquee). */
    static void smallMarquee(GuiGraphics g,String text,int x,int y,float size,int color,boolean leftAnchor,int width) {
        var font=Minecraft.getInstance().font;float w=Math.min(width,font.width(text))*size;
        g.pose().pushPose();g.pose().translate(leftAnchor?x:x-w,y,0);g.pose().scale(size,size,1);
        Marquee.draw(g,font,text,0,0,width,color,true);g.pose().popPose();
    }
    /** Scaled label; leftAnchor=false right-aligns it to x. */
    static void small(GuiGraphics g,String text,int x,int y,float size,int color,boolean centered,boolean leftAnchor) {
        var font=Minecraft.getInstance().font;float w=font.width(text)*size;
        float sx=centered?x-w/2:leftAnchor?x:x-w;
        g.pose().pushPose();g.pose().translate(sx,y,0);g.pose().scale(size,size,1);
        g.drawString(font,text,0,0,color,true);g.pose().popPose();
    }
    private DeathmatchHud() { }
}

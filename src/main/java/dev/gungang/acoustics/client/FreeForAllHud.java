package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MatchSnapshot;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class FreeForAllHud {
    private static final int ACCENT=TacticalStyle.GOLD,GOLD=0xFFFFD166,SILVER=0xFFC9D3DE,BRONZE=0xFFE0A070,DIM=TacticalStyle.DIM;
    private static final int HEAD=28,COLS=13,ROW=18,FOOT=13;
    public static boolean blocked(){var p=Minecraft.getInstance().player;var r=p==null?null:row(p.getUUID());return r!=null&&r.protectionTicks()>0;}
    static boolean is(MatchSnapshot s){return s!=null&&s.point()!=null&&"FREE_FOR_ALL".equals(s.point().mode());}
    static MatchSnapshot.Row row(UUID id){var s=MatchHud.state();return !is(s)||!s.active()?null:s.players().stream().filter(r->r.id().equals(id.toString())).findFirst().orElse(null);}
    /** Respawn protection hides both ways: nobody sees the protected player, and they see no one, held weapons included. */
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre event){
        var mc=Minecraft.getInstance();if(event.getEntity()==mc.player)return;
        var r=row(event.getEntity().getUUID());
        if(r!=null&&r.protectionTicks()>0||r!=null&&blocked())event.setCanceled(true);
    }
    static int rankColor(int rank){return rank==1?GOLD:rank==2?SILVER:rank==3?BRONZE:DIM;}

    /**
     * Top strip in the deathmatch style, drawn in DeathmatchHud's centred pose: own plate on the left,
     * the player to beat (leader, or runner-up while leading) on the right. Each underline fills toward the goal.
     */
    static void top(GuiGraphics g,MatchSnapshot s) {
        var rows=s.players();var mc=Minecraft.getInstance();
        String self=mc.player==null?"":mc.player.getUUID().toString();
        int mine=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(self)){mine=i;break;}
        int goal=s.point().goal();
        if(mine<0) {
            // Spectators follow the race for first place.
            if(!rows.isEmpty())plate(g,true,rows.get(0),1,goal,null);
            if(rows.size()>1)plate(g,false,rows.get(1),2,goal,null);
        } else {
            int rival=mine==0?1:0;var me=rows.get(mine);
            Integer lead=rows.size()>1?me.score()-rows.get(rival).score():null;
            plate(g,true,me,mine+1,goal,lead);
            if(rows.size()>1)plate(g,false,rows.get(rival),rival+1,goal,null);
        }
        DeathmatchHud.small(g,Lang.tr("목표 ")+goal,0,37,.8f,DeathmatchHud.MUTED,true);
    }
    private static void plate(GuiGraphics g,boolean left,MatchSnapshot.Row row,int rank,int goal,Integer lead) {
        String note=lead==null?"":lead>0?"+"+lead:lead==0?"±0":String.valueOf(lead);
        MatchRibbonHud.wing(g,left,0xFF000000|row.color(),row.score(),goal,rank+Lang.tr("위 · ")+row.name(),note);
    }
    static int capacity(int height){return Math.max(1,(height-HEAD-COLS-FOOT)/ROW);}
    static int pages(MatchSnapshot s,int height){return Math.max(1,(s.players().size()+capacity(height)-1)/capacity(height));}
    /** One ranking card in the team-card style: mint header, column strip, player-coloured stripes and top-three ranks. */
    static int table(GuiGraphics g,MatchSnapshot s,int x,int y,int width,int height,int requested){
        var mc=Minecraft.getInstance();var font=mc.font;
        int count=capacity(height),pages=pages(s,height),page=Math.min(requested,pages-1),right=x+width;
        int shown=Math.max(1,Math.min(count,s.players().size()-page*count)),bottom=y+HEAD+COLS+shown*ROW+(pages>1?FOOT:4);
        int rgb=ACCENT&0xFFFFFF;
        g.fill(right,y+2,right+2,bottom+2,0x40000000);g.fill(x+2,bottom,right,bottom+2,0x40000000);
        g.fillGradient(x,y,right,bottom,0xB01D1F18,0xB010110E);
        g.fillGradient(x,y,right,y+HEAD,rgb|0x50000000,rgb|0x08000000);
        g.fill(x,y,right,y+2,ACCENT);g.fill(x+8,y+9,x+10,y+19,ACCENT);
        g.drawString(font,Lang.tr("개인전"),x+14,y+7,0xFFFFFFFF,false);
        long kills=s.players().stream().mapToLong(MatchSnapshot.Row::kills).sum();
        g.drawString(font,s.players().size()+Lang.tr("명 · ")+kills+Lang.tr(" 킬 · 목표 ")+s.point().goal()+Lang.tr("점"),x+14,y+17,TacticalStyle.MUTED,false);
        var leader=s.players().isEmpty()?null:s.players().get(0);
        if(leader!=null) {
            String total=String.valueOf(leader.score()),label=Lang.tr("1위");
            g.pose().pushPose();g.pose().translate(right-8-HudText.numberWidth(total)*2,y+6,0);g.pose().scale(2,2,1);
            HudText.drawNumber(g,HudText.number(total),0,0,0xFFFFFFFF);g.pose().popPose();
            g.drawString(font,label,right-12-HudText.numberWidth(total)*2-font.width(label),y+13,GOLD,false);
        }
        int dmg=right-8,assist=dmg-46,death=assist-22,kill=death-22,score=kill-30,face=x+26,nameX=face+30,cy=y+HEAD;
        g.fill(x,cy,right,cy+COLS,0x30000000);g.fill(x,cy+COLS-1,right,cy+COLS,TacticalStyle.EDGE);
        g.drawString(font,"#",x+8,cy+3,DIM,false);g.drawString(font,Lang.tr("플레이어"),face,cy+3,DIM,false);
        MatchHud.column(g,Lang.tr("점수"),score,cy+3,DIM);MatchHud.column(g,"K",kill,cy+3,DIM);MatchHud.column(g,"D",death,cy+3,DIM);
        MatchHud.column(g,"A",assist,cy+3,DIM);MatchHud.column(g,"DMG",dmg,cy+3,DIM);
        String self=mc.player==null?"":mc.player.getUUID().toString();
        if(s.players().isEmpty())g.drawString(font,Lang.tr("플레이어 없음"),x+(width-font.width(Lang.tr("플레이어 없음")))/2,cy+COLS+5,DIM,false);
        for(int i=page*count;i<Math.min(s.players().size(),(page+1)*count);i++){
            var r=s.players().get(i);int ry=cy+COLS+(i-page*count)*ROW,rank=i+1,color=0xFF000000|r.color();boolean mine=r.id().equals(self);
            if(mine)g.fill(x,ry,right,ry+ROW,(r.color()&0xFFFFFF)|0x38000000);
            else if(i%2==1)g.fill(x,ry,right,ry+ROW,0x10FFFFFF);
            g.fill(x,ry,x+2,ry+ROW,color);
            String number=String.valueOf(rank);
            HudText.drawNumber(g,HudText.number(number),x+8+(10-HudText.numberWidth(number))/2,ry+5,rank<=3&&r.score()>0?rankColor(rank):DIM);
            g.fill(face-1,ry+2,face+13,ry+16,mine?color:TacticalStyle.EDGE);
            PlayerFaceRenderer.draw(g,MatchHud.skin(r.id()),face,ry+3,12);
            PlayerLevels.draw(g,r.id(),face+15,ry+3,12);
            Marquee.draw(g,font,r.name(),nameX,ry+5,Math.max(1,score-30-nameX),mine?0xFFFFFFFF:color,false);
            MatchHud.column(g,String.valueOf(r.score()),score,ry+5,0xFFFFFFFF);
            MatchHud.column(g,String.valueOf(r.kills()),kill,ry+5,TacticalStyle.INK);
            MatchHud.column(g,String.valueOf(r.deaths()),death,ry+5,0xFFE08A8A);
            MatchHud.column(g,String.valueOf(r.assists()),assist,ry+5,TacticalStyle.MUTED);
            MatchHud.column(g,String.format(Locale.ROOT,"%.1f",r.damage()),dmg,ry+5,0xFFFFC870);
        }
        if(pages>1) {
            String label=(page+1)+" / "+pages;
            g.fill(x+8,bottom-FOOT,right-8,bottom-FOOT+1,TacticalStyle.EDGE);
            g.drawString(font,label,right-8-font.width(label),bottom-FOOT+3,DIM,false);
        }
        return pages;
    }
    private FreeForAllHud(){}
}

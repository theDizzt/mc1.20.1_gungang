package dev.gungang.acoustics.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import dev.gungang.acoustics.server.ProgressionService.StatsView;

/** A server-provided snapshot, also usable for offline players queried by name or UUID. */
public final class PlayerStatsScreen extends ArenaScreen {
    static final net.minecraft.resources.ResourceLocation STATS_FONT=new net.minecraft.resources.ResourceLocation("gungang_acoustics","stats");
    @Override Component screenText(Component text){return Component.literal(text.getString()).withStyle(s->s.withFont(STATS_FONT));}
    private Component text(String value){return screenText(Component.literal(value));}
    /** One combat-record tile: label, large value with a small unit, a derived note at the top right, and its colour. */
    private record Tile(String label,String value,String unit,String note,int color) {}
    /** Tile colours: brass, green, steel blue (career row); red-orange, lavender, rose (combat row). */
    private static final int MATCHES=ACCENT,WINS=0xFF70B58E,TIME=0xFF6FA3C7,KILLS=0xFFD9714E,DEATHS=0xFF9A8BC4,DAMAGE=0xFFCF6F95;
    private final StatsView stats;
    private final Tile[] tiles;
    private int left,top,panelWidth,panelHeight;
    private PlayerStatsScreen(StatsView stats){
        super("GUNGANG / STATS");this.stats=stats;
        long seconds=stats.playSeconds(),matches=stats.matches(),average=matches>0?seconds/matches:0;
        tiles=new Tile[]{
            new Tile("통산 경기",String.format(Locale.ROOT,"%,d",matches),"경기",matches>0?"패배 "+String.format(Locale.ROOT,"%,d",Math.max(0,matches-stats.wins())):"",MATCHES),
            new Tile("승리",String.format(Locale.ROOT,"%,d",stats.wins()),"승",matches>0?String.format(Locale.ROOT,"승률 %.1f%%",stats.wins()*100.0/matches):"",WINS),
            new Tile("경기 플레이 시간",String.format(Locale.ROOT,"%,d",seconds/3600),String.format(Locale.ROOT,"시간 %d분 %d초",seconds/60%60,seconds%60),
                    matches>0?String.format(Locale.ROOT,"평균 %d:%02d",average/60,average%60):"",TIME),
            new Tile("킬",String.format(Locale.ROOT,"%,d",stats.kills()),"",matches>0?String.format(Locale.ROOT,"경기당 %.1f",stats.kills()/(double)matches):"",KILLS),
            new Tile("데스",String.format(Locale.ROOT,"%,d",stats.deaths()),"",String.format(Locale.ROOT,"K/D %.2f",stats.kills()/(double)Math.max(1,stats.deaths())),DEATHS),
            new Tile("누적 피해량",String.format(Locale.ROOT,"%,.1f",stats.damage()),"",matches>0?String.format(Locale.ROOT,"경기당 %,.0f",stats.damage()/matches):"",DAMAGE)
        };
    }
    public static void open(String json){
        var view=new com.google.gson.Gson().fromJson(json,StatsView.class);
        if(view!=null)Minecraft.getInstance().setScreen(new PlayerStatsScreen(view));
    }
    @Override protected void init(){
        left=left()+12;top=62;panelWidth=body()-24;panelHeight=height-122;
        closeButton(left+panelWidth-80,height-48,80,22);
        int historyWidth=Math.max(100,font.width(text("전체 경기 기록"))+20);
        button("전체 경기 기록",left+panelWidth-80-6-historyWidth,height-48,historyWidth,22,true,ACCENT,
                ()->MatchHistoryScreen.open(this,stats.id()==null?"":stats.id(),stats.name()));
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        shell(g,"플레이어 전적 / 누적 경기 기록");
        card(g,left,top,left+panelWidth,top+88);
        TacticalStyle.band(g,left,top,left+panelWidth,3,ACCENT,PANEL);
        var skin=MatchHud.skin(stats.id()==null?"":stats.id());
        TacticalStyle.tile(g,left+10,top+12,42,42,TacticalStyle.TRACK,BORDER);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin,left+12,top+14,38,true,false);
        TacticalStyle.brackets(g,left+10,top+12,42,42,6,ACCENT);
        var name=text(stats.name());
        float nameScale=Math.min(1.6f,(panelWidth-76)/(float)Math.max(1,font.width(name)));
        g.pose().pushPose();g.pose().translate(left+64,top+16,0);g.pose().scale(nameScale,nameScale,1);
        g.drawString(font,name,0,0,INK,false);g.pose().popPose();
        if(stats.tag()!=null&&!stats.tag().isEmpty())g.drawString(font,text("#"+stats.tag()),left+64,top+36,MUTED,false);
        LevelEmblem.draw(g,stats.level(),left+10,top+59,20);
        double fraction=stats.level()>=91?1:stats.required()>0?(double)stats.current()/stats.required():0;
        fraction=Math.max(0,Math.min(1,fraction));
        String xp=stats.level()>=91?"MAX":stats.current()+"/"+stats.required();
        Marquee.draw(g,font,text(xp+"   ·   "+String.format(Locale.ROOT,"%.2f%%",fraction*100)),left+40,top+59,panelWidth-52,INK,false);
        int barLeft=left+40,barWidth=panelWidth-52;
        TacticalStyle.goldGauge(g,barLeft,top+74,barWidth,6,(float)fraction,(float)fraction,true);
        combat(g,top+98);
        super.render(g,mouseX,mouseY,partialTick);
    }
    /** Every record at once: a 3×2 tile grid, or one row of six when the window is too short for two. */
    private void combat(GuiGraphics g,int cardTop){
        int gap=4,gridTop=cardTop+20,avail=top+panelHeight-6-gridTop;
        int rows=avail>=2*34+gap?2:1,cols=tiles.length/rows;
        int tileHeight=Math.max(18,Math.min(58,(avail-gap*(rows-1))/rows));
        int gridLeft=left+6,gridWidth=panelWidth-12,tileWidth=(gridWidth-gap*(cols-1))/cols;
        int cardBottom=gridTop+rows*tileHeight+gap*(rows-1)+6;
        card(g,left,cardTop,left+panelWidth,cardBottom);
        cardHeader(g,left,cardTop,left+panelWidth,"전투 기록",ACCENT,"");
        for(int i=0;i<tiles.length;i++){
            int x=gridLeft+(i%cols)*(tileWidth+gap),y=gridTop+(i/cols)*(tileHeight+gap);
            int w=i%cols==cols-1?gridLeft+gridWidth-x:tileWidth;
            tile(g,tiles[i],x,y,w,tileHeight);
        }
    }
    private void tile(GuiGraphics g,Tile t,int x,int y,int w,int h){
        TacticalStyle.tile(g,x,y,w,h,TacticalStyle.TRACK,TacticalStyle.HAIRLINE);
        // The tile's colour: side bar, a tint fading down from the top, and the label; values stay ivory to read.
        g.fill(x,y,x+2,y+h,t.color());
        g.fillGradient(x+2,y+1,x+w-1,y+h-1,blend(TacticalStyle.TRACK,t.color(),.13f),TacticalStyle.TRACK);
        g.fill(x+2,y+h-2,x+w-1,y+h-1,blend(TacticalStyle.TRACK,t.color(),.35f));
        var label=text(t.label());var note=text(t.note());
        int labelWidth=font.width(label),noteWidth=t.note().isEmpty()?0:font.width(note);
        boolean showNote=noteWidth>0&&labelWidth+noteWidth+22<=w;
        Marquee.draw(g,font,label,x+8,y+5,showNote?labelWidth:w-14,blend(MUTED,t.color(),.75f),false);
        if(showNote)g.drawString(font,note,x+w-7-noteWidth,y+5,TacticalStyle.DIM,false);
        if(h<30){ // Too short for a large value: label left, value right on one line.
            var value=text(t.value()+t.unit());
            if(!showNote&&labelWidth+font.width(value)+22<=w)g.drawString(font,value,x+w-7-font.width(value),y+5,INK,false);
            return;
        }
        var value=text(t.value());var unit=text(t.unit());
        int valueWidth=font.width(value),unitWidth=t.unit().isEmpty()?0:font.width(unit)+3;
        float scale=Math.max(1,Math.min(2f,Math.min((h-20)/9f,(w-16-unitWidth)/(float)Math.max(1,valueWidth))));
        int baseline=y+h-6;
        g.pose().pushPose();g.pose().translate(x+8,baseline-8*scale,0);g.pose().scale(scale,scale,1);
        g.drawString(font,value,0,0,INK,false);g.pose().popPose();
        if(unitWidth>0)Marquee.draw(g,font,unit,x+8+valueWidth*scale+3,baseline-8,Math.max(1,w-16-Math.round(valueWidth*scale)-3),MUTED,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}

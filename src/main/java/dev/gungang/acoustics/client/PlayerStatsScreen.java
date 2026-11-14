package dev.gungang.acoustics.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.gungang.acoustics.server.ProgressionService.StatsView;
import dev.gungang.acoustics.server.PlayerTag;

/** A server-provided snapshot, also usable for offline players queried by name or UUID. */
public final class PlayerStatsScreen extends Screen {
    private final StatsView stats;
    private final List<String> values;
    private static final String[] LABELS={"통산 경기","승리","킬","데스","누적 피해량","경기 플레이 시간"};
    private int left,top,panelWidth,panelHeight,scroll;
    private PlayerStatsScreen(StatsView stats){
        super(Component.literal("플레이어 전적"));this.stats=stats;
        long seconds=stats.playSeconds();
        values=List.of(stats.matches()+"경기",stats.wins()+"승",Long.toString(stats.kills()),Long.toString(stats.deaths()),
                String.format(Locale.ROOT,"%,.1f",stats.damage()),String.format(Locale.ROOT,"%d시간 %d분 %d초",seconds/3600,seconds/60%60,seconds%60));
    }
    public static void open(String json){
        var view=new com.google.gson.Gson().fromJson(json,StatsView.class);
        if(view!=null)Minecraft.getInstance().setScreen(new PlayerStatsScreen(view));
    }
    @Override protected void init(){
        panelWidth=Math.min(360,width-16);panelHeight=Math.min(294,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;scroll=0;
        addRenderableWidget(Button.builder(Component.literal("닫기"),b->onClose())
                .bounds(width/2-40,top+panelHeight-27,80,20).build());
    }
    private int viewHeight(){return Math.max(1,panelHeight-132);}
    private int maxScroll(){return Math.max(0,LABELS.length*25-viewHeight());}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(delta*25)));return true;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);
        g.fill(left,top,left+panelWidth,top+panelHeight,0xF018202B);
        g.renderOutline(left,top,panelWidth,panelHeight,0xFF40566D);
        g.drawCenteredString(font,title,width/2,top+12,0xFFFFFFFF);
        var name=PlayerTag.named(stats.name(),stats.tag());
        g.drawCenteredString(font,name,width/2,top+32,0xFF9DE1D5);
        LevelEmblem.draw(g,stats.level(),left+14,top+53,20);
        String xp=stats.level()>=91?"MAX":stats.current()+" / "+stats.required();
        g.drawString(font,"Lv. "+stats.level()+"   ·   경험치 "+xp,left+42,top+57,0xFFE1E7EF,false);
        int barLeft=left+14,barWidth=panelWidth-28;
        double fraction=stats.level()>=91?1:stats.required()>0?(double)stats.current()/stats.required():0;
        g.fill(barLeft,top+81,barLeft+barWidth,top+85,0xFF344254);
        g.fill(barLeft,top+81,barLeft+(int)(barWidth*Math.max(0,Math.min(1,fraction))),top+85,0xFF70CDBF);
        int listTop=top+96,listBottom=listTop+viewHeight();
        g.enableScissor(left+8,listTop,left+panelWidth-8,listBottom);
        for(int i=0;i<LABELS.length;i++){
            int y=listTop+i*25-scroll;
            if(i%2==0)g.fill(left+10,y,left+panelWidth-10,y+24,0xFF202C39);
            g.drawString(font,LABELS[i],left+18,y+8,0xFFABBACB,false);
            String value=values.get(i);
            g.drawString(font,value,left+panelWidth-18-font.width(value),y+8,0xFFFFFFFF,false);
        }
        g.disableScissor();
        if(maxScroll()>0){
            int thumb=Math.max(10,viewHeight()*viewHeight()/(LABELS.length*25));
            int y=listTop+(viewHeight()-thumb)*scroll/maxScroll();
            g.fill(left+panelWidth-6,y,left+panelWidth-3,y+thumb,0xFF70CDBF);
        }
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}

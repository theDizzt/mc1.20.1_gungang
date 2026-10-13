package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MatchHud {
    private static MatchSnapshot state;
    private static int page;
    private static MatchSnapshot pendingResult;
    private static int resultDelay;
    public static boolean active() { return state!=null&&state.active(); }
    public static void receive(String json) {
        state=ServerFiles.GSON.fromJson(json,MatchSnapshot.class);
        MapEnvironmentClient.receive(state);
        RespawnPortrait.sync(state.respawns());
        pendingResult=null;resultDelay=0;
        if(state.result()) {
            var mc=Minecraft.getInstance();
            pendingResult=state;resultDelay=60;
            mc.setScreen(null);
            mc.gui.setTimes(10,40,10);
            mc.gui.setSubtitle(Component.empty());
            mc.gui.setTitle(resultTitle(state));
        }
    }
    static Component resultTitle(MatchSnapshot result) {
        if(result.winner().equals("DRAW"))return Component.literal("무승부");
        if(result.winner().equals("ABORTED"))return Component.literal("경기 종료");
        var options=result.options()==null?TeamOptions.defaults():result.options();
        return Component.empty().append(Component.literal("Team "+result.winner()).withStyle(s->s.withColor(options.color(result.winner())&0xFFFFFF)))
                .append(Component.literal(" 승리").withStyle(ChatFormatting.WHITE));
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||pendingResult==null)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null) {pendingResult=null;resultDelay=0;return;}
        if(--resultDelay<=0) {
            var result=pendingResult;pendingResult=null;
            mc.gui.clear();
            mc.setScreen(new Results(result));
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { state=null; page=0;pendingResult=null;resultDelay=0;MapEnvironmentClient.receive(null); }
    @SubscribeEvent public static void hide(RenderGuiOverlayEvent.Pre e) {
        if(state!=null&&state.active()&&e.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) e.setCanceled(true);
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e) {
        var mc=Minecraft.getInstance();
        if(state!=null&&state.active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()) { page=Math.max(0,page+(e.getScrollDelta()<0?1:-1)); e.setCanceled(true); }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance(); if(state==null||!state.active()||mc.options.hideGui)return;
        var g=e.getGuiGraphics();
        boolean deathmatch=state.point()!=null&&"DEATHMATCH".equals(state.point().mode());
        int y;
        if(deathmatch)y=DeathmatchHud.draw(g,state);
        else {y=ControlPointHud.draw(g,state)+2;MatchTimerHud.draw(g,MatchReport.time(state.seconds()),y);}
        if(mc.screen==null&&mc.options.keyPlayerList.isDown()) {
            String elapsed="경과 시간 · "+MatchReport.time(state.elapsedSeconds());
            g.drawString(mc.font,elapsed,(g.guiWidth()-mc.font.width(elapsed))/2,y+21,0xFFFFFFFF,true);
            table(g,state,Math.max(4,(g.guiWidth()-480)/2),y+35,Math.min(480,g.guiWidth()-8),g.guiHeight()-y-48,page);
        }
    }
    static int table(GuiGraphics g,MatchSnapshot value,int x,int y,int width,int height,int requestedPage) {
        var font=Minecraft.getInstance().font;int capacity=Math.max(1,(height-28)/28),pages=tablePages(value,height),p=Math.min(requestedPage,pages-1);
        int half=(width-8)/2;
        var options=value.options()==null?TeamOptions.defaults():value.options();
        for(int team=0;team<2;team++) {
            String name=team==0?"ALPHA":"BRAVO";int left=x+team*(half+8),color=options.color(name);
            var rows=value.players().stream().filter(r->r.team().equals(name)).toList();
            g.fill(left,y,left+half,y+height,0xEE101B29);g.fill(left,y,left+half,y+2,color);
            g.drawString(font,name+" · "+(team==0?value.alphaScore():value.bravoScore()),left+8,y+8,color,false);
            for(int i=0;i<capacity&&p*capacity+i<rows.size();i++) {
                var row=rows.get(p*capacity+i);int ry=y+26+i*28;
                g.drawString(font,font.plainSubstrByWidth(row.name(),half-16),left+8,ry,0xFFFFFFFF,false);
                String stats=String.format(Locale.ROOT,"%d/%d/%d · DMG %.1f",row.kills(),row.deaths(),row.assists(),row.damage());
                g.drawString(font,font.plainSubstrByWidth(stats,half-16),left+8,ry+12,0xFF9AAABD,false);
            }
        }
        return pages;
    }
    private static int tablePages(MatchSnapshot value,int height) {
        long count=Math.max(value.players().stream().filter(r->r.team().equals("ALPHA")).count(),value.players().stream().filter(r->r.team().equals("BRAVO")).count());
        int capacity=Math.max(1,(height-28)/28);return Math.max(1,(int)((count+capacity-1)/capacity));
    }
    private static final class Results extends ArenaScreen {
        final MatchSnapshot result; int current;
        Results(MatchSnapshot result) { super(resultTitle(result)); this.result=result; }
        @Override protected void init() {
            button("이전",left()+10,height-40,60,20,true,ACCENT,()->current=Math.max(0,current-1));
            button("다음",left()+76,height-40,60,20,true,ACCENT,()->current=Math.min(tablePages(result,height-112)-1,current+1));
            closeButton(left()+body()-80,height-40,70,20);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            shell(g,Component.literal(result.map()+" · 플레이 시간 "+MatchReport.time(result.seconds())+" · ")
                    .append(Component.literal("Team ALPHA "+result.alphaScore()).withStyle(s->s.withColor((result.options()==null?TeamOptions.defaults():result.options()).color("ALPHA")&0xFFFFFF)))
                    .append(Component.literal(" : ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Team Bravo "+result.bravoScore()).withStyle(s->s.withColor((result.options()==null?TeamOptions.defaults():result.options()).color("BRAVO")&0xFFFFFF))));
            table(g,result,left()+8,62,body()-16,height-112,current); super.render(g,mx,my,partial);
        }
    }
    private MatchHud() {}
}

package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MatchHud {
    private static MatchSnapshot state;
    private static int page;
    public static boolean active() { return state!=null&&state.active(); }
    public static void receive(String json) {
        state=ServerFiles.GSON.fromJson(json,MatchSnapshot.class);
        if(state.result()) Minecraft.getInstance().setScreen(new Results(state));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { state=null; page=0; }
    @SubscribeEvent public static void hide(RenderGuiOverlayEvent.Pre e) {
        if(state!=null&&state.active()&&e.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) e.setCanceled(true);
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e) {
        var mc=Minecraft.getInstance();
        if(state!=null&&state.active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()) { page=Math.max(0,page+(e.getScrollDelta()<0?1:-1)); e.setCanceled(true); }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance(); if(state==null||!state.active()||mc.options.hideGui)return;
        var g=e.getGuiGraphics(); var text=Component.literal(MatchReport.time(state.seconds())).withStyle(s->s.withFont(new ResourceLocation(GungangAcoustics.ID,"timer")));
        int x=(g.guiWidth()-mc.font.width(text))/2,y=BossBarHud.bottom+8;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)g.drawString(mc.font,text,x+dx,y+dy,0xFF000000,false);
        g.flush();
        // TTF glyphs may extend above the draw origin. Cover both ascenders and descenders.
        // Advance the fill slightly toward the camera so the outline cannot obscure it.
        g.pose().pushPose(); g.pose().translate(0,0,0.1f);
        for(int row=-16;row<20;row++) {
            g.enableScissor(x-2,y+row,x+mc.font.width(text)+3,y+row+1);
            float t=Math.max(0,Math.min(1,(row+5)/12f));
            int red=Math.round(180-125*t),green=Math.round(255-60*t),blue=Math.round(170-125*t);
            g.drawString(mc.font,text,x,y,0xFF000000|(red<<16)|(green<<8)|blue,false);
            g.flush(); g.disableScissor();
        }
        g.pose().popPose();
        if(mc.screen==null&&mc.options.keyPlayerList.isDown()) table(g,state,Math.max(4,(g.guiWidth()-480)/2),y+22,Math.min(480,g.guiWidth()-8),g.guiHeight()-y-35,page);
    }
    static int table(GuiGraphics g,MatchSnapshot value,int x,int y,int width,int height,int requestedPage) {
        var font=Minecraft.getInstance().font; int capacity=Math.max(1,(height-32)/18),pages=Math.max(1,(value.players().size()+capacity-1)/capacity),p=Math.min(requestedPage,pages-1);
        g.fill(x,y,x+width,y+height,0xEE101B29);
        g.drawString(font,"PLAYER",x+8,y+6,0xFF9AAABD,false);
        g.drawString(font,"K / D / A",x+width-160,y+6,0xFF9AAABD,false);
        g.drawString(font,"DAMAGE",x+width-60,y+6,0xFF9AAABD,false);
        for(int i=0;i<capacity&&p*capacity+i<value.players().size();i++) {
            var r=value.players().get(p*capacity+i); int ry=y+24+i*18;
            int color=r.team().equals("ALPHA")?0xFFFF7777:r.team().equals("BRAVO")?0xFF77AAFF:0xFFAAAAAA;
            g.drawString(font,font.plainSubstrByWidth(r.name(),Math.max(20,width-180)),x+8,ry,color,false);
            g.drawString(font,String.format(Locale.ROOT,"%d / %d / %d",r.kills(),r.deaths(),r.assists()),x+width-160,ry,0xFFFFFFFF,false);
            String damage=String.format(Locale.ROOT,"%.1f",r.damage());
            g.drawString(font,damage,x+width-12-font.width(damage),ry,0xFFFFFFFF,false);
        }
        return pages;
    }
    private static final class Results extends ArenaScreen {
        final MatchSnapshot result; int current;
        Results(MatchSnapshot result) { super(result.winner().equals("DRAW")?"무승부":result.winner().equals("ABORTED")?"경기 종료":"Team "+result.winner()+" 승리"); this.result=result; }
        @Override protected void init() {
            button("이전",left()+10,height-40,60,20,true,ACCENT,()->current=Math.max(0,current-1));
            button("다음",left()+76,height-40,60,20,true,ACCENT,()->current=Math.min(Math.max(0,(result.players().size()-1)/Math.max(1,(height-144)/18)),current+1));
            button("닫기",left()+body()-80,height-40,70,20,true,ACCENT,this::onClose);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            shell(g,Component.literal(result.map()+" · 플레이 시간 "+MatchReport.time(result.seconds())+" · ")
                    .append(Component.literal("Team ALPHA "+result.alphaScore()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" : ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Team Bravo "+result.bravoScore()).withStyle(ChatFormatting.BLUE)));
            table(g,result,left()+8,62,body()-16,height-112,current); super.render(g,mx,my,partial);
        }
    }
    private MatchHud() {}
}

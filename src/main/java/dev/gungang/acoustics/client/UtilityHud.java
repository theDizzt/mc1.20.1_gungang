package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class UtilityHud {
    private static CombatGadgets.Hud state=new CombatGadgets.Hud(java.util.List.of(),0,0,3,"");
    private static long received;
    public static void receive(String json) {state=ServerFiles.GSON.fromJson(json,CombatGadgets.Hud.class);var level=Minecraft.getInstance().level;received=level==null?0:level.getGameTime();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {state=new CombatGadgets.Hud(java.util.List.of(),0,0,3,"");received=0;}
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if(CombatGadgets.holdingShield(Minecraft.getInstance().player)) {event.setCanceled(true);event.setSwingHand(false);}
    }
    static void gauges(GuiGraphics g,int center,int healthBarY,int width,int height,float partial) {
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator())return;
        int y=healthBarY-36-height;
        for(var gauge:state.gauges()) {
            float remaining=gauge.remaining();if(gauge.timed())remaining-=Math.max(0,mc.level.getGameTime()-received+partial);
            if(remaining<=0)continue;
            if(gauge.kind().equals("shield")&&!CombatGadgets.holdingShield(mc.player))continue;
            String name=switch(gauge.kind()){case "shield"->"방패";case "infrared_scanner"->"스캐너";default->"아드레날린";};
            String value=gauge.timed()?String.format(java.util.Locale.ROOT,"%.1fs",remaining/20f):HudHealth.number(remaining)+"/"+HudHealth.number(gauge.maximum());
            gauge(g,center,y,width,height,remaining,gauge.maximum(),name+" "+value,partial);
            y-=height+4;
        }
        var ability=SurvivalGadgets.clientState(mc.player);
        if(ability!=null) {
            float remaining=ability.jetEnd()-mc.level.getGameTime()-partial;
            if(remaining>0)gauge(g,center,y,width,height,remaining,SurvivalRules.JET_TICKS,
                    String.format(java.util.Locale.ROOT,"제트팩 %.1fs",remaining/20f),partial);
        }
    }
    private static void gauge(GuiGraphics g,int center,int y,int width,int height,float remaining,float maximum,String label,float partial) {
        float fraction=Math.min(1,remaining/Math.max(1,maximum));int x=center-width/2;
        ShooterHud.panel(g,x,y,width,height,0xFF60696D,0x8029353E);
        HudHealthBars.fill(g,x+1,y+1,width-2,height-2,fraction,0xFF65D9FF);
        HudHealthBars.stripes(g,x+1,y+1,width-2,height-2,fraction,(Minecraft.getInstance().player.tickCount+partial)/20.0);
        HudText.health(g,label,x+3,y+1,width-6,height-2,0xFFFFFFFF);
    }
    static void placements(GuiGraphics g,int x,int y) {
        var font=Minecraft.getInstance().font;
        if(state.equipped().equals("landmine")||state.mines()>0) {
            g.drawString(font,"지뢰: "+state.mines()+"/"+state.maxMines(),x,y,state.mines()>=state.maxMines()?0xFFFF5555:0xFFFFFFFF,true);y+=12;
        }
        if(state.equipped().equals("barrier")||state.barriers()>0)
            g.drawString(font,"방벽: "+state.barriers()+"/1",x,y,state.barriers()>=1?0xFFFF5555:0xFFFFFFFF,true);
    }
    static int placementWidth() {
        var font=Minecraft.getInstance().font;int width=0;
        if(state.equipped().equals("landmine")||state.mines()>0)width=font.width("지뢰: "+state.mines()+"/"+state.maxMines());
        if(state.equipped().equals("barrier")||state.barriers()>0)width=Math.max(width,font.width("방벽: "+state.barriers()+"/1"));
        return width==0?0:width+8;
    }
    private UtilityHud() { }
}

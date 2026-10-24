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
    private static CombatGadgets.Hud state=new CombatGadgets.Hud(java.util.List.of(),0,0,3,1,"");
    private static long received;
    public static void receive(String json) {state=ServerFiles.GSON.fromJson(json,CombatGadgets.Hud.class);BearTraps.clientArmed=state.bearTrapArmed();var level=Minecraft.getInstance().level;received=level==null?0:level.getGameTime();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {state=new CombatGadgets.Hud(java.util.List.of(),0,0,3,1,"");received=0;BearTraps.clientArmed=false;}
    /** Pinned by an enemy bear trap: movement input is dropped so the client does not fight the server's anchor. */
    static boolean rooted() {
        var mc=Minecraft.getInstance();if(mc.level==null)return false;
        for(var gauge:state.gauges())if(gauge.kind().equals("bear_trap_root")&&gauge.remaining()-(mc.level.getGameTime()-received)>0)return true;
        return false;
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if(FreeForAllHud.blocked()||CombatGadgets.holdingShield(Minecraft.getInstance().player)) {event.setCanceled(true);event.setSwingHand(false);}
    }
    static void gauges(GuiGraphics g,int center,int healthBarY,int width,int height,float partial) {
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator())return;
        int y=healthBarY-36-height;
        var protection=FreeForAllHud.row(mc.player.getUUID());
        if(protection!=null&&protection.protectionTicks()>0){
            gauge(g,center,y,width,height,protection.protectionTicks(),protection.protectionTotal(),(protection.protectionTotal()==600?"무적 ":"은신 ")+(protection.protectionTicks()+19)/20+"초",partial);
            y-=height+4;
        }
        for(var gauge:state.gauges()) {
            float remaining=gauge.remaining();if(gauge.timed())remaining-=Math.max(0,mc.level.getGameTime()-received+partial);
            if(remaining<=0&&!gauge.kind().equals("turret_ammo"))continue;
            if(gauge.kind().equals("shield")&&!CombatGadgets.holdingShield(mc.player))continue;
            String name=switch(gauge.kind()){case "turret_ammo"->"터렛 탄약";case "bomb_plant"->"폭탄 설치";case "bomb_defuse"->"폭탄 해체";case "shield"->"방패";case "infrared_scanner"->"스캐너";case "jetpack"->"제트팩";case "bear_trap_recall"->"베어트랩 회수";case "bear_trap_root"->"베어트랩 속박";default->"아드레날린";};
            String value=gauge.timed()?String.format(java.util.Locale.ROOT,"%.1fs",remaining/20f):HudHealth.number(remaining)+"/"+HudHealth.number(gauge.maximum());
            gauge(g,center,y,width,height,remaining,gauge.maximum(),name+" "+value,partial);
            y-=height+4;
        }
    }
    private static void gauge(GuiGraphics g,int center,int y,int width,int height,float remaining,float maximum,String label,float partial) {
        float fraction=Math.min(1,remaining/Math.max(1,maximum));int x=center-width/2;
        ShooterHud.card(g,x,y,width,height,ShooterHud.BAR_EDGE,ShooterHud.SURFACE,0);
        HudHealthBars.fill(g,x+1,y+1,width-2,height-2,fraction,0xFF65D9FF);
        HudHealthBars.stripes(g,x+1,y+1,width-2,height-2,fraction,(Minecraft.getInstance().player.tickCount+partial)/20.0);
        ShooterHud.finish(g,x+1,y+1,width-2,height-2,fraction);
        HudText.health(g,label,x+3,y+1,width-6,height-2,0xFFFFFFFF);
    }
    static void placements(GuiGraphics g,int x,int y) {
        var font=Minecraft.getInstance().font;
        if(state.equipped().equals("landmine")||state.mines()>0) {
            g.drawString(font,"지뢰: "+state.mines()+"/"+state.maxMines(),x,y,state.mines()>=state.maxMines()?0xFFFF5555:0xFFFFFFFF,true);y+=12;
        }
        if(state.equipped().equals("barrier")||state.barriers()>0) {
            g.drawString(font,"방벽: "+state.barriers()+"/"+state.maxBarriers(),x,y,state.barriers()>=state.maxBarriers()?0xFFFF5555:0xFFFFFFFF,true);y+=12;
        }
        if(state.equipped().equals("bear_trap")||state.bearTraps()>0)
            g.drawString(font,"베어트랩: "+state.bearTraps()+"/"+state.maxBearTraps(),x,y,state.bearTraps()>=state.maxBearTraps()?0xFFFF5555:0xFFFFFFFF,true);
    }
    static int placementWidth() {
        var font=Minecraft.getInstance().font;int width=0;
        if(state.equipped().equals("landmine")||state.mines()>0)width=font.width("지뢰: "+state.mines()+"/"+state.maxMines());
        if(state.equipped().equals("barrier")||state.barriers()>0)width=Math.max(width,font.width("방벽: "+state.barriers()+"/"+state.maxBarriers()));
        if(state.equipped().equals("bear_trap")||state.bearTraps()>0)width=Math.max(width,font.width("베어트랩: "+state.bearTraps()+"/"+state.maxBearTraps()));
        return width==0?0:width+8;
    }
    private UtilityHud() { }
}

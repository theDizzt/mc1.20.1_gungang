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
    /** Stacked secondary bars ({@link AuxBar}) above the health bar at {@code healthBarY}, nearest first. */
    static void gauges(GuiGraphics g,int center,int healthBarY,int width,int height,float partial) {
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator())return;
        int y=healthBarY-36-height;
        y=PropAbilityHud.gauges(g,center,y,width,height,partial);
        // While a secondary is in use the rapier sits in the off hand; its combo keeps counting down there.
        var blade=mc.player.getMainHandItem().getItem() instanceof RapierItem?mc.player.getMainHandItem():mc.player.getOffhandItem();
        if(blade.getItem() instanceof RapierItem&&blade.hasTag()){
            var tag=blade.getTag();int stacks=tag.getInt(RapierItem.COMBO);
            float left=tag.getLong(RapierItem.COMBO_END)-mc.level.getGameTime()-partial;
            if(stacks>0&&left>0&&left<=RapierCombo.DURATION){
                String caption=Lang.format("콤보 · ×%.2f · %s",RapierCombo.damage(stacks)/21f,AuxBar.seconds(left));
                AuxBar.draw(g,center,y,width,height,AuxBar.Kind.OTHER,left,RapierCombo.DURATION,stacks+"/5",caption,partial);y-=height+4;
            }
        }
        var boots=HermesBootsLayer.worn(mc.player);
        if(!boots.isEmpty()&&SecondaryRuntime.secondary(boots)){chargeGauge(g,center,y,width,height,boots,partial);y-=height+4;}
        var awn=PropAwn.is(mc.player.getMainHandItem())?mc.player.getMainHandItem():mc.player.getOffhandItem();
        if(PropAwn.is(awn)){
            long remaining=SecondaryRuntime.remaining(mc.player,awn);
            if(remaining>0){AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,remaining,600,AuxBar.seconds(remaining),Lang.tr("AWN 재장전"),partial);y-=height+4;}
        }
        var protection=FreeForAllHud.row(mc.player.getUUID());
        if(protection!=null&&protection.protectionTicks()>0){
            AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,protection.protectionTicks(),protection.protectionTotal(),AuxBar.seconds(protection.protectionTicks()),Lang.tr("무적"),partial);
            y-=height+4;
        }
        String flagRange="";
        CombatGadgets.Gauge turretAmmo=null,flagHealth=null;
        for(var gauge:state.gauges())if(gauge.kind().equals("turret_ammo"))turretAmmo=gauge;
        for(var gauge:state.gauges()) {
            if(gauge.kind().equals("command_flag_range")){flagRange=" · "+HudHealth.number(gauge.remaining())+Lang.tr("블럭");continue;}
            // Each flag: its survival time first (purple bar), then its health above it.
            if(gauge.kind().equals("command_flag_health")){flagHealth=gauge;continue;}
            if(gauge.kind().equals("command_flag_age")){
                float age=gauge.remaining()+Math.max(0,mc.level.getGameTime()-received+partial);
                AuxBar.draw(g,center,y,width,height,AuxBar.Kind.OTHER,age,gauge.maximum(),AuxBar.seconds(age),
                        Lang.tr("깃발 생존")+(age>=gauge.maximum()?Lang.tr(" · 모든 아군"):flagRange),partial);y-=height+4;
                if(flagHealth!=null){y=flagHealth(g,center,y,width,height,flagHealth,partial);flagHealth=null;}
                continue;
            }
            if(gauge.kind().equals("m95_observed"))continue;
            if(gauge.kind().equals("m95_scope")){
                float remaining=Math.max(0,gauge.remaining()-(mc.level.getGameTime()-received+partial));
                AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,gauge.maximum()-remaining,gauge.maximum(),
                        remaining>0?AuxBar.seconds(remaining):"",remaining>0?Lang.tr("투시까지"):Lang.tr("투시 준비 완료"),partial);
                y-=height+4;continue;
            }
            if(gauge.kind().equals("m95")){
                AuxBar.draw(g,center,y,width,height,AuxBar.Kind.OTHER,gauge.remaining(),gauge.maximum(),AuxBar.ratio(gauge.remaining(),gauge.maximum()),Lang.tr("관통력"),partial);
                y-=height+4;continue;
            }
            if(gauge.kind().equals("turret_ammo"))continue;
            if(gauge.kind().equals("turret_health")) {
                // Turret: its health on one bar, its magazine on the bar above.
                AuxBar.draw(g,center,y,width,height,AuxBar.Kind.HEALTH,gauge.remaining(),gauge.maximum(),AuxBar.ratio(gauge.remaining(),gauge.maximum()),Lang.tr("터렛 체력"),partial);
                y-=height+4;
                if(turretAmmo!=null){
                    int count=Math.max(0,Math.round(turretAmmo.remaining())),capacity=Math.max(1,Math.round(turretAmmo.maximum()));
                    AuxBar.draw(g,center,y,width,height,AuxBar.Kind.AMMO,count,capacity,count+"/"+capacity,Lang.tr("터렛 탄약"),partial);y-=height+4;
                }
                continue;
            }
            float remaining=gauge.remaining();if(gauge.timed())remaining-=Math.max(0,mc.level.getGameTime()-received+partial);
            if(remaining<=0)continue;
            if(gauge.kind().equals("shield")&&!CombatGadgets.holdingShield(mc.player))continue;
            String name=switch(gauge.kind()){case "bomb_plant"->Lang.tr("폭탄 설치");case "bomb_defuse"->Lang.tr("폭탄 해체");case "shield"->Lang.tr("방패");case "infrared_scanner"->Lang.tr("스캐너");case "jetpack"->Lang.tr("제트팩");case "bear_trap_recall"->Lang.tr("베어트랩 회수");case "bear_trap_root"->Lang.tr("베어트랩 속박");default->Lang.tr("아드레날린");};
            var kind=gauge.timed()?AuxBar.Kind.COOLDOWN:gauge.kind().equals("shield")?AuxBar.Kind.HEALTH:AuxBar.Kind.OTHER;
            String value=gauge.timed()?AuxBar.seconds(remaining):AuxBar.ratio(remaining,gauge.maximum());
            AuxBar.draw(g,center,y,width,height,kind,remaining,gauge.maximum(),value,name,partial);
            y-=height+4;
        }
        if(flagHealth!=null)y=flagHealth(g,center,y,width,height,flagHealth,partial);
        for(var gauge:state.gauges())if(gauge.kind().equals("m95_observed")&&gauge.remaining()-(mc.level.getGameTime()-received+partial)>0){
            g.drawString(mc.font,Lang.tr("관측됨"),center-mc.font.width(Lang.tr("관측됨"))/2,y,0xFFFF7777,true);break;
        }
    }
    private static int flagHealth(GuiGraphics g,int center,int y,int width,int height,CombatGadgets.Gauge gauge,float partial){
        AuxBar.draw(g,center,y,width,height,AuxBar.Kind.HEALTH,gauge.remaining(),gauge.maximum(),AuxBar.ratio(gauge.remaining(),gauge.maximum()),Lang.tr("깃발 체력"),partial);
        return y-height-4;
    }
    /**
     * 74 Hermes' Boots charges: the bar split into one cell per stored dash. Ready cells are full; the one refilling
     * fills up dimmer, and the text gives the count and the time to the next charge.
     */
    private static void chargeGauge(GuiGraphics g,int center,int y,int width,int height,net.minecraft.world.item.ItemStack boots,float partial) {
        var mc=Minecraft.getInstance();
        int max=SecondaryRuntime.maxCharges(boots),ready=SecondaryRuntime.charges(mc.player,boots);
        float next=SecondaryRuntime.nextCharge(mc.player,boots,partial);
        long wait=SecondaryRuntime.refill(mc.player,boots);
        int interval=Math.max(1,boots.getTag().getInt("GunGangCooldownTicks"));
        String caption=Lang.tr("헤르메스")+(ready<max?" · "+AuxBar.seconds(Math.max(0,(wait-1)%interval+1-partial)):"");
        AuxBar.cells(g,center,y,width,height,AuxBar.Kind.COOLDOWN,ready,max,next,ready+"/"+max,caption,partial);
    }
    static void placements(GuiGraphics g,int x,int y) {
        var font=Minecraft.getInstance().font;
        if(state.equipped().equals("landmine")||state.mines()>0) {
            g.drawString(font,Lang.tr("지뢰: ")+state.mines()+"/"+state.maxMines(),x,y,state.mines()>=state.maxMines()?0xFFFF5555:0xFFFFFFFF,true);y+=12;
        }
        if(state.equipped().equals("barrier")||state.barriers()>0) {
            g.drawString(font,Lang.tr("방벽: ")+state.barriers()+"/"+state.maxBarriers(),x,y,state.barriers()>=state.maxBarriers()?0xFFFF5555:0xFFFFFFFF,true);y+=12;
        }
        if(state.equipped().equals("bear_trap")||state.bearTraps()>0)
            g.drawString(font,Lang.tr("베어트랩: ")+state.bearTraps()+"/"+state.maxBearTraps(),x,y,state.bearTraps()>=state.maxBearTraps()?0xFFFF5555:0xFFFFFFFF,true);
    }
    static int placementWidth() {
        var font=Minecraft.getInstance().font;int width=0;
        if(state.equipped().equals("landmine")||state.mines()>0)width=font.width(Lang.tr("지뢰: ")+state.mines()+"/"+state.maxMines());
        if(state.equipped().equals("barrier")||state.barriers()>0)width=Math.max(width,font.width(Lang.tr("방벽: ")+state.barriers()+"/"+state.maxBarriers()));
        if(state.equipped().equals("bear_trap")||state.bearTraps()>0)width=Math.max(width,font.width(Lang.tr("베어트랩: ")+state.bearTraps()+"/"+state.maxBearTraps()));
        return width==0?0:width+8;
    }
    private UtilityHud() { }
}

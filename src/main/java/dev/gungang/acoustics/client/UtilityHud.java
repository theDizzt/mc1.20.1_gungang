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
    private static final net.minecraft.resources.ResourceLocation OBSERVED_ICON=new net.minecraft.resources.ResourceLocation("gungang","textures/gui/m95_observed.png");
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
    /**
     * Fill colours of the stacked secondary bars, from the one nearest the health bar: blue (the original), then
     * pastel green, yellow, orange and red; a sixth starts over.
     */
    static final int[] GAUGE_COLORS={0xFF65D9FF,0xFF8EE6A6,0xFFF2E17D,0xFFFFBC85,0xFFFF9A9A};
    private static int drawn;
    private static int nextColor(){return GAUGE_COLORS[drawn++%GAUGE_COLORS.length];}
    static void gauges(GuiGraphics g,int center,int healthBarY,int width,int height,float partial) {
        drawn=0;
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator())return;
        int y=healthBarY-36-height;
        y=PropAbilityHud.gauges(g,center,y,width,height,partial);
        var blade=mc.player.getMainHandItem();
        if(blade.getItem() instanceof RapierItem&&blade.hasTag()){
            var tag=blade.getTag();int stacks=tag.getInt(RapierItem.COMBO);
            float left=tag.getLong(RapierItem.COMBO_END)-mc.level.getGameTime()-partial;
            if(stacks>0&&left>0&&left<=RapierCombo.DURATION){
                String label=String.format(java.util.Locale.ROOT,Lang.tr("콤보 %d/5 · ×%.2f · %.1fs"),stacks,RapierCombo.damage(stacks)/21f,left/20f);
                gauge(g,center,y,width,height,left,RapierCombo.DURATION,label,partial);y-=height+4;
            }
        }
        var boots=HermesBootsLayer.worn(mc.player);
        if(!boots.isEmpty()&&SecondaryRuntime.secondary(boots)){chargeGauge(g,center,y,width,height,boots,partial);y-=height+4;}
        var awn=PropAwn.is(mc.player.getMainHandItem())?mc.player.getMainHandItem():mc.player.getOffhandItem();
        if(PropAwn.is(awn)){
            long remaining=SecondaryRuntime.remaining(mc.player,awn);
            if(remaining>0){gauge(g,center,y,width,height,remaining,600,Lang.tr("AWN 재장전 ")+(remaining+19)/20+Lang.tr("초"),partial);y-=height+4;}
        }
        var protection=FreeForAllHud.row(mc.player.getUUID());
        if(protection!=null&&protection.protectionTicks()>0){
            gauge(g,center,y,width,height,protection.protectionTicks(),protection.protectionTotal(),Lang.tr("무적 ")+(protection.protectionTicks()+19)/20+Lang.tr("초"),partial);
            y-=height+4;
        }
        String flagRange="";
        CombatGadgets.Gauge turretAmmo=null;
        for(var gauge:state.gauges())if(gauge.kind().equals("turret_ammo"))turretAmmo=gauge;
        for(var gauge:state.gauges()) {
            if(gauge.kind().equals("command_flag_range")){flagRange=" · "+HudHealth.number(gauge.remaining())+Lang.tr("블럭");continue;}
            if(gauge.kind().equals("command_flag_health")){
                gauge(g,center,y,width,height,gauge.remaining(),gauge.maximum(),Lang.tr("깃발 체력 ")+HudHealth.number(gauge.remaining())+"/"+HudHealth.number(gauge.maximum()),partial);y-=height+4;continue;
            }
            if(gauge.kind().equals("command_flag_age")){
                float age=gauge.remaining()+Math.max(0,mc.level.getGameTime()-received+partial);
                gauge(g,center,y,width,height,age,gauge.maximum(),Lang.tr("깃발 생존 ")+(int)(age/20)+Lang.tr("초")+(age>=gauge.maximum()?Lang.tr(" · 모든 아군"):flagRange),partial);y-=height+4;continue;
            }
            if(gauge.kind().equals("m95_observed"))continue;
            if(gauge.kind().equals("m95_scope")){
                float remaining=Math.max(0,gauge.remaining()-(mc.level.getGameTime()-received+partial));
                String label=remaining>0?String.format(java.util.Locale.ROOT,Lang.tr("투시까지 %.1f초"),remaining/20):Lang.tr("투시 준비 완료");
                gauge(g,center,y,width,height,gauge.maximum()-remaining,gauge.maximum(),label,partial);
                y-=height+4;continue;
            }
            if(gauge.kind().equals("m95")){
                gauge(g,center,y,width,height,gauge.remaining(),gauge.maximum(),Lang.tr("관통력 ")+HudHealth.number(gauge.remaining())+"/"+HudHealth.number(gauge.maximum()),partial);
                y-=height+4;continue;
            }
            if(gauge.kind().equals("turret_ammo"))continue;
            if(gauge.kind().equals("turret_health")) {
                // Turret: its health on the bar, its magazine as a line above.
                gauge(g,center,y,width,height,gauge.remaining(),gauge.maximum(),Lang.tr("터렛 체력 ")+HudHealth.number(gauge.remaining())+"/"+HudHealth.number(gauge.maximum()),partial);
                if(turretAmmo!=null)y=turretAmmo(g,center,y,turretAmmo);
                y-=height+4;continue;
            }
            float remaining=gauge.remaining();if(gauge.timed())remaining-=Math.max(0,mc.level.getGameTime()-received+partial);
            if(remaining<=0)continue;
            if(gauge.kind().equals("shield")&&!CombatGadgets.holdingShield(mc.player))continue;
            String name=switch(gauge.kind()){case "bomb_plant"->Lang.tr("폭탄 설치");case "bomb_defuse"->Lang.tr("폭탄 해체");case "shield"->Lang.tr("방패");case "infrared_scanner"->Lang.tr("스캐너");case "jetpack"->Lang.tr("제트팩");case "bear_trap_recall"->Lang.tr("베어트랩 회수");case "bear_trap_root"->Lang.tr("베어트랩 속박");default->Lang.tr("아드레날린");};
            String value=gauge.timed()?String.format(java.util.Locale.ROOT,"%.1fs",remaining/20f):HudHealth.number(remaining)+"/"+HudHealth.number(gauge.maximum());
            gauge(g,center,y,width,height,remaining,gauge.maximum(),name+" "+value,partial);
            y-=height+4;
        }
        for(var gauge:state.gauges())if(gauge.kind().equals("m95_observed")&&gauge.remaining()-(mc.level.getGameTime()-received+partial)>0){
            observedWarning(g,center,y);break;
        }
    }
    private static void observedWarning(GuiGraphics g,int center,int aboveY){
        var mc=Minecraft.getInstance();String label=Lang.tr("관측됨");
        int textWidth=(int)Math.ceil(mc.font.width(label)*1.5f);
        int panelWidth=Math.max(108,textWidth+44),left=center-panelWidth/2,top=Math.max(6,aboveY-24);
        boolean bright=(mc.level.getGameTime()/4)%2==0;
        int edge=bright?0xFFFF5252:0xFFAE3038;
        g.fill(left,top,left+panelWidth,top+30,0xEE25090D);
        g.fill(left,top,left+panelWidth,top+2,edge);g.fill(left,top+28,left+panelWidth,top+30,edge);
        g.fill(left,top,left+2,top+30,edge);g.fill(left+panelWidth-2,top,left+panelWidth,top+30,edge);
        g.blit(OBSERVED_ICON,left+4,top+3,24,24,0f,0f,64,64,64,64);
        g.pose().pushPose();g.pose().translate(left+34,top+9,0);g.pose().scale(1.5f,1.5f,1);
        g.drawString(mc.font,label,0,0,bright?0xFFFFFFFF:0xFFFFB4B4,true);g.pose().popPose();
    }
    static void gauge(GuiGraphics g,int center,int y,int width,int height,float remaining,float maximum,String label,float partial) {
        float fraction=Math.min(1,remaining/Math.max(1,maximum));int x=center-width/2;
        ShooterHud.card(g,x,y,width,height,ShooterHud.BAR_EDGE,ShooterHud.SURFACE,0);
        HudHealthBars.fill(g,x+1,y+1,width-2,height-2,fraction,nextColor());
        HudHealthBars.stripes(g,x+1,y+1,width-2,height-2,fraction,((Minecraft.getInstance().player==null?0:Minecraft.getInstance().player.tickCount)+partial)/20.0);
        ShooterHud.finish(g,x+1,y+1,width-2,height-2,fraction);
        HudText.gungangOutlined(g,label,x+3,y+1,width-6,height-2,0xFFFFFFFF);
    }
    /**
     * 74 Hermes' Boots charges: the bar split into one cell per stored dash. Ready cells are full; the one refilling
     * fills up dimmer, and the label gives the count and the time to the next charge.
     */
    private static void chargeGauge(GuiGraphics g,int center,int y,int width,int height,net.minecraft.world.item.ItemStack boots,float partial) {
        var mc=Minecraft.getInstance();
        int max=SecondaryRuntime.maxCharges(boots),ready=SecondaryRuntime.charges(mc.player,boots);
        float next=SecondaryRuntime.nextCharge(mc.player,boots,partial);
        int x=center-width/2,gap=2,inner=width-2,cell=(inner-gap*(max-1))/max,color=nextColor();
        ShooterHud.card(g,x,y,width,height,ShooterHud.BAR_EDGE,ShooterHud.SURFACE,0);
        double seconds=(mc.player.tickCount+partial)/20.0;
        for(int i=0;i<max;i++){
            int cx=x+1+i*(cell+gap),cw=i==max-1?x+width-1-cx:cell;
            if(i>0)g.fill(cx-gap,y+1,cx,y+height-1,ShooterHud.BAR_EDGE);
            float fill=i<ready?1:i==ready?next:0;
            HudHealthBars.fill(g,cx,y+1,cw,height-2,fill,i<ready?color:(color&0xFFFFFF)|0x80000000);
            if(i<ready)HudHealthBars.stripes(g,cx,y+1,cw,height-2,1,seconds);
            ShooterHud.finish(g,cx,y+1,cw,height-2,fill);
        }
        long wait=SecondaryRuntime.refill(mc.player,boots);
        int interval=Math.max(1,boots.getTag().getInt("GunGangCooldownTicks"));
        String label=Lang.tr("헤르메스 ")+ready+"/"+max+(ready<max?String.format(java.util.Locale.ROOT," · %.1fs",Math.max(0,((wait-1)%interval+1-partial)/20f)):"");
        HudText.gungangOutlined(g,label,x+3,y+1,width-6,height-2,0xFFFFFFFF);
    }
    /** "탄약: 120/120 (100%)" centred just above the turret bar at {@code barY}, digits in the GUNGANG number font; returns the new top. */
    private static int turretAmmo(GuiGraphics g,int center,int barY,CombatGadgets.Gauge ammo) {
        int count=Math.round(ammo.remaining()),capacity=Math.max(1,Math.round(ammo.maximum()));
        int percent=Math.round(100f*count/capacity);
        var text=HudText.gungang(Lang.tr("탄약: ")+count+"/"+capacity+" ("+percent+"%)");
        // The number font's glyphs fill the whole 9-pixel line; the Hangul label keeps the regular font.
        var font=Minecraft.getInstance().font;int width=font.width(text),y=barY-11;
        int color=count<=0?0xFFFF5555:count<=capacity*.25?0xFFFFFF55:0xFFFFFFFF;
        g.drawString(font,text,center-width/2+1,y+1,0x80000000,false);
        g.drawString(font,text,center-width/2,y,color,false);
        return y;
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

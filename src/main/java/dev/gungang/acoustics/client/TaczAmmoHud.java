package dev.gungang.acoustics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.GungangAcoustics;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces TaCZ's ammo overlay with a minimal one: gun silhouette, current magazine and weapon name.
 * TaCZ is optional and reached by reflection; if its API differs, TaCZ's own overlay is left untouched.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TaczAmmoHud {
    private static final ResourceLocation OVERLAY=new ResourceLocation("tacz","tac_gun_hud_overlay");
    /** Fixed units, like ShooterHud: two framebuffer pixels each, independent of GUI scale. */
    private static final int MARGIN=18,ICON_W=39,ICON_H=13,BAR_GAP=1,BAR_H=2,SHADOW=0x66000000;
    private static final float ICON_SCALE=2f,ICON_ALPHA=.7f;
    static final float AMMO_SCALE=2.5f;
    /** Shared gun/prop/melee coordinates in fixed two-pixel HUD units. */
    record Layout(int right,int countW,int countTop,int barTop,int rowTop,int iconX,int iconY){}
    static Layout layout(int places){
        var mc=Minecraft.getInstance();int right=mc.getWindow().getWidth()/2-MARGIN,bottom=mc.getWindow().getHeight()/2-MARGIN;
        int w=Math.round(places*digitCell(mc.font)*AMMO_SCALE),countH=Math.round(8*AMMO_SCALE);
        int iconW=Math.round(ICON_W*ICON_SCALE),iconH=Math.round(ICON_H*ICON_SCALE),rowH=Math.max(countH,iconH);
        int countTop=(rowH-countH)/2,barTop=countTop+Math.round(7*AMMO_SCALE)+BAR_GAP+1;
        int rowTop=bottom-Math.max(rowH,barTop+BAR_H);
        return new Layout(right,w,countTop+rowTop,barTop+rowTop,rowTop,right-w-6-iconW,rowTop+(rowH-iconH)/2);
    }
    static void bar(GuiGraphics g,Layout l,float ratio,int color){
        int left=l.right()-l.countW(),fill=Math.round(l.countW()*Math.max(0,Math.min(1,ratio)));
        g.fill(left+1,l.barTop()+1,l.right()+1,l.barTop()+BAR_H+1,SHADOW);
        g.fill(left,l.barTop(),l.right(),l.barTop()+BAR_H,0x803A3B32);
        g.fill(left,l.barTop(),left+fill,l.barTop()+BAR_H,color);
    }
    /** Guns without a real magazine (LAS-5's capacity is Integer.MAX_VALUE): the count shows heat in percent instead. */
    private static final java.util.Set<String> HEAT_GAUGE=java.util.Set.of("zeta:las_5");
    private static Api api;
    /** Same fixed-pixel position and number styling as ammo; independent of TaCZ being installed. */
    public static void rapier(net.minecraftforge.client.gui.overlay.ForgeGui gui,GuiGraphics g,float partial,int screenWidth,int screenHeight){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        if(PropAbilityHud.ammo(g,partial))return;
        if(mc.player.isSpectator())return;
        var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        drawRapier(g,dev.gungang.acoustics.server.RapierItem.gauge(stack));
    }
    static void drawRapier(GuiGraphics g,int amount){
        var mc=Minecraft.getInstance();amount=Math.max(0,Math.min(100,amount));
        int color=amount<20?0xFFFF6565:TacticalStyle.GOLD;
        var l=layout(3);int right=l.right(),w=l.countW(),top=l.countTop();
        float scale=(float)(2.0/mc.getWindow().getGuiScale());g.pose().pushPose();g.pose().scale(scale,scale,1);
        rapierSilhouette(g,l.iconX(),l.iconY()+6,0xB3000000);
        text(g,mc.font,"레이피어 · 대시 20",right-mc.font.width("레이피어 · 대시 20"),l.rowTop()-11,TacticalStyle.INK);
        String value=String.valueOf(amount),padding="0".repeat(3-value.length());
        g.pose().pushPose();g.pose().translate(right-w,top,0);g.pose().scale(AMMO_SCALE,AMMO_SCALE,1);
        digits(g,mc.font,padding,0,TacticalStyle.DIM);digits(g,mc.font,value,padding.length(),color);g.pose().popPose();
        bar(g,l,amount/100f,color);
        // Cost marker: immediately see whether the next dash is available.
        int mark=right-w+Math.round(w*.20f);g.fill(mark,l.barTop()-1,mark+1,l.barTop()+BAR_H+1,TacticalStyle.INK);
        g.pose().popPose();
    }
    private static boolean unavailable;
    private static void rapierSilhouette(GuiGraphics g,int x,int y,int color){
        // Long tapered blade, swept knuckle guard, grip and pommel.
        g.fill(x+24,y+5,x+70,y+7,color);g.fill(x+70,y+5,x+77,y+6,color);
        g.fill(x+18,y,x+20,y+13,color);g.fill(x+9,y+5,x+24,y+8,color);
        g.fill(x+5,y+4,x+9,y+9,color);
        g.fill(x+11,y+1,x+18,y+2,color);g.fill(x+9,y+2,x+11,y+5,color);
        g.fill(x+11,y+11,x+18,y+12,color);g.fill(x+9,y+8,x+11,y+11,color);
        g.fill(x+20,y+2,x+25,y+3,color);g.fill(x+24,y+3,x+26,y+10,color);g.fill(x+20,y+10,x+25,y+11,color);
    }
    private record Api(Method getIGun,Method current,Method barrel,Method gunId,Method display,Method hud,Method hudEmpty,
                       Method index,Method gunData,Method bolt,Method capacity,Object hudEnabled,Method inventoryAmmo,Method refresh,Method fireMode,Method fireModes,Heat heat) {
        boolean enabled() throws ReflectiveOperationException {return hudEnabled==null||(Boolean)hudEnabled.getClass().getMethod("get").invoke(hudEnabled);}
    }
    /** TaCZ heat API; null on builds without it, which keep the ammo count. */
    private record Heat(Method amount,Method locked,Method data,Method max) { }
    private static Api api() {
        if(api!=null||unavailable)return api;
        try {
            Class<?> gun=Class.forName("com.tacz.guns.api.item.IGun"),timeless=Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> display=Class.forName("com.tacz.guns.client.resource.GunDisplayInstance"),index=Class.forName("com.tacz.guns.client.resource.index.ClientGunIndex");
            Class<?> data=Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData"),attachments=Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            Object hudEnabled=null;
            try {hudEnabled=Class.forName("com.tacz.guns.config.client.RenderConfig").getField("GUN_HUD_ENABLE").get(null);} catch(ReflectiveOperationException ignored) { }
            Method refresh=null;
            try {
                // TaCZ refreshes the displayed count of inventory-fed guns inside its overlay; keep doing that.
                refresh=Class.forName("com.tacz.guns.client.gui.overlay.GunHudOverlay").getDeclaredMethod("handleCacheCount",
                        net.minecraft.client.player.LocalPlayer.class,ItemStack.class,data,gun,boolean.class);
                refresh.setAccessible(true);
            } catch(ReflectiveOperationException|RuntimeException ignored) {refresh=null;}
            Heat heat=null;
            try {
                heat=new Heat(gun.getMethod("getHeatAmount",ItemStack.class),gun.getMethod("isOverheatLocked",ItemStack.class),data.getMethod("getHeatData"),
                        Class.forName("com.tacz.guns.resource.pojo.data.gun.GunHeatData").getMethod("getHeatMax"));
            } catch(ReflectiveOperationException|LinkageError ignored) {heat=null;}
            api=new Api(gun.getMethod("getIGunOrNull",ItemStack.class),gun.getMethod("getCurrentAmmoCount",ItemStack.class),gun.getMethod("hasBulletInBarrel",ItemStack.class),
                    gun.getMethod("getGunId",ItemStack.class),timeless.getMethod("getGunDisplay",ItemStack.class),display.getMethod("getHUDTexture"),display.getMethod("getHudEmptyTexture"),
                    timeless.getMethod("getClientGunIndex",ResourceLocation.class),index.getMethod("getGunData"),data.getMethod("getBolt"),
                    attachments.getMethod("getAmmoCountWithAttachment",ItemStack.class,data),hudEnabled,gun.getMethod("useInventoryAmmo",ItemStack.class),refresh,
                    gun.getMethod("getFireMode",ItemStack.class),data.getMethod("getFireModeSet"),heat);
        } catch(ReflectiveOperationException|LinkageError missing) {unavailable=true;}
        return api;
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event) {
        if(!event.getOverlay().id().equals(OVERLAY))return;
        if(PropHuntClient.hider()){event.setCanceled(true);return;}
        var api=api();if(api==null)return;
        try {
            event.setCanceled(true);
            if(api.enabled())draw(event.getGuiGraphics(),api);
        } catch(ReflectiveOperationException|RuntimeException failure) {
            // Unexpected TaCZ build: stop replacing and let its own overlay draw from the next frame.
            unavailable=true;TaczAmmoHud.api=null;
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] TaCZ ammo HUD replacement disabled: {}",failure.toString());
        }
    }
    private static void draw(GuiGraphics g,Api api) throws ReflectiveOperationException {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        ItemStack stack=mc.player.getMainHandItem();
        Object gun=api.getIGun().invoke(null,stack);if(gun==null)return;
        Optional<?> display=(Optional<?>)api.display().invoke(null,stack);if(display.isEmpty())return;
        Object gunId=api.gunId().invoke(gun,stack);
        Optional<?> index=(Optional<?>)api.index().invoke(null,gunId);
        Object data=index.isPresent()?api.gunData().invoke(index.get()):null;
        if(data!=null&&api.refresh()!=null)api.refresh().invoke(null,mc.player,stack,data,gun,api.inventoryAmmo().invoke(gun,stack));
        int ammo,capacity,color;boolean alert;
        Object heatData=data!=null&&api.heat()!=null&&HEAT_GAUGE.contains(String.valueOf(gunId))?api.heat().data().invoke(data):null;
        if(heatData!=null) {
            // Heat gauge: 000-100 percent, yellow from 75, red while overheat-locked.
            float max=((Number)api.heat().max().invoke(heatData)).floatValue(),heat=((Number)api.heat().amount().invoke(gun,stack)).floatValue();
            ammo=max>0?Math.round(Math.max(0,Math.min(1,heat/max))*100):0;capacity=100;
            alert=(Boolean)api.heat().locked().invoke(gun,stack);
            color=alert?0xFFFF5555:ammo>=75?0xFFFFFF55:0xFFFFFFFF;
        } else {
            // A round chambered in a closed-bolt gun counts toward the magazine, as in TaCZ's own HUD.
            boolean openBolt=data!=null&&"OPEN_BOLT".equals(String.valueOf(api.bolt().invoke(data)));
            ammo=(Integer)api.current().invoke(gun,stack)+((Boolean)api.barrel().invoke(gun,stack)&&!openBolt?1:0);
            capacity=data==null?0:(Integer)api.capacity().invoke(null,stack,data);
            alert=ammo<=0;
            color=alert?0xFFFF5555:capacity>0&&ammo<=capacity*.25?0xFFFFFF55:0xFFFFFFFF;
        }
        // Most gun packs define no empty silhouette (null), e.g. while a reload has emptied the magazine: use the normal one tinted red.
        var texture=(ResourceLocation)api.hud().invoke(display.get());
        if(alert){var empty=(ResourceLocation)api.hudEmpty().invoke(display.get());if(empty!=null)texture=empty;}

        float scale=(float)(2.0/mc.getWindow().getGuiScale());
        int width=mc.getWindow().getWidth()/2,height=mc.getWindow().getHeight()/2,right=width-MARGIN,bottom=height-MARGIN;
        var font=mc.font;String count=String.valueOf(ammo);
        // Always three digits so the number keeps one width from full to empty.
        int places=Math.max(3,count.length());
        String padding="0".repeat(places-count.length());
        var layout=layout(places);int countW=layout.countW(),countTop=layout.countTop(),barTop=layout.barTop(),rowTop=layout.rowTop();
        int iconW=Math.round(ICON_W*ICON_SCALE),iconH=Math.round(ICON_H*ICON_SCALE);
        g.pose().pushPose();g.pose().scale(scale,scale,1);
        if(texture!=null) {
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            if(alert)RenderSystem.setShaderColor(1,.3f,.3f,ICON_ALPHA);else RenderSystem.setShaderColor(1,1,1,ICON_ALPHA);
            int ix=layout.iconX(),iy=layout.iconY();
            g.blit(texture,ix,iy,iconW,iconH,0,0,ICON_W,ICON_H,ICON_W,ICON_H);
            RenderSystem.setShaderColor(1,1,1,1);
        }
        float ratio=capacity>0?Math.max(0,Math.min(1,ammo/(float)capacity)):ammo>0?1:0;
        int fill=Math.round(countW*ratio);
        g.fill(right-countW+1,barTop+1,right+1,barTop+BAR_H+1,SHADOW);
        g.fill(right-countW,barTop,right,barTop+BAR_H,0x803A3B32);
        g.fill(right-countW,barTop,right-countW+fill,barTop+BAR_H,color);
        g.pose().pushPose();g.pose().translate(right-countW,countTop,0);g.pose().scale(AMMO_SCALE,AMMO_SCALE,1);
        // An empty magazine shows a solid red "000" instead of dimmed padding.
        digits(g,font,padding,0,alert?color:ArenaScreen.blend(0xFF1A2230,color,.35f));
        digits(g,font,count,padding.length(),color);g.pose().popPose();
        // Fire selector to the right of the name: every mode the gun has, the current one lit.
        String current=String.valueOf(api.fireMode().invoke(gun,stack));
        var modes=new java.util.ArrayList<String>();
        if(data!=null&&api.fireModes().invoke(data) instanceof java.util.List<?> set)for(Object mode:set)if(!"UNKNOWN".equals(String.valueOf(mode)))modes.add(String.valueOf(mode));
        if(modes.isEmpty()&&!"UNKNOWN".equals(current))modes.add(current);
        int x=right;
        for(int i=modes.size()-1;i>=0;i--) {
            String mode=modes.get(i);x-=font.width(mode);
            text(g,font,mode,x,rowTop-11,mode.equals(current)?0xFFFFFFFF:0xFF5A6474);
            if(i>0)x-=font.width(" ");
        }
        int nameRight=modes.isEmpty()?right:x-8;
        String name=font.plainSubstrByWidth(stack.getHoverName().getString(),Math.max(1,width/2-MARGIN-(right-nameRight)));
        text(g,font,name,nameRight-font.width(name),rowTop-11,0xFFE2E8F0);
        g.pose().popPose();
    }
    /** Width of the widest number-font digit, so every digit gets the same cell. */
    private static int digitCell(net.minecraft.client.gui.Font font) {
        int cell=0;for(char c='0';c<='9';c++)cell=Math.max(cell,HudText.numberWidth(String.valueOf(c)));return cell;
    }
    /**
     * Tabular digits: each character centred in a fixed cell so the count does not shift as it changes.
     * No drop shadow: the number font's counters and gaps are one pixel wide, so a 1px shadow fills them as dark lines.
     */
    static void digits(GuiGraphics g,net.minecraft.client.gui.Font font,String text,int firstCell,int color) {
        int cell=digitCell(font);
        for(int i=0;i<text.length();i++) {
            String c=String.valueOf(text.charAt(i));int x=(firstCell+i)*cell+(cell-HudText.numberWidth(c))/2;
            g.drawString(font,HudText.number(c),x,0,color,false);
        }
    }
    /** Text with a translucent drop shadow instead of vanilla's opaque one. */
    static void text(GuiGraphics g,net.minecraft.client.gui.Font font,String text,int x,int y,int color) {
        g.drawString(font,text,x+1,y+1,SHADOW,false);
        g.drawString(font,text,x,y,color,false);
    }
    private TaczAmmoHud() { }
}

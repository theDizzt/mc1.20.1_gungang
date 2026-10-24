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
    private static final float ICON_SCALE=2f,AMMO_SCALE=2.5f,ICON_ALPHA=.7f;
    private static Api api;
    private static boolean unavailable;
    private record Api(Method getIGun,Method current,Method barrel,Method gunId,Method display,Method hud,Method hudEmpty,
                       Method index,Method gunData,Method bolt,Method capacity,Object hudEnabled,Method inventoryAmmo,Method refresh,Method fireMode,Method fireModes) {
        boolean enabled() throws ReflectiveOperationException {return hudEnabled==null||(Boolean)hudEnabled.getClass().getMethod("get").invoke(hudEnabled);}
    }
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
            api=new Api(gun.getMethod("getIGunOrNull",ItemStack.class),gun.getMethod("getCurrentAmmoCount",ItemStack.class),gun.getMethod("hasBulletInBarrel",ItemStack.class),
                    gun.getMethod("getGunId",ItemStack.class),timeless.getMethod("getGunDisplay",ItemStack.class),display.getMethod("getHUDTexture"),display.getMethod("getHudEmptyTexture"),
                    timeless.getMethod("getClientGunIndex",ResourceLocation.class),index.getMethod("getGunData"),data.getMethod("getBolt"),
                    attachments.getMethod("getAmmoCountWithAttachment",ItemStack.class,data),hudEnabled,gun.getMethod("useInventoryAmmo",ItemStack.class),refresh,
                    gun.getMethod("getFireMode",ItemStack.class),data.getMethod("getFireModeSet"));
        } catch(ReflectiveOperationException|LinkageError missing) {unavailable=true;}
        return api;
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event) {
        if(!event.getOverlay().id().equals(OVERLAY))return;
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
        Optional<?> index=(Optional<?>)api.index().invoke(null,api.gunId().invoke(gun,stack));
        Object data=index.isPresent()?api.gunData().invoke(index.get()):null;
        if(data!=null&&api.refresh()!=null)api.refresh().invoke(null,mc.player,stack,data,gun,api.inventoryAmmo().invoke(gun,stack));
        // A round chambered in a closed-bolt gun counts toward the magazine, as in TaCZ's own HUD.
        boolean openBolt=data!=null&&"OPEN_BOLT".equals(String.valueOf(api.bolt().invoke(data)));
        int ammo=(Integer)api.current().invoke(gun,stack)+((Boolean)api.barrel().invoke(gun,stack)&&!openBolt?1:0);
        int capacity=data==null?0:(Integer)api.capacity().invoke(null,stack,data);
        // Most gun packs define no empty silhouette (null), e.g. while a reload has emptied the magazine: use the normal one tinted red.
        var texture=(ResourceLocation)api.hud().invoke(display.get());
        if(ammo<=0){var empty=(ResourceLocation)api.hudEmpty().invoke(display.get());if(empty!=null)texture=empty;}

        float scale=(float)(2.0/mc.getWindow().getGuiScale());
        int width=mc.getWindow().getWidth()/2,height=mc.getWindow().getHeight()/2,right=width-MARGIN,bottom=height-MARGIN;
        var font=mc.font;String count=String.valueOf(ammo);
        // Always three digits so the number keeps one width from full to empty.
        int places=Math.max(3,count.length());
        String padding="0".repeat(places-count.length());
        int countW=Math.round(places*digitCell(font)*AMMO_SCALE),countH=Math.round(8*AMMO_SCALE);
        int iconW=Math.round(ICON_W*ICON_SCALE),iconH=Math.round(ICON_H*ICON_SCALE),rowH=Math.max(countH,iconH);
        // The magazine bar sits just under the digits (glyphs are 7 of the 8 font rows), as wide as the count.
        int countTop=(rowH-countH)/2,barTop=countTop+Math.round(7*AMMO_SCALE)+BAR_GAP+1,rowTop=bottom-Math.max(rowH,barTop+BAR_H);
        countTop+=rowTop;barTop+=rowTop;
        int color=ammo<=0?0xFFFF5555:capacity>0&&ammo<=capacity*.25?0xFFFFFF55:0xFFFFFFFF;
        g.pose().pushPose();g.pose().scale(scale,scale,1);
        if(texture!=null) {
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            if(ammo<=0)RenderSystem.setShaderColor(1,.3f,.3f,ICON_ALPHA);else RenderSystem.setShaderColor(1,1,1,ICON_ALPHA);
            int ix=right-countW-6-iconW,iy=rowTop+(rowH-iconH)/2;
            g.blit(texture,ix,iy,iconW,iconH,0,0,ICON_W,ICON_H,ICON_W,ICON_H);
            RenderSystem.setShaderColor(1,1,1,1);
        }
        float ratio=capacity>0?Math.max(0,Math.min(1,ammo/(float)capacity)):ammo>0?1:0;
        int fill=Math.round(countW*ratio);
        g.fill(right-countW+1,barTop+1,right+1,barTop+BAR_H+1,SHADOW);
        g.fill(right-countW,barTop,right,barTop+BAR_H,0x80303A4A);
        g.fill(right-countW,barTop,right-countW+fill,barTop+BAR_H,color);
        g.pose().pushPose();g.pose().translate(right-countW,countTop,0);g.pose().scale(AMMO_SCALE,AMMO_SCALE,1);
        // An empty magazine shows a solid red "000" instead of dimmed padding.
        digits(g,font,padding,0,ammo<=0?color:ArenaScreen.blend(0xFF1A2230,color,.35f));
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
    private static void digits(GuiGraphics g,net.minecraft.client.gui.Font font,String text,int firstCell,int color) {
        int cell=digitCell(font);
        for(int i=0;i<text.length();i++) {
            String c=String.valueOf(text.charAt(i));int x=(firstCell+i)*cell+(cell-HudText.numberWidth(c))/2;
            g.drawString(font,HudText.number(c),x,0,color,false);
        }
    }
    /** Text with a translucent drop shadow instead of vanilla's opaque one. */
    private static void text(GuiGraphics g,net.minecraft.client.gui.Font font,String text,int x,int y,int color) {
        g.drawString(font,text,x+1,y+1,SHADOW,false);
        g.drawString(font,text,x,y,color,false);
    }
    private TaczAmmoHud() { }
}

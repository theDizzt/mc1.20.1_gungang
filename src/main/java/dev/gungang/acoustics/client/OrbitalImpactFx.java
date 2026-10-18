package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Camera shake and a white-hot screen flash for an orbital strike, scaled by distance to the impact. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class OrbitalImpactFx {
    private static final float SHAKE_SECONDS=1.6f,FLASH_SECONDS=.6f,SHAKE_RANGE=110,FLASH_RANGE=140;
    private static long started;
    private static float shake,flash;

    public static void trigger(Vec3 impact) {
        var player=Minecraft.getInstance().player;
        if(player==null)return;
        double distance=player.getEyePosition().distanceTo(impact);
        float nextShake=(float)Math.max(0,1-distance/SHAKE_RANGE),nextFlash=(float)Math.max(0,1-distance/FLASH_RANGE);
        if(nextShake<=0&&nextFlash<=0)return;
        // A second strike while the first is still shaking keeps the stronger of the two.
        if(active())nextShake=Math.max(nextShake,shake*(1-elapsed()/SHAKE_SECONDS));
        shake=nextShake;flash=nextFlash*nextFlash;started=System.nanoTime();
    }
    private static float elapsed() { return (System.nanoTime()-started)/1e9f; }
    private static boolean active() { return started!=0&&elapsed()<SHAKE_SECONDS; }

    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(!active()||shake<=0)return;
        float t=elapsed()/SHAKE_SECONDS,decay=(1-t)*(1-t),amplitude=6*shake*decay,time=elapsed();
        // Incommensurate frequencies read as rumble rather than a regular wobble.
        event.setPitch(event.getPitch()+amplitude*(float)(Math.sin(time*47)*.6+Math.sin(time*83)*.4));
        event.setYaw(event.getYaw()+amplitude*.7f*(float)(Math.sin(time*39+1.3)*.6+Math.sin(time*71)*.4));
        event.setRoll(event.getRoll()+amplitude*.5f*(float)Math.sin(time*29+.7));
    }
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post event) {
        if(started==0||flash<=0)return;
        float t=elapsed()/FLASH_SECONDS;
        if(t>=1)return;
        // Pure white for an instant, then cooling to orange as it fades.
        float k=(1-t)*(1-t);
        int alpha=Math.round(235*flash*k);if(alpha<=4)return;
        int green=Math.round(255-70*t),blue=Math.round(250-200*t);
        var g=event.getGuiGraphics();
        g.fill(0,0,g.guiWidth(),g.guiHeight(),alpha<<24|0xFF0000|green<<8|blue);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { started=0;shake=flash=0; }
    private OrbitalImpactFx() { }
}

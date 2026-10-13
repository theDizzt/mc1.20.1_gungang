package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MapEnvironmentClient {
    private static MatchSnapshot state;
    public static void receive(MatchSnapshot value){state=value;}
    public static String weather(){var level=Minecraft.getInstance().level;return state!=null&&state.active()&&level!=null&&level.dimension().location().toString().equals(state.dimension())?state.weather():"";}
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){if("fog".equals(weather())&&e.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.NONE){e.setNearPlaneDistance(0);e.setFarPlaneDistance(32);e.setCanceled(true);}}
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor e){if("fog".equals(weather())&&e.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.NONE){e.setRed(.65f);e.setGreen(.69f);e.setBlue(.72f);}}
    private MapEnvironmentClient(){}
}

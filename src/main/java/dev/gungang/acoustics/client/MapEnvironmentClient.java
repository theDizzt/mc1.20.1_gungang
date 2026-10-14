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
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||weather().isEmpty())return;
        var level=Minecraft.getInstance().level;boolean wet=weather().equals("rain")||weather().equals("snow");
        level.setRainLevel(wet?1:0);level.setThunderLevel(0);
        level.setDayTime(switch(state.timeOfDay()==null?"day":state.timeOfDay()){case "dusk"->12000;case "night"->18000;case "dawn"->23000;default->6000;});
    }
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){if("fog".equals(weather())&&e.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.NONE){e.setNearPlaneDistance(0);e.setFarPlaneDistance(32);e.setCanceled(true);}}
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor e){if("fog".equals(weather())&&e.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.NONE){e.setRed(.65f);e.setGreen(.69f);e.setBlue(.72f);}}
    private MapEnvironmentClient(){}
}

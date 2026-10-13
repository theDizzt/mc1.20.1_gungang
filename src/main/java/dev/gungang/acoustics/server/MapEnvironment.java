package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MapEnvironment {
    private static ServerLevel level;
    private static long oldTime,time;
    private static int clear,rain,thunder;
    private static boolean raining,thundering,wet;
    public static void begin(MinecraftServer server,ArenaMap map) {
        restore();
        level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(map.dimension())));
        if(level==null)return;
        var data=server.getWorldData().overworldData();oldTime=level.getDayTime();clear=data.getClearWeatherTime();rain=data.getRainTime();thunder=data.getThunderTime();raining=data.isRaining();thundering=data.isThundering();
        time=switch(map.timeOfDay()){case "dusk"->12000;case "night"->18000;case "dawn"->23000;default->6000;};
        wet=map.weather().equals("rain")||map.weather().equals("snow");apply();
    }
    private static void apply(){if(level!=null){level.setDayTime(time);level.setWeatherParameters(wet?0:6000,6000,wet,false);}}
    public static void restore(){if(level!=null){level.setDayTime(oldTime);var d=level.getServer().getWorldData().overworldData();d.setClearWeatherTime(clear);d.setRainTime(rain);d.setThunderTime(thunder);d.setRaining(raining);d.setThundering(thundering);level=null;}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)apply();}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event){restore();}
    private MapEnvironment(){}
}

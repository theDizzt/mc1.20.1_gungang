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
    private static boolean raining,thundering,wet,snow;
    public static boolean preventSnowAccumulation(net.minecraft.world.level.LevelReader target){return level!=null&&level==target&&snow;}
    public static void begin(MinecraftServer server,ArenaMap map) {
        restore();
        level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(map.dimension())));
        if(level==null)return;
        var data=server.getWorldData().overworldData();oldTime=level.getDayTime();clear=data.getClearWeatherTime();rain=data.getRainTime();thunder=data.getThunderTime();raining=data.isRaining();thundering=data.isThundering();
        time=switch(map.timeOfDay()){case "dusk"->12000;case "night"->18000;case "dawn"->23000;default->6000;};
        snow=map.weather().equals("snow");wet=map.weather().equals("rain")||snow;apply();sync(level,true);
    }
    private static void apply(){if(level!=null){level.setDayTime(time);level.setWeatherParameters(wet?0:6000,6000,wet,false);level.setRainLevel(wet?1:0);level.setThunderLevel(0);if(level.getServer().getTickCount()%20==0)sync(level,true);}}
    private static void sync(ServerLevel target,boolean frozen){
        for(var player:target.players()){
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(target.getGameTime(),target.getDayTime(),!frozen&&target.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(target.isRaining()?net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING:net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING,0));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,target.getRainLevel(1)));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,target.getThunderLevel(1)));
        }
    }
    public static void resetDefaults(MinecraftServer server){
        restore();
        for(var world:server.getAllLevels()){world.setDayTime(6000);world.setWeatherParameters(6000,0,false,false);world.setRainLevel(0);world.setThunderLevel(0);sync(world,false);}
    }
    public static void restore(){if(level!=null){level.setDayTime(oldTime);var d=level.getServer().getWorldData().overworldData();d.setClearWeatherTime(clear);d.setRainTime(rain);d.setThunderTime(thunder);d.setRaining(raining);d.setThundering(thundering);level.setRainLevel(raining?1:0);level.setThunderLevel(thundering?1:0);sync(level,false);level=null;}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)apply();}
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent event){
        var server=event.getServer();server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
        for(var world:server.getAllLevels()){world.setDayTime(6000);sync(world,false);}
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event){restore();}
    private MapEnvironment(){}
}

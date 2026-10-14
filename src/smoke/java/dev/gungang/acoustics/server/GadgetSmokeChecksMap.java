package dev.gungang.acoustics.server;

final class GadgetSmokeChecksMap {
    static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        var level=server.overworld();var floor=new net.minecraft.core.BlockPos(12,200,12);var roof=floor.above(2);
        var oldFloor=level.getBlockState(floor);var oldRoof=level.getBlockState(roof);
        long oldTime=level.getDayTime();var oldSpawn=level.getSharedSpawnPos();float oldAngle=level.getSharedSpawnAngle();
        try{
            level.setBlock(floor,net.minecraft.world.level.block.Blocks.RED_WOOL.defaultBlockState(),3);
            level.setBlock(roof,net.minecraft.world.level.block.Blocks.BLUE_WOOL.defaultBlockState(),3);
            var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();
            json.add("previewArea",ServerFiles.GSON.toJsonTree(new MapPreviewArea(0,200,0,47,201,31)));
            json.addProperty("weather","snow");json.addProperty("timeOfDay","night");
            var map=ServerFiles.GSON.fromJson(json,ArenaMap.class).validate();
            var type=Class.forName("dev.gungang.acoustics.server.MapPreviewGenerator$Job");
            var ctor=type.getDeclaredConstructor(ArenaMap.class,net.minecraft.server.level.ServerLevel.class);ctor.setAccessible(true);
            var job=ctor.newInstance(map,level);var step=type.getDeclaredMethod("step");step.setAccessible(true);
            int ticks=0;while(!(boolean)step.invoke(job)){if(++ticks>10)throw new AssertionError("Preview chunk budget");}
            var finish=type.getDeclaredMethod("finish");finish.setAccessible(true);byte[] png=(byte[])finish.invoke(job);
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));int scale=image.getWidth()/48;int rgb=image.getRGB(12*scale,12*scale);
            if(((rgb>>16)&255)<=(rgb&255))throw new AssertionError("Preview must show red floor below excluded blue roof");
            var path=java.nio.file.Path.of("../build/ui-smoke/generated-map.png");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,png);
            MapEnvironment.begin(server,map);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            MapEnvironment.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server));
            if(level.getDayTime()!=18000||!level.isRaining()||level.getRainLevel(1)!=1)throw new AssertionError("Map environment must override external time/weather changes");
            var reset=LobbyManager.class.getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(null);
            if(level.getDayTime()!=6000||level.isRaining()||level.getRainLevel(1)!=0||level.isThundering())throw new AssertionError("Reset must apply clear noon");
            System.out.println("GUNGANG_MAP_SMOKE_PASS preview=png roof=cut environment=forced reset=clear-noon");
        }finally{MapEnvironment.restore();level.setBlock(floor,oldFloor,3);level.setBlock(roof,oldRoof,3);level.setDayTime(oldTime);level.setDefaultSpawnPos(oldSpawn,oldAngle);MapPreviewGenerator.clear();}
    }
}

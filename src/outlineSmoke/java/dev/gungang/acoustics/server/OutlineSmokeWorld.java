package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class OutlineSmokeWorld {
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))return;
        var level=p.serverLevel();
        for(int x=-10;x<=10;x++)for(int z=-5;z<=12;z++){
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=100;y<=105;y++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        for(int x=-7;x<=7;x++)for(int y=100;y<=102;y++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,2),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(level,0,100,9,180,0);level.setDayTime(6000);
    }
}

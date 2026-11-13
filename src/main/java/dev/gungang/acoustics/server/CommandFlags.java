package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class CommandFlags {
    private static final Set<CommandFlagEntity> FLAGS=new HashSet<>();
    private static final net.minecraft.server.level.TicketType<UUID> TICKET=net.minecraft.server.level.TicketType.create("gungang_command_flag",Comparator.<UUID>naturalOrder());
    static void track(CommandFlagEntity flag){
        if(FLAGS.add(flag)&&flag.level() instanceof net.minecraft.server.level.ServerLevel level)
            level.getChunkSource().addRegionTicket(TICKET,flag.chunkPosition(),2,flag.getUUID());
    }
    static void untrack(CommandFlagEntity flag){
        if(FLAGS.remove(flag)&&flag.level() instanceof net.minecraft.server.level.ServerLevel level)
            level.getChunkSource().removeRegionTicket(TICKET,flag.chunkPosition(),2,flag.getUUID());
    }
    public static List<CommandFlagEntity> owned(UUID owner){return FLAGS.stream().filter(f->f.isAlive()&&!f.isRemoved()&&owner.equals(f.owner())).sorted(Comparator.comparingInt(CommandFlagEntity::age).reversed()).toList();}
    public static void clear(UUID owner){for(var flag:owned(owner))flag.discard();}
    public static AABB bounds(net.minecraft.world.level.Level level,BlockPos pos){double y=PlacementSurface.height(level,pos);return new AABB(pos.getX()+.15,y,pos.getZ()+.15,pos.getX()+.85,y+2.5,pos.getZ()+.85);}
    private static java.util.List<ArenaMap.Area> clientSpawnAreas=java.util.List.of();
    public static void receiveSpawnAreas(java.util.List<ArenaMap.Area> areas){clientSpawnAreas=java.util.List.copyOf(areas);}
    public static boolean spawnBlocked(Player p,BlockPos pos){
        if(pos==null)return false;
        var box=bounds(p.level(),pos);
        if(!p.level().isClientSide)return LobbyManager.inEitherSpawn(p.level(),box);
        return clientSpawnAreas.stream().anyMatch(a->box.intersects(new AABB(a.minX(),a.minY(),a.minZ(),a.maxX(),a.maxY(),a.maxZ())));
    }
    public static boolean valid(Player p,BlockPos pos){return pos!=null&&!spawnBlocked(p,pos)&&GadgetManager.valid(p,pos,false,false)&&bounds(p.level(),pos).maxY<=p.level().getMaxBuildHeight()&&p.level().noCollision(bounds(p.level(),pos))&&p.level().getEntities(p,bounds(p.level(),pos),e->!e.isSpectator()).isEmpty();}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof net.minecraft.server.level.ServerPlayer p)||p.tickCount%20!=0)return;
        float heal=0;for(var flag:FLAGS)if(flag.affects(p))heal=Math.max(heal,flag.healing());
        if(heal>0)p.heal(heal);
    }
    /** Allied bots stand on no scoreboard team: they get the same healing on their own tick. */
    @SubscribeEvent public static void bot(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof BotEntity bot)||bot.level().isClientSide||bot.tickCount%20!=0||!BotManager.live(bot))return;
        float heal=0;for(var flag:FLAGS)if(flag.affects(bot))heal=Math.max(heal,flag.healing());
        if(heal>0)bot.heal(heal);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        var p=event.getEntity();
        if(!(p instanceof net.minecraft.server.level.ServerPlayer||p instanceof BotEntity)||event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        float reduction=0;for(var flag:FLAGS)if(flag.affects(p))reduction=Math.max(reduction,flag.reduction());
        if(reduction>0)event.setAmount(event.getAmount()*(1-reduction/100f));
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){FLAGS.clear();}
    private CommandFlags(){}
}

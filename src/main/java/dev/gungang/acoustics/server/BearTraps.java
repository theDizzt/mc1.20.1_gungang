package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owner → placed trap, and who is currently pinned. A trap whose chunk unloads is dropped, not kept as an invisible slot. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class BearTraps {
    private record Root(long end,int total) { }
    private static final Map<UUID,UUID> OWNED=new HashMap<>();
    private static final Map<UUID,Root> ROOTED=new HashMap<>();
    /** Client copy of "my trap is set and recallable", fed by the utility HUD packet. */
    public static volatile boolean clientArmed;
    static boolean owns(UUID owner,UUID trap) {return trap.equals(OWNED.get(owner));}
    static int count(UUID owner) {return OWNED.containsKey(owner)?1:0;}
    static void placed(BearTrapEntity trap) {OWNED.put(trap.owner(),trap.getUUID());}
    static void removed(BearTrapEntity trap) {if(trap.owner()!=null)OWNED.remove(trap.owner(),trap.getUUID());}
    static void rooted(UUID victim,long end,int total) {ROOTED.put(victim,new Root(end,total));}
    static void unrooted(UUID victim) {ROOTED.remove(victim);}
    static CombatGadgets.Gauge rootGauge(ServerPlayer p) {
        var root=ROOTED.get(p.getUUID());long left=root==null?0:root.end()-p.level().getGameTime();
        return left>0?new CombatGadgets.Gauge("bear_trap_root",left,root.total(),true):null;
    }
    static BearTrapEntity trap(MinecraftServer server,UUID owner) {
        UUID id=OWNED.get(owner);if(id==null||server==null)return null;
        for(var level:server.getAllLevels())if(level.getEntity(id) instanceof BearTrapEntity trap&&!trap.isRemoved())return trap;
        return null;
    }
    /** Only a trap nobody has stepped in yet can be picked back up. */
    public static boolean recallable(Player p) {
        if(p==null)return false;
        if(p.level().isClientSide)return clientArmed;
        var trap=trap(p.getServer(),p.getUUID());return trap!=null&&!trap.sprung();
    }
    static void recall(ServerPlayer p) {
        var trap=trap(p.server,p.getUUID());if(trap==null||trap.sprung())return;
        trap.discard();
        p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.2f);
        p.displayClientMessage(Component.literal("베어트랩을 회수했습니다."),true);
    }
    static void clear(ServerPlayer p) {clear(p.server,p.getUUID());}
    static void clear(MinecraftServer server,UUID owner) {
        var trap=trap(server,owner);if(trap!=null)trap.discard();
        OWNED.remove(owner);
    }
    static int clearAll(MinecraftServer server) {
        int count=0;
        for(var level:server.getAllLevels()) {
            var traps=new ArrayList<BearTrapEntity>();
            for(var entity:level.getAllEntities())if(entity instanceof BearTrapEntity trap)traps.add(trap);
            for(var trap:traps) {trap.discard();count++;}
        }
        OWNED.clear();ROOTED.clear();return count;
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {ROOTED.remove(event.getEntity().getUUID());BearTrapEntity.clearModifier(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {ROOTED.remove(event.getEntity().getUUID());BearTrapEntity.clearModifier(event.getEntity());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {OWNED.clear();ROOTED.clear();clientArmed=false;}
    private BearTraps() { }
}

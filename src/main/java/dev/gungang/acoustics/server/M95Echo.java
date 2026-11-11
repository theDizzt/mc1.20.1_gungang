package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Each scope pulse reveals current positions, including enemies behind terrain. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class M95Echo {
    public record Target(double x,double y,double z,UUID id){}
    public static final int PULSE_INTERVAL=50, PULSE_VISIBLE=10;
    private record Pending(Set<UUID> targets,long expires){}
    private static final Map<ServerPlayer,Long> STARTED=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> NEXT_SCAN=new WeakHashMap<>();
    private static final Map<ServerPlayer,Pending> PENDING=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> OBSERVED=new WeakHashMap<>();
    public static CombatGadgets.Gauge scopeGauge(ServerPlayer player){
        Long start=STARTED.get(player);if(start==null||!M95.is(player.getMainHandItem())||!aiming(player))return null;
        int total=ExtraGadgetConfig.ticks(ExtraGadgetConfig.m95.scopeRevealSeconds());
        return new CombatGadgets.Gauge("m95_scope",Math.max(0,total-(player.level().getGameTime()-start)),total,true);
    }
    public static CombatGadgets.Gauge observedGauge(ServerPlayer player){
        long remaining=OBSERVED.getOrDefault(player,0L)-player.level().getGameTime();
        if(remaining<=0){OBSERVED.remove(player);return null;}
        return new CombatGadgets.Gauge("m95_observed",remaining,20,true);
    }
    /** The client acknowledges only targets inside its actual zoom viewport, once per server pulse. */
    public static void observed(ServerPlayer viewer,List<UUID> ids){
        if(viewer==null)return;
        var pending=PENDING.remove(viewer);
        if(pending==null||pending.expires()<viewer.level().getGameTime()||!viewer.isAlive()||!M95.is(viewer.getMainHandItem())||!aiming(viewer))return;
        for(UUID id:new HashSet<>(ids))if(pending.targets().contains(id)&&viewer.serverLevel().getEntity(id) instanceof ServerPlayer target&&target.isAlive()){
            OBSERVED.put(target,target.level().getGameTime()+20);
            GungangNetwork.send(target,new GungangNetwork.Utility(ServerFiles.GSON.toJson(CombatGadgets.hud(target))));
        }
    }
    public static void fired(LivingEntity owner){
        if(owner instanceof ServerPlayer player){
            STARTED.put(player,player.level().getGameTime());
            NEXT_SCAN.remove(player);
            PENDING.remove(player);
            GungangNetwork.send(player,new GungangNetwork.M95EchoPacket(List.of()));
        }
    }
    private static java.lang.reflect.Method aiming;
    public static boolean aiming(LivingEntity entity){
        try{
            if(aiming==null)aiming=Class.forName("com.tacz.guns.api.entity.IGunOperator").getMethod("getSynIsAiming");
            return (boolean)aiming.invoke(entity);
        }catch(ReflectiveOperationException|IllegalArgumentException unavailable){return false;}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(var level:server.getAllLevels()){
            long now=level.getGameTime();
            for(var player:level.players()){
                if(!player.isAlive()||player.isSpectator()||!M95.is(player.getMainHandItem())||!aiming(player)){
                    if(STARTED.remove(player)!=null)GungangNetwork.send(player,new GungangNetwork.M95EchoPacket(List.of()));
                    NEXT_SCAN.remove(player);
                    PENDING.remove(player);
                    continue;
                }
                long started=STARTED.computeIfAbsent(player,p->now);
                if(now<started){STARTED.put(player,now);NEXT_SCAN.remove(player);PENDING.remove(player);continue;}
                if(now-started<ExtraGadgetConfig.ticks(ExtraGadgetConfig.m95.scopeRevealSeconds()))continue;
                if(now<NEXT_SCAN.getOrDefault(player,Long.MIN_VALUE))continue;
                NEXT_SCAN.put(player,now+PULSE_INTERVAL);
                var targets=new ArrayList<Target>();
                Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
                double range=ExtraGadgetConfig.m95.hitscanRange(),rangeSquared=range*range;
                // Use Minecraft's spatial index rather than iterating every loaded entity in the dimension.
                var entities=level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(range),
                        e->e!=player&&e.isAlive()&&!e.isSpectator()&&(CombatRules.target(e)||e instanceof Enemy));
                for(var enemy:entities){
                    Vec3 current=enemy.getBoundingBox().getCenter();
                    double dx=current.x-eye.x,dy=current.y-eye.y,dz=current.z-eye.z;
                    if(dx*dx+dy*dy+dz*dz>rangeSquared||dx*look.x+dy*look.y+dz*look.z<=0)continue;
                    if(enemy==player||enemy.isAlliedTo(player)||enemy instanceof Player p&&p.isCreative()||SafeZones.contains(enemy)
                            ||LobbyManager.respawning(enemy.getUUID())||FreeForAllMatch.blocked(enemy))continue;
                    // Deliberately no line-of-sight ray: blocks do not hide this scan.
                    targets.add(new Target(current.x,current.y,current.z,enemy.getUUID()));
                    if(targets.size()>=512)break;
                }
                var ids=new HashSet<UUID>();for(var target:targets)ids.add(target.id());
                PENDING.put(player,new Pending(ids,now+20));
                GungangNetwork.send(player,new GungangNetwork.M95EchoPacket(targets));
            }
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){STARTED.clear();NEXT_SCAN.clear();PENDING.clear();OBSERVED.clear();}
    private static void clear(ServerPlayer player){STARTED.remove(player);NEXT_SCAN.remove(player);PENDING.remove(player);OBSERVED.remove(player);GungangNetwork.send(player,new GungangNetwork.M95EchoPacket(List.of()));}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    private M95Echo(){}
}

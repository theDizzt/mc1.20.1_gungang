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
    public record View(List<Target> targets){}
    private static final Map<ServerPlayer,Long> STARTED=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> NEXT_SCAN=new WeakHashMap<>();
    public static void fired(LivingEntity owner){
        if(owner instanceof ServerPlayer player){
            STARTED.put(player,player.level().getGameTime());
            NEXT_SCAN.remove(player);
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
                    continue;
                }
                long started=STARTED.computeIfAbsent(player,p->now);
                if(now<started){STARTED.put(player,now);continue;}
                if(now-started<ExtraGadgetConfig.ticks(ExtraGadgetConfig.m95.scopeRevealSeconds()))continue;
                if(now<NEXT_SCAN.getOrDefault(player,Long.MIN_VALUE))continue;
                NEXT_SCAN.put(player,now+5);
                var targets=new ArrayList<Target>();
                Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
                double range=ExtraGadgetConfig.m95.hitscanRange(),rangeSquared=range*range;
                // Use Minecraft's spatial index rather than iterating every loaded entity in the dimension.
                var entities=level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(range),
                        e->e!=player&&e.isAlive()&&!e.isSpectator()&&(e instanceof Player||e instanceof BotEntity||e instanceof Enemy));
                for(var enemy:entities){
                    Vec3 current=enemy.getBoundingBox().getCenter();
                    double dx=current.x-eye.x,dy=current.y-eye.y,dz=current.z-eye.z;
                    if(dx*dx+dy*dy+dz*dz>rangeSquared||dx*look.x+dy*look.y+dz*look.z<=0)continue;
                    if(enemy==player||enemy.isAlliedTo(player)||enemy instanceof Player p&&p.isCreative()||SafeZones.contains(enemy)
                            ||LobbyManager.respawning(enemy.getUUID())||FreeForAllMatch.blocked(enemy))continue;
                    // Deliberately no line-of-sight ray: blocks do not hide this scan.
                    targets.add(new Target(current.x,current.y,current.z,enemy.getUUID()));
                }
                GungangNetwork.send(player,new GungangNetwork.M95EchoPacket(targets));
            }
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){STARTED.clear();NEXT_SCAN.clear();}
    private M95Echo(){}
}

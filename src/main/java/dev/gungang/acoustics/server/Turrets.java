package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loaded turrets have one owner slot. Unloaded turrets are retired, like bear traps. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Turrets {
    public record Config(double damage,double detectionRange,double cooldownSeconds,double health,int capacity,
                         int fireIntervalTicks,double reloadPerSecond,double reloadRange,double placementRange) { }
    public static Config config=new Config(6,16,120,150,120,4,20,3,5);
    private static final ThreadLocal<net.minecraft.world.entity.LivingEntity> BULLET_VICTIM=new ThreadLocal<>();
    static void bullet(net.minecraft.world.entity.LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float damage) {
        var previous=BULLET_VICTIM.get();BULLET_VICTIM.set(target);
        try{target.hurt(source,damage);}finally{if(previous==null)BULLET_VICTIM.remove();else BULLET_VICTIM.set(previous);}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void knockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event){if(BULLET_VICTIM.get()==event.getEntity())event.setCanceled(true);}
    private static final Map<UUID,TurretEntity> OWNED=new HashMap<>();
    static void load(Map<String,com.google.gson.JsonObject> overrides,boolean apply)throws java.io.IOException {
        var c=GadgetConfig.read("turret",Config.class,new Config(6,16,120,150,120,4,20,3,5),overrides,apply);
        // Upgrade earlier shipped configurations (1.0, then 1 damage every tick); preserve deliberately customized server settings.
        if(!overrides.containsKey("turret")&&(c.equals(new Config(4,16,210,150,120,4,20,3,5))||c.equals(new Config(10,16,210,100,250,5,20,3,5))||c.equals(new Config(1,16,210,150,250,1,20,3,5)))) {
            c=new Config(6,16,120,150,120,4,20,3,5);
            if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/turret.json"),ServerFiles.GSON.toJson(c));
        }
        GadgetConfig.bounded(c.damage(),0,10000);GadgetConfig.bounded(c.detectionRange(),1,64);
        GadgetConfig.bounded(c.cooldownSeconds(),0,86400);GadgetConfig.bounded(c.health(),1,1024);
        GadgetConfig.bounded(c.capacity(),1,10000);GadgetConfig.bounded(c.fireIntervalTicks(),1,1200);
        GadgetConfig.bounded(c.reloadPerSecond(),1,1000);GadgetConfig.bounded(c.reloadRange(),1,8);GadgetConfig.bounded(c.placementRange(),1,8);
        if(apply)config=c;
    }
    public static AABB bounds(BlockPos pos) {return new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+2,pos.getY()+2,pos.getZ()+2);}
    public static boolean supported(Level level,BlockPos pos) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
            var below=pos.offset(x,-1,z);
            if(!level.hasChunkAt(below)||!level.getBlockState(below).isFaceSturdy(level,below,Direction.UP))return false;
        }
        return true;
    }
    public static boolean valid(Player p,BlockPos pos) {
        if(pos==null||!p.isAlive()||p.isSpectator()||SafeZones.contains(p))return false;
        var level=p.level();var box=bounds(pos);
        if(pos.getY()<level.getMinBuildHeight()||pos.getY()+2>level.getMaxBuildHeight()
                ||!level.getWorldBorder().isWithinBounds(box)||!supported(level,pos))return false;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++) {
            var cell=pos.offset(x,y,z);
            if(!level.hasChunkAt(cell)||!level.getFluidState(cell).isEmpty()||SafeZones.contains(level,cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5))return false;
        }
        return level.noCollision(box)&&level.getEntities(p,box,e->!e.isSpectator()).isEmpty()&&!box.intersects(p.getBoundingBox());
    }
    public static TurretEntity owned(UUID owner) {
        var turret=OWNED.get(owner);return turret!=null&&!turret.isRemoved()?turret:null;
    }
    static boolean claim(TurretEntity turret) {
        if(owned(turret.owner())!=null)return false;
        OWNED.put(turret.owner(),turret);return true;
    }
    static void removed(TurretEntity turret) {OWNED.remove(turret.owner(),turret);}
    public static double distanceSquared(Player p,TurretEntity turret) {
        var box=turret.getBoundingBox();var point=p.position();
        double x=Math.max(box.minX,Math.min(box.maxX,point.x)),y=Math.max(box.minY,Math.min(box.maxY,point.y)),z=Math.max(box.minZ,Math.min(box.maxZ,point.z));
        return point.distanceToSqr(x,y,z);
    }
    public static boolean canReload(Player p,TurretEntity turret) {
        return canService(p,turret,false);
    }
    public static boolean canRepair(Player p,TurretEntity turret){return canService(p,turret,true);}
    private static boolean canService(Player p,TurretEntity turret,boolean repair) {
        return canInteract(p,turret)&&(repair?turret.getHealth()<turret.getMaxHealth():turret.ammo()<turret.capacity());
    }
    public static boolean canInteract(Player p,TurretEntity turret) {
        if(p==null||turret==null||p.level()!=turret.level()||!p.isAlive()||p.isSpectator()||!turret.isAlive()
                ||!p.getUUID().equals(turret.owner())||LobbyManager.respawning(p.getUUID())
                ||FreeForAllMatch.blocked(p)||SurvivalGadgets.jetting(p)||GadgetManager.placing(p)
                ||distanceSquared(p,turret)>turret.reloadRange()*turret.reloadRange())return false;
        return p.level().clip(new ClipContext(p.getEyePosition(),turret.position().add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    public static void key(ServerPlayer p,String action) {
        var turret=owned(p.getUUID());if(turret==null)return;
        if(action.equals("up"))turret.stopReload();
        else if((action.equals("down")||action.equals("hold"))&&p.containerMenu==p.inventoryMenu&&canReload(p,turret))turret.keepReloading();
        else if((action.equals("repair_down")||action.equals("repair_hold"))&&p.containerMenu==p.inventoryMenu&&canRepair(p,turret))turret.keepRepairing();
    }
    static void clear(MinecraftServer server,UUID owner) {var turret=owned(owner);if(turret!=null)turret.discard();OWNED.remove(owner);}
    static int clearAll(MinecraftServer server) {
        int count=0;
        for(var level:server.getAllLevels()) {
            var turrets=new ArrayList<TurretEntity>();
            for(var entity:level.getAllEntities())if(entity instanceof TurretEntity turret)turrets.add(turret);
            for(var turret:turrets){turret.discard();count++;}
        }
        OWNED.clear();return count;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){OWNED.clear();}
    private Turrets() { }
}

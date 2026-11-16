package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/**
 * A 37 glue gun pool: a white disc on the floor for poolSeconds. Everyone standing in it (owner and allies included)
 * takes poolDamagePerSecond and is {@link GluedEffect glued}: slower and unable to jump.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class GluePoolEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(GluePoolEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE=SynchedEntityData.defineId(GluePoolEntity.class,EntityDataSerializers.INT);
    /** Game tick each body last took pool damage: overlapping pools hurt once per interval, not once each. */
    private static final Map<UUID,Long> LAST_HURT=new HashMap<>();
    private UUID owner;

    public GluePoolEntity(EntityType<? extends GluePoolEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(RADIUS,3f);entityData.define(LIFE,60);}
    public float radius(){return entityData.get(RADIUS);}
    public int life(){return entityData.get(LIFE);}

    static void spawn(Level level,Vec3 floor,Entity owner){
        if(!(level instanceof ServerLevel server))return;
        var c=ExtraGadgetConfig.glueGun;double r=c.poolRadius();
        var area=new AABB(floor.x-r,floor.y,floor.z-r,floor.x+r,floor.y+1,floor.z+r);
        if(LobbyManager.inEitherSpawn(level,area)||SafeZones.contains(level,floor.x,floor.y+.1,floor.z))return;
        var pool=GadgetItems.GLUE_POOL.get().create(server);if(pool==null)return;
        pool.owner=owner==null?null:owner.getUUID();
        pool.entityData.set(RADIUS,(float)r);pool.entityData.set(LIFE,Math.max(1,ExtraGadgetConfig.ticks(c.poolSeconds())));
        pool.setPos(floor.x,floor.y,floor.z);
        server.addFreshEntity(pool);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        if(tickCount>=life()){discard();return;}
        var c=ExtraGadgetConfig.glueGun;var level=(ServerLevel)level();long now=level.getGameTime();
        double r=radius();var center=position();
        int interval=Math.max(1,ExtraGadgetConfig.ticks(c.damageIntervalSeconds()));
        if(now%1200==0)LAST_HURT.values().removeIf(at->now-at>40);
        for(var body:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(r+1,1.5,r+1),HiddenWeaponTargets::valid)){
            if(!body.isAlive()||body.isSpectator()||!inside(body,center,r)||SafeZones.contains(body)||LobbyManager.respawning(body.getUUID()))continue;
            // Short and refreshed every tick: it ends a moment after the body steps out.
            body.addEffect(new MobEffectInstance(GadgetItems.GLUED.get(),4,0,false,false,false));
            if(now%interval!=0||Long.valueOf(now).equals(LAST_HURT.put(body.getUUID(),now)))continue;
            hurt(body,c.poolDamagePerSecond()*interval/20);
        }
        if(tickCount%3==0){
            // Glue strands rising off the pool.
            level.sendParticles(GlueShotEntity.GLUE,center.x,center.y+.1,center.z,Math.max(1,(int)Math.round(r*1.5)),r*.45,.02,r*.45,0);
        }
    }
    /** Standing on the pool: feet within its disc and near its surface (a jump over it does not count, nor a floor below). */
    private static boolean inside(LivingEntity body,Vec3 center,double r){
        double dx=body.getX()-center.x,dz=body.getZ()-center.z,reach=r+body.getBbWidth()/2;
        return dx*dx+dz*dz<=reach*reach&&body.getY()>=center.y-.6&&body.getY()<=center.y+.6;
    }
    private void hurt(LivingEntity body,double amount){
        var server=((ServerLevel)level()).getServer();
        Entity attacker=owner==null?null:server.getPlayerList().getPlayer(owner);
        if(attacker==null&&owner!=null)attacker=((ServerLevel)level()).getEntity(owner);
        var type=level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC);
        int immunity=body.invulnerableTime;body.invulnerableTime=0;
        // Area damage: hits the owner and teammates too (OrdnanceDamage passes friendly-fire protection).
        OrdnanceDamage.hurt(body,new DamageSource(type,this,attacker),(float)amount);
        if(body.isAlive())body.invulnerableTime=Math.min(immunity,body.invulnerableTime);
    }
    @Override public AABB getBoundingBoxForCulling(){double r=radius()+.5;return new AABB(getX()-r,getY()-.5,getZ()-r,getX()+r,getY()+.5,getZ()+r);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<128*128;}
    @Override public boolean isPickable(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){LAST_HURT.clear();}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){LAST_HURT.remove(event.getEntity().getUUID());}
}

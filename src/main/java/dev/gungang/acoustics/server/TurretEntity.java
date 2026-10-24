package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Stationary, damageable sentry. Targeting, ammunition and reload timing are server authoritative. */
public final class TurretEntity extends LivingEntity {
    private static final net.minecraft.resources.ResourceKey<DamageType> BULLET=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,new net.minecraft.resources.ResourceLocation("gungang","turret_bullet"));
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(TurretEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> AMMO=SynchedEntityData.defineId(TurretEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAPACITY=SynchedEntityData.defineId(TurretEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RELOAD_RANGE=SynchedEntityData.defineId(TurretEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM=SynchedEntityData.defineId(TurretEntity.class,EntityDataSerializers.FLOAT);
    private String team="";
    private long reloadUntil;
    private double reloadFraction;
    private int fireDelay;
    public TurretEntity(EntityType<? extends LivingEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(OWNER,Optional.empty());entityData.define(AMMO,250);entityData.define(CAPACITY,250);entityData.define(RELOAD_RANGE,3f);entityData.define(AIM,0f);}
    public UUID owner(){return entityData.get(OWNER).orElse(null);}
    public int ammo(){return entityData.get(AMMO);}
    public int capacity(){return entityData.get(CAPACITY);}
    public float reloadRange(){return entityData.get(RELOAD_RANGE);}
    public float aimYaw(){return entityData.get(AIM);}
    public void setup(LivingEntity owner){
        entityData.set(OWNER,Optional.of(owner.getUUID()));team=owner.getTeam()==null?"":owner.getTeam().getName();
        entityData.set(CAPACITY,Turrets.config.capacity());entityData.set(AMMO,capacity());entityData.set(RELOAD_RANGE,(float)Turrets.config.reloadRange());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Turrets.config.health());setHealth(getMaxHealth());entityData.set(AIM,getYRot());
    }
    public BlockPos anchor(){return BlockPos.containing(getX()-1,getY(),getZ()-1);}
    void keepReloading(){reloadUntil=level().getGameTime()+15;}
    void stopReload(){reloadUntil=0;reloadFraction=0;}
    private LivingEntity ownerEntity(){return owner()!=null&&((ServerLevel)level()).getEntity(owner()) instanceof LivingEntity e?e:null;}
    @Override public void tick(){
        super.tick();setDeltaMovement(Vec3.ZERO);
        if(level().isClientSide){
            // Synced ammo shows the empty warning to all nearby clients. Remaining
            // particles fade naturally once reloading supplies the first round.
            if(isAlive()&&!isRemoved()&&ammo()==0&&tickCount%4==0){
                for(int i=0;i<2;i++)level().addParticle(ParticleTypes.SMOKE,
                        getX()+(random.nextDouble()-.5)*.35,getY()+.8,getZ()+(random.nextDouble()-.5)*.35,
                        (random.nextDouble()-.5)*.01,.035,(random.nextDouble()-.5)*.01);
            }
            return;
        }
        if(owner()==null||Turrets.owned(owner())!=this||!Turrets.supported(level(),anchor())){discard();return;}
        var ownerEntity=ownerEntity();
        if(ownerEntity==null||!ownerEntity.isAlive()||ownerEntity.isSpectator()){discard();return;}
        if(reloadUntil>level().getGameTime()&&ownerEntity instanceof ServerPlayer p&&p.containerMenu==p.inventoryMenu&&Turrets.canReload(p,this)){
            reloadFraction+=Turrets.config.reloadPerSecond()/20;
            int rounds=(int)reloadFraction;
            if(rounds>0){entityData.set(AMMO,Math.min(capacity(),ammo()+rounds));reloadFraction-=rounds;}
            return;
        }
        stopReload();
        if(fireDelay>0){fireDelay--;return;}
        if(ammo()==0||SafeZones.contains(this)||SafeZones.contains(ownerEntity)||FreeForAllMatch.blocked(ownerEntity))return;
        if(MatchManager.preparationGate()!=null||MatchManager.bomb()!=null&&!MatchManager.bomb().activePlay())return;
        double range=Turrets.config.detectionRange();
        var target=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(range),e->enemy(e)&&distanceToSqr(e)<=range*range&&clearShot(e))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(target==null)return;
        Vec3 from=muzzle(),to=target.getBoundingBox().getCenter(),direction=to.subtract(from);
        entityData.set(AIM,(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90));
        entityData.set(AMMO,ammo()-1);fireDelay=Turrets.config.fireIntervalTicks()-1;
        var source=new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET),this,ownerEntity);
        target.hurt(source,(float)Turrets.config.damage());
        var server=(ServerLevel)level();
        int steps=Math.max(1,(int)Math.ceil(direction.length()*3));
        for(int i=1;i<=steps;i++){Vec3 point=from.lerp(to,(double)i/steps);server.sendParticles(ParticleTypes.CRIT,point.x,point.y,point.z,1,0,0,0,0);}
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.8f);
    }
    private Vec3 muzzle(){return position().add(0,.574,0);}
    private boolean enemy(LivingEntity e){
        return (e instanceof Player||e instanceof BotEntity)&&e.isAlive()&&!e.isSpectator()&&!e.getUUID().equals(owner())
                &&!(e instanceof Player p&&p.isCreative())&&!LobbyManager.respawning(e.getUUID())&&!FreeForAllMatch.blocked(e)&&!SafeZones.contains(e)
                &&(team.isEmpty()||e.getTeam()==null||!team.equals(e.getTeam().getName()));
    }
    private boolean clearShot(LivingEntity target){
        Vec3 from=muzzle(),to=target.getBoundingBox().getCenter();
        if(level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)return false;
        var hit=ProjectileUtil.getEntityHitResult(level(),this,from,to,new AABB(from,to).inflate(.1),e->e!=this&&e.isPickable()&&!e.isSpectator());
        return hit!=null&&hit.getEntity()==target;
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||isRemoved())return false;
        invulnerableTime=0;boolean result=super.hurt(source,amount);if(getHealth()<=0)discard();return result;
    }
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean canBeCollidedWith(){return !isRemoved();}
    @Override public boolean isPushable(){return false;}
    @Override public void move(MoverType type,Vec3 movement){setDeltaMovement(Vec3.ZERO);}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void push(double x,double y,double z){}
    @Override protected void pushEntities(){}
    @Override public void push(Entity entity){}
    @Override public Iterable<ItemStack> getArmorSlots(){return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){}
    @Override public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide)Turrets.removed(this);super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(owner()!=null)tag.putUUID("Owner",owner());tag.putString("Team",team);tag.putInt("Ammo",ammo());tag.putInt("Capacity",capacity());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());team=tag.getString("Team");entityData.set(CAPACITY,Math.max(1,tag.getInt("Capacity")));entityData.set(AMMO,Math.max(0,Math.min(capacity(),tag.getInt("Ammo"))));}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

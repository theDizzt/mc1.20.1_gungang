package dev.gungang.acoustics.server;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;

public final class DeployableEntity extends LivingEntity {
    private static final EntityDataAccessor<Boolean> WALL=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EAST=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOW_HEALTH=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TEAM_COLOR=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> TEAM=SynchedEntityData.defineId(DeployableEntity.class,EntityDataSerializers.STRING);
    public DeployableEntity(EntityType<? extends LivingEntity> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData() {super.defineSynchedData();entityData.define(WALL,false);entityData.define(EAST,false);entityData.define(OWNER,Optional.empty());entityData.define(SHOW_HEALTH,false);entityData.define(TEAM_COLOR,0xFFFFFFFF);entityData.define(TEAM,"");}
    private String team() {return entityData.get(TEAM);}
    /** Scoreboard team of the owner ("Red"/"Blue"), empty outside team modes. */
    public String teamName() {return team();}
    public boolean mineOutlineVisibleTo(net.minecraft.world.entity.player.Player viewer) {
        return !wall()&&!isRemoved()&&viewer!=null&&(viewer.getUUID().equals(owner())
                ||!team().isEmpty()&&viewer.getTeam()!=null&&team().equals(viewer.getTeam().getName()));
    }
    @Override public int getTeamColor() {return teamColor()&0xFFFFFF;}
    public int teamColor() {return entityData.get(TEAM_COLOR);}
    public boolean showHealthBar() {return wall()||entityData.get(SHOW_HEALTH);}
    public boolean wall() {return entityData.get(WALL);} public boolean east() {return entityData.get(EAST);}
    public UUID owner() {return entityData.get(OWNER).orElse(null);}
    public void setup(LivingEntity player,boolean wall,boolean east) {
        entityData.set(WALL,wall);entityData.set(EAST,east);entityData.set(OWNER,Optional.of(player.getUUID()));
        entityData.set(SHOW_HEALTH,GadgetConfig.mine.showHealthBar());
        String team=player.getTeam()==null?"":player.getTeam().getName();entityData.set(TEAM,team);
        entityData.set(TEAM_COLOR,team.equals("Red")?LobbyManager.options().color("ALPHA"):team.equals("Blue")?LobbyManager.options().color("BRAVO"):0xFFFFFFFF);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(wall?GadgetConfig.barrier.health():20);setHealth(getMaxHealth());
        setBoundingBox(makeBoundingBox());
        if(!level().isClientSide)DeployableLedger.get(level().getServer()).add(this);
    }
    @Override protected AABB makeBoundingBox() {
        if(entityData!=null&&wall())return new AABB(getX()-(east()?0.18:1.5),getY(),getZ()-(east()?1.5:0.18),getX()+(east()?0.18:1.5),getY()+3,getZ()+(east()?1.5:0.18));
        return new AABB(getX()-.35,getY(),getZ()-.35,getX()+.35,getY()+.15,getZ()+.35);
    }
    @Override public void tick() {
        super.tick();setDeltaMovement(0,0,0);setBoundingBox(makeBoundingBox());
        if(level().isClientSide)return;
        entityData.set(SHOW_HEALTH,GadgetConfig.mine.showHealthBar());
        GadgetManager.track(this);
        if(isRemoved()||wall()||tickCount<10)return;
        var config=GadgetConfig.mine;
        boolean triggered=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(config.triggerRadius()),this::enemy).stream().anyMatch(e->e.distanceTo(this)<=config.triggerRadius()+.5);
        if(triggered)detonate();
    }
    private boolean detonating;
    private void detonate(){
        if(level().isClientSide||wall()||isRemoved()||detonating)return;
        detonating=true;
        var config=GadgetConfig.mine;
            // Any owner entity (players and the bot) gets the kill credit.
            var player=owner()==null?null:((net.minecraft.server.level.ServerLevel)level()).getEntity(owner());
            for(var victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(config.radius()),this::enemy))
                if(victim.distanceTo(this)<=config.radius())victim.hurt(level().damageSources().explosion(this,player),(float)config.damage());
            level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
            ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,getX(),getY()+.2,getZ(),1,0,0,0,0);discard();
    }
    private boolean enemy(LivingEntity e) {return e!=this&&!(e instanceof DeployableEntity)&&e.isAlive()&&!e.isSpectator()&&!e.getUUID().equals(owner())&&(team().isEmpty()||e.getTeam()==null||!team().equals(e.getTeam().getName()));}
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide||isRemoved())return false;
        invulnerableTime=0;
        boolean hit=super.hurt(source,amount);if(hit&&getHealth()<=0){if(wall())discard();else detonate();}return hit;
    }
    @Override public boolean canBeCollidedWith() {return wall()&&!isRemoved();}
    @Override public boolean isPickable() {return !isRemoved();}
    @Override public boolean isPushable() {return false;}
    @Override public void knockback(double strength,double x,double z) {if(!wall())super.knockback(strength,x,z);}
    @Override public void push(double x,double y,double z) {if(!wall())super.push(x,y,z);}
    @Override public void move(MoverType type,net.minecraft.world.phys.Vec3 movement) {
        if(wall()) {setDeltaMovement(0,0,0);return;}
        super.move(type,movement);
    }
    // LivingEntity still pushes nearby players from its own tick even when isPushable is false.
    // Deployables are stationary geometry, not mobs that should separate overlapping entities.
    @Override protected void pushEntities() { }
    @Override public void push(Entity entity) { }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide&&(reason==RemovalReason.KILLED||reason==RemovalReason.DISCARDED))DeployableLedger.get(level().getServer()).remove(getUUID());
        super.remove(reason);
    }
    @Override public Iterable<ItemStack> getArmorSlots() {return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) {return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack) { }
    @Override public HumanoidArm getMainArm() {return HumanoidArm.RIGHT;}
    @Override public void addAdditionalSaveData(CompoundTag tag) {super.addAdditionalSaveData(tag);tag.putBoolean("Wall",wall());tag.putBoolean("East",east());if(owner()!=null)tag.putUUID("Owner",owner());tag.putString("Team",team());tag.putInt("TeamColor",teamColor());}
    @Override public void readAdditionalSaveData(CompoundTag tag) {super.readAdditionalSaveData(tag);entityData.set(WALL,tag.getBoolean("Wall"));entityData.set(EAST,tag.getBoolean("East"));entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());String team=tag.getString("Team");entityData.set(TEAM,team);entityData.set(TEAM_COLOR,tag.contains("TeamColor")?tag.getInt("TeamColor"):team.equals("Red")?LobbyManager.options().color("ALPHA"):team.equals("Blue")?LobbyManager.options().color("BRAVO"):0xFFFFFFFF);}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

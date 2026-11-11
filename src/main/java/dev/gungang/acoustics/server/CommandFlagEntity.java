package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A stationary, destructible team rally point. Age is measured in active server ticks. */
public final class CommandFlagEntity extends LivingEntity {
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(CommandFlagEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TEAM=SynchedEntityData.defineId(CommandFlagEntity.class,EntityDataSerializers.STRING);
    private UUID owner;
    private String team="";
    private int age;
    private ExtraGadgetConfig.CommandFlag config=ExtraGadgetConfig.commandFlag;
    public CommandFlagEntity(EntityType<? extends LivingEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(COLOR,0xFFFFFF);entityData.define(TEAM,"");}
    public void setup(ServerPlayer p){
        owner=p.getUUID();team=p.getTeam()==null?"":p.getTeam().getName();
        entityData.set(TEAM,team);
        Integer fallback=p.getTeam()==null?null:p.getTeam().getColor().getColor();
        entityData.set(COLOR,team.equals("Red")?LobbyManager.options().color("ALPHA"):team.equals("Blue")?LobbyManager.options().color("BRAVO"):fallback==null?0xFFFFFF:fallback);
        config=ExtraGadgetConfig.commandFlag;
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(config.health());
        setHealth((float)config.health());setGlowingTag(true);setYRot(p.getYRot());
    }
    public UUID owner(){return owner;}
    public int age(){return age;}
    public int color(){return entityData.get(COLOR);}
    public String teamName(){return entityData.get(TEAM);}
    @Override public int getTeamColor(){return color();}
    public int globalTicks(){return ExtraGadgetConfig.ticks(config.globalUpgradeSeconds());}
    public boolean global(){return age>=globalTicks();}
    public double radius(){return age>=ExtraGadgetConfig.ticks(config.defenseUpgradeSeconds())?config.defenseRadius():age>=ExtraGadgetConfig.ticks(config.healingUpgradeSeconds())?config.healingRadius():config.baseRadius();}
    public float healing(){return (float)(age>=ExtraGadgetConfig.ticks(config.healingUpgradeSeconds())?config.upgradedHealPerSecond():config.baseHealPerSecond());}
    public float reduction(){return age>=ExtraGadgetConfig.ticks(config.defenseUpgradeSeconds())?(float)config.damageReductionPercent():0;}
    public boolean affects(ServerPlayer p){
        return isAlive()&&!isRemoved()&&p.isAlive()&&!p.isSpectator()&&!LobbyManager.respawning(p.getUUID())&&!SafeZones.contains(p)
                &&(team.isEmpty()?p.getUUID().equals(owner):p.getTeam()!=null&&team.equals(p.getTeam().getName()))
                &&(global()||p.level()==level()&&distanceToSqr(p)<=radius()*radius());
    }
    @Override public void tick(){
        super.tick();setDeltaMovement(Vec3.ZERO);
        if(level().isClientSide)return;
        if(!isAlive()||SafeZones.contains(this)){discard();return;}
        age++;CommandFlags.track(this);
        if(age%10==0&&!global()){
            var level=(net.minecraft.server.level.ServerLevel)level();int c=color();
            var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),1);
            for(int i=0;i<96;i++){double a=i*Math.PI*2/96;level.sendParticles(dust,getX()+Math.cos(a)*radius(),getY()+.12,getZ()+Math.sin(a)*radius(),1,0,0,0,0);}
        }
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||!isAlive())return false;
        var attacker=source.getEntity();
        if(attacker!=null&&(attacker.getUUID().equals(owner)||!team.isEmpty()&&attacker.getTeam()!=null&&team.equals(attacker.getTeam().getName())))return false;
        invulnerableTime=0;return super.hurt(source,amount);
    }
    @Override public void die(DamageSource source){super.die(source);discard();}
    @Override public void remove(RemovalReason reason){CommandFlags.untrack(this);super.remove(reason);}
    @Override public void travel(Vec3 input){}
    @Override public void move(MoverType type,Vec3 movement){}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isPickable(){return isAlive();}
    @Override public boolean isInWall(){return false;}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void push(double x,double y,double z){}
    @Override protected void pushEntities(){}
    @Override public Iterable<ItemStack> getArmorSlots(){return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){}
    @Override public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

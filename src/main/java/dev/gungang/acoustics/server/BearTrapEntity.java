package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Placed bear trap: snaps shut on the first enemy that steps in and pins them in place, then disappears. */
public final class BearTrapEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(BearTrapEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> TEAM=SynchedEntityData.defineId(BearTrapEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TEAM_COLOR=SynchedEntityData.defineId(BearTrapEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPRUNG=SynchedEntityData.defineId(BearTrapEntity.class,EntityDataSerializers.BOOLEAN);
    private static final UUID ROOT=UUID.fromString("5d0c8f43-2a7e-4b8e-9a51-6c1f4f0b8d27");
    private UUID victim;
    private long release;
    /** Client tick at which the jaws were first seen shut, for the snap animation. */
    public float closedAt=-1;
    public BearTrapEntity(EntityType<?> type,Level level) {super(type,level);setNoGravity(true);noPhysics=true;}
    @Override protected void defineSynchedData() {entityData.define(OWNER,Optional.empty());entityData.define(TEAM,"");entityData.define(TEAM_COLOR,0xFFFFFFFF);entityData.define(SPRUNG,false);}
    public UUID owner() {return entityData.get(OWNER).orElse(null);}
    public boolean sprung() {return entityData.get(SPRUNG);}
    public int teamColor() {return entityData.get(TEAM_COLOR);}
    private String team() {return entityData.get(TEAM);}
    /** Scoreboard team of the owner ("Red"/"Blue"), empty outside team modes. */
    public String teamName() {return team();}
    public boolean outlineVisibleTo(Player viewer) {
        return viewer!=null&&(viewer.getUUID().equals(owner())||!team().isEmpty()&&viewer.getTeam()!=null&&team().equals(viewer.getTeam().getName()));
    }
    public void setup(LivingEntity player) {
        entityData.set(OWNER,Optional.of(player.getUUID()));
        String team=player.getTeam()==null?"":player.getTeam().getName();entityData.set(TEAM,team);
        entityData.set(TEAM_COLOR,team.equals("Red")?LobbyManager.options().color("ALPHA"):team.equals("Blue")?LobbyManager.options().color("BRAVO"):0xFFFFFFFF);
    }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide)return;
        if(owner()==null||!BearTraps.owns(owner(),getUUID())) {discard();return;}
        if(sprung()) {hold();return;}
        if(tickCount<10)return;
        // Stepping in means standing on the pan, not brushing past the jaws.
        var box=getBoundingBox().inflate(-.15,.35,-.15);
        level().getEntitiesOfClass(LivingEntity.class,box,this::enemy).stream().findFirst().ifPresent(this::spring);
    }
    private boolean enemy(LivingEntity e) {
        if(!(e instanceof Player||e instanceof BotEntity)||!e.isAlive()||e.isSpectator()||e.getUUID().equals(owner())||LobbyManager.respawning(e.getUUID()))return false;
        if(e instanceof Player p&&(p.isCreative()||FreeForAllMatch.blocked(p)))return false;
        // Teammates never trigger it; outside team modes everyone but the owner does.
        return team().isEmpty()||e.getTeam()==null||!team().equals(e.getTeam().getName());
    }
    private void spring(LivingEntity target) {
        entityData.set(SPRUNG,true);victim=target.getUUID();
        release=level().getGameTime()+ExtraGadgetConfig.ticks(GadgetConfig.bearTrap.rootSeconds());
        BearTraps.rooted(victim,release,ExtraGadgetConfig.ticks(GadgetConfig.bearTrap.rootSeconds()));
        var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null&&speed.getModifier(ROOT)==null)speed.addTransientModifier(new AttributeModifier(ROOT,"GunGang bear trap",-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
        level().playSound(null,blockPosition(),SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.PLAYERS,1.4f,.6f);
        level().playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.5f,1.6f);
        hold();
    }
    private void hold() {
        var entity=victim==null?null:((ServerLevel)level()).getEntity(victim);
        if(!(entity instanceof LivingEntity target)||!target.isAlive()||target.isSpectator()) {discard();return;}
        if(level().getGameTime()>=release) {
            // Breaking free leaves the leg hurt: Slowness after the pin (level 1 = amplifier 0).
            int slow=ExtraGadgetConfig.ticks(GadgetConfig.bearTrap.slowSeconds());
            if(slow>0)target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,slow,GadgetConfig.bearTrap.slowAmplifier()));
            discard();return;
        }
        Vec3 anchor=position();
        if(target.position().distanceToSqr(anchor)>.0025) {
            if(target instanceof ServerPlayer p)p.connection.teleport(anchor.x,anchor.y,anchor.z,p.getYRot(),p.getXRot());
            else target.teleportTo(anchor.x,anchor.y,anchor.z);
        }
        target.setDeltaMovement(0,Math.min(0,target.getDeltaMovement().y),0);
    }
    private void free() {
        if(victim==null)return;
        BearTraps.unrooted(victim);
        if(((ServerLevel)level()).getEntity(victim) instanceof LivingEntity target) {var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(ROOT);}
        victim=null;
    }
    /** Victims that log out or change dimension while pinned keep no leftover slow. */
    static void clearModifier(LivingEntity entity) {var speed=entity.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(ROOT);}
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide) {free();BearTraps.removed(this);}
        super.remove(reason);
    }
    @Override public boolean isPickable() {return false;}
    @Override public boolean isPushable() {return false;}
    @Override public boolean canBeCollidedWith() {return false;}
    @Override public boolean isAttackable() {return false;}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {if(owner()!=null)tag.putUUID("Owner",owner());tag.putString("Team",team());tag.putInt("TeamColor",teamColor());}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());
        entityData.set(TEAM,tag.getString("Team"));entityData.set(TEAM_COLOR,tag.getInt("TeamColor"));
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

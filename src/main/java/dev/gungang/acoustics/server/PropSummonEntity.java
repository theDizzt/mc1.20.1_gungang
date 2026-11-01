package dev.gungang.acoustics.server;

import java.util.List;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.*;

/** Ability actor: deliberately not a Mob, Monster or Animal, even in peaceful worlds. */
public final class PropSummonEntity extends LivingEntity {
    public enum Kind { SPIDER, BEE, SILVERFISH }
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"gungang");
    public static final RegistryObject<EntityType<PropSummonEntity>> TYPE=TYPES.register("prop_summon",()->EntityType.Builder.<PropSummonEntity>of(PropSummonEntity::new,MobCategory.MISC).sized(.7f,.5f).clientTrackingRange(10).updateInterval(1).noSave().build("gungang:prop_summon"));
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(PropSummonEntity.class,EntityDataSerializers.INT);
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){TYPES.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(TYPE.get(),LivingEntity.createLivingAttributes().build()));}
    public PropSummonEntity(EntityType<? extends LivingEntity> type,Level level){super(type,level);setNoGravity(true);setMaxUpStep(1);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,0);}
    public Kind kind(){return Kind.values()[Math.floorMod(entityData.get(KIND),Kind.values().length)];}
    public void kind(Kind kind){entityData.set(KIND,kind.ordinal());refreshDimensions();}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(KIND.equals(key))refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose pose){return switch(kind()){case SPIDER->EntityDimensions.scalable(.7f,.5f);case BEE->EntityDimensions.scalable(.7f,.6f);case SILVERFISH->EntityDimensions.scalable(.4f,.3f);};}
    // Movement is driven once by PropSummons; vanilla living travel must not move it a second time.
    @Override public void travel(net.minecraft.world.phys.Vec3 input){}
    @Override public void tick(){super.tick();if(!level().isClientSide&&(MatchManager.propHunt()==null||!isAlive()))discard();}
    @Override protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source){}
    @Override public Iterable<ItemStack> getArmorSlots(){return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){}
    @Override public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

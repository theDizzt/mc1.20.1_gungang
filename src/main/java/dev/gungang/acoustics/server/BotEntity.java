package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraftforge.registries.*;

/** A non-persistent match participant. No vanilla hostile goals, loot, sunlight burn or mob griefing. */
public final class BotEntity extends PathfinderMob {
    final BotParkour parkour=new BotParkour();
    @Override public void travel(net.minecraft.world.phys.Vec3 input){
        var steering=level().isClientSide?null:parkour.steer(this);
        super.travel(steering==null?input:steering);
    }
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> SKIN=net.minecraft.network.syncher.SynchedEntityData.defineId(BotEntity.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** Absorption is a plain server field on mobs; synced so teammates' health bars show the yellow bonus (mercy bullet, life support). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> ABSORPTION=net.minecraft.network.syncher.SynchedEntityData.defineId(BotEntity.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    /** 숨바꼭질: entity id of the bot's own disguise, or -1. The body then takes the block's size and steps aside for bullets. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DISGUISE=net.minecraft.network.syncher.SynchedEntityData.defineId(BotEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SKIN,BotDatabase.DEFAULT_SKIN);entityData.define(ABSORPTION,0f);entityData.define(DISGUISE,-1);}
    public void disguise(PropEntity prop){entityData.set(DISGUISE,prop==null?-1:prop.getId());}
    /** The live disguise, on either side; null for a plain bot. */
    public PropEntity disguise(){int id=entityData.get(DISGUISE);return id>=0&&level().getEntity(id) instanceof PropEntity prop&&!prop.isRemoved()?prop:null;}
    public boolean disguised(){return entityData.get(DISGUISE)>=0;}
    @Override public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(DISGUISE.equals(key))refreshDimensions();}
    /** A disguised bot has the block's size, as {@link PropDimensions} gives a hiding player. */
    @Override public EntityDimensions getDimensions(Pose pose){
        var prop=entityData==null?null:disguise();if(prop==null)return super.getDimensions(pose);
        var box=prop.getBoundingBox();float width=(float)Math.max(.1,Math.max(box.getXsize(),box.getZsize())),height=(float)Math.max(.1,box.getYsize());
        if(prop.healthPack())height=1;
        if(prop.item().equals("minecraft:painting"))width=(float)Math.max(.1,Math.min(box.getXsize(),box.getZsize()));
        return EntityDimensions.scalable(width,height);
    }
    @Override protected float getStandingEyeHeight(Pose pose,EntityDimensions size){return entityData!=null&&disguised()?Math.max(.1f,size.height*.85f):super.getStandingEyeHeight(pose,size);}
    /** Bullets hit the disguise, never the invisible body behind it; nobody shoves a block around. */
    @Override public boolean isPickable(){return !disguised()&&super.isPickable();}
    @Override public boolean isPushable(){return !disguised()&&super.isPushable();}
    @Override protected void pushEntities(){if(!disguised())super.pushEntities();}
    @Override public boolean isInvisibleTo(net.minecraft.world.entity.player.Player viewer){return disguised()||super.isInvisibleTo(viewer);}
    @Override public float getAbsorptionAmount(){return entityData.get(ABSORPTION);}
    @Override public void setAbsorptionAmount(float amount){super.setAbsorptionAmount(amount);entityData.set(ABSORPTION,Math.max(0,amount));}
    public String skin(){return entityData.get(SKIN);}
    public void skin(String value){entityData.set(SKIN,value);}
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"gungang");
    public static final RegistryObject<EntityType<BotEntity>> TYPE=TYPES.register("bot",()->EntityType.Builder.<BotEntity>of(BotEntity::new,MobCategory.MISC)
            .sized(.6f,1.8f).clientTrackingRange(10).updateInterval(2).noSave().build("gungang:bot"));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){
        TYPES.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(TYPE.get(),Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,100).add(Attributes.ATTACK_DAMAGE,2).add(Attributes.MOVEMENT_SPEED,.23).add(Attributes.FOLLOW_RANGE,48).build()));
    }
    public BotEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setPersistenceRequired();setMaxUpStep(1);}
    @Override protected void registerGoals(){}
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){
        return new GroundPathNavigation(this,level){
            @Override protected PathFinder createPathFinder(int nodes){
                nodeEvaluator=new WalkNodeEvaluator(){
                    @Override protected Node getNode(int x,int y,int z) {
                        Node node=super.getNode(x,y,z);
                        // Personal experience changes cost, never the terrain's walkability or collision type.
                        if(!node.closed&&node.costMalus>=0)node.costMalus=Math.max(node.costMalus,BotManager.routeCost(BotEntity.this,x,y,z));
                        return node;
                    }
                    @Override public BlockPathTypes getBlockPathType(net.minecraft.world.level.BlockGetter world,int x,int y,int z){
                        var state=world.getBlockState(new net.minecraft.core.BlockPos(x,y,z));
                        // An own-team gate is a doorway: walkable when it stands on a floor, open air above that.
                        // Reporting plain OPEN made the pathfinder see a hole with no floor and never route through it.
                        if(state.getBlock() instanceof SpawnGateBlock gate&&gate.present(state)&&gate.allows(BotEntity.this)){
                            var below=getBlockPathType(world,x,y-1,z);
                            return below==BlockPathTypes.OPEN||below==BlockPathTypes.WALKABLE||below==BlockPathTypes.WATER||below==BlockPathTypes.LAVA
                                    ?BlockPathTypes.OPEN:BlockPathTypes.WALKABLE;
                        }
                        // A shutter's panel is thinner than a block, so vanilla calls it open even while it is shut.
                        if(state.getBlock() instanceof ShutterBlock&&!BotNavGraph.shutterOpen(world,new net.minecraft.core.BlockPos(x,y,z)))return BlockPathTypes.BLOCKED;
                        var type=super.getBlockPathType(world,x,y,z);
                        // Seen enemy mines and traps, fire and strike zones cost extra, so paths bend around them.
                        return type==BlockPathTypes.WALKABLE&&BotManager.avoid(BotEntity.this,x,y,z)?BlockPathTypes.DAMAGE_OTHER:type;
                    }
                };
                nodeEvaluator.setCanPassDoors(true);
                // Swim at the surface (the bot jumps in water) instead of treating water as a wall.
                nodeEvaluator.setCanFloat(true);
                return new PathFinder(nodeEvaluator,nodes);
            }
        };
    }
    /**
     * Who map hazards and gadgets act on: living, non-spectating players and the bot. Code that only looked for
     * players left the bot immune to barrels, UXO, acid, jetpack hits and the like.
     */
    public static boolean participant(net.minecraft.world.entity.Entity entity){
        return entity instanceof LivingEntity living&&living.isAlive()&&!living.isSpectator()
                &&(entity instanceof net.minecraft.world.entity.player.Player||entity instanceof BotEntity);
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&!BotManager.owns(this))discard();}
    @Override protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source){}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean canPickUpLoot(){return false;}
}

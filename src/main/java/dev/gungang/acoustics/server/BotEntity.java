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
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SKIN,BotDatabase.DEFAULT_SKIN);}
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
                        var block=world.getBlockState(new net.minecraft.core.BlockPos(x,y,z)).getBlock();
                        // An own-team gate is a doorway: walkable when it stands on a floor, open air above that.
                        // Reporting plain OPEN made the pathfinder see a hole with no floor and never route through it.
                        if(block instanceof SpawnGateBlock gate&&gate.allows(BotEntity.this)){
                            var below=getBlockPathType(world,x,y-1,z);
                            return below==BlockPathTypes.OPEN||below==BlockPathTypes.WALKABLE||below==BlockPathTypes.WATER||below==BlockPathTypes.LAVA
                                    ?BlockPathTypes.OPEN:BlockPathTypes.WALKABLE;
                        }
                        var type=super.getBlockPathType(world,x,y,z);
                        // Seen enemy mines and traps, fire and strike zones cost extra, so paths bend around them.
                        return type==BlockPathTypes.WALKABLE&&BotManager.avoid(BotEntity.this,x,y,z)?BlockPathTypes.DAMAGE_OTHER:type;
                    }
                };
                nodeEvaluator.setCanPassDoors(true);
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

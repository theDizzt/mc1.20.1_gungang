package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.registries.*;

/** The visible disguise is also the authoritative bullet target, including very small props. */
public final class PropEntity extends LivingEntity {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"gungang");
    public static final RegistryObject<EntityType<PropEntity>> TYPE=TYPES.register("prop",()->EntityType.Builder.<PropEntity>of(PropEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(32).updateInterval(1).noSave().build("gungang:prop"));
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ITEM=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PAINTING=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROTATION=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OPEN=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TEXT=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> SKIN=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(PropEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    public PropEntity(EntityType<? extends LivingEntity> type,Level level){super(type,level);setNoGravity(true);}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){TYPES.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(TYPE.get(),LivingEntity.createLivingAttributes().build()));}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(STATE,0);entityData.define(ITEM,"");entityData.define(PAINTING,0);entityData.define(ROTATION,0);entityData.define(OPEN,false);entityData.define(OWNER,Optional.empty());entityData.define(TEXT,"");entityData.define(SKIN,Optional.empty());entityData.define(MODE,0);entityData.define(HEIGHT,1);}
    public String text(){return entityData.get(TEXT);}public void text(String text){entityData.set(TEXT,text);}
    public UUID skin(){return entityData.get(SKIN).orElse(owner());}public void skin(UUID id){entityData.set(SKIN,Optional.ofNullable(id));}
    public int mode(){return entityData.get(MODE);}public void mode(int mode){entityData.set(MODE,mode);}
    public int height(){return entityData.get(HEIGHT);}public void height(int height){entityData.set(HEIGHT,height);setBoundingBox(makeBoundingBox());}
    public BlockState state(){return Block.stateById(entityData.get(STATE));}
    public void state(BlockState state){entityData.set(STATE,Block.getId(state));setBoundingBox(makeBoundingBox());}
    public String item(){return entityData.get(ITEM);}
    public int painting(){return entityData.get(PAINTING);}
    public int rotation(){return entityData.get(ROTATION);}
    public boolean open(){return entityData.get(OPEN);}
    public void toggle(){entityData.set(OPEN,!open());}
    public void open(boolean open){entityData.set(OPEN,open);}
    /** Health packs toggle with E between standing and sunk one block into the floor; their bottle hologram stays above. */
    public boolean healthPack(){return state().getBlock() instanceof StationBlock station&&station.healthPack();}
    public boolean sunk(){return sinkDepth()>0;}
    public double sinkDepth(){return healthPack()&&open()?1:state().getBlock() instanceof JumpPadBlock&&open()?.125:state().getBlock() instanceof ConveyorBlock&&mode()>=2?.5:0;}
    /** R: 90° turn; a dispenser instead steps through all six facings (N, E, S, W, up, down). */
    public void rotate(){
        if(state().getBlock() instanceof net.minecraft.world.level.block.DispenserBlock){
            var order=List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN);
            var facing=state().getValue(net.minecraft.world.level.block.DispenserBlock.FACING);
            state(state().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,order.get((order.indexOf(facing)+1)%order.size())));return;
        }
        entityData.set(ROTATION,(rotation()+1)%4);state(state().rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90));
    }
    public UUID owner(){return entityData.get(OWNER).orElse(null);}
    public void setup(UUID owner,String item,int painting){entityData.set(OWNER,Optional.of(owner));entityData.set(ITEM,item);entityData.set(PAINTING,painting);entityData.set(MODE,0);entityData.set(HEIGHT,1);entityData.set(TEXT,"");entityData.set(OPEN,false);state(PropCatalog.state(item));}
    public boolean marker(){return isCurrentlyGlowing();}
    @Override public void tick(){super.tick();setDeltaMovement(0,0,0);setBoundingBox(makeBoundingBox());if(!level().isClientSide&&MatchManager.propHunt()==null)discard();}
    @Override protected AABB makeBoundingBox(){
        if(entityData==null)return super.makeBoundingBox();
        if(item().equals("minecraft:minecart"))return new AABB(getX()-.49,getY(),getZ()-.49,getX()+.49,getY()+.7,getZ()+.49);
        if(item().equals("minecraft:end_crystal"))return new AABB(getX()-.5,getY(),getZ()-.5,getX()+.5,getY()+2,getZ()+.5);
        if(item().equals("minecraft:painting")){
            var list=level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT).holders().toList();
            var variant=list.get(Math.floorMod(painting(),list.size())).value();double w=variant.getWidth()/16d,h=variant.getHeight()/16d;
            return new AABB(getX()-(rotation()%2==0?w/2:.0625),getY(),getZ()-(rotation()%2==0?.0625:w/2),getX()+(rotation()%2==0?w/2:.0625),getY()+h,getZ()+(rotation()%2==0?.0625:w/2));
        }
        AABB box=null;
        for(var part:PropGeometry.parts(this)){
            var shape=part.state().getShape(level(),blockPosition().offset(part.offset()));
            if(shape.isEmpty())continue;
            var bounds=shape.bounds().move(part.offset());box=box==null?bounds:box.minmax(bounds);
        }
        if(box==null)box=new AABB(0,0,0,1,1,1);
        // The bottle hologram above a health pack is part of the target, as is the sunk block below it,
        // so a shot at the hologram or at the floor cell it sank into still lands.
        if(healthPack())box=box.minmax(new AABB(.3,1.35,.3,.7,1.95,.7));
        return box.move(getX()-.5,getY(),getZ()-.5);
    }
    /** Real collision shapes of nearby disguises for this mover; the owner walks through its own. */
    public static List<net.minecraft.world.phys.shapes.VoxelShape> collisions(Level level,Entity entity,AABB box){
        if(entity instanceof PropEntity||entity.isSpectator()||entity.noPhysics)return List.of();
        var result=new ArrayList<net.minecraft.world.phys.shapes.VoxelShape>();
        for(var prop:level.getEntitiesOfClass(PropEntity.class,box.inflate(1),p->!p.isRemoved()&&!entity.getUUID().equals(p.owner()))){
            for(var part:PropGeometry.parts(prop)){
                var shape=part.state().getCollisionShape(level,prop.blockPosition().offset(part.offset()),net.minecraft.world.phys.shapes.CollisionContext.of(entity));
                if(!shape.isEmpty())result.add(shape.move(prop.getX()-.5+part.offset().getX(),prop.getY()+part.offset().getY(),prop.getZ()-.5+part.offset().getZ()));
            }
        }
        return result;
    }
    /** Anyone in the match can use a disguise like the real block: doors open, chests and tables open their menus. */
    @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||player.isSpectator()||marker())return net.minecraft.world.InteractionResult.PASS;
        if(!level().isClientSide){
            var match=MatchManager.propHunt();
            if(match==null||!match.useProp((net.minecraft.server.level.ServerPlayer)player,this))return net.minecraft.world.InteractionResult.PASS;
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public boolean hurt(DamageSource source,float amount){var match=MatchManager.propHunt();return !level().isClientSide&&match!=null&&match.hit(this,source,amount);}
    @Override public boolean isPickable(){return !isRemoved()&&!marker();}
    @Override public boolean isPushable(){return false;}
    @Override public void move(MoverType type,net.minecraft.world.phys.Vec3 delta){}
    @Override protected void pushEntities(){}
    @Override public void push(Entity other){}
    @Override public Iterable<ItemStack> getArmorSlots(){return List.of();}
    @Override public ItemStack getItemBySlot(EquipmentSlot slot){return ItemStack.EMPTY;}
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack){}
    @Override public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}

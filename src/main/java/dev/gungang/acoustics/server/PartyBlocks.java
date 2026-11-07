package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Prop hunt 243~252, also placeable: analog wall clock, mirror, dice, chandelier, 777 slot machine, billiard ball,
 * wishing well, the statue of honorary embezzlement, Christmas tree and Sanga's doll. All but the dice are drawn by
 * PartyRenderer from one block entity type; each stands on its cell's floor centred on the cell, so a disguise (drawn
 * around its hider) and the placed block look the same. The big ones keep a one-cell placed footprint (clipped shape)
 * like the nuke warhead; as disguises their full size comes from {@link #propBox}.
 */
public final class PartyBlocks {
    public enum Kind {CLOCK,MIRROR,CHANDELIER,SLOT,BALL,WELL,STATUE,TREE,SANGA,PHOTO,AUTO,SHADOW}
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    private static BlockBehaviour.Properties props(Block like){return BlockBehaviour.Properties.copy(like).noOcclusion();}
    // Shapes are pixel boxes for a north-facing block (front towards -z), relative to the cell's corner.
    public static final RegistryObject<Block> WALL_CLOCK=block("wall_clock",()->new Faced(Kind.CLOCK,props(Blocks.DARK_OAK_PLANKS).strength(1),box(-16,0,13,32,32,16)));
    public static final RegistryObject<Block> MIRROR=block("mirror",()->new Faced(Kind.MIRROR,props(Blocks.GLASS).strength(.6f),box(0,0,12,16,32,16),box(1,0,4,15,2,16)));
    public static final RegistryObject<Dice> DICE=block("dice",Dice::new);
    public static final RegistryObject<Block> CHANDELIER=block("chandelier",()->new Party(Kind.CHANDELIER,props(Blocks.GOLD_BLOCK).strength(1).lightLevel(s->15),box(-16,-14,-16,32,4,32),box(7,4,7,9,16,9)));
    public static final RegistryObject<Block> SLOT_MACHINE=block("slot_machine",()->new Faced(Kind.SLOT,props(Blocks.IRON_BLOCK).strength(2).lightLevel(s->6),box(0,0,2,16,32,16)));
    public static final RegistryObject<Block> BILLIARD_BALL=block("billiard_ball",()->new Ball(props(Blocks.WHITE_CONCRETE).strength(.8f),box(2,0,2,14,12,14)));
    public static final RegistryObject<Block> WISHING_WELL=block("wishing_well",()->new Faced(Kind.WELL,props(Blocks.STONE_BRICKS).strength(2),
            box(-16,0,-16,32,16,-8),box(-16,0,24,32,16,32),box(-16,0,-8,-8,16,24),box(24,0,-8,32,16,24),box(-12,16,6,-8,32,10),box(24,16,6,28,32,10)));
    public static final RegistryObject<Block> STATUE=block("embezzlement_statue",()->new Faced(Kind.STATUE,props(Blocks.STONE_BRICKS).strength(3,1200).lightLevel(s->12),box(-16,0,-16,32,32,32)));
    public static final RegistryObject<Block> TREE=block("christmas_tree",()->new Party(Kind.TREE,props(Blocks.SPRUCE_LEAVES).strength(.6f).sound(net.minecraft.world.level.block.SoundType.GRASS).lightLevel(s->10),box(-12,0,-12,28,32,28)));
    public static final RegistryObject<Block> SANGA_DOLL=block("sanga_doll",()->new Faced(Kind.SANGA,props(Blocks.OAK_PLANKS).strength(1),box(3,0,3,13,32,13)));
    public static final RegistryObject<Block> PHOTO=block("unpleasant_photo",()->new Faced(Kind.PHOTO,props(Blocks.OAK_PLANKS).strength(.6f),box(1,0,6,15,22,10)));
    public static final RegistryObject<Block> AUTO_UNIT=block("combat_unit",()->new Faced(Kind.AUTO,props(Blocks.IRON_BLOCK).strength(2),box(4,0,4,12,29,12)));
    public static final RegistryObject<Block> SHADOW=block("shadow",()->new Party(Kind.SHADOW,BlockBehaviour.Properties.copy(Blocks.BLACK_CARPET).noCollission().noOcclusion().strength(.1f),box(1,0,1,15,1,15)));
    public static final RegistryObject<BlockEntityType<PartyEntity>> ENTITY=ENTITIES.register("party_block",()->BlockEntityType.Builder.of(PartyEntity::new,
            WALL_CLOCK.get(),MIRROR.get(),CHANDELIER.get(),SLOT_MACHINE.get(),BILLIARD_BALL.get(),WISHING_WELL.get(),STATUE.get(),TREE.get(),SANGA_DOLL.get(),
            PHOTO.get(),AUTO_UNIT.get(),SHADOW.get()).build(null));
    private static <T extends Block> RegistryObject<T> block(String name,java.util.function.Supplier<T> factory){
        var b=BLOCKS.register(name,factory);ITEMS.register(name,()->new BlockItem(b.get(),new Item.Properties()));return b;
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
    private static AABB box(double x1,double y1,double z1,double x2,double y2,double z2){return new AABB(x1,y1,z1,x2,y2,z2);}

    public static final String CLOCK="gungang:wall_clock",MIRROR_ID="gungang:mirror",DICE_ID="gungang:dice",CHANDELIER_ID="gungang:chandelier",SLOT="gungang:slot_machine",
            BALL="gungang:billiard_ball",WELL="gungang:wishing_well",STATUE_ID="gungang:embezzlement_statue",TREE_ID="gungang:christmas_tree",SANGA="gungang:sanga_doll",
            PHOTO_ID="gungang:unpleasant_photo",AUTO="gungang:combat_unit",SHADOW_ID="gungang:shadow";

    /**
     * A disguise's full box in blocks around its hider (x/z centred, y from the feet) for the props bigger than their
     * placed cell, or null for the others (their block shape gives it). Faced ones turn with the state.
     */
    public static AABB propBox(String item,BlockState state){
        AABB box=switch(item){
            case CLOCK->box(-24,0,5,24,48,8);
            case CHANDELIER_ID->box(-24,0,-24,24,32,24);
            case WELL->box(-24,0,-24,24,46,24);
            case STATUE_ID->box(-24,0,-24,24,128,24);
            case TREE_ID->box(-22,0,-22,22,63,22);
            // 254 turns with its hider's view, so its box is the same whichever way it faces.
            case PHOTO_ID->box(-7,0,-7,7,22,7);
            default->null;
        };
        if(box==null)return null;
        var facing=state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?state.getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;
        double[] a=turn(box.minX,box.minZ,facing),b=turn(box.maxX,box.maxZ,facing);
        return new AABB(Math.min(a[0],b[0])/16,box.minY/16,Math.min(a[1],b[1])/16,Math.max(a[0],b[0])/16,box.maxY/16,Math.max(a[1],b[1])/16);
    }
    /** Thin disguises whose hider takes the thin side (like a painting), so they back flat against a wall. */
    public static boolean thin(String item){return item.equals(CLOCK)||item.equals(MIRROR_ID);}
    /** x/z about the centre turned for {@code facing}, as the renderer turns the model (north as modelled). */
    static double[] turn(double x,double z,Direction facing){
        return switch(facing){case EAST->new double[]{-z,x};case SOUTH->new double[]{-x,-z};case WEST->new double[]{z,-x};default->new double[]{x,z};};
    }

    /** A block drawn by its block entity; pixel boxes give its (cell-clipped) shape. */
    public static class Party extends BaseEntityBlock {
        public final Kind kind;private final List<AABB> boxes;private final Map<BlockState,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
        Party(Kind kind,Properties properties,AABB... boxes){super(properties);this.kind=kind;this.boxes=List.of(boxes);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PartyEntity(pos,state);}
        Direction facing(BlockState s){return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?s.getValue(BlockStateProperties.HORIZONTAL_FACING):Direction.NORTH;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){
            return shapes.computeIfAbsent(s,state->{
                VoxelShape shape=Shapes.empty();var facing=facing(state);
                for(var b:boxes){
                    double[] p=turn(b.minX-8,b.minZ-8,facing),q=turn(b.maxX-8,b.maxZ-8,facing);
                    double x1=Math.max(-16,Math.min(p[0],q[0])+8),x2=Math.min(32,Math.max(p[0],q[0])+8),z1=Math.max(-16,Math.min(p[1],q[1])+8),z2=Math.min(32,Math.max(p[1],q[1])+8);
                    double y1=Math.max(-16,b.minY),y2=Math.min(32,b.maxY);
                    if(x2>x1&&y2>y1&&z2>z1)shape=Shapes.or(shape,Block.box(x1,y1,z1,x2,y2,z2));
                }
                return shape.optimize();
            });
        }
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator()||!(level.getBlockEntity(pos) instanceof PartyEntity be))return InteractionResult.PASS;
            switch(kind){
                case SLOT->{if(!level.isClientSide){be.spin(level.random,level.getGameTime());level.playSound(null,pos,SoundEvents.UI_LOOM_SELECT_PATTERN,SoundSource.BLOCKS,1,.7f);}}
                case WELL->{if(!level.isClientSide){be.down=!be.down;be.sync();level.playSound(null,pos,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,1,.8f);}}
                default->{return InteractionResult.PASS;}
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void animateTick(BlockState s,Level level,BlockPos pos,RandomSource random){
            if(kind==Kind.CHANDELIER&&random.nextInt(3)==0){
                // A flame flickers on one of the eight outer candles (the chandelier hangs one block below its cell's top).
                double a=random.nextInt(8)*Math.PI/4;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,pos.getX()+.5+Math.cos(a)*1.25,pos.getY()+1.1-1,pos.getZ()+.5+Math.sin(a)*1.25,0,0,0);
            }
        }
    }
    /** Faces the placer (front towards them). */
    public static class Faced extends Party {
        public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
        Faced(Kind kind,Properties properties,AABB... boxes){super(kind,properties,boxes);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState s,Rotation rotation){return s.setValue(FACING,rotation.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror mirror){return s.rotate(mirror.getRotation(s.getValue(FACING)));}
    }
    /** 248: one of the fifteen numbered balls, the white cue ball or the yellow / red carom balls. */
    public static final class Ball extends Party {
        public static final int KINDS=18;
        public static final IntegerProperty BALL=IntegerProperty.create("ball",0,KINDS-1);
        Ball(Properties properties,AABB... boxes){super(Kind.BALL,properties,boxes);registerDefaultState(stateDefinition.any().setValue(BALL,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BALL);}
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResult.PASS;
            if(!level.isClientSide){level.setBlock(pos,s.setValue(BALL,(s.getValue(BALL)+1)%KINDS),3);level.playSound(null,pos,SoundEvents.STONE_BUTTON_CLICK_ON,SoundSource.BLOCKS,1,1.9f);}
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        /** The ball's name: its number, or 흰 공 / 노란 공 / 빨간 공. */
        public static String name(int ball){return ball<15?(ball+1)+"번 공":ball==15?"흰 공":ball==16?"노란 공":"빨간 공";}
    }
    /** 245: a white die showing VALUE on top (a block model per face); right click rolls it. */
    public static final class Dice extends Block {
        public static final IntegerProperty VALUE=IntegerProperty.create("value",1,6);
        private static final VoxelShape SHAPE=Block.box(2,0,2,14,12,14);
        Dice(){super(BlockBehaviour.Properties.copy(Blocks.BONE_BLOCK).strength(.6f).noOcclusion());registerDefaultState(stateDefinition.any().setValue(VALUE,1));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(VALUE);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResult.PASS;
            if(!level.isClientSide){level.setBlock(pos,s.setValue(VALUE,1+level.random.nextInt(6)),3);level.playSound(null,pos,SoundEvents.BONE_BLOCK_HIT,SoundSource.BLOCKS,1,1.4f);}
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** 247 slot symbols, index 0 the jackpot seven. */
    public static final String[] SYMBOLS={"7","$","★","♥","♣"};
    /** A reel: the seven on a quarter of the stops, the others sharing the rest. */
    public static int reel(RandomSource random){int r=random.nextInt(8);return r<2?0:r<4?1:r<6?2:r<7?3:4;}
    /** Ticks a spin lasts; the reels stop one after another over its second half. */
    public static final int SPIN_TICKS=40;

    /**
     * State the renderer reads. Placed: the slot machine's reels and when they started (game time) and whether the
     * well's bucket is down. A disguise's copy (PropEffects) is filled from its prop every frame by {@link #fromProp}.
     */
    public static final class PartyEntity extends BlockEntity {
        public int[] reels={0,1,2};public long spinAt=-1000;public boolean down;
        /** Client only: the bucket's shown depth (0 up, 1 down), the lights, the disguise's yaw and pose, the ball's roll. */
        public float bucket;public boolean lightsOff,disguise;public float yaw;public int mode;public int ball=-1;
        public final org.joml.Quaternionf roll=new org.joml.Quaternionf();public Vec3 last;
        /** 255: the skin it wears (a seeker's, as the mannequin), or null for the default. */
        public java.util.UUID skin;
        public PartyEntity(BlockPos pos,BlockState state){super(ENTITY.get(),pos,state);}
        public Kind kind(){return getBlockState().getBlock() instanceof Party p?p.kind:Kind.MIRROR;}
        void spin(RandomSource random,long time){for(int i=0;i<3;i++)reels[i]=reel(random);spinAt=time;sync();}
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        /** A disguise's look from its prop entity: TEXT carries the slot's "a,b,c,start", OPEN the bucket / lights off, MODE the doll's pose. */
        public void fromProp(PropEntity prop,float partial){
            disguise=true;mode=prop.mode();skin=prop.skin();yaw=net.minecraft.util.Mth.rotLerp(partial,prop.yRotO,prop.getYRot());
            down=prop.open();lightsOff=prop.open();
            if(kind()==Kind.SLOT){
                String[] part=prop.text().split(",");
                if(part.length==4)try{for(int i=0;i<3;i++)reels[i]=Math.floorMod(Integer.parseInt(part[i]),SYMBOLS.length);spinAt=Long.parseLong(part[3]);}catch(NumberFormatException ignored){}
            }
            if(kind()==Kind.BALL){
                var at=prop.getPosition(partial);
                if(last!=null){
                    var move=at.subtract(last);double d=Math.sqrt(move.x*move.x+move.z*move.z);
                    // Rolling without slipping: about the axis up × motion, by distance over the radius (6 px).
                    if(d>1e-4&&d<2)roll.premul(new org.joml.Quaternionf().rotateAxis((float)(d/(6/16d)),(float)(move.z/d),0,(float)(-move.x/d)));
                }
                last=at;
            }
        }
        @Override public AABB getRenderBoundingBox(){
            return switch(kind()){
                case STATUE->new AABB(worldPosition).inflate(2,9,2);
                case CLOCK,CHANDELIER,WELL,TREE->new AABB(worldPosition).inflate(2,4,2);
                default->new AABB(worldPosition).inflate(1,2,1);
            };
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putIntArray("Reels",reels);tag.putBoolean("Down",down);}
        @Override public void load(CompoundTag tag){super.load(tag);var r=tag.getIntArray("Reels");if(r.length==3)reels=r;down=tag.getBoolean("Down");if(tag.contains("SpinAt"))spinAt=tag.getLong("SpinAt");}
        @Override public CompoundTag getUpdateTag(){var tag=super.getUpdateTag();tag.putIntArray("Reels",reels);tag.putBoolean("Down",down);tag.putLong("SpinAt",spinAt);return tag;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
    private PartyBlocks(){}
}

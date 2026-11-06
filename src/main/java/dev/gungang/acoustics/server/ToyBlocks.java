package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Prop hunt's toy blocks that are also real, placeable blocks: 224 aquarium, 231 chameleon block, 232 capsule machine,
 * 234 error block, 235 garden fountain, 239 nuke warhead and 241 easel.
 */
public final class ToyBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    public static final RegistryObject<Aquarium> AQUARIUM=block("aquarium",Aquarium::new);
    public static final RegistryObject<Block> CHAMELEON=block("chameleon_block",()->new Block(BlockBehaviour.Properties.copy(Blocks.MOSS_BLOCK).strength(1)));
    // 232 capsule machine is CapsuleMachine (the lobby machine that opens the prop list), two blocks tall.
    public static final RegistryObject<Block> ERROR=block("error_block",()->new Block(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Fountain> FOUNTAIN=block("garden_fountain",Fountain::new);
    public static final RegistryObject<Nuke> NUKE=block("nuke_warhead",Nuke::new);
    public static final RegistryObject<Easel> EASEL=block("easel",Easel::new);
    public static final RegistryObject<BlockEntityType<AquariumEntity>> AQUARIUM_ENTITY=ENTITIES.register("aquarium",()->BlockEntityType.Builder.of(AquariumEntity::new,AQUARIUM.get()).build(null));
    public static final RegistryObject<BlockEntityType<NukeEntity>> NUKE_ENTITY=ENTITIES.register("nuke_warhead",()->BlockEntityType.Builder.of(NukeEntity::new,NUKE.get()).build(null));
    public static final RegistryObject<BlockEntityType<EaselEntity>> EASEL_ENTITY=ENTITIES.register("easel",()->BlockEntityType.Builder.of(EaselEntity::new,EASEL.get()).build(null));
    private static <T extends Block> RegistryObject<T> block(String name,java.util.function.Supplier<T> factory){
        var b=BLOCKS.register(name,factory);ITEMS.register(name,()->new BlockItem(b.get(),new Item.Properties()));return b;
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}

    // ---- 224 aquarium -------------------------------------------------------------------------------------------

    /**
     * A glass tank of water. Neighbouring tanks merge (no glass or water faces between them). Its contents live in the
     * state, so a disguise carries them too: SUBSTRATE (0 none, 1 sand, 2 gravel), ROCK, PLANT and FISH
     * (0 none, 1 cod, 2 salmon, 3 tropical fish, 4 pufferfish, 5 small squid). Right click with sand or gravel, a stone,
     * seagrass or kelp, a fish bucket, a fish or squid spawn egg (display only, nothing spawns) or an ink sac to furnish it; an empty bucket takes the fish back out; an empty hand
     * while sneaking clears it.
     */
    public static final class Aquarium extends AbstractGlassBlock implements EntityBlock {
        public static final IntegerProperty SUBSTRATE=IntegerProperty.create("substrate",0,2),FISH=IntegerProperty.create("fish",0,5);
        public static final BooleanProperty ROCK=BooleanProperty.create("rock"),PLANT=BooleanProperty.create("plant");
        Aquarium(){super(BlockBehaviour.Properties.copy(Blocks.GLASS).lightLevel(s->0));
            registerDefaultState(stateDefinition.any().setValue(SUBSTRATE,0).setValue(FISH,0).setValue(ROCK,false).setValue(PLANT,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SUBSTRATE,FISH,ROCK,PLANT);}
        @Override public boolean skipRendering(BlockState state,BlockState next,Direction side){return next.is(this)||super.skipRendering(state,next,side);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AquariumEntity(pos,state);}
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(player.isSpectator())return InteractionResult.PASS;
            ItemStack stack=player.getItemInHand(hand);BlockState next=s;ItemStack back=null;boolean consume=false;
            if(stack.is(Items.SAND)||stack.is(Items.RED_SAND)){next=s.setValue(SUBSTRATE,1);consume=true;}
            else if(stack.is(Items.GRAVEL)){next=s.setValue(SUBSTRATE,2);consume=true;}
            else if(stack.is(Items.COBBLESTONE)||stack.is(Items.STONE)||stack.is(Items.MOSSY_COBBLESTONE)||stack.is(Items.ANDESITE)||stack.is(Items.GRANITE)||stack.is(Items.DIORITE)||stack.is(Items.TUFF)){next=s.setValue(ROCK,true);consume=true;}
            else if(stack.is(Items.SEAGRASS)||stack.is(Items.KELP)){next=s.setValue(PLANT,true);consume=true;}
            else if(fish(stack)>0){next=s.setValue(FISH,fish(stack));back=new ItemStack(Items.WATER_BUCKET);}
            else if(egg(stack)>0){next=s.setValue(FISH,egg(stack));consume=true;}
            else if(stack.is(Items.INK_SAC)||stack.is(Items.GLOW_INK_SAC)){next=s.setValue(FISH,5);consume=true;}
            else if(stack.is(Items.BUCKET)&&s.getValue(FISH)>=1&&s.getValue(FISH)<=4){back=new ItemStack(bucket(s.getValue(FISH)));next=s.setValue(FISH,0);}
            else if(stack.isEmpty()&&player.isShiftKeyDown())next=defaultBlockState();
            if(next==s)return InteractionResult.PASS;
            if(level.isClientSide)return InteractionResult.SUCCESS;
            level.setBlock(pos,next,3);
            level.playSound(null,pos,back!=null?SoundEvents.BUCKET_EMPTY_FISH:SoundEvents.GENERIC_SPLASH,SoundSource.BLOCKS,.6f,1.2f);
            if(!player.getAbilities().instabuild){
                if(back!=null){stack.shrink(1);if(stack.isEmpty())player.setItemInHand(hand,back);else if(!player.getInventory().add(back))player.drop(back,false);}
                else if(consume)stack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        /** A fish or squid spawn egg only sets the tank's display fish: no entity is spawned (servers may block mob spawning). */
        private static int egg(ItemStack stack){
            return stack.is(Items.COD_SPAWN_EGG)?1:stack.is(Items.SALMON_SPAWN_EGG)?2:stack.is(Items.TROPICAL_FISH_SPAWN_EGG)?3
                    :stack.is(Items.PUFFERFISH_SPAWN_EGG)?4:stack.is(Items.SQUID_SPAWN_EGG)||stack.is(Items.GLOW_SQUID_SPAWN_EGG)?5:0;
        }
        private static int fish(ItemStack stack){return stack.is(Items.COD_BUCKET)?1:stack.is(Items.SALMON_BUCKET)?2:stack.is(Items.TROPICAL_FISH_BUCKET)?3:stack.is(Items.PUFFERFISH_BUCKET)?4:0;}
        private static Item bucket(int fish){return switch(fish){case 1->Items.COD_BUCKET;case 2->Items.SALMON_BUCKET;case 3->Items.TROPICAL_FISH_BUCKET;default->Items.PUFFERFISH_BUCKET;};}
    }
    /** Holds nothing: the tank's water, contents and fish are drawn by AquariumRenderer from the state. */
    public static final class AquariumEntity extends BlockEntity {
        public AquariumEntity(BlockPos pos,BlockState state){super(AQUARIUM_ENTITY.get(),pos,state);}
    }

    // ---- 235 garden fountain ------------------------------------------------------------------------------------

    /**
     * A three-tier sandstone garden fountain on a 2x2 footprint, two blocks tall: octagonal plinth, pedestal, wide basin,
     * middle and top bowls and a pineapple finial, with water spilling from the bowls. The anchor (part 0) is the
     * north-west bottom cell; the fountain's centre is the anchor's south-east corner.
     */
    public static final class Fountain extends MultiPartBlock {
        /** Outline in pixels from the anchor cell (centre at 16,16). */
        private static final java.util.List<net.minecraft.world.phys.AABB> OUTLINE=java.util.List.of(
                new net.minecraft.world.phys.AABB(1,0,1,31,1.5,31),new net.minecraft.world.phys.AABB(10,1.5,10,22,11,22),
                new net.minecraft.world.phys.AABB(1,11,1,31,14.5,31),new net.minecraft.world.phys.AABB(12.5,14.5,12.5,19.5,19,19.5),
                new net.minecraft.world.phys.AABB(7,19,7,25,22,25),new net.minecraft.world.phys.AABB(11,22,11,21,27,21),
                new net.minecraft.world.phys.AABB(14,27,14,18,32,18));
        Fountain(){super(BlockBehaviour.Properties.copy(Blocks.SANDSTONE).strength(1.5f,6).noOcclusion());registerDefaultState(stateDefinition.any().setValue(PART,0));}
        @Override public int partCount(){return 8;}
        @Override protected BlockPos cell(int part){return new BlockPos(part&1,part>>2,(part>>1)&1);}
        @Override protected java.util.List<net.minecraft.world.phys.AABB> outline(){return OUTLINE;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return null;}
        @Override public void animateTick(BlockState s,Level level,BlockPos pos,RandomSource random){
            if(!isAnchor(s))return;
            double cx=pos.getX()+1,cz=pos.getZ()+1;
            // A bubbling jet on the finial, and water spilling over each bowl's rim.
            if(random.nextInt(2)==0)level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,cx+(random.nextDouble()-.5)*.15,pos.getY()+2,cz+(random.nextDouble()-.5)*.15,0,.05,0);
            double[][] rims={{5/16.,27/16.},{9/16.,22/16.},{15/16.,14.5/16}};
            for(var rim:rims)if(random.nextInt(2)==0){
                double angle=random.nextDouble()*Math.PI*2;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_WATER,cx+Math.cos(angle)*rim[0],pos.getY()+rim[1],cz+Math.sin(angle)*rim[0],0,0,0);
            }
        }
    }

    // ---- 239 nuke warhead ---------------------------------------------------------------------------------------

    /** A 2x3x2 warhead, drawn by NukeRenderer; the placed block itself keeps a one-block footprint. */
    public static final class Nuke extends BaseEntityBlock {
        Nuke(){super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5,1200).noOcclusion());}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NukeEntity(pos,state);}
    }
    public static final class NukeEntity extends BlockEntity {
        public NukeEntity(BlockPos pos,BlockState state){super(NUKE_ENTITY.get(),pos,state);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(1,3,1);}
    }

    // ---- 241 easel ----------------------------------------------------------------------------------------------

    /**
     * A wooden easel two blocks wide and two tall whose 16x16 canvas anyone can paint (right click on any part opens the
     * painting screen). The anchor (part 0) is the bottom cell on the model's left (-x); the canvas lives in its block entity.
     */
    public static final class Easel extends MultiPartBlock {
        public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
        private static final java.util.List<net.minecraft.world.phys.AABB> OUTLINE=java.util.List.of(new net.minecraft.world.phys.AABB(2,0,3.5,30,32,12.5));
        Easel(){super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0));}
        @Override protected boolean faced(){return true;}
        @Override public int partCount(){return 4;}
        @Override protected BlockPos cell(int part){return new BlockPos(part&1,part>>1,0);}
        @Override protected java.util.List<net.minecraft.world.phys.AABB> outline(){return OUTLINE;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return isAnchor(state)?new EaselEntity(pos,state):null;}
        /** Client hook (set by ClientSetup): opens the painting screen for a placed easel. */
        public static java.util.function.BiConsumer<BlockPos,String> clientPaint=(pos,pixels)->{};
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResult.PASS;
            var root=anchor(pos,s);
            if(level.isClientSide&&level.getBlockEntity(root) instanceof EaselEntity be)clientPaint.accept(root,be.pixels);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    /** The canvas: 256 palette indices as hex digits, row by row from the top left ("" is a blank canvas). */
    public static final class EaselEntity extends BlockEntity {
        public String pixels="";
        public EaselEntity(BlockPos pos,BlockState state){super(EASEL_ENTITY.get(),pos,state);}
        /** The canvas spans the easel's two-by-two front. */
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
        public void paint(String value){pixels=canvas(value);setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putString("Pixels",pixels);}
        @Override public void load(CompoundTag tag){super.load(tag);pixels=canvas(tag.getString("Pixels"));}
        @Override public CompoundTag getUpdateTag(){var tag=super.getUpdateTag();tag.putString("Pixels",pixels);return tag;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
    /** Canvas size in pixels per side, and its 16-colour palette (index 0 is the blank white). */
    public static final int CANVAS=16;
    public static final int[] PALETTE={0xFFFFFF,0x1D1D21,0x9D9D97,0x474F52,0xB02E26,0xF9801D,0xFED83D,0x80C71F,
            0x5E7C16,0x3AB3DA,0x3C44AA,0x8932B8,0xC74EBD,0xF38BAA,0x835432,0xF5D7A8};
    /** A valid canvas string (256 hex digits), or "" for anything else. */
    public static String canvas(String value){
        if(value==null||value.length()!=CANVAS*CANVAS)return "";
        for(int i=0;i<value.length();i++)if(Character.digit(value.charAt(i),16)<0)return "";
        return value.toLowerCase(java.util.Locale.ROOT);
    }
    private ToyBlocks(){}
}

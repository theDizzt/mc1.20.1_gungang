package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.registries.*;

/** Backported decorative props; these never run vanilla command/spawning logic. */
public final class PropExtraBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    public static final RegistryObject<Block> MANNEQUIN=block("mannequin",()->new ShapeBlock(Properties.copy(Blocks.OAK_PLANKS),Block.box(4,0,4,12,29,12)));
    /** 184·191: mannequins carrying a fixed skin (PropObjectRenderer) and an ability on the attack key (PropAbilities). */
    public static final RegistryObject<Block> EUNBI_DOLL=block("eunbi_doll",()->new ShapeBlock(Properties.copy(Blocks.OAK_PLANKS),Block.box(4,0,4,12,29,12)));
    public static final RegistryObject<Block> HANBI_DOLL=block("hanbi_doll",()->new ShapeBlock(Properties.copy(Blocks.OAK_PLANKS),Block.box(4,0,4,12,29,12)));
    /** 187: a full block whose animated texture flows through pastel rainbow colours. */
    public static final RegistryObject<Block> PRETTIEST_BLOCK=block("prettiest_block",()->new Block(Properties.copy(Blocks.WHITE_CONCRETE)));
    public static final RegistryObject<Block> DRIED_GHAST=block("dried_ghast",()->new ShapeBlock(Properties.copy(Blocks.SOUL_SAND).noOcclusion(),Block.box(3,0,3,13,10,13)));
    /** Twice a grass block in every direction, centred on its cell so a disguise stays centred on the hider. */
    public static final RegistryObject<Block> BIG_GRASS_BLOCK=block("big_grass_block",()->new ShapeBlock(Properties.copy(Blocks.GRASS_BLOCK).noOcclusion(),Block.box(-8,0,-8,24,32,24)));
    public static final RegistryObject<Block> WILDFLOWERS=block("wildflowers",()->new ShapeBlock(Properties.copy(Blocks.PINK_PETALS).noCollission().noOcclusion(),Block.box(0,0,0,16,3,16)));
    /** Death zone marker (DeathZoneBlock): invisible except to whoever holds one; kills match participants who touch it. */
    public static final RegistryObject<Block> DEATH_ZONE=block("death_zone",DeathZoneBlock::new);
    /** Everlasting snow: carpet-thin snow with no random tick, so neither light nor weather ever melts it. */
    public static final RegistryObject<Block> SNOW_CARPET=block("snow_carpet",()->new CarpetBlock(Properties.of().mapColor(net.minecraft.world.level.material.MapColor.SNOW)
            .strength(.1f).sound(SoundType.SNOW).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    private static RegistryObject<Block> block(String id,java.util.function.Supplier<Block> supplier){var result=BLOCKS.register(id,supplier);ITEMS.register(id,()->new BlockItem(result.get(),new Item.Properties()));return result;}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    /**
     * Figures and decorations with a front (mannequin, dolls, dried ghast ...) face whoever places them. rotate() is left
     * as vanilla's no-op on purpose: a prop hunt disguise turns by its own rotation, not by this state.
     */
    private static final class ShapeBlock extends Block {
        static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        private final VoxelShape shape;
        ShapeBlock(Properties properties,VoxelShape shape){super(properties);this.shape=shape;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH));}
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape;}
        @Override public void animateTick(BlockState s,net.minecraft.world.level.Level l,BlockPos p,net.minecraft.util.RandomSource r){
            if(this==DRIED_GHAST.get()){l.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,p.getX()+.2+r.nextDouble()*.6,p.getY()+.55,p.getZ()+.2+r.nextDouble()*.6,0,0,0);if(r.nextInt(3)==0)l.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,p.getX()+.2+r.nextDouble()*.6,p.getY()+.6,p.getZ()+.2+r.nextDouble()*.6,0,0,0);}
        }
    }
    private PropExtraBlocks(){}
}

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
    public static final RegistryObject<Block> DRIED_GHAST=block("dried_ghast",()->new ShapeBlock(Properties.copy(Blocks.SOUL_SAND).noOcclusion(),Block.box(3,0,3,13,10,13)));
    public static final RegistryObject<Block> WILDFLOWERS=block("wildflowers",()->new ShapeBlock(Properties.copy(Blocks.PINK_PETALS).noCollission().noOcclusion(),Block.box(0,0,0,16,3,16)));
    private static RegistryObject<Block> block(String id,java.util.function.Supplier<Block> supplier){var result=BLOCKS.register(id,supplier);ITEMS.register(id,()->new BlockItem(result.get(),new Item.Properties()));return result;}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    private static final class ShapeBlock extends Block {
        private final VoxelShape shape;
        ShapeBlock(Properties properties,VoxelShape shape){super(properties);this.shape=shape;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape;}
        @Override public void animateTick(BlockState s,net.minecraft.world.level.Level l,BlockPos p,net.minecraft.util.RandomSource r){
            if(this==DRIED_GHAST.get()){l.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,p.getX()+.2+r.nextDouble()*.6,p.getY()+.55,p.getZ()+.2+r.nextDouble()*.6,0,0,0);if(r.nextInt(3)==0)l.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,p.getX()+.2+r.nextDouble()*.6,p.getY()+.6,p.getZ()+.2+r.nextDouble()*.6,0,0,0);}
        }
    }
    private PropExtraBlocks(){}
}

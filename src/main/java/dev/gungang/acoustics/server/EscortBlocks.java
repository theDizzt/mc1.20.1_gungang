package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

public final class EscortBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"gungang");
    public static final RegistryObject<Marker> PATH=marker("escort_path",0),START=marker("escort_start",1),END=marker("escort_end",2),CHECKPOINT=marker("escort_checkpoint",3);
    public static final RegistryObject<EntityType<EscortCart>> CART=ENTITIES.register("escort_cart",()->EntityType.Builder.<EscortCart>of(EscortCart::new,MobCategory.MISC).sized(3,2.5f).clientTrackingRange(16).updateInterval(1).build("gungang:escort_cart"));
    public static final RegistryObject<EntityType<EscortRail>> RAIL=ENTITIES.register("escort_rail",()->EntityType.Builder.<EscortRail>of(EscortRail::new,MobCategory.MISC).sized(3,.15f).clientTrackingRange(12).updateInterval(20).build("gungang:escort_rail"));
    private static RegistryObject<Marker> marker(String name,int kind){var b=BLOCKS.register(name,()->new Marker(kind));ITEMS.register(name,()->new BlockItem(b.get(),new Item.Properties()));return b;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
    public static final class Marker extends Block {
        public static final BooleanProperty HIDDEN=BooleanProperty.create("hidden");
        public final int kind;
        Marker(int kind){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());this.kind=kind;registerDefaultState(stateDefinition.any().setValue(HIDDEN,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HIDDEN);}
        @Override public RenderShape getRenderShape(BlockState state){return state.getValue(HIDDEN)?RenderShape.INVISIBLE:RenderShape.MODEL;}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return state.getValue(HIDDEN)?net.minecraft.world.phys.shapes.Shapes.empty():net.minecraft.world.phys.shapes.Shapes.block();}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
            if(!level.isClientSide&&old.getBlock()!=this)EscortTracks.changed((net.minecraft.server.level.ServerLevel)level,pos,true);
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!level.isClientSide&&next.getBlock()!=this)EscortTracks.changed((net.minecraft.server.level.ServerLevel)level,pos,false);
            super.onRemove(state,level,pos,next,moving);
        }
    }
    private EscortBlocks(){}
}

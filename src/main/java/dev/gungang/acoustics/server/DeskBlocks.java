package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 221 desk clock: a placeable LCD clock showing the real time (24-hour HH:MM:SS, DeskClockRenderer) whose alarm beeps
 * while ALARM is on (right-click, or E as a disguise). 222 money cart: a hand cart loaded with cash.
 */
public final class DeskBlocks {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"gungang");
    public static final RegistryObject<Clock> CLOCK=BLOCKS.register("desk_clock",Clock::new);
    public static final RegistryObject<Cart> CART=BLOCKS.register("money_cart",Cart::new);
    static{
        ITEMS.register("desk_clock",()->new BlockItem(CLOCK.get(),new Item.Properties()));
        ITEMS.register("money_cart",()->new BlockItem(CART.get(),new Item.Properties()));
    }
    public static final RegistryObject<BlockEntityType<ClockEntity>> CLOCK_ENTITY=ENTITIES.register("desk_clock",()->BlockEntityType.Builder.of(ClockEntity::new,CLOCK.get()).build(null));
    public static final RegistryObject<SoundEvent> ALARM=sound("clock_alarm"),CASH=sound("cash_machine");
    private static RegistryObject<SoundEvent> sound(String name){return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation("gungang",name)));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);SOUNDS.register(bus);}

    /** One alarm burst every this many ticks (the sound is four beeps and a pause). */
    public static final int ALARM_PERIOD=20;
    public static void beep(Level level,double x,double y,double z){level.playSound(null,x,y,z,ALARM.get(),SoundSource.BLOCKS,1,1);}

    public static final class Clock extends BaseEntityBlock {
        public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
        public static final BooleanProperty ALARM=BooleanProperty.create("alarm");
        private static final VoxelShape[] SHAPES={Block.box(2,0,5,14,7,11),Block.box(5,0,2,11,7,14)};
        Clock(){super(Properties.copy(Blocks.IRON_BLOCK).strength(1,3).noOcclusion().lightLevel(s->4));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ALARM,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b){b.add(FACING,ALARM);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[s.getValue(FACING).getAxis()==Direction.Axis.Z?0:1];}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ClockEntity(p,s);}
        @Override public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator())return InteractionResult.PASS;
            if(level.isClientSide)return InteractionResult.SUCCESS;
            boolean on=!s.getValue(ALARM);level.setBlock(pos,s.setValue(ALARM,on),3);
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,SoundSource.BLOCKS,.5f,1.6f);
            if(on)level.scheduleTick(pos,this,1);
            return InteractionResult.CONSUME;
        }
        @Override public void tick(BlockState s,ServerLevel level,BlockPos pos,RandomSource random){
            if(!s.getValue(ALARM))return;
            beep(level,pos.getX()+.5,pos.getY()+.3,pos.getZ()+.5);level.scheduleTick(pos,this,ALARM_PERIOD);
        }
    }
    /** Holds nothing; it only gives the clock face its renderer. */
    public static final class ClockEntity extends BlockEntity {
        public ClockEntity(BlockPos pos,BlockState state){super(CLOCK_ENTITY.get(),pos,state);}
    }
    public static final class Cart extends HorizontalDirectionalBlock {
        private static final VoxelShape[] SHAPES={Block.box(2,0,0,14,12,16),Block.box(0,0,2,16,12,14)};
        Cart(){super(Properties.copy(Blocks.OAK_PLANKS).strength(1,3).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[s.getValue(FACING).getAxis()==Direction.Axis.Z?0:1];}
    }
    private DeskBlocks(){}
}

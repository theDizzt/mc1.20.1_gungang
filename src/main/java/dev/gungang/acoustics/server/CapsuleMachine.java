package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Capsule machine (gungang:capsule_machine, also prop hunt's 232 disguise): two blocks tall (cabinet below, glass dome of capsules above). Using either half
 * outside a match opens the prop list (/gungang prop list), whose disguises end when a match starts or the lobby resets.
 */
public final class CapsuleMachine extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER=Block.box(2,0,2,14,16,14);
    private static final VoxelShape UPPER=Shapes.or(Block.box(2.5,0,2.5,13.5,11,13.5),Block.box(2,11,2,14,14,14));

    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    public static final RegistryObject<CapsuleMachine> BLOCK=BLOCKS.register("capsule_machine",CapsuleMachine::new);
    static{ITEMS.register("capsule_machine",()->new BlockItem(BLOCK.get(),new Item.Properties()));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}

    private CapsuleMachine(){
        super(Properties.copy(Blocks.IRON_BLOCK).strength(-1,3600000).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,HALF);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(HALF)==DoubleBlockHalf.LOWER?LOWER:UPPER;}

    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var pos=context.getClickedPos();var level=context.getLevel();
        if(pos.getY()>=level.getMaxBuildHeight()-1||!level.getBlockState(pos.above()).canBeReplaced(context))return null;
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),3);
    }
    /** Either half goes when the other one does, like a door. */
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos){
        var half=state.getValue(HALF);
        if(direction.getAxis()==Direction.Axis.Y&&(half==DoubleBlockHalf.LOWER)==(direction==Direction.UP)
                &&!(neighbor.is(this)&&neighbor.getValue(HALF)!=half))return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    /** Broken from the top in creative: clear the bottom without a drop (only the bottom half has a loot table). */
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide&&player.isCreative()&&state.getValue(HALF)==DoubleBlockHalf.UPPER){
            var below=pos.below();var other=level.getBlockState(below);
            if(other.is(this)&&other.getValue(HALF)==DoubleBlockHalf.LOWER)level.setBlock(below,Blocks.AIR.defaultBlockState(),35);
        }
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}

    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer p){
            level.playSound(null,pos,net.minecraft.sounds.SoundEvents.UI_STONECUTTER_TAKE_RESULT,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.4f);
            LobbyManager.openPropList(p);
        }
        return InteractionResult.CONSUME;
    }
}
